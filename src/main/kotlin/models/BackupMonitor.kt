package com.waldirbaia.models

import com.waldirbaia.agent.Config
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.io.File

@Serializable
data class BackupLog(
    val client_id: String,
    val file_name: String,
    val file_size_bytes: Long,
    val file_creation_date: String
)

class BackupMonitor {
    private val logger = LoggerFactory.getLogger(BackupMonitor::class.java)

    private val supabase = createSupabaseClient(Config.supabaseUrl, Config.supabaseKey) {
        install(Postgrest)
    }

    suspend fun checkAndReportBackups() {
        logger.info("Monitor: Verificando pasta de backups em '${Config.backupFolderPath}'...")

        val backupDir = File(Config.backupFolderPath)

        if (!backupDir.exists() || !backupDir.isDirectory) {
            logger.info("Monitor ERRO: A pasta de backup não existe ou não é um diretório.")
            return
        }

        val last5Files = backupDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.take(5)

        if (last5Files.isNullOrEmpty()) {
            logger.info("Monitor: Nenhum arquivo de backup encontrado na pasta.")
            return
        }

        logger.info("Monitor: Encontrados ${last5Files.size} arquivos recentes. Verificando quais são novos...")

        try {
            val existingLogs = supabase.from("backup_logs")
                .select {
                    filter {
                        eq("client_id", Config.clientId)
                    }
                }
                .decodeList<BackupLog>()

            val existingFileNames = existingLogs.map { it.file_name }.toSet()

            val newFiles = last5Files.filter { file ->
                !existingFileNames.contains(file.name)
            }

            if (newFiles.isEmpty()) {
                logger.info("Monitor: Todos os arquivos já estão registrados no banco. Nenhum novo arquivo encontrado.")
                return
            }

            logger.info("Monitor: ${newFiles.size} arquivo(s) novo(s) encontrado(s). Preparando para enviar...")

            var successCount = 0
            for (file in newFiles) {
                try {
                    // --- CORREÇÃO APLICADA AQUI ---
                    // Criar uma instância da data class em vez de um mapa genérico
                    val logEntry = BackupLog(
                        client_id = Config.clientId,
                        file_name = file.name,
                        file_size_bytes = file.length(),
                        file_creation_date = java.time.Instant.ofEpochMilli(file.lastModified()).toString()
                    )

                    // Enviar o objeto serializável
                    supabase.from("backup_logs").insert(logEntry)
                    // -----------------------------

                    successCount++
                    logger.info("Monitor: Arquivo ${file.name} enviado com sucesso!")
                } catch (e: Exception) {
                    logger.error("Monitor ERRO ao enviar arquivo ${file.name}: ${e.message}")
                }
            }

            logger.info("Monitor: Informações de $successCount arquivo(s) enviadas com sucesso para o Supabase!")

        } catch (e: Exception) {
            logger.error("Monitor ERRO: Falha ao processar informações. Causa: ${e.message}", e)
        }
    }
}