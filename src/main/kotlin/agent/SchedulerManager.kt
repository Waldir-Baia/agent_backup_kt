package com.waldirbaia.agent

import com.waldirbaia.models.SchedulePayload
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

interface SchedulerManager {
    fun createOrUpdateTask(payload: SchedulePayload)
    fun deleteTask(scheduleName: String)
}

// Implementação para Windows
class WindowsSchedulerManager : SchedulerManager {
    private val logger = LoggerFactory.getLogger(WindowsSchedulerManager::class.java)

    override fun createOrUpdateTask(payload: SchedulePayload) {
        deleteTask(payload.schedule_name)
        if (!payload.is_active) {
            logger.info("Tarefa '${payload.schedule_name}' inativa. Agendamento removido.")
            return
        }
        val parts = payload.cron_expression.split(" ")
        val minute = parts[0]
        val hour = parts[1]
        val time = "${hour.padStart(2, '0')}:${minute.padStart(2, '0')}"
        val command = listOf("schtasks", "/create", "/tn", "\"RClone - ${payload.schedule_name}\"", "/tr", "\"${payload.rclone_command}\"", "/sc", "DAILY", "/st", time, "/f")
        logger.info("Executando no Windows: ${command.joinToString(" ")}")
        executeCommand(command)
    }

    override fun deleteTask(scheduleName: String) {
        val command = listOf("schtasks", "/delete", "/tn", "\"RClone - ${scheduleName}\"", "/f")
        logger.info("Deletando no Windows: ${command.joinToString(" ")}")
        executeCommand(command)
    }
}

// Implementação para Linux
class LinuxSchedulerManager : SchedulerManager {
    private val logger = LoggerFactory.getLogger(LinuxSchedulerManager::class.java)

    private val cronMarker = "# RClone Agent: "
    override fun createOrUpdateTask(payload: SchedulePayload) {
        deleteTask(payload.schedule_name)
        if (!payload.is_active) {
            logger.info("Tarefa '${payload.schedule_name}' inativa. Agendamento removido.")
            return
        }
        val newCronLine = "${payload.cron_expression} ${payload.rclone_command} $cronMarker${payload.schedule_name}\n"
        logger.info("Adicionando ao crontab: $newCronLine")
        val command = "bash -c ' (crontab -l 2>/dev/null; echo \"$newCronLine\") | crontab - '"
        executeCommand(listOf("bash", "-c", command))
    }

    override fun deleteTask(scheduleName: String) {
        logger.info("Removendo tarefa '$scheduleName' do crontab.")
        val command = "bash -c 'crontab -l 2>/dev/null | grep -v \"$cronMarker$scheduleName\" | crontab -'"
        executeCommand(listOf("bash", "-c", command))
    }
}

private fun executeCommand(command: List<String>) {
    try {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        process.inputStream.reader(Charsets.UTF_8).use { println(it.readText()) }
        process.waitFor(10, TimeUnit.SECONDS)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun getSchedulerManager(): SchedulerManager {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("win") -> WindowsSchedulerManager()
        osName.contains("nix") || osName.contains("nux") -> LinuxSchedulerManager()
        else -> throw UnsupportedOperationException("Sistema Operacional não suportado: $osName")
    }
}