package com.morimil.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeWebDocumentHardenerTest {
    @Test
    fun stripsActiveNetworkCapableMarkupAndInjectsRestrictivePolicy() {
        val hardened = SafeWebDocumentHardener.hardenHtml(
            """
                <html><head>
                  <base href="https://private.example/">
                  <meta http-equiv="refresh" content="0;url=https://127.0.0.1/">
                  <script>fetch('https://127.0.0.1/')</script>
                </head><body onload="alert(1)"><h1>Public text</h1></body></html>
            """.trimIndent()
        )

        assertTrue(hardened.contains("Content-Security-Policy"))
        assertTrue(hardened.contains("connect-src 'none'"))
        assertTrue(hardened.contains("Public text"))
        assertFalse(hardened.contains("http-equiv=\"refresh\"", ignoreCase = true))
        assertFalse(hardened.contains("<base", ignoreCase = true))
        assertFalse(hardened.contains("<script", ignoreCase = true))
        assertFalse(hardened.contains("onload=", ignoreCase = true))
    }

    @Test
    fun plainTextIsEscapedBeforeRendering() {
        val wrapped = SafeWebDocumentHardener.wrapPlainText("<script>bad()</script> & text")

        assertTrue(wrapped.contains("&lt;script&gt;bad()&lt;/script&gt;"))
        assertTrue(wrapped.contains("&amp; text"))
        assertFalse(wrapped.contains("<script>bad()"))
    }

    @Test
    fun extractsStaticTextWithoutExecutingOrReturningActiveMarkup() {
        val document = SafeWebDocument(
            finalUrl = "https://example.com/evidence",
            html = """
                <!doctype html>
                <html>
                <head>
                    <title>Morimil &amp; evidencia</title>
                    <style>.hidden { color: red; }</style>
                </head>
                <body>
                    <h1>Evidencia&nbsp;publica</h1>
                    <p>Primera &amp; segunda linea.</p>
                    <script>fetch('https://private.example')</script>
                    <noscript>contenido condicional</noscript>
                    <div>Cohete &#x1f680;</div>
                </body>
                </html>
            """.trimIndent()
        )

        val extracted = SafeWebDocumentTextExtractor.extract(document, maxTextChars = 12_000)

        assertEquals("Morimil & evidencia", extracted.title)
        assertTrue(extracted.text.contains("Evidencia publica"))
        assertTrue(extracted.text.contains("Primera & segunda linea."))
        assertTrue(extracted.text.contains("Cohete 🚀"))
        assertFalse(extracted.text.contains(".hidden"))
        assertFalse(extracted.text.contains("fetch("))
        assertFalse(extracted.text.contains("contenido condicional"))
    }

    @Test
    fun extractionLimitIsFailClosedAndDeterministic() {
        val document = SafeWebDocument(
            finalUrl = "https://example.com/limited",
            html = "<html><body><p>123456789</p></body></html>"
        )

        val extracted = SafeWebDocumentTextExtractor.extract(document, maxTextChars = 5)

        assertEquals("12345", extracted.text)
    }
}
