package com.sonefall.blt

import org.slf4j.event.Level

actual fun configureLogging(level: LogLevel) {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", level.name)
}

actual typealias LogLevel = Level
