plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization) // Plugin para JSON
    application // Plugin para criar um programa executável
}

group = "com.waldirbaia"
version = "1.0.0"

// MUITO IMPORTANTE: Define que a classe principal é a do nosso AGENTE,
// e NÃO o motor de um servidor Ktor.
application {
    mainClass.set("com.waldirbaia.agent.MainKt")
}
kotlin {
    jvmToolchain(24)
}

repositories {
    mavenCentral()
}

dependencies {
    // Apenas a dependência do Supabase Realtime é necessária
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.cio)
    // Outras dependências que nosso agente precisa
    implementation(libs.logback.classic)
    implementation(libs.kotlinx.coroutines.core)
}