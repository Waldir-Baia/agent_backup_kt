package com.waldirbaia

import com.waldirbaia.agent.BackupMonitor
import com.waldirbaia.agent.Config
import com.waldirbaia.agent.ConfigSetup
import com.waldirbaia.agent.RealtimeManager
import com.waldirbaia.models.ClientInfoEntity
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Count
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory


private val logger = LoggerFactory.getLogger("MainKt")

suspend fun registerClientIfNeeded() {
    logger.info("Registro: Verificando se o cliente '${Config.clientId}' já está cadastrado...")
    val supabase = createSupabaseClient(Config.supabaseUrl, Config.supabaseKey) {
        install(Postgrest)
    }

    try {
        val result = supabase.from("clientes").select {
            filter { eq("client_id", Config.clientId) }
            count(Count.EXACT)
        }

        if (result.countOrNull() == 0L) {
            logger.info("Registro: Cliente não encontrado. Registrando novo cliente...")
            val newClient = ClientInfoEntity(
                client_id = Config.clientId,
                nome_empresa = Config.companyName,
                cnpj_empresa = Config.companyCnpj
            )
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
    // Exibir banner do sistema
    printBanner()

    // Verificar e criar configuração se necessário
    if (!ConfigSetup.ensureConfigExists()) {
        logger.error("Não foi possível criar o arquivo de configuração.")
        println("\n❌ Sistema encerrado. Configure manualmente o arquivo 'config.properties' e tente novamente.")
        return@runBlocking
    }

    // Validar configurações carregadas
    if (!Config.isValid()) {
        logger.error("Configurações inválidas ou incompletas detectadas!")
        println("\n❌ Erro: O arquivo 'config.properties' está incompleto ou com valores vazios.")
        println("💡 Dica: Delete o arquivo 'config.properties' e execute novamente para reconfigurar.\n")
        return@runBlocking
    }

    // Exibir informações do sistema
    displaySystemInfo()

    logger.info("Iniciando Agente de Backup (Modo Supabase Realtime)...")
    logger.info("ID do Cliente: ${Config.clientId}")

    registerClientIfNeeded()

    val realtimeManager = RealtimeManager()
    launch {
        realtimeManager.connectAndListen()
    }

    val backupMonitor = BackupMonitor()
    launch {
        while (true) {
            backupMonitor.checkAndReportBackups()
            delay(60_000L)
        }
    }

    logger.info("Agente iniciado. Ouvindo por agendamentos e monitorando backups.")
}

/**
 * Exibe o banner do sistema
 */
fun printBanner() {
    println("\n╔═══════════════════════════════════════════════════════════════╗")
    println("║                                                               ║")
    println("║              🔄 AGENTE DE BACKUP - VERSÃO 1.0.0              ║")
    println("║                                                               ║")
    println("║          Sistema de Monitoramento de Backups em Tempo Real   ║")
    println("║                                                               ║")
    println("╚═══════════════════════════════════════════════════════════════╝\n")
}

/**
 * Exibe informações do sistema carregado
 */
fun displaySystemInfo() {
    println("╔═══════════════════════════════════════════════════════════════╗")
    println("║                  INFORMAÇÕES DO SISTEMA                       ║")
    println("╚═══════════════════════════════════════════════════════════════╝")
    println("🆔 Client ID:      ${Config.clientId}")
    println("🏢 Empresa:        ${Config.companyName}")
    println("📄 CNPJ:           ${formatCNPJ(Config.companyCnpj)}")
    println("🔗 Supabase URL:   ${Config.supabaseUrl}")
    println("📁 Pasta Backups:  ${Config.backupFolderPath}")
    println("═══════════════════════════════════════════════════════════════\n")
}

/**
 * Formata o CNPJ no padrão XX.XXX.XXX/XXXX-XX
 */
fun formatCNPJ(cnpj: String): String {
    if (cnpj.length != 14) return cnpj

    return "${cnpj.substring(0, 2)}.${cnpj.substring(2, 5)}.${cnpj.substring(5, 8)}/" +
            "${cnpj.substring(8, 12)}-${cnpj.substring(12, 14)}"
}