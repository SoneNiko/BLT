@file:UseSerializers(HttpStatusCodeSerializer::class)

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

@Serializable
data class LinkResult(
    val parent: String?,
    val url: String,
    val status: HttpStatusCode? = null,
    val errorMsg: String? = null,
    val redirect: String? = null
)

val logger by lazy { KotlinLogging.logger {} }

private val allowedProtocols = listOf(URLProtocol.HTTP, URLProtocol.HTTPS)

fun main(args: Array<String>) = BLT().main(args)

class BLT : CliktCommand() {
    private val client = HttpClient {
        expectSuccess = false
        followRedirects = false
    }

    private val baseUrl by option(
        "-u", "--url", help = "The URL to traverse"
    ).convert { Url(it) }.required()

    private val stopAfterRecursion by option(
        "-s", "--stop-after", help = "The number of recursions to stop crawling after. Default is infinite."
    ).int()

    private val ignoreRegex by option(
        "-i", "--ignoreRegex", help = "The Regex for ignoring"
    )

    private val urlList by option(
        "-l",
        "--list",
        help = "path to a file with a list of urls. You still need to specify the base url. " + "currently only 1 base url is allowed even though you might have multiple urls from " + "different domains. I am not planning on fixing that"
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

    private val visited = ConcurrentSet<Url>()
    private val crawled = ConcurrentSet<Url>()
    private val results = mutableListOf<LinkResult>()

    private val resultsMutex = Mutex()

    private suspend fun findLinksInHttpResponse(parent: String?, httpResponse: HttpResponse, currentUrl: Url): Set<Url> {
        val body = httpResponse.bodyAsChannel().toByteArray()
        val document = runCatching { Ksoup.parse(body.decodeToString()) }.getOrElse { error ->
            logger.warn(error) { "Could not retrieve body of $currentUrl. Skipping ..." }
            results.add(
                LinkResult(
                    parent,
                    currentUrl.toString(),
                    errorMsg = "[${error::class.simpleName ?: ""}]: ${error.message ?: ""}"
                )
            )
            return emptySet()
        }
        val links = document.select("a[href]").asSequence().map {
            it.attr("href")
        }.filter { it.isNotBlank() }.filter {
            try {
                Url(it)
                true // continue checking this expression
            } catch (e: Exception) {
                logger.warn { "Failed to build Url object from: $it" }
                results.add(LinkResult(parent, it, errorMsg = "[${e::class.simpleName ?: ""}]: ${e.message ?: ""}"))
                false // add a LinkResult for invalid urls
            }
        }.mapToAbsoluteUrls(onlyForDomainFrom = currentUrl, allowedProtocols = allowedProtocols)
            .filterNot { ignoreRegex != null && it.contains(ignoreRegex!!.toRegex()) }.map(::Url).toSet()

        return links
    }

    private fun CoroutineScope.checkRecursive(
        parent: String?, currentUrl: Url, client: HttpClient, recursionStep: Int
    ): Job = launch {
        if (stopAfterRecursion != null && recursionStep > stopAfterRecursion!!) {
            logger.debug { "Stopping after $stopAfterRecursion recursions on $currentUrl" }
            return@launch
        }
        if (currentUrl in crawled) {
            val existingResult = resultsMutex.withLock { results.find { it.url == currentUrl.toString() } }
            if (existingResult == null) {
                logger.warn { "A link was in the visited list without having a link result in the results list" }
                logger.debug { "Link with the problem: $currentUrl" }
            } else {
                if (existingResult.errorMsg != null || (existingResult.status != null && !existingResult.status.isSuccess())) {
                    resultsMutex.withLock {
                        results.add(
                            existingResult.copy(parent = parent, url = currentUrl.toString())
                        )
                    }
                }
            }
            logger.debug { "Already crawled $currentUrl. ignoring..." }
            return@launch
        }

        // if visited then don't check. otherwise add it to the visited links list and proceed
        if (currentUrl in visited) {
            logger.debug { "Already visited $currentUrl. ignoring..." }
            return@launch
        }
        visited.add(currentUrl)


        val httpResponse = try {
            logger.info { "Checking $currentUrl" }
            client.get(currentUrl)
        } catch (exception: Exception) {
            logger.error(exception) { "Failed to get $currentUrl" }
            resultsMutex.withLock {
                results.add(
                    LinkResult(
                        parent,
                        currentUrl.toString(),
                        errorMsg = "[${exception::class.simpleName ?: ""}]: ${exception.message ?: ""}"
                    )
                )
            }
            return@launch
        }

        var linkResult: LinkResult? = null
        var target: String? = null

        if (httpResponse.status.value in 300..399) {
            // in case we are dealing with a redirect

            when (httpResponse.status.value) {
                300 -> {
                    // https://httpwg.org/specs/rfc9110.html#status.300
                    // this header is barely used. it is in the responsibility of the user agent to decide
                    // what to do here. This is the code for multiple choices, which will return an xml response
                    // that contains multiple options. Servers can declare a preferred option using the Location header
                    // therefore let's send a warning that BLT can not check this link.
                    logger.warn {
                        "Found status code 300 being used: url: " + "$currentUrl\nHere are the headers ${httpResponse.headers.toMap()}"
                    }
                }
                301, 302, 303, 307, 308 -> {
                    // https://httpwg.org/specs/rfc9110.html#status.301
                    // https://httpwg.org/specs/rfc9110.html#status.302
                    // https://httpwg.org/specs/rfc9110.html#status.303
                    // https://httpwg.org/specs/rfc9110.html#status.307
                    // https://httpwg.org/specs/rfc9110.html#status.308
                    // Moved Permanently, Moved Temporarily, See Other, Temporary Redirect, Permanent Redirect.
                    // Location header SHOULD or MUST be included

                    target = httpResponse.headers[HttpHeaders.Location]

                    if (target == null) {
                        println(httpResponse.headers.toMap())
                        logger.warn { "Location header was '$target' for: ${httpResponse.status} at $currentUrl, ${httpResponse.headers.toMap()}" }
                    }
                }
                304 -> {
                    // https://httpwg.org/specs/rfc9110.html#status.304
                    // Not Modified. I'm unsure what to do here.
                    logger.warn {
                        "Received 304 Not Modified for $currentUrl\nHere are the headers: " + "${httpResponse.headers.toMap()}"
                    }
                }
                305 -> {
                    // https://httpwg.org/specs/rfc9110.html#status.305
                    // Use Proxy. This is deprecated and a security problem
                    logger.warn {
                        "Found 305 Use Proxy. Not following instruction due to security concern. url: " + "$currentUrl\nHere are the headers ${httpResponse.headers.toMap()}"
                    }
                }
                306 -> {
                    // https://httpwg.org/specs/rfc9110.html#status.306
                    logger.warn {
                        "306 Unused. Ignoring... url: $currentUrl\nHere are the headers ${httpResponse.headers.toMap()}"
                    }
                }
            }

            val targetUrl: Url? = try {
                Url(target!!)
            } catch (t: Throwable) {
                logger.warn { "Redirect to invalid url $target" }
                null
            }

            linkResult = LinkResult(
                parent = parent,
                url = currentUrl.toString(),
                status = HttpStatusCode.fromValue(httpResponse.status.value),
                redirect = targetUrl?.toString(),
                errorMsg = if (targetUrl == null) "Couldn't determine redirect location from response" else null
            )

            // Yes, this line exists in the other branch of the conditional. but it needs to happen before the recursion
            crawled.add(currentUrl)
            resultsMutex.withLock { results.add(linkResult!!) }

            if (targetUrl != null) {
                // lets not count recursion steps for redirects
                if (!currentUrl.isSimilarHost(baseUrl)) {
                    logger.warn { "The redirecting Url was not from the same host" }
                    // TODO(feature): add option to follow redirects from other domains as long as we make sure to stop
                    return@launch
                }
                checkRecursive(currentUrl.toString(), targetUrl, client, recursionStep = recursionStep).join()
            }
            return@launch
        } else {
            linkResult = LinkResult(
                parent = parent,
                url = currentUrl.toString(),
                // this is necessary to add a description in case of a HTTP 2 server which doesn't return a description
                // theoretically we could leave this, but that would cause all the descriptions to blank until we
                // serialize them so im doing it anyway to reduce the potential for bugs and misunderstandings later.
                status = HttpStatusCode.fromValue(httpResponse.status.value)
            )
        }

        resultsMutex.withLock { results.add(linkResult) }
        crawled.add(currentUrl)

        if (httpResponse.contentType()?.withoutParameters() != ContentType.Text.Html) {
            logger.debug { "Not an html page. Skipping $currentUrl" }
            return@launch
        }

        // if not same domain, only basic check
        if (currentUrl.isSimilarHost(baseUrl)) {
            logger.debug { "Checking links in $currentUrl for recursion step $recursionStep" }
            val links = findLinksInHttpResponse(parent, httpResponse, currentUrl)
            links.toSet().forEach {
                checkRecursive(currentUrl.toString(), it, client, recursionStep = recursionStep + 1).join()
            }
        } else {
            logger.debug { "Not checking links in $currentUrl because it is not the same domain as $baseUrl" }
        }
    }

    override fun run(): Unit = runBlocking(Dispatchers.Default) {
        (LoggerFactory.getLogger(SLF4JLogger.ROOT_LOGGER_NAME) as Logger).level =
            LogbackLevel.convertAnSLF4JLevel(logLevel)
        val urls = mutableSetOf(baseUrl)

        urlList?.let {
            val file = Path(it)
            if (!SystemFileSystem.exists(file)) {
                error("error")
            }
            val list = SystemFileSystem.source(file).buffered().use(Source::readString).lines().map(::Url)

            urls.addAll(list)
        }

        client.use {
            coroutineScope {
                urls.forEach {
                    checkRecursive(null, it, client, recursionStep = 0)
                }
            }
        }

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
