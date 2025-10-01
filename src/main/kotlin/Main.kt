package com.waldirbaia

import com.waldirbaia.agent.Config
import com.waldirbaia.agent.RealtimeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("Iniciando Agente de Backup (Modo Supabase Realtime)...")
    println("ID do Cliente: ${Config.clientId}")

    val manager = RealtimeManager()
    manager.connectAndListen()

    // Mantém a aplicação principal rodando indefinidamente para
    // que a escuta em segundo plano não seja interrompida.
    delay(Long.MAX_VALUE)
}