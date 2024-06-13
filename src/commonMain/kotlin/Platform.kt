package com.sonefall.blt

expect enum class LogLevel {
    INFO
}

expect fun configureLogging(level: LogLevel)
