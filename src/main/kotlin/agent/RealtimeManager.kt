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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

class RealtimeManager {
    private val schedulerManager = getSchedulerManager()
    private val supabase = createSupabaseClient(Config.supabaseUrl, Config.supabaseKey) {
        install(Realtime)
        install(Postgrest)
    }
    private val jsonDecoder = Json { ignoreUnknownKeys = true }

    suspend fun connectAndListen() {
        println("Conectando ao Supabase Realtime...")
        val channel = supabase.channel("schedules-channel")

        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "agendamentos"
        }.filter { change ->
            // Filtra baseado no tipo de ação e verifica o client_id
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
        }.onEach { change ->
            println("Mudança recebida do Supabase: $change")

            when (change) {
                is PostgresAction.Insert, is PostgresAction.Update -> {
                    val payload = jsonDecoder.decodeFromJsonElement(SchedulePayload.serializer(), change.record)
                    println("Ação: Criar/Atualizar tarefa '${payload.schedule_name}'")
                    schedulerManager.createOrUpdateTask(payload)
                }
                is PostgresAction.Delete -> {
                    val scheduleName = change.oldRecord["schedule_name"]?.jsonPrimitive?.content
                    if (scheduleName != null) {
                        println("Ação: Deletar tarefa '$scheduleName'")
                        schedulerManager.deleteTask(scheduleName)
                    }
                }
                else -> println("Ação desconhecida ou não tratada.")
            }
        }.launchIn(CoroutineScope(Dispatchers.Default))

        supabase.realtime.connect()
        channel.subscribe()
        println("Inscrito com sucesso no canal de agendamentos para o cliente: ${Config.clientId}")
    }
}