import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
}

group = "com.sonefall"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    applyDefaultHierarchyTemplate()
    jvmToolchain(21)
    mingwX64()
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        mainRun {
            mainClass = "com.sonefall.blt.MainKt"
        }
    }
    linuxX64()
    targets.withType<KotlinNativeTarget> {
        binaries.executable {
            baseName = "BLT-$targetName"
            entryPoint = "com.sonefall.blt.main"

            runTask?.args(project.findProperty("runArgs")?.toString()?.split(" ") ?: emptyList<String>())
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project.dependencies.platform("io.ktor:ktor-bom:2.3.12"))
                implementation("io.ktor:ktor-client-core")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
                implementation("com.fleeksoft.ksoup:ksoup:0.1.2")
                implementation("com.github.ajalt.clikt:clikt:4.4.0")
                implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.4.0")
                implementation("io.github.oshai:kotlin-logging:7.0.0")
            }
        }
        named("jvmMain") {
            dependencies {
                implementation("org.slf4j:slf4j-simple:2.0.13")
                implementation("io.ktor:ktor-client-okhttp")
            }
        }
        named("linuxMain") {
            dependencies {
                implementation("io.ktor:ktor-client-curl")
            }
        }
        named("mingwMain") {
            dependencies {
                implementation("io.ktor:ktor-client-winhttp")
            }
        }
    }
}
