
package com.sonefall.blt

import ch.qos.logback.classic.Logger
import com.fleeksoft.ksoup.Ksoup
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.collections.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import ch.qos.logback.classic.Level as LogbackLevel
import org.slf4j.Logger as SLF4JLogger



val logger by lazy { KotlinLogging.logger {} }

fun main(args: Array<String>) = BLT().main(args)

class BLT : CliktCommand() {
    val baseUrl by option(
        "-u", "--url", help = "The URL to traverse"
    ).convert { Url(it) }.required()

    val stopAfterRecursion by option(
        "-s", "--stop-after", help = "The number of recursions to stop crawling after. Default is infinite."
    ).int()

    val ignoreRegex by option(
        "-i", "--ignoreRegex", help = "The Regex for ignoring"
    )

    val urlList by option(
        "-l",
        "--list",
        help = "path to a file with a list of urls. You still need to specify the base url. "
                + "currently only 1 base url is allowed even though you might have multiple urls from "
                + "different domains. I am not planning on fixing that"
    )

    private val saveToFile by option(
        "-o", "--output-file", help = "The file to save for."
    )

    private val logLevel by option(
        "-L", "--log-level", help = "The log level to log at"
    ).enum<Level>().default(Level.INFO)

    private val dontPrintResult by option(
        "--dont-print-result", help = "Whether to print the result to stdout"
    ).flag()

    private val prettyPrint: Boolean by option(
        "--pretty-print", help = "Whether to pretty print the json output"
    ).flag()

    override fun run(): Unit = runBlocking(Dispatchers.Default) {
        (LoggerFactory.getLogger(SLF4JLogger.ROOT_LOGGER_NAME) as Logger).level =
            LogbackLevel.convertAnSLF4JLevel(logLevel)

        val results = WebCrawler.crawl(this@BLT)

        val mapper = Json { prettyPrint = this@BLT.prettyPrint }
        val resultString = mapper.encodeToString(results)

        if (!dontPrintResult) {
            println(resultString)
        }

        saveToFile?.let { filePath ->
            val file = Path(filePath)

            SystemFileSystem.sink(file).buffered().use {
                it.writeString(resultString)
            }
        }
    }
}
