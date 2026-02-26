plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.3.10"
    application
    `jvm-test-suite`
}

group = "com.sonefall"
version = "2.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation(platform("io.ktor:ktor-bom:2.3.12"))
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-okhttp")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("com.fleeksoft.ksoup:ksoup:0.1.2")
    implementation("com.github.ajalt.clikt:clikt-jvm:4.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.4.0")
    implementation("io.github.oshai:kotlin-logging:7.0.0")
    implementation("ch.qos.logback:logback-classic:1.5.32")
    implementation("com.github.alturkovic:robots-txt:1.0.1")

    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation(kotlin("test-junit5"))
}

application {
    mainClass = "com.sonefall.blt.MainKt"
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter()
        }
    }
}