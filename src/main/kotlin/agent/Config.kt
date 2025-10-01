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

    val clientId: String = properties.getProperty("client_id")
    val supabaseUrl: String = properties.getProperty("supabase_url")
    val supabaseKey: String = properties.getProperty("supabase_key")
}