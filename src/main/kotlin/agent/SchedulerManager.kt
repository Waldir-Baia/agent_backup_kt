package com.waldirbaia.agent

import com.waldirbaia.models.SchedulePayload
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// --- Interface para Execução de Comandos ---
interface CommandExecutor {
    suspend fun executeRcloneCommand(payload: SchedulePayload)
}

// --- Implementação para Windows ---
class WindowsCommandExecutor : CommandExecutor {
    private val logger = LoggerFactory.getLogger(WindowsCommandExecutor::class.java)

    override suspend fun executeRcloneCommand(payload: SchedulePayload) {
        logger.info("Executando comando (Windows): ${payload.rclone_command}")
        val command = listOf("cmd.exe", "/c", payload.rclone_command)
        executeCommand(command)
    }
}

// --- Implementação para Linux ---
class LinuxCommandExecutor : CommandExecutor {
    private val logger = LoggerFactory.getLogger(LinuxCommandExecutor::class.java)

    override suspend fun executeRcloneCommand(payload: SchedulePayload) {
        logger.info("Executando comando (Linux): ${payload.rclone_command}")
        val command = listOf("bash", "-c", payload.rclone_command)
        executeCommand(command)
    }
}

// --- Gerenciador de Agendamento Interno ---
class InternalSchedulerManager(private val commandExecutor: CommandExecutor) {
    private val logger = LoggerFactory.getLogger(InternalSchedulerManager::class.java)
    private val scheduledTasks = ConcurrentHashMap<String, Job>()
    private val cronParser = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX))
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun createOrUpdateTask(payload: SchedulePayload) {
        deleteTask(payload.schedule_name)

        if (!payload.is_active) {
            logger.info("⏸️ Tarefa '${payload.schedule_name}' está inativa.")
            return
        }

        try {
            val cron = cronParser.parse(payload.cron_expression)
            val executionTime = ExecutionTime.forCron(cron)

            logger.info("⏰ Agendando '${payload.schedule_name}' com cron: ${payload.cron_expression}")

            val job = scope.launch {
                while (isActive) {
                    try {
                        val now = ZonedDateTime.now()
                        val nextExecution = executionTime.nextExecution(now).orElse(null)

                        if (nextExecution != null) {
                            val delayMillis = nextExecution.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()

                            if (delayMillis > 0) {
                                delay(delayMillis)
                                logger.info("🚀 Executando tarefa agendada: '${payload.schedule_name}'")
                                commandExecutor.executeRcloneCommand(payload)
                            }
                        } else {
                            break
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.error("❌ Erro: ${e.message}", e)
                        delay(60_000)
                    }
                }
            }

            scheduledTasks[payload.schedule_name] = job
            logger.info("✅ Tarefa '${payload.schedule_name}' agendada!")

        } catch (e: Exception) {
            logger.error("❌ Erro ao agendar '${payload.schedule_name}': ${e.message}", e)
        }
    }

    fun deleteTask(scheduleName: String) {
        scheduledTasks[scheduleName]?.let { job ->
            logger.info("🗑️ Cancelando: '$scheduleName'")
            job.cancel()
            scheduledTasks.remove(scheduleName)
        }
    }

    fun shutdown() {
        logger.info("Desligando scheduler...")
        scheduledTasks.values.forEach { it.cancel() }
        scheduledTasks.clear()
        scope.cancel()
    }
}

// --- Função Auxiliar ---
private suspend fun executeCommand(command: List<String>) = withContext(Dispatchers.IO) {
    try {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()

        process.inputStream.reader(Charsets.UTF_8).use { reader ->
            reader.forEachLine { line ->
                LoggerFactory.getLogger("RcloneOutput").info(line)
            }
        }

        val completed = process.waitFor(30, TimeUnit.MINUTES)

        if (completed) {
            val exitCode = process.exitValue()
            if (exitCode == 0) {
                LoggerFactory.getLogger("CommandExecutor").info("✅ Sucesso")
            } else {
                LoggerFactory.getLogger("CommandExecutor").warn("⚠️ Código: $exitCode")
            }
        } else {
            process.destroyForcibly()
            LoggerFactory.getLogger("CommandExecutor").error("❌ Timeout")
        }
    } catch (e: Exception) {
        LoggerFactory.getLogger("CommandExecutor").error("❌ Erro: ${e.message}", e)
    }
}

// --- Fábrica ---
fun getCommandExecutor(): CommandExecutor {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("win") -> WindowsCommandExecutor()
        osName.contains("nix") || osName.contains("nux") -> LinuxCommandExecutor()
        else -> throw UnsupportedOperationException("SO não suportado: $osName")
    }
}

fun getSchedulerManager(): InternalSchedulerManager {
    return InternalSchedulerManager(getCommandExecutor())
}