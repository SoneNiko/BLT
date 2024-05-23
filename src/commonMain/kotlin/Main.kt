import com.fleeksoft.ksoup.Ksoup
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.collections.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class LinkResult(val url: String, val status: String? = null, val errorMsg: String = "")

fun main(args: Array<String>) = Check().main(args)

private val allowedProtocols = listOf(URLProtocol.HTTP, URLProtocol.HTTPS)

fun Sequence<String>.mapToAbsoluteUrls(onlyForDomainFrom: Url) =
    asSequence()
        .map(::Url)
        .filter { it.protocol in allowedProtocols }
        .map {
            if (it.host == "localhost") {
                // if the host is localhost, the link is relative
                URLBuilder(onlyForDomainFrom).apply {
                    if (it.encodedPath.startsWith("/")) {
                        parameters.clear()
                        encodedPathSegments = it.pathSegments
                    } else {
                        appendEncodedPathSegments(it.pathSegments)
                    }
                    fragment = it.fragment
                    parameters.appendAll(it.parameters)
                }.build()
            } else {
                it
            }
        }
        .filter { it.host == onlyForDomainFrom.host }
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

    private suspend fun filterLinks(httpResponse: HttpResponse, currentUrl: Url): Set<Url> {
        val document = Ksoup.parse(httpResponse.bodyAsText())
        val links = document.select("a[href]")
            .asSequence()
            .map {
                it.attr("href")
            }
            .filter { it.isNotBlank() }
            .mapToAbsoluteUrls(currentUrl)
            .filterNot { ignoreRegex != null && it.contains(ignoreRegex!!.toRegex()) }
            .map(::Url)
            .toSet()

        return links
    }

    private fun CoroutineScope.checkRecursive(currentUrl: Url, client: HttpClient, recursionStep: Int): Job = launch {

        if (stopAfterRecursion != null && recursionStep > stopAfterRecursion!!) return@launch

        // if visited then dont check. otherwise add it to the visited links list and proceed
        if (currentUrl in visited) return@launch
        visited.add(currentUrl)

        val httpResponse = try {
            client.get(currentUrl)
        } catch (exception: IOException) {
            results.add(
                LinkResult(
                    currentUrl.toString(),
                    errorMsg = "[${exception::class.simpleName ?: ""}]: ${exception.message ?: ""}"
                )
            )
            return@launch
        }

        if (httpResponse.contentType()?.withoutParameters() != ContentType.Text.Html) return@launch
        results.add(LinkResult(currentUrl.toString(), HttpStatusCode.fromValue(httpResponse.status.value).toString()))

        // if not same domain, only basic check
        if (currentUrl.host == baseUrl.host) {
            val links = filterLinks(httpResponse, currentUrl)
            links.forEach { checkRecursive(it, client, recursionStep = recursionStep + 1) }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun run(): Unit = runBlocking(Dispatchers.Default) {

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
                    checkRecursive(it, client, recursionStep = 0)
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
