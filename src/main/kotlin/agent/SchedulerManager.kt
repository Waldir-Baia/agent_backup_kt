package com.waldirbaia.agent

import com.waldirbaia.models.SchedulePayload
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

interface CommandExecutor {
    fun executeRcloneCommand(payload: SchedulePayload)
}

// --- Interface para Agendamento (Linux) ---
interface SchedulerManager {
    fun createOrUpdateTask(payload: SchedulePayload)
    fun deleteTask(scheduleName: String)
}

// --- Implementação para Windows (Executor Imediato) ---
class WindowsCommandExecutor : CommandExecutor {
    private val logger = LoggerFactory.getLogger(WindowsCommandExecutor::class.java)

    override fun executeRcloneCommand(payload: SchedulePayload) {
        if (!payload.is_active) {
            logger.info("Comando para '${payload.schedule_name}' recebido (Windows), mas está inativo. Nenhuma ação tomada.")
            return
        }
        logger.info("Executando IMEDIATAMENTE (Windows) o comando: ${payload.rclone_command}")
        val command = listOf("cmd.exe", "/c", payload.rclone_command)
        executeCommand(command)
    }
}

// --- Implementação para Linux (Agendador - Sem Alterações) ---
class LinuxSchedulerManager : SchedulerManager {
    private val logger = LoggerFactory.getLogger(LinuxSchedulerManager::class.java)
    private val cronMarker = "# RClone Agent: "

    override fun createOrUpdateTask(payload: SchedulePayload) {
        deleteTask(payload.schedule_name)
        if (!payload.is_active) {
            logger.info("Tarefa '${payload.schedule_name}' inativa (Linux). Agendamento removido.")
            return
        }
        val newCronLine = "${payload.cron_expression} ${payload.rclone_command} $cronMarker${payload.schedule_name}\n"
        logger.info("Adicionando ao crontab (Linux): $newCronLine")
        val command = "bash -c ' (crontab -l 2>/dev/null; echo \"$newCronLine\") | crontab - '"
        executeCommand(listOf("bash", "-c", command))
    }

    override fun deleteTask(scheduleName: String) {
        logger.info("Removendo tarefa '$scheduleName' do crontab (Linux).")
        val command = "bash -c 'crontab -l 2>/dev/null | grep -v \"$cronMarker$scheduleName\" | crontab -'"
        executeCommand(listOf("bash", "-c", command))
    }
}

// --- Função Auxiliar (sem alterações) ---
private fun executeCommand(command: List<String>) {
    try {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        process.inputStream.reader(Charsets.UTF_8).use {
            it.forEachLine { line -> LoggerFactory.getLogger("RcloneOutput").info(line) }
        }
        process.waitFor(30, TimeUnit.MINUTES)
        // ... (código de log de sucesso/erro) ...
    } catch (e: Exception) {
        LoggerFactory.getLogger("OsActionHandler").error("Falha ao executar comando.", e)
    }
}

// --- Nova "Fábrica" que decide qual implementação usar ---
fun getOsActionHandler(): Any {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("win") -> WindowsCommandExecutor() // Retorna o executor para Windows
        osName.contains("nix") || osName.contains("nux") -> LinuxSchedulerManager() // Retorna o agendador para Linux
        else -> throw UnsupportedOperationException("Sistema Operacional não suportado: $osName")
    }
}