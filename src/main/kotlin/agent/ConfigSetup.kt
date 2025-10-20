package com.waldirbaia.agent

import java.io.File
import java.io.FileOutputStream

object ConfigSetup {
    private const val CONFIG_FILE = "config.properties"

    /**
     * Verifica se o arquivo de configuração existe.
     * Se não existir, inicia o processo interativo de configuração.
     */
    fun ensureConfigExists(): Boolean {
        val configFile = File(CONFIG_FILE)

        return if (!configFile.exists()) {
            println("Arquivo 'config.properties' não encontrado.")
            println("Iniciando assistente de configuração inicial...\n")
            createConfigInteractively(configFile)
        } else {
            println("✅ Arquivo de configuração '$CONFIG_FILE' encontrado.")
            true
        }
    }

    /**
     * Cria o arquivo de configuração de forma interativa.
     */
    private fun createConfigInteractively(configFile: File): Boolean {
        try {
            val configs = mutableMapOf<String, String>()

            println("--- Configuração Inicial do Agente ---")
            println("Por favor, forneça as seguintes informações.\n")

            // 1. Client ID
            print("➤ [1/7] ID do Cliente: ")
            configs["client_id"] = readLine()?.trim()?.takeIf { it.isNotEmpty() }
                ?: "CLIENTE_${System.currentTimeMillis()}"

            // 2. Nome da Empresa
            print("➤ [2/7] Nome da Empresa: ")
            configs["nome_empresa"] = readLine()?.trim()?.takeIf { it.isNotEmpty() }
                ?: "Empresa Não Informada"

            // 3. CNPJ
            print("➤ [3/7] CNPJ (apenas números, Padrão: 000...): ")
            configs["cnpj_empresa"] = readLine()?.trim()?.replace(Regex("[^0-9]"), "")
                ?.takeIf { it.isNotEmpty() } ?: "00000000000000"

            // 4. Supabase URL
            println("\n🔗 Insira a URL da sua instância Supabase")
            configs["supabase_url"] = promptForRequiredField("URL do Supabase")

            // 5. Supabase Key
            println("\n🔑 Insira a Chave Pública (anon key) do seu projeto Supabase")
            configs["supabase_key"] = promptForRequiredField("Supabase Key")

            // 6. Backup Folder Path (Local)
            println("\n📁 Insira o caminho completo da pasta de backups LOCAL")
            println("   Exemplo: C:/Backups ou C:/Waldir/Backups")
            val localPath = promptForRequiredField("Pasta de Backups Local")
            // Normalizar o caminho (trocar \ por /)
            configs["backup_folder_path"] = localPath.replace("\\", "/")

            // Valida e cria a pasta de backup, se necessário
            validateAndCreateBackupDir(configs["backup_folder_path"]!!)

            // 7. Backup Folder Path Remote (Nuvem)
            println("\n☁️  Insira o caminho completo da pasta de backups na NUVEM")
            println("   Exemplo: dropbox:/BACKUP/AGROFERTIL")
            println("   Formato: <remote>:/<caminho>")
            val remotePath = promptForRequiredField("Pasta de Backups Remota")
            // Normalizar o caminho (trocar \ por /)
            configs["backup_folder_path_remote"] = remotePath.replace("\\", "/")

            // Confirmação das configurações
            println("\n--- Resumo das Configurações ---")
            println("Client ID:         ${configs["client_id"]}")
            println("Nome Empresa:      ${configs["nome_empresa"]}")
            println("CNPJ:              ${configs["cnpj_empresa"]}")
            println("Supabase URL:      ${configs["supabase_url"]}")
            println("Supabase Key:      ${configs["supabase_key"]?.take(20)}...")
            println("Pasta Local:       ${configs["backup_folder_path"]}")
            println("Pasta Remota:      ${configs["backup_folder_path_remote"]}")
            println("--------------------------------")

            print("As configurações estão corretas? (S/n): ")
            val confirm = readLine()?.trim()?.uppercase() ?: "S"

            if (confirm != "S" && confirm.isNotEmpty()) {
                println("❌ Configuração cancelada pelo usuário.")
                return false
            }

            // Salva o arquivo
            saveConfigFile(configFile, configs)

            println("\n✅ Arquivo 'config.properties' criado com sucesso em: ${configFile.absolutePath}")
            println("🚀 Iniciando o sistema...\n")
            return true

        } catch (e: Exception) {
            println("\n❌ Erro ao criar o arquivo de configuração: ${e.message}")
            return false
        }
    }

    /**
     * Valida se a pasta de backup existe e oferece a opção de criá-la.
     */
    private fun validateAndCreateBackupDir(path: String) {
        val backupDir = File(path)
        if (!backupDir.exists()) {
            println("\nA pasta '$path' não existe.")
            print("Deseja criá-la agora? (S/n): ")
            val createFolder = readLine()?.trim()?.uppercase() ?: "S"

            if (createFolder == "S" || createFolder.isEmpty()) {
                try {
                    backupDir.mkdirs()
                    println("✅ Pasta criada com sucesso!")
                } catch (e: Exception) {
                    println("❌ Erro ao criar a pasta: ${e.message}")
                    println("⚠️ Você precisará criar a pasta manualmente.")
                }
            }
        } else {
            println("✅ Pasta de backups encontrada.")
        }
    }

    /**
     * Solicita um campo obrigatório até que o usuário forneça um valor.
     */
    private fun promptForRequiredField(fieldName: String): String {
        var value: String?
        do {
            print("➤ $fieldName: ")
            value = readLine()?.trim()
            if (value.isNullOrEmpty()) {
                println("   ⚠️  Este campo é obrigatório. Tente novamente.")
            }
        } while (value.isNullOrEmpty())
        return value
    }

    /**
     * Salva as configurações em um arquivo .properties.
     */
    private fun saveConfigFile(file: File, configs: Map<String, String>) {
        val content = """
        # Configuração do Agente de Backup
        # Gerado em: ${java.time.LocalDateTime.now()}

        # ID único para este cliente. O agente só ouvirá mudanças com este ID.
        client_id=${configs["client_id"]}

        # Nome da empresa
        nome_empresa=${configs["nome_empresa"]}

        # CNPJ da empresa (apenas números)
        cnpj_empresa=${configs["cnpj_empresa"]}

        # URL da instância Supabase
        supabase_url=${configs["supabase_url"]}

        # Chave de API do Supabase (anon key)
        supabase_key=${configs["supabase_key"]}

        # Caminho da pasta onde os backups são salvos (LOCAL)
        backup_folder_path=${configs["backup_folder_path"]}

        # Caminho da pasta onde os backups estão na nuvem (REMOTO)
        # Formato: <remote>:/<caminho> - Exemplo: dropbox:/BACKUP/AGROFERTIL
        backup_folder_path_remote=${configs["backup_folder_path_remote"]}
        """.trimIndent()

        FileOutputStream(file).use { it.write(content.toByteArray()) }
    }
}