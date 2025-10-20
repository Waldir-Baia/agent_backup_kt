package com.waldirbaia.agent

import BackupFileNameEntity
import com.waldirbaia.models.BackupLogEntity
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.security.MessageDigest

class BackupMonitor {
    private val logger = LoggerFactory.getLogger(BackupMonitor::class.java)

    private val supabase = createSupabaseClient(Config.supabaseUrl, Config.supabaseKey) {
        install(Postgrest)
    }

    suspend fun checkAndReportBackups() {
        logger.info("Monitor: Verificando pasta de backups em '${Config.backupFolderPath}'...")

        val backupDir = File(Config.backupFolderPath)

        if (!backupDir.exists() || !backupDir.isDirectory) {
            logger.error("Monitor ERRO: A pasta de backup não existe ou não é um diretório.")
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
            // Buscar apenas os nomes dos arquivos existentes
            val existingFiles = supabase.from("backup_logs")
                .select(columns = Columns.list("file_name")) {
                    filter {
                        eq("client_id", Config.clientId)
                    }
                }.decodeAs<List<BackupFileNameEntity>>()

            val existingFileNames = existingFiles.map { it.file_name }.toSet()

            // Filtrar apenas arquivos novos
            val newFiles = last5Files.filter { file ->
                !existingFileNames.contains(file.name)
            }

            if (newFiles.isEmpty()) {
                logger.info("Monitor: Todos os arquivos já estão registrados. Nenhum novo arquivo.")
                return
            }

            logger.info("Monitor: ${newFiles.size} arquivo(s) novo(s) encontrado(s). Verificando integridade...")

            var successCount = 0
            var errorCount = 0

            for (file in newFiles) {
                try {
                    logger.info("🔍 Verificando integridade do arquivo '${file.name}'...")

                    // Verificar integridade usando rclone
                    val integrityCheck = verifyFileIntegrity(file)

                    if (!integrityCheck.success) {
                        logger.error("❌ Monitor: Falha na verificação de integridade de '${file.name}': ${integrityCheck.errorMessage}")

                        // Registrar erro no banco
                        val errorLog = BackupLogEntity(
                            client_id = Config.clientId,
                            file_name = file.name,
                            file_size_bytes = file.length(),
                            file_creation_date = Instant.ofEpochMilli(file.lastModified())
                                .atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            error_message = "ERRO DE INTEGRIDADE: ${integrityCheck.errorMessage}"
                        )

                        supabase.from("backup_logs").insert(errorLog)
                        errorCount++
                        continue
                    }

                    logger.info("✅ Integridade verificada com sucesso!")

                    // Criar a data formatada corretamente
                    val creationDate = Instant.ofEpochMilli(file.lastModified())
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                    // Criar o objeto BackupLog
                    val logEntry = BackupLogEntity(
                        client_id = Config.clientId,
                        file_name = file.name,
                        file_size_bytes = file.length(),
                        file_creation_date = creationDate
                    )

                    // Inserir no Supabase
                    supabase.from("backup_logs").insert(logEntry)

                    successCount++
                    logger.info("✅ Monitor: Arquivo '${file.name}' registrado (${formatFileSize(file.length())})")

                } catch (e: Exception) {
                    logger.error("❌ Monitor: Erro ao processar '${file.name}': ${e.message}")
                    errorCount++
                }
            }

            logger.info("✅ Monitor: $successCount de ${newFiles.size} arquivo(s) enviado(s) com sucesso!")
            if (errorCount > 0) {
                logger.warn("⚠️ Monitor: $errorCount arquivo(s) com erro de integridade!")
            }

        } catch (e: Exception) {
            logger.error("❌ Monitor: Falha ao processar informações: ${e.message}", e)
        }
    }

    /**
     * Verifica a integridade do arquivo comparando o hash local com o hash remoto usando rclone
     */
    private fun verifyFileIntegrity(file: File): IntegrityResult {
        try {
            // Calcular hash local do arquivo (DropboxHash)
            val localHash = calculateDropboxHash(file)
            logger.debug("Hash local calculado: $localHash")

            // Executar comando rclone para obter hash remoto
            val remotePath = "${Config.backupFolderPathRemote}/${file.name}"
            val command = listOf("rclone", "hashsum", "DropboxHash", remotePath)

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                return IntegrityResult(
                    success = false,
                    errorMessage = "Arquivo não encontrado na nuvem ou erro ao executar rclone: $output"
                )
            }

            // Parse do output do rclone (formato: "hash filename")
            val remoteHash = output.trim().split("\\s+".toRegex()).firstOrNull()

            if (remoteHash.isNullOrBlank()) {
                return IntegrityResult(
                    success = false,
                    errorMessage = "Não foi possível obter o hash do arquivo remoto"
                )
            }

            logger.debug("Hash remoto obtido: $remoteHash")

            // Comparar hashes
            if (localHash.equals(remoteHash, ignoreCase = true)) {
                return IntegrityResult(success = true)
            } else {
                return IntegrityResult(
                    success = false,
                    errorMessage = "Hash local ($localHash) não corresponde ao hash remoto ($remoteHash)"
                )
            }

        } catch (e: Exception) {
            return IntegrityResult(
                success = false,
                errorMessage = "Erro ao verificar integridade: ${e.message}"
            )
        }
    }

    /**
     * Calcula o DropboxHash do arquivo local
     * DropboxHash é um hash SHA256 dos blocos de 4MB do arquivo
     */
    private fun calculateDropboxHash(file: File): String {
        val blockSize = 4 * 1024 * 1024 // 4MB
        val digest = MessageDigest.getInstance("SHA-256")
        val overallDigest = MessageDigest.getInstance("SHA-256")

        file.inputStream().use { input ->
            val buffer = ByteArray(blockSize)
            var bytesRead: Int

            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.reset()
                digest.update(buffer, 0, bytesRead)
                overallDigest.update(digest.digest())
            }
        }

        return overallDigest.digest().joinToString("") { "%02x".format(it) }
    }

    // Função auxiliar para formatar tamanho do arquivo
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    // Classe para resultado da verificação de integridade
    private data class IntegrityResult(
        val success: Boolean,
        val errorMessage: String? = null
    )
}