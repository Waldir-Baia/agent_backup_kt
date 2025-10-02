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
    val file_creation_date: String // Enviamos como texto no formato ISO 8601
)

class BackupMonitor {
    private val logger = LoggerFactory.getLogger(BackupMonitor::class.java)
    private val supabase = createSupabaseClient(Config.supabaseUrl, Config.supabaseKey) {
        install(Postgrest)
    }

    suspend fun checkAndReportBackups() {
        logger.info("Monitor: Verificando pasta de backups em '${Config.backupFolderPath}'...")

        val backupDir = File(Config.backupFolderPath)

        // 1. Validação básica da pasta
        if (!backupDir.exists() || !backupDir.isDirectory) {
            logger.info("Monitor ERRO: A pasta de backup não existe ou não é um diretório.")
            return
        }

        // 2. Listar, filtrar por arquivos, ordenar por data de modificação (mais recente primeiro) e pegar os 5 primeiros
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
            // 3. BUSCAR os arquivos que já existem no banco para este client_id
            val existingLogs = supabase.from("backup_logs")
                .select {
                    filter {
                        eq("client_id", Config.clientId)
                    }
                }
                .decodeList<BackupLog>()

            // Criar um Set com os nomes dos arquivos já registrados (para busca rápida)
            val existingFileNames = existingLogs.map { it.file_name }.toSet()

            // 4. Filtrar apenas os arquivos NOVOS (que não estão no banco)
            val newFiles = last5Files.filter { file ->
                !existingFileNames.contains(file.name)
            }

            if (newFiles.isEmpty()) {
                logger.info("Monitor: Todos os arquivos já estão registrados no banco. Nenhum novo arquivo encontrado.")
                return
            }

            logger.info("Monitor: ${newFiles.size} arquivo(s) novo(s) encontrado(s). Preparando para enviar...")

            // 5. Mapear APENAS os arquivos novos para o modelo de dados
            val logsToSend = newFiles.map { file ->
                BackupLog(
                    client_id = Config.clientId,
                    file_name = file.name,
                    file_size_bytes = file.length(),
                    file_creation_date = java.time.Instant.ofEpochMilli(file.lastModified()).toString()
                )
            }

            // 6. Enviar APENAS os novos dados para o Supabase
            supabase.from("backup_logs").insert(logsToSend)
            logger.info("Monitor: Informações de ${logsToSend.size} arquivo(s) novo(s) enviadas com sucesso para o Supabase!")

        } catch (e: Exception) {
            logger.info("Monitor ERRO: Falha ao processar informações. Causa: ${e.message}")
            e.printStackTrace()
        }
    }
}