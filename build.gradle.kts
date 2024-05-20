import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
}

group = "com.sonefall"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(21)
    mingwX64()
    jvm()
    linuxX64()
    macosX64()
    macosArm64()
    targets.withType<KotlinNativeTarget> {
        binaries.executable {
            baseName = "BLT-$targetName"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation("io.ktor:ktor-client-core:2.3.11")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("com.fleeksoft.ksoup:ksoup:0.1.2")
                implementation("com.github.ajalt.clikt:clikt:4.4.0")
                implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.3.5")

            }
        }
        named("jvmMain") {
            dependencies {
                implementation("org.slf4j:slf4j-simple:2.0.13")
                implementation("io.ktor:ktor-client-okhttp:2.3.11")
            }
        }
        named("linuxMain") {
            dependencies {
                implementation("io.ktor:ktor-client-curl:2.3.11")
            }
        }
        named("appleMain") {
            dependencies {
                implementation("io.ktor:ktor-client-darwin:2.3.11")
            }
        }
        named("mingwMain") {
            dependencies {
                implementation("io.ktor:ktor-client-winhttp:2.3.11")
            }
        }
    }
}
