package com.morimil.app.ui

import com.morimil.app.web.NativeWebRequest
import com.morimil.app.web.WebSearchIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeWebSearchResultParserTest {
    @Test
    fun ranksOfficialAndroidSourceAndRejectsUnsafeOrLowValueLinks() {
        val html = """
            <html><body>
              <a href="https://youtube.com/watch?v=bad">Android video</a>
              <a href="http://developer.android.com/unsafe">HTTP result</a>
              <a href="https://example.com/login">Android login</a>
              <a href="https://stackoverflow.com/questions/1">Android WebView answer</a>
              <a href="https://developer.android.com/privacy-and-security/risks/webview">
                Official Android WebView security documentation
              </a>
            </body></html>
        """.trimIndent()

        val candidates = NativeWebSearchResultParser.selectCandidates(
            html = html,
            request = request("Android WebView security", WebSearchIntent.ANDROID_CODE)
        )

        assertEquals("developer.android.com", candidates.first().host)
        assertTrue(candidates.any { it.host == "stackoverflow.com" })
        assertFalse(candidates.any { it.host == "youtube.com" })
        assertFalse(candidates.any { it.url.startsWith("http://") })
        assertFalse(candidates.any { "/login" in it.url })
    }

    @Test
    fun decodesBraveRedirectAndKeepsOnlyBestResultPerHost() {
        val html = """
            <html><body>
              <a href="https://github.com/example/project">short</a>
              <a href="https://search.brave.com/redirect?source=web&amp;url=https%3A%2F%2Fdocs.github.com%2Fen%2Fpull-requests">
                Official GitHub pull request documentation
              </a>
              <a href="https://github.com/example/project/issues">
                GitHub project issue and pull request workflow
              </a>
            </body></html>
        """.trimIndent()

        val candidates = NativeWebSearchResultParser.selectCandidates(
            html = html,
            request = request("GitHub pull request", WebSearchIntent.GITHUB_PROJECT)
        )

        assertEquals("docs.github.com", candidates.first().host)
        assertTrue(candidates.first().url.startsWith("https://docs.github.com/"))
        assertEquals(1, candidates.count { it.host == "github.com" })
    }

    @Test
    fun parserOutputIsBounded() {
        val html = buildString {
            append("<html><body>")
            repeat(300) { index ->
                append("<a href=\"https://host$index.example/docs\">Useful documentation $index</a>")
            }
            append("</body></html>")
        }

        val candidates = NativeWebSearchResultParser.selectCandidates(
            html = html,
            request = request("useful documentation", WebSearchIntent.DOCUMENTATION)
        )

        assertTrue(candidates.size <= 5)
        assertTrue(candidates.all { it.url.length <= 600 })
        assertTrue(candidates.all { it.title.length <= 160 })
    }

    private fun request(query: String, intent: WebSearchIntent): NativeWebRequest {
        return NativeWebRequest(
            query = query,
            searchQuery = query,
            intent = intent,
            requestedAtMillis = 1L
        )
    }
}
