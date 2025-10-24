package com.waldirbaia.agent

import com.waldirbaia.models.ExecutionPayload
import com.waldirbaia.models.SchedulePayload
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

class RealtimeManager {
    private val logger = LoggerFactory.getLogger(RealtimeManager::class.java)
    private val commandExecutor = getCommandExecutor()
    private val schedulerManager = getSchedulerManager()

    // Json único e estável (bom para native-image)
    private val supabaseJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    // Supabase client configurado com KotlinXSerializer
    private val supabase = createSupabaseClient(Config.supabaseUrl, Config.supabaseKey) {
        // Aplica o serializer por padrão para todos os módulos (Postgrest/Realtime)
        defaultSerializer = KotlinXSerializer(supabaseJson)

        install(Postgrest) // sem serializer custom adicional
        install(Realtime)  // config padrão; adicione opções se precisar
    }

    // Use o mesmo Json para decodificação dos payloads do Realtime
    private val jsonDecoder = supabaseJson

    private val executionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun connectAndListen() {
        connectExecutionsRealtime()
        connectSchedulesRealtime()
    }

    // Canal 1: Execuções Imediatas
    private suspend fun connectExecutionsRealtime() {
        logger.info("🔌 Conectando: execucoes_realtime...")
        val channel = supabase.channel("execucoes-realtime-channel")

        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "execucoes_realtime"
        }
            .filter { change ->
                when (change) {
                    is PostgresAction.Insert -> {
                        val clientId = change.record["client_id"]?.jsonPrimitive?.content
                        clientId == Config.clientId
                    }
                    else -> false
                }
            }
            .onEach { change ->
                executionScope.launch {
                    try {
                        processImmediateCommand(change)
                    } catch (e: Exception) {
                        logger.error("❌ Erro: ${e.message}", e)
                    }
                }
            }
            .launchIn(CoroutineScope(Dispatchers.Default))

        channel.subscribe()
        logger.info("✅ Canal 'execucoes_realtime' ativo")
    }

    // Canal 2: Agendamentos
    private suspend fun connectSchedulesRealtime() {
        logger.info("🔌 Conectando: agendamentos...")
        val channel = supabase.channel("agendamentos-channel")

        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "agendamentos"
        }
            .filter { change ->
                when (change) {
                    is PostgresAction.Insert, is PostgresAction.Update -> {
                        val clientId = change.record["client_id"]?.jsonPrimitive?.content
                        clientId == Config.clientId
                    }
                    is PostgresAction.Delete -> {
                        val clientId = change.oldRecord["client_id"]?.jsonPrimitive?.content
                        clientId == Config.clientId
                    }
                    else -> false
                }
            }
            .onEach { change ->
                try {
                    processScheduleChange(change)
                } catch (e: Exception) {
                    logger.error("❌ Erro ao processar agendamento: ${e.message}", e)
                }
            }
            .launchIn(CoroutineScope(Dispatchers.Default))

        channel.subscribe()
        logger.info("✅ Canal 'agendamentos' ativo (cron interno)")
    }

    private suspend fun processImmediateCommand(change: PostgresAction) {
        if (change !is PostgresAction.Insert) return

        val executionData = jsonDecoder.decodeFromJsonElement(
            ExecutionPayload.serializer(),
            change.record
        )

        val payload = SchedulePayload(
            schedule_name = executionData.nome_tarefa,
            rclone_command = executionData.comando,
            cron_expression = "* * * * *",
            is_active = true
        )

        logger.info("🚀 Execução imediata: '${payload.schedule_name}'")
        commandExecutor.executeRcloneCommand(payload)
    }

    private fun processScheduleChange(change: PostgresAction) {
        when (change) {
            is PostgresAction.Insert -> {
                val payload = jsonDecoder.decodeFromJsonElement(SchedulePayload.serializer(), change.record)
                logger.info("➕ Novo agendamento: ${payload.schedule_name}")
                schedulerManager.createOrUpdateTask(payload)
            }
            is PostgresAction.Update -> {
                val payload = jsonDecoder.decodeFromJsonElement(SchedulePayload.serializer(), change.record)
                logger.info("🔄 Agendamento atualizado: ${payload.schedule_name}")
                schedulerManager.createOrUpdateTask(payload)
            }
            is PostgresAction.Delete -> {
                val scheduleName = change.oldRecord["schedule_name"]?.jsonPrimitive?.content
                if (scheduleName != null) {
                    logger.info("🗑️ Removendo: $scheduleName")
                    schedulerManager.deleteTask(scheduleName)
                }
            }
            is PostgresAction.Select -> {
                // normalmente não precisa tratar
            }
        }
    }

    fun shutdown() {
        executionScope.cancel()
        schedulerManager.shutdown()
    }
}
