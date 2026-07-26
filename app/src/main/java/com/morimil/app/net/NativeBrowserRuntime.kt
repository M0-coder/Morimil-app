package com.morimil.app.net

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NativeBrowserRuntime {
    @Volatile
    private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    fun renderedFetcher(): NetRenderedFetcher {
        return NetRenderedFetcher { rawUrl ->
            if (appContext == null) {
                return@NetRenderedFetcher NetRenderedResult(
                    ok = false,
                    error = "browser_context_missing"
                )
            }
            NativeBrowserReader().read(rawUrl)
        }
    }
}

internal class NativeBrowserReader(
    private val documentLoader: SafeWebDocumentLoader = SafeWebDocumentLoader()
) {
    suspend fun read(rawUrl: String): NetRenderedResult {
        val initialPolicy = NetSourcePolicy.validateUrl(rawUrl)
        if (!initialPolicy.allowed) {
            return NetRenderedResult(ok = false, error = "browser_source_denied:${initialPolicy.reason}")
        }

        val fetched = withContext(Dispatchers.IO) { documentLoader.fetch(rawUrl) }
        val document = fetched.document
            ?: return NetRenderedResult(
                ok = false,
                error = "browser_fetch_failed:${fetched.error.orEmpty().take(160)}"
            )
        val finalPolicy = NetSourcePolicy.validateUrl(document.finalUrl)
        if (!finalPolicy.allowed) {
            return NetRenderedResult(
                ok = false,
                error = "browser_final_url_denied:${finalPolicy.reason}"
            )
        }

        val extracted = SafeWebDocumentTextExtractor.extract(
            document = document,
            maxTextChars = MAX_RENDERED_TEXT_CHARS
        )
        val text = buildString {
            if (extracted.title.isNotBlank()) appendLine(extracted.title)
            append(extracted.text)
        }.trim().take(MAX_RENDERED_TEXT_CHARS)
        return NetRenderedResult(
            ok = text.isNotBlank(),
            text = text,
            error = if (text.isBlank()) "browser_empty_text" else null
        )
    }

    companion object {
        private const val MAX_RENDERED_TEXT_CHARS = 40_000
    }
}

internal fun blockedWebResponse(reason: String): WebResourceResponse {
    return WebResourceResponse(
        "text/plain",
        Charsets.UTF_8.name(),
        403,
        "Blocked",
        mapOf("X-Morimil-Block-Reason" to reason.take(120)),
        ByteArrayInputStream(ByteArray(0))
    )
}

data class NetRenderedResult(
    val ok: Boolean,
    val text: String = "",
    val error: String? = null
)

fun interface NetRenderedFetcher {
    suspend fun fetch(rawUrl: String): NetRenderedResult
}
