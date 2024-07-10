plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    application
}

group = "com.sonefall"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
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
    implementation("org.slf4j:slf4j-simple:2.0.13")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.sonefall.blt.MainKt")
}