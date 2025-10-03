import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    id("org.graalvm.buildtools.native") version "0.10.2"
}

group = "com.waldirbaia"
version = "1.0.0"

application {
    mainClass.set("com.waldirbaia.agent.MainKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}

// Configuração do JAR para incluir o manifesto correto
tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.waldirbaia.MainKt"
    }
    // Incluir dependências no JAR (Fat JAR)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("agente-backup")
            mainClass.set("com.waldirbaia.MainKt")

            buildArgs.addAll(
                "--verbose",
                "--no-fallback",
                "-H:+ReportExceptionStackTraces",
                "--initialize-at-build-time=org.slf4j",
                "--initialize-at-build-time=ch.qos.logback"
            )
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.cio)
    implementation(libs.logback.classic)
    implementation(libs.kotlinx.coroutines.core)
}