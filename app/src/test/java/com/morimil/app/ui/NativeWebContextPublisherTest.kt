package com.morimil.app.ui

import com.morimil.app.net.SafeWebDocument
import com.morimil.app.web.NativeWebRequest
import com.morimil.app.web.WebSearchIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class NativeWebContextPublisherTest {
    @Test
    fun finalRedirectHostCannotInheritCandidateTrustOrScore() {
        val source = NativeSelectedWebSource(
            url = "https://docs.github.com/en/example",
            title = "Official GitHub documentation",
            host = "docs.github.com",
            score = 140,
            reason = "documentacion oficial GitHub"
        )
        val document = SafeWebDocument(
            finalUrl = "https://untrusted.example/redirected",
            html = """
                <html><head><title>Redirected page</title></head><body>
                <p>${"evidencia estatica ".repeat(40)}</p>
                <script>fetch('https://private.example')</script>
                </body></html>
            """.trimIndent()
        )

        val capture = NativeWebContextPublisher.captureDocument(
            document = document,
            request = request(),
            selectedSource = source
        )

        assertNotNull(capture)
        val verifiedCapture = requireNotNull(capture)
        assertEquals("untrusted.example", verifiedCapture.source?.host)
        assertEquals(0, verifiedCapture.source?.score)
        assertEquals("LOW", verifiedCapture.confidence)
        assertFalse(verifiedCapture.text.contains("fetch("))
    }

    private fun request(): NativeWebRequest {
        return NativeWebRequest(
            query = "GitHub documentation",
            searchQuery = "GitHub documentation",
            intent = WebSearchIntent.GITHUB_PROJECT,
            requestedAtMillis = 1L
        )
    }
}
