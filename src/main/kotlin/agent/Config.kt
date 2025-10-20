package com.waldirbaia.agent

import java.io.File
import java.util.Properties

object Config {
    private val properties = Properties()

    init {
        val configFile = File("config.properties")
        val inputStream = if (configFile.exists()) {
            configFile.inputStream()
        } else {
            javaClass.classLoader.getResourceAsStream("config.properties")
        }
        inputStream?.use { properties.load(it) }
    }

    val clientId: String = properties.getProperty("client_id", "")
    val supabaseUrl: String = properties.getProperty("supabase_url", "")
    val supabaseKey: String = properties.getProperty("supabase_key", "")
    val backupFolderPath: String = properties.getProperty("backup_folder_path", "")
    val backupFolderPathRemote: String = properties.getProperty("backup_folder_path_remote", "")
    val companyName: String = properties.getProperty("nome_empresa", "")
    val companyCnpj: String = properties.getProperty("cnpj_empresa", "")

    /**
     * Valida se todas as configurações obrigatórias estão preenchidas
     */
    fun isValid(): Boolean {
        return clientId.isNotBlank() &&
                supabaseUrl.isNotBlank() &&
                supabaseKey.isNotBlank() &&
                backupFolderPath.isNotBlank() &&
                backupFolderPathRemote.isNotBlank() &&
                companyName.isNotBlank() &&
                companyCnpj.isNotBlank()
    }
}