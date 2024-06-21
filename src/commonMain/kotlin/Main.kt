package com.sonefall.blt

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
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class LinkResult(val parent: String?, val url: String, val status: String? = null, val errorMsg: String = "")

val logger by lazy { KotlinLogging.logger {} }

fun main(args: Array<String>) = Check().main(args)

private val allowedProtocols = listOf(URLProtocol.HTTP, URLProtocol.HTTPS)

private fun Url.isSimilarHost(other: Url) =
    host.replace("^www\\.".toRegex(), "") == other.host.replace("^www\\.".toRegex(), "")

fun Sequence<String>.mapToAbsoluteUrls(onlyForDomainFrom: Url, dropFragment: Boolean = false) =
    asSequence()
        .map(::Url)
        .filter { it.protocol in allowedProtocols }
        .map {
            if (it.host == "localhost") {
                // if the host is localhost, the link is relative
                return@map URLBuilder(onlyForDomainFrom).apply {
                    // encodedPath of relative url "#comment-1234" is "/", but actual path is still relative
                    if (it.encodedPath != "/" && it.encodedPath.startsWith("/")) {
                        parameters.clear()
                        encodedPathSegments = it.pathSegments
                    } else {
                        appendEncodedPathSegments(it.pathSegments)
                    }
                    fragment = if (dropFragment) {
                        ""
                    } else {
                        it.fragment
                    }
                    parameters.appendAll(it.parameters)
                }.build()
            } else {
                it
            }
        }
        .map(Url::toString)

val client = HttpClient {
    expectSuccess = false
}

class Check : CliktCommand() {
    private val baseUrl by option(
        "-u",
        "--url",
        help = "The URL to traverse"
    ).convert { Url(it) }.required()

    private val stopAfterRecursion by option(
        "-s",
        "--stop-after",
        help = "The number of recursions to stop crawling after. Default is infinite."
    ).int()

    private val ignoreRegex by option(
        "-i",
        "--ignoreRegex",
        help = "The Regex for ignoring"
    )

    private val urlList by option(
        "-l",
        "--list",
        help = "path to a file with a list of urls. You still need to specify the base url. " +
            "currently only 1 base url is allowed even though you might have multiple urls from " +
            "different domains. I am not planning on fixing that"
    )

    private val saveToFile by option(
        "-o",
        "--output-file",
        help = "The file to save for."
    )

    private val logLevel by option(
        "-L",
        "--log-level",
        help = "The log level to log at"
    ).enum<LogLevel>().default(LogLevel.INFO)

    private val dontPrintResult by option(
        "--dont-print-result",
        help = "Whether to print the result to stdout"
    ).flag()

    private val prettyPrint: Boolean by option(
        "--pretty-print",
        help = "Whether to pretty print the json output"
    ).flag()

    private val visited = ConcurrentSet<Url>()
    private val results = mutableListOf<LinkResult>()

    private suspend fun filterLinks(parent: String?, httpResponse: HttpResponse, currentUrl: Url): Set<Url> {
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
        val links = document.select("a[href]")
            .asSequence()
            .map {
                it.attr("href")
            }
            .filter { it.isNotBlank() }
            .filter {
                try {
                    Url(it)
                    true // continue checking this expression
                } catch (e: Exception) {
                    logger.warn { "Failed to build Url object from: $it" }
                    results.add(LinkResult(parent, it, errorMsg = "[${e::class.simpleName ?: ""}]: ${e.message ?: ""}"))
                    false// add a linkresult for invalid urls
                }
            }
            .mapToAbsoluteUrls(currentUrl)
            .filterNot { ignoreRegex != null && it.contains(ignoreRegex!!.toRegex()) }
            .map(::Url)
            .toSet()

        return links
    }

    private fun CoroutineScope.checkRecursive(
        parent: String?,
        currentUrl: Url,
        client: HttpClient,
        recursionStep: Int
    ): Job = launch {
        if (stopAfterRecursion != null && recursionStep > stopAfterRecursion!!) {
            logger.debug { "Stopping after $stopAfterRecursion recursions on $currentUrl" }
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
            results.add(
                LinkResult(
                    parent,
                    currentUrl.toString(),
                    errorMsg = "[${exception::class.simpleName ?: ""}]: ${exception.message ?: ""}"
                )
            )
            return@launch
        }

        if (httpResponse.contentType()?.withoutParameters() != ContentType.Text.Html) {
            logger.debug { "Not an html page. Skipping $currentUrl" }
            return@launch
        }
        results.add(
            LinkResult(
                parent,
                currentUrl.toString(),
                HttpStatusCode.fromValue(httpResponse.status.value).toString()
            )
        )

        // if not same domain, only basic check
        if (currentUrl.isSimilarHost(baseUrl)) {
            logger.debug { "Checking links in $currentUrl for recursion step $recursionStep" }
            val links = filterLinks(parent, httpResponse, currentUrl)
            links.forEach { checkRecursive(currentUrl.toString(), it, client, recursionStep = recursionStep + 1) }
        } else {
            logger.debug { "Not checking links in $currentUrl because it is not the same domain as $baseUrl" }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun run(): Unit = runBlocking(Dispatchers.Default) {

        configureLogging(logLevel)

        val urls = mutableSetOf(baseUrl)

        urlList?.let {
            val file = Path(it)
            if (!SystemFileSystem.exists(file)) {
                error("error")
            }
            val list = SystemFileSystem.source(file)
                .buffered()
                .use(Source::readString)
                .lines()
                .map(::Url)

            urls.addAll(list)
        }

        client.use {
            coroutineScope {
                urls.forEach {
                    checkRecursive(null, it, client, recursionStep = 0)
                }
            }
        }


        val mapper = Json { prettyPrint = this@Check.prettyPrint }
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
