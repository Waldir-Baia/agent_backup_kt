package com.waldirbaia.agent

import com.waldirbaia.models.SchedulePayload
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter // Garanta que esta importação exista
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

class RealtimeManager {
    private val logger = LoggerFactory.getLogger(RealtimeManager::class.java)

    // Mantém a lógica híbrida para Windows/Linux
    private val osActionHandler = getOsActionHandler()

    private val supabase = createSupabaseClient(Config.supabaseUrl, Config.supabaseKey) {
        install(Realtime)
        install(Postgrest)
    }
    private val jsonDecoder = Json { ignoreUnknownKeys = true }

    suspend fun connectAndListen() {
        logger.info("Conectando ao Supabase Realtime...")
        val channel = supabase.channel("agendamentos-channel")

        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "agendamentos"
        }
            // AJUSTE AQUI 👇: Usando o filtro no lado do cliente, como você pediu.
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
                logger.info("Mudança recebida e filtrada para este cliente: $change")

                when (change) {
                    is PostgresAction.Insert, is PostgresAction.Update -> {
                        val payload = jsonDecoder.decodeFromJsonElement(SchedulePayload.serializer(), change.record)

                        // Mantém a lógica que verifica se é Windows ou Linux
                        when (osActionHandler) {
                            is CommandExecutor -> { // Se for Windows, executa imediatamente
                                logger.info("Disparando execução imediata para '${payload.schedule_name}'")
                                osActionHandler.executeRcloneCommand(payload)
                            }
                            is SchedulerManager -> { // Se for Linux, agenda a tarefa
                                logger.info("Disparando criação/atualização de tarefa para '${payload.schedule_name}'")
                                osActionHandler.createOrUpdateTask(payload)
                            }
                        }
                    }

                    is PostgresAction.Delete -> {
                        // A exclusão de tarefa só é relevante para o Linux
                        if (osActionHandler is SchedulerManager) {
                            val scheduleName = change.oldRecord["schedule_name"]?.jsonPrimitive?.content
                            if (scheduleName != null) {
                                logger.info("Disparando exclusão de tarefa para '$scheduleName'")
                                osActionHandler.deleteTask(scheduleName)
                            }
                        } else {
                            logger.info("Evento de exclusão recebido (Windows). Nenhuma ação local necessária.")
                        }
                    }
                    else -> logger.warn("Ação desconhecida ou não tratada.")
                }
            }.launchIn(CoroutineScope(Dispatchers.Default))

        supabase.realtime.connect()
        channel.subscribe()
        logger.info("Inscrito com sucesso no canal 'agendamentos'. Aguardando notificações...")
    }
}