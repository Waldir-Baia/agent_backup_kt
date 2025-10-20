import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    id("org.graalvm.buildtools.native") version "0.10.2"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}
System.setProperty("org.graalvm.home", "C:\\GraalVM\\graalvm-community-openjdk-24.0.2+11.1")
group = "com.waldirbaia"
version = "1.0.0"

application {
    mainClass.set("com.waldirbaia.MainKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
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

            metadataRepository {
                enabled.set(true)
            }

            buildArgs.addAll(
                "--no-fallback",
                "-H:+ReportExceptionStackTraces",
                "--enable-url-protocols=http,https,ws,wss",
                "-H:+AddAllCharsets",
                "-H:+UnlockExperimentalVMOptions",
                "--initialize-at-run-time",
                "-march=compatibility",
                "-H:+AllowDeprecatedInitializeAllClassesAtBuildTime"
            )
        }
    }
    toolchainDetection.set(false)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.cio)
    implementation(libs.logback.classic)
    implementation(libs.kotlinx.coroutines.core)
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation(libs.cron.utils)
}