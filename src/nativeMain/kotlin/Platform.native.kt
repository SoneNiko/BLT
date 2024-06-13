package com.sonefall.blt

import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level

actual fun configureLogging(level: LogLevel) {
    KotlinLoggingConfiguration.logLevel = level
}

actual typealias LogLevel = Level
