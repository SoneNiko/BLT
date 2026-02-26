import com.sonefall.blt.BLT
import com.sonefall.blt.WebCrawler
import com.sonefall.blt.isSimilarHost
import com.sonefall.blt.mapToAbsoluteUrls
import io.ktor.http.*
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebCrawlerTests {

    private lateinit var command: BLT
    private val allowedProtocols = listOf(URLProtocol.HTTP, URLProtocol.HTTPS)

    @BeforeEach
    fun setUp() {
        command = mockk()
        every { command.ignoreRegex } returns null // or provide a regex if needed
    }

    @Test
    fun `isSimilarHost should return true if the 2 urls are from a similar host (treat www as nonexistent)`() {

        val a = Url("https://www.sonefall.com")
        val b = Url("https://sonefall.com")
        val c = Url("https://schlau.bi")
        val d = Url("https://www.schlau.bi")

        assertTrue(a.isSimilarHost(a))
        assertTrue(a.isSimilarHost(b))
        assertFalse(a.isSimilarHost(c))
        assertFalse(a.isSimilarHost(d))
    }

    @Test
    fun `mapToAbsoluteUrls should map relative urls to absolute urls for the same domain`() {
        // Arrange
        val urls = sequenceOf(
            "http://example.com/path",
            "https://example.com/anotherpath",
            "/relativePath#fragment",
            "/anotherRelativePath"
        )
        val baseDomain = Url("http://example.com")

        val expected = listOf(
            "http://example.com/path",
            "https://example.com/anotherpath",
            "http://example.com/relativePath",
            "http://example.com/anotherRelativePath"
        )

        // Act
        val result = urls.mapToAbsoluteUrls(
            onlyForDomainFrom = baseDomain,
            dropFragment = true,  // Dropping fragments
            allowedProtocols = allowedProtocols
        ).toList()

        // Assert
        assertEquals(expected, result)
    }

    @Test
    fun `mapToAbsoluteUrls should filter out unsupported protocols`() {
        // Arrange
        val urls = sequenceOf(
            "http://example.com/path",
            "https://example.com/anotherpath",
            "ftp://example.com/notallowed"
        )
        val baseDomain = Url("http://example.com")
        val expected = listOf(
            "http://example.com/path",
            "https://example.com/anotherpath"
        )

        // Act
        val result = urls.mapToAbsoluteUrls(
            onlyForDomainFrom = baseDomain,
            dropFragment = false,
            allowedProtocols = allowedProtocols
        ).toList()

        // Assert
        assertEquals(expected, result)
    }

    @Test
    fun `mapToAbsoluteUrls should keep fragments if dropFragment is false`() {
        // Arrange
        val urls = sequenceOf(
            "http://example.com/path#section",
            "/relativePath#fragment"
        )
        val baseDomain = Url("http://example.com")
        val expected = listOf(
            "http://example.com/path#section",
            "http://example.com/relativePath#fragment"
        )

        // Act
        val result = urls.mapToAbsoluteUrls(
            onlyForDomainFrom = baseDomain,
            dropFragment = false,  // Do not drop fragments
            allowedProtocols = allowedProtocols
        ).toList()

        // Assert
        assertEquals(expected, result)
    }

    @Test
    fun `buildAbsoluteUrl should return base URL when relative URL host is localhost`() {
        val baseUrl = Url("http://example.com/")
        val relativeUrl = Url("http://localhost/path")
        val result = WebCrawler.buildAbsoluteUrl(relativeUrl, baseUrl, dropFragment = false)

        assertEquals("http://example.com/path", result.toString())
    }

    @Test
    fun `buildAbsoluteUrl should append relative path when it starts with a slash`() {
        val baseUrl = Url("http://example.com/")
        val relativeUrl = Url("http://localhost/newPath")
        val result = WebCrawler.buildAbsoluteUrl(relativeUrl, baseUrl, dropFragment = false)

        assertEquals("http://example.com/newPath", result.toString())
    }

    @Test
    fun `buildAbsoluteUrl should keep parameters when relative path starts with slash`() {
        val baseUrl = Url("http://example.com/")
        val relativeUrl = Url("http://localhost/path?param=value")
        val result = WebCrawler.buildAbsoluteUrl(relativeUrl, baseUrl, dropFragment = false)

        assertEquals("http://example.com/path?param=value", result.toString())
    }

    @Test
    fun `buildAbsoluteUrl should drop fragment if dropFragment is true`() {
        val baseUrl = Url("http://example.com/")
        val relativeUrl = Url("http://localhost/path#fragment")
        val result = WebCrawler.buildAbsoluteUrl(relativeUrl, baseUrl, dropFragment = true)

        assertEquals("http://example.com/path", result.toString())
    }

    @Test
    fun `buildAbsoluteUrl should keep fragment if dropFragment is false`() {
        val baseUrl = Url("http://example.com/")
        val relativeUrl = Url("http://localhost/path#fragment")
        val result = WebCrawler.buildAbsoluteUrl(relativeUrl, baseUrl, dropFragment = false)

        assertEquals("http://example.com/path#fragment", result.toString())
    }

    @Test
    fun `buildAbsoluteUrl should return relativeUrl if host is not localhost`() {
        val baseUrl = Url("http://example.com/")
        val relativeUrl = Url("http://otherhost/path")
        val result = WebCrawler.buildAbsoluteUrl(relativeUrl, baseUrl, dropFragment = false)

        assertEquals("http://otherhost/path", result.toString())
    }

}