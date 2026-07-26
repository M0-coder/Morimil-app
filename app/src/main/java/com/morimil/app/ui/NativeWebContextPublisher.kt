package com.morimil.app.ui

import com.morimil.app.net.NetSourcePolicy
import com.morimil.app.net.SafeWebDocument
import com.morimil.app.net.SafeWebDocumentTextExtractor
import com.morimil.app.web.NativeWebContextStore
import com.morimil.app.web.NativeWebPageContext
import com.morimil.app.web.NativeWebRequest

internal object NativeWebContextPublisher {
    fun captureDocument(
        document: SafeWebDocument,
        request: NativeWebRequest,
        selectedSource: NativeSelectedWebSource?
    ): NativeWebCapture? {
        val finalDecision = NetSourcePolicy.validateUrl(document.finalUrl)
        if (!finalDecision.allowed) return null

        return runCatching {
            val extracted = SafeWebDocumentTextExtractor.extract(
                document = document,
                maxTextChars = MAX_DOCUMENT_CAPTURE_CHARS
            )
            val text = extracted.text.trim()
            if (text.length < MIN_DOCUMENT_CAPTURE_CHARS) return@runCatching null

            val url = document.finalUrl
            val finalHost = hostFromDisplayUrl(url)
            val effectiveSource = selectedSource?.let { source ->
                val hostChanged = source.host != finalHost
                source.copy(
                    url = url,
                    host = finalHost,
                    score = if (hostChanged) 0 else source.score,
                    reason = if (hostChanged) {
                        "${source.reason}; final_redirect_host_changed=${source.host}->$finalHost"
                    } else {
                        source.reason
                    }.take(MAX_SOURCE_REASON_CHARS)
                )
            }
            val title = extracted.title.ifBlank { effectiveSource?.title.orEmpty() }.take(MAX_TITLE_CHARS)
            val host = effectiveSource?.host ?: finalHost
            val score = effectiveSource?.score ?: 0
            val confidence = NativeWebEvidenceRules.confidence(host = host, score = score, textChars = text.length)
            NativeWebCapture(
                title = title,
                url = url,
                text = text,
                textChars = text.length,
                source = effectiveSource,
                confidence = confidence,
                evidenceGate = webEvidenceGateText(
                    request = request,
                    selectedSource = effectiveSource,
                    url = url,
                    textChars = text.length
                )
            )
        }.getOrNull()
    }

    fun publishWebContext(
        request: NativeWebRequest,
        primary: NativeWebCapture?,
        secondary: NativeWebCapture?,
        navigationTrace: String,
        verifier: NativeMultiSourceVerification,
        retryCount: Int
    ) {
        val title = primary?.title ?: secondary?.title ?: "Sin captura suficiente"
        val url = primary?.url ?: secondary?.url ?: "about:blank"
        val contextText = buildString {
            appendLine("FUENTE_EXTERNA")
            appendLine("modo=web_nativa_multisource")
            appendLine("query_original=${request.query}")
            appendLine("query_busqueda=${request.searchQuery}")
            appendLine("intent=${request.intent}")
            appendLine("strategy=${request.strategy}")
            appendLine("research_retry_count=$retryCount")
            appendLine(multiSourceVerifierText(verifier))
            appendLine(navigationTrace)
            primary?.let { capture ->
                appendLine("PRIMARY_SOURCE")
                appendLine(capture.evidenceGate)
                capture.source?.let { source ->
                    appendLine("selected_source_url=${source.url}")
                    appendLine("selected_source_host=${source.host}")
                    appendLine("selected_source_score=${source.score}")
                    appendLine("selected_source_reason=${source.reason}")
                }
                appendLine("title=${capture.title}")
                appendLine("url=${capture.url}")
                appendLine("content:")
                appendLine(capture.text.take(MAX_PRIMARY_CAPTURED_TEXT_CHARS))
            }
            secondary?.let { capture ->
                appendLine("SECONDARY_SOURCE")
                appendLine(capture.evidenceGate)
                capture.source?.let { source ->
                    appendLine("secondary_source_url=${source.url}")
                    appendLine("secondary_source_host=${source.host}")
                    appendLine("secondary_source_score=${source.score}")
                    appendLine("secondary_source_reason=${source.reason}")
                }
                appendLine("title=${capture.title}")
                appendLine("url=${capture.url}")
                appendLine("content:")
                appendLine(capture.text.take(MAX_SECONDARY_CAPTURED_TEXT_CHARS))
            }
            if (primary == null && secondary == null) {
                appendLine("capture_status=failed")
                appendLine("content:")
                appendLine("No se pudo capturar evidencia web suficiente para esta busqueda.")
            }
        }
        NativeWebContextStore.update(
            NativeWebPageContext(
                title = title,
                url = url,
                text = contextText
            )
        )
    }

    private fun webEvidenceGateText(
        request: NativeWebRequest,
        selectedSource: NativeSelectedWebSource?,
        url: String,
        textChars: Int
    ): String {
        val host = selectedSource?.host?.ifBlank { hostFromDisplayUrl(url) } ?: hostFromDisplayUrl(url)
        val score = selectedSource?.score ?: 0
        val confidence = NativeWebEvidenceRules.confidence(host = host, score = score, textChars = textChars)
        return buildString {
            appendLine("WEB_EVIDENCE_GATE")
            appendLine("classification=external_web_evidence")
            appendLine("scope=temporary_context")
            appendLine("direct_long_term_ingest=" + "blocked")
            appendLine("approval_required_for_long_term_ingest=true")
            appendLine("confidence=$confidence")
            appendLine("confidence_reason=${NativeWebEvidenceRules.confidenceReason(host = host, score = score, textChars = textChars)}")
            appendLine("source_host=$host")
            appendLine("source_score=$score")
            appendLine("captured_chars=$textChars")
            appendLine("intent=${request.intent}")
        }.take(MAX_EVIDENCE_GATE_CHARS)
    }

    private fun multiSourceVerifierText(verifier: NativeMultiSourceVerification): String {
        return buildString {
            appendLine("MULTI_SOURCE_VERIFIER")
            appendLine("status=${verifier.status}")
            appendLine("confidence=${verifier.confidence}")
            appendLine("reason=${verifier.reason}")
        }.take(MAX_MULTI_SOURCE_VERIFIER_CHARS)
    }

    private fun hostFromDisplayUrl(url: String): String {
        return displayUrl(url).substringBefore('/').removePrefix("www.")
    }

    private fun displayUrl(url: String): String {
        return url
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
    }

    private const val MIN_DOCUMENT_CAPTURE_CHARS = 120
    private const val MAX_DOCUMENT_CAPTURE_CHARS = 40_000
    private const val MAX_TITLE_CHARS = 500
    private const val MAX_SOURCE_REASON_CHARS = 600
    private const val MAX_PRIMARY_CAPTURED_TEXT_CHARS = 7_000
    private const val MAX_SECONDARY_CAPTURED_TEXT_CHARS = 3_000
    private const val MAX_EVIDENCE_GATE_CHARS = 1_200
    private const val MAX_MULTI_SOURCE_VERIFIER_CHARS = 800
}
