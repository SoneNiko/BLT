package com.sonefall.blt

import com.fleeksoft.ksoup.Ksoup
import com.github.alturkovic.robots.txt.RobotsTxtReader
import com.sonefall.blt.WebCrawler.buildAbsoluteUrl
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.collections.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CoroutineScope

fun Url.isSimilarHost(other: Url) =
    host.replace("^www\\.".toRegex(), "") == other.host.replace("^www\\.".toRegex(), "")

fun Sequence<String>.mapToAbsoluteUrls(
    onlyForDomainFrom: Url,
    dropFragment: Boolean = false,
    allowedProtocols: List<URLProtocol>
) = asSequence()
    .map(::Url)
    .filter { it.protocol in allowedProtocols }
    .map { buildAbsoluteUrl(it, onlyForDomainFrom, dropFragment) }
    .map(Url::toString)

private val allowedProtocols = listOf(URLProtocol.HTTP, URLProtocol.HTTPS)

object WebCrawler {
    private val client = HttpClient {
        expectSuccess = false
        followRedirects = false
    }

    private val redirectFollowingClient = HttpClient {
        expectSuccess = false
        followRedirects = true
    }

    private val visited = ConcurrentSet<Url>()
    private val crawled = ConcurrentSet<Url>()
    private val robotsFiles = ConcurrentMap<String, String>()
    private val results = mutableListOf<LinkResult>()
    private val resultsMutex = Mutex()

    suspend fun crawl(command: BLT): MutableList<LinkResult> {
        val urls = mutableSetOf(command.baseUrl)

        command.urlList?.let {
            val file = Path(it)
            if (!SystemFileSystem.exists(file)) error("URL list file not found")
            urls.addAll(readUrlsFromFile(file))
        }

        client.use {
            coroutineScope {
                urls.forEach { checkRecursive(command, null, it, 0) }
            }
        }

        return results
    }

    private fun CoroutineScope.checkRecursive(
        command: BLT, parent: String?, currentUrl: Url, recursionStep: Int
    ): Job = launch {
        if (shouldStopRecursion(command, recursionStep, currentUrl)) return@launch
        if (alreadyVisitedOrCrawled(currentUrl)) return@launch
        if (isRobotsExcluded(currentUrl)) return@launch

        val httpResponse = fetchUrl(currentUrl, parent, command) ?: return@launch
        handleResponse(command, parent, currentUrl, httpResponse, recursionStep)
    }

    fun buildAbsoluteUrl(relativeUrl: Url, baseUrl: Url, dropFragment: Boolean): Url {
        return if (relativeUrl.host == "localhost") {
            URLBuilder(baseUrl).apply {
                if (relativeUrl.encodedPath != "/" && relativeUrl.encodedPath.startsWith("/")) {
                    parameters.clear()
                    encodedPathSegments = relativeUrl.pathSegments
                } else {
                    appendEncodedPathSegments(relativeUrl.pathSegments)
                }
                fragment = if (dropFragment) "" else relativeUrl.fragment
                parameters.appendAll(relativeUrl.parameters)
            }.build()
        } else {
            relativeUrl
        }
    }

    private suspend fun findLinksInHttpResponse(
        command: BLT, parent: String?, httpResponse: HttpResponse, currentUrl: Url
    ): Set<Url> {
        val body = httpResponse.bodyAsChannel().toByteArray()
        val document = runCatching { Ksoup.parse(body.decodeToString()) }.getOrElse { error ->
            addResultError(currentUrl, parent, error)
            return emptySet()
        }

        return document.select("a[href]").asSequence()
            .map { it.attr("href") }
            .filter { it.isNotBlank() }
            .mapToAbsoluteUrls(currentUrl, allowedProtocols = allowedProtocols)
            .filterNot { command.ignoreRegex?.toRegex()?.let(it::contains) ?: false }
            .map(::Url)
            .toSet()
    }

    private suspend fun addResultError(currentUrl: Url, parent: String?, throwable: Throwable) {
        logger.warn(throwable) { "Failure processing $currentUrl. Skipping..." }

        addResult(
            LinkResult(
                parent = parent,
                url = currentUrl.toString(),
                errorMsg = "[${throwable::class.simpleName ?: ""}]: ${throwable.message ?: ""}"
            )
        )
    }

    private suspend fun addResult(linkResult: LinkResult) = resultsMutex.withLock { results.add(linkResult) }

    private fun shouldStopRecursion(command: BLT, recursionStep: Int, currentUrl: Url): Boolean {
        if (command.stopAfterRecursion != null && recursionStep > command.stopAfterRecursion!!) {
            logger.debug { "Stopping after ${command.stopAfterRecursion} recursions on $currentUrl" }
            return true
        }
        return false
    }

    private fun alreadyVisitedOrCrawled(currentUrl: Url): Boolean {
        if (currentUrl in crawled || currentUrl in visited) {
            logger.debug { "Already visited/crawled $currentUrl. ignoring..." }
            return true
        }
        visited.add(currentUrl)
        return false
    }

    private suspend fun isRobotsExcluded(currentUrl: Url): Boolean {
        val robotsFile: String = robotsFiles.getOrPut(currentUrl.host) { fetchMissingRobotsFile(currentUrl) }

        val parsedRobotsFile = blocking { RobotsTxtReader.read(ByteArrayInputStream(robotsFile.toByteArray())) }
        val grant = parsedRobotsFile.query("BLT", currentUrl.encodedPathAndQuery)

        return !grant.allowed
    }

    private suspend fun fetchMissingRobotsFile(currentUrl: Url): String {
        val httpResponse = redirectFollowingClient.get(currentUrl) {
            url {
                path("robots.txt")
            }
        }
        val status = httpResponse.status.value

        return if (status in 500..599 || (status in 400..499 && status != 404)) {
            // if there is any failure indicating code that is not a 404 we assume that we are not allowed to crawl
            """User-agent: *
                |Disallow: /""".trimMargin()
        } else {
            httpResponse.bodyAsText()
        }
    }
    
    private suspend fun fetchUrl(currentUrl: Url, parent: String?, command: BLT): HttpResponse? {
        return try {
            logger.info { "Checking $currentUrl" }
            client.get(currentUrl) { headers { userAgent(command.userAgent ?: "Ktor client") } }
        } catch (exception: Exception) {
            logger.error(exception) { "Failed to get $currentUrl" }
            addResultError(currentUrl, parent, exception)
            null
        }
    }

    private suspend fun handleResponse(
        command: BLT, parent: String?, currentUrl: Url, httpResponse: HttpResponse, recursionStep: Int
    ) {
        val statusCode = httpResponse.status.value
        when (statusCode) {
            in 300..399 -> handleRedirect(command, parent, currentUrl, httpResponse, recursionStep)
            else -> handleNonRedirectResponse(command, parent, currentUrl, httpResponse, recursionStep)
        }
    }

    private suspend fun handleRedirect(
        command: BLT, parent: String?, currentUrl: Url, httpResponse: HttpResponse, recursionStep: Int
    ) {
        val target = httpResponse.headers[HttpHeaders.Location]
        val targetUrl = target?.let { runCatching { Url(it) }.getOrNull() }

        val linkResult = LinkResult(
            parent = parent,
            url = currentUrl.toString(),
            status = HttpStatusCode.fromValue(httpResponse.status.value),
            redirect = targetUrl?.toString(),
            errorMsg = if (targetUrl != null) null else "Couldn't determine redirect location"
        )
        addResult(linkResult)

        if (targetUrl != null && currentUrl.isSimilarHost(command.baseUrl)) {
            coroutineScope {
                checkRecursive(command, currentUrl.toString(), targetUrl, recursionStep).join()
            }
        }
    }

    private suspend fun handleNonRedirectResponse(
        command: BLT, parent: String?, currentUrl: Url, httpResponse: HttpResponse, recursionStep: Int
    ) {
        val linkResult = LinkResult(
            parent = parent, url = currentUrl.toString(), status = HttpStatusCode.fromValue(httpResponse.status.value)
        )
        addResult(linkResult)
        crawled.add(currentUrl)

        if (httpResponse.contentType()?.withoutParameters() != ContentType.Text.Html) {
            logger.debug { "Not an HTML page. Skipping $currentUrl" }
            return
        }

        if (currentUrl.isSimilarHost(command.baseUrl)) {
            val links = findLinksInHttpResponse(command, parent, httpResponse, currentUrl)
            links.forEach {
                coroutineScope {
                    checkRecursive(command, currentUrl.toString(), it, recursionStep + 1).join()
                }
            }
        }
    }

    private fun readUrlsFromFile(file: Path): List<Url> {
        return SystemFileSystem.source(file).buffered().use(Source::readString).lines().map(::Url)
    }
}
