package com.waldirbaia

import com.waldirbaia.agent.Config
import com.waldirbaia.agent.RealtimeManager
import com.waldirbaia.models.BackupMonitor
import com.waldirbaia.models.ClientInfo
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Count
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("MainKt")

// Função de registro que roda no início
suspend fun registerClientIfNeeded() {
//    println("Registro: Verificando se o cliente '${Config.clientId}' já está cadastrado...")
    logger.info("Registro: Verificando se o cliente '${Config.clientId}' já está cadastrado...")
    val supabase = createSupabaseClient(Config.supabaseUrl, Config.supabaseKey) {
        install(Postgrest)
    }

    try {
        // MUDANÇA AQUI 👇: Trocado 'clientes_backup' por 'clientes'
        val result = supabase.from("clientes").select {
            filter { eq("client_id", Config.clientId) }
            count(Count.EXACT)
        }

        if (result.countOrNull() == 0L) {
//            println("Registro: Cliente não encontrado. Registrando novo cliente...")
            logger.info("Registro: Cliente não encontrado. Registrando novo cliente...")
            val newClient = ClientInfo(
                client_id = Config.clientId,
                nome_empresa = Config.companyName,
                cnpj_empresa = Config.companyCnpj
            )
            // MUDANÇA AQUI 👇: Trocado 'clientes_backup' por 'clientes'
            supabase.from("clientes").insert(newClient)
            logger.info("Registro: Cliente '${Config.companyName}' registrado com sucesso!")

        } else {
            logger.info("Registro: Cliente já está cadastrado. Nenhuma ação necessária.")
        }
    } catch (e: Exception) {
        logger.error("Registro ERRO: Falha ao tentar registrar o cliente. Causa: ${e.message}")
    }
}


fun main() = runBlocking {
    logger.info("Iniciando Agente de Backup (Modo Supabase Realtime)...")
    logger.info("ID do Cliente: ${Config.clientId}")


    // --- ETAPA 0: Registrar o cliente (de forma sequencial e aguardando o término) ---
    registerClientIfNeeded()

    // --- ETAPA 1: Iniciar o listener de agendamentos em paralelo ---
    val realtimeManager = RealtimeManager()
    // MUDANÇA AQUI 👇: Colocamos o listener dentro de um 'launch' para que ele rode em segundo plano
    launch {
        realtimeManager.connectAndListen()
    }

    // --- ETAPA 2: Iniciar o monitor de backups em paralelo ---
    val backupMonitor = BackupMonitor()
    launch {
        while (true) {
            backupMonitor.checkAndReportBackups()
            // Delay ajustado para 1 minuto para testes, mude para um valor maior em produção
            delay(60_000L)
        }
    }

    logger.info("Agente iniciado. Ouvindo por agendamentos e monitorando backups.")
    // MUDANÇA AQUI 👇: O delay(Long.MAX_VALUE) foi removido pois não é mais necessário.
    // O 'runBlocking' manterá o programa vivo enquanto os 'launch' estiverem ativos.
}