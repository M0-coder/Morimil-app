package com.morimil.app.ui

import com.morimil.app.net.NetSourcePolicy
import com.morimil.app.net.SafeWebDocument
import com.morimil.app.net.SafeWebDocumentLoader
import com.morimil.app.net.SafeWebDocumentTextExtractor
import com.morimil.app.web.NativeWebRequest
import com.morimil.app.web.WebSearchIntent
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class NativeWebResearchEngine(
    private val documentLoader: SafeWebDocumentLoader = SafeWebDocumentLoader()
) {
    suspend fun research(request: NativeWebRequest): NativeWebResearchRun =
        withContext(Dispatchers.IO) {
            researchBlocking(request)
        }

    private fun researchBlocking(request: NativeWebRequest): NativeWebResearchRun {
        var retryCount = 0
        var searchQuery = request.searchQuery
        var lastCandidates = emptyList<NativeSelectedWebSource>()
        var events = NativeWebNavigationTrace.started(request, searchUrl(searchQuery))
        var lastError: String? = null

        fun record(
            type: String,
            detail: String,
            source: NativeSelectedWebSource? = null,
            url: String? = source?.url,
            reason: String? = source?.reason
        ) {
            events = NativeWebNavigationTrace.append(
                events = events,
                type = type,
                detail = detail,
                url = url,
                title = source?.title,
                host = source?.host,
                score = source?.score,
                reason = reason
            )
        }

        while (retryCount <= MAX_RESEARCH_RETRIES) {
            val currentSearchUrl = searchUrl(searchQuery)
            record(
                type = "EGRESS_STARTED",
                detail = "salida web iniciada por una solicitud explicita del usuario",
                url = currentSearchUrl,
                reason = "user_requested_web_search"
            )
            val searchResult = documentLoader.fetch(currentSearchUrl)
            val searchDocument = searchResult.document
            if (searchDocument == null) {
                lastError = "search_fetch_failed:${searchResult.error.orEmpty().take(160)}"
                record(
                    type = "SEARCH_FETCH_FAILED",
                    detail = "fallo al descargar resultados mediante transporte filtrado",
                    url = currentSearchUrl,
                    reason = lastError
                )
                if (retryCount < MAX_RESEARCH_RETRIES) {
                    retryCount += 1
                    searchQuery = retrySearchQuery(request, retryCount)
                    record(
                        type = "RESEARCH_RETRY",
                        detail = "reintento de busqueda por fallo de descarga",
                        url = searchUrl(searchQuery),
                        reason = lastError
                    )
                    continue
                }
                break
            }

            val candidates = NativeWebSearchResultParser.selectCandidates(
                html = searchDocument.html,
                request = request.copy(searchQuery = searchQuery)
            )
            lastCandidates = candidates
            record(
                type = "SEARCH_PARSED",
                detail = "resultados analizados sin ejecutar JavaScript; candidatos=${candidates.size}",
                url = searchDocument.finalUrl
            )
            if (candidates.isEmpty()) {
                lastError = "no_useful_candidate"
                if (retryCount < MAX_RESEARCH_RETRIES) {
                    retryCount += 1
                    searchQuery = retrySearchQuery(request, retryCount)
                    record(
                        type = "RESEARCH_RETRY",
                        detail = "reintento por falta de candidatos utiles",
                        url = searchUrl(searchQuery),
                        reason = lastError
                    )
                    continue
                }
                break
            }

            var primary: NativeWebCapture? = null
            for (candidate in candidates) {
                record(
                    type = "SOURCE_FETCH_STARTED",
                    detail = "descarga filtrada de candidato primario",
                    source = candidate
                )
                primary = fetchCapture(request, candidate)
                if (primary != null) {
                    record(
                        type = "SOURCE_CAPTURED",
                        detail = "fuente primaria capturada sin JavaScript",
                        source = candidate,
                        url = primary.url
                    )
                    break
                }
                record(
                    type = "SOURCE_FALLBACK",
                    detail = "candidato rechazado por descarga o evidencia insuficiente",
                    source = candidate,
                    reason = "static_capture_insufficient"
                )
            }

            if (primary == null) {
                lastError = "primary_capture_failed"
                if (retryCount < MAX_RESEARCH_RETRIES) {
                    retryCount += 1
                    searchQuery = retrySearchQuery(request, retryCount)
                    record(
                        type = "RESEARCH_RETRY",
                        detail = "reintento por captura primaria insuficiente",
                        url = searchUrl(searchQuery),
                        reason = lastError
                    )
                    continue
                }
                break
            }

            val secondaryCandidate = NativeWebResearchPolicy.secondaryCandidateAfter(
                primary = primary.source,
                candidates = candidates
            )
            val decision = NativeWebResearchPolicy.multiSourceDecision(primary, secondaryCandidate)
            val secondary = if (decision.shouldOpenSecondary && secondaryCandidate != null) {
                record(
                    type = "SECONDARY_SOURCE_SELECTED",
                    detail = "segunda fuente seleccionada para contraste",
                    source = secondaryCandidate
                )
                fetchCapture(request, secondaryCandidate)?.also { capture ->
                    record(
                        type = "SECONDARY_SOURCE_CAPTURED",
                        detail = "fuente secundaria capturada sin JavaScript",
                        source = secondaryCandidate,
                        url = capture.url
                    )
                }
            } else {
                null
            }
            val verifier = if (decision.shouldOpenSecondary) {
                NativeWebResearchPolicy.verifySources(primary, secondary)
            } else {
                NativeWebResearchPolicy.toVerification(decision)
            }

            if (verifier.confidence == "LOW" && retryCount < MAX_RESEARCH_RETRIES) {
                retryCount += 1
                searchQuery = retrySearchQuery(request, retryCount)
                lastError = "weak_multisource_verification"
                record(
                    type = "RESEARCH_RETRY",
                    detail = "reintento por verificacion multifuente debil",
                    url = searchUrl(searchQuery),
                    reason = lastError
                )
                continue
            }

            return NativeWebResearchRun(
                primary = primary,
                secondary = secondary,
                verifier = verifier,
                retryCount = retryCount,
                candidates = candidates,
                navigationEvents = events
            )
        }

        return NativeWebResearchRun(
            primary = null,
            secondary = null,
            verifier = NativeMultiSourceVerification(
                status = "no_sources_captured",
                confidence = "LOW",
                reason = lastError ?: "ninguna fuente estatica suficiente"
            ),
            retryCount = retryCount,
            candidates = lastCandidates,
            navigationEvents = events,
            error = lastError ?: "research_failed"
        )
    }

    private fun fetchCapture(
        request: NativeWebRequest,
        source: NativeSelectedWebSource
    ): NativeWebCapture? {
        val sourceDecision = NetSourcePolicy.validateUrl(source.url)
        if (!sourceDecision.allowed) return null
        val fetched = documentLoader.fetch(source.url)
        val document = fetched.document ?: return null
        return NativeWebContextPublisher.captureDocument(
            document = document,
            request = request,
            selectedSource = source
        )
    }

    private fun searchUrl(query: String): String {
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        return "$BRAVE_SEARCH_URL$encoded"
    }

    private fun retrySearchQuery(request: NativeWebRequest, retryCount: Int): String {
        val suffix = when (request.intent) {
            WebSearchIntent.GRADLE_ERROR -> "exact error official documentation solution"
            WebSearchIntent.ANDROID_CODE -> "official Android Kotlin Compose documentation"
            WebSearchIntent.GITHUB_PROJECT -> "official GitHub documentation troubleshooting"
            WebSearchIntent.DOCUMENTATION -> "official docs reference"
            WebSearchIntent.GENERAL -> "reliable source explanation"
        }
        return "${request.searchQuery} $suffix retry $retryCount"
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_RETRY_QUERY_CHARS)
    }

    private companion object {
        const val BRAVE_SEARCH_URL = "https://search.brave.com/search?q="
        const val MAX_RESEARCH_RETRIES = 1
        const val MAX_RETRY_QUERY_CHARS = 240
    }
}

internal object NativeWebSearchResultParser {
    fun selectCandidates(
        html: String,
        request: NativeWebRequest
    ): List<NativeSelectedWebSource> {
        val queryTerms = tokenize(request.searchQuery)
        val bestByHost = linkedMapOf<String, NativeSelectedWebSource>()
        ANCHOR_REGEX.findAll(html)
            .take(MAX_ANCHORS)
            .forEachIndexed { index, match ->
                val rawHref = match.groupValues.drop(1).take(3).firstOrNull(String::isNotBlank)
                    ?: return@forEachIndexed
                val url = cleanUrl(
                    decodeHtmlText(
                        rawHref.take(MAX_URL_SOURCE_CHARS),
                        MAX_URL_CHARS
                    )
                )
                val uri = parseHttpsUri(url) ?: return@forEachIndexed
                val host = uri.host.orEmpty().lowercase().removePrefix("www.")
                val title = decodeHtmlText(
                    match.groupValues[4].take(MAX_TITLE_SOURCE_CHARS),
                    MAX_TITLE_CHARS
                )
                if (isBlocked(uri, host, title)) return@forEachIndexed

                val scored = scoreCandidate(
                    url = url,
                    host = host,
                    title = title,
                    index = index,
                    intent = request.intent,
                    queryTerms = queryTerms
                )
                if (scored.score < MIN_CANDIDATE_SCORE) return@forEachIndexed
                val candidate = NativeSelectedWebSource(
                    url = url.take(MAX_URL_CHARS),
                    title = title.ifBlank { host }.take(MAX_TITLE_CHARS),
                    host = host.take(MAX_HOST_CHARS),
                    score = scored.score,
                    reason = scored.reasons.joinToString("; ").take(MAX_REASON_CHARS)
                )
                val existing = bestByHost[host]
                if (existing == null || candidate.score > existing.score) {
                    bestByHost[host] = candidate
                }
            }
        return bestByHost.values
            .sortedByDescending(NativeSelectedWebSource::score)
            .take(MAX_CANDIDATES)
    }

    private fun cleanUrl(rawHref: String): String {
        val raw = rawHref.trim()
        val normalized = when {
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("/") -> "https://search.brave.com$raw"
            else -> raw
        }
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return normalized
        val host = uri.host.orEmpty().lowercase().removePrefix("www.")
        if (host != "search.brave.com") return normalized

        val parameters = runCatching {
            uri.rawQuery.orEmpty()
                .split('&')
                .mapNotNull { pair ->
                    val key = pair.substringBefore('=', missingDelimiterValue = "")
                    if (key.isBlank()) return@mapNotNull null
                    val value = pair.substringAfter('=', missingDelimiterValue = "")
                    URLDecoder.decode(key, Charsets.UTF_8.name()) to
                        URLDecoder.decode(value, Charsets.UTF_8.name())
                }
                .toMap()
        }.getOrDefault(emptyMap())
        return parameters["url"]
            ?.takeIf { it.startsWith("https://", ignoreCase = true) }
            ?: parameters["q"]
                ?.takeIf { it.startsWith("https://", ignoreCase = true) }
            ?: normalized
    }

    private fun parseHttpsUri(value: String): URI? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.userInfo != null || uri.host.isNullOrBlank()) return null
        return uri
    }

    private fun isBlocked(uri: URI, host: String, title: String): Boolean {
        val path = uri.path.orEmpty().lowercase()
        val combined = "${uri} $title".lowercase()
        return host == "search.brave.com" ||
            host in BLOCKED_HOSTS ||
            path.contains("/images") ||
            path.contains("/videos") ||
            path.contains("/maps") ||
            path.contains("/shopping") ||
            path.startsWith("/news") ||
            combined.contains("login") ||
            combined.contains("signin") ||
            combined.contains("sign-in") ||
            combined.contains("signup") ||
            combined.contains("oauth") ||
            combined.contains("captcha") ||
            combined.contains("subscribe")
    }

    private fun scoreCandidate(
        url: String,
        host: String,
        title: String,
        index: Int,
        intent: WebSearchIntent,
        queryTerms: Set<String>
    ): ScoredCandidate {
        val lower = "$url $host $title".lowercase()
        val path = runCatching { URI(url).path.orEmpty().lowercase() }.getOrDefault("")
        val overlap = queryTerms.count { it in lower }
        var score = 18 + (20 - index).coerceAtLeast(0)
        val reasons = mutableListOf("resultado visible")
        if (overlap > 0) {
            score += (overlap * 7).coerceAtMost(35)
            reasons += "coincide con la consulta"
        }

        when (intent) {
            WebSearchIntent.ANDROID_CODE -> when (host) {
                "developer.android.com" -> {
                    score += 100
                    reasons += "documentacion oficial Android"
                }
                "kotlinlang.org" -> {
                    score += 75
                    reasons += "documentacion oficial Kotlin"
                }
                "github.com" -> {
                    score += 40
                    reasons += "referencia tecnica GitHub"
                }
                "stackoverflow.com" -> {
                    score += 25
                    reasons += "respuesta tecnica comunitaria"
                }
            }
            WebSearchIntent.GRADLE_ERROR -> when (host) {
                "docs.gradle.org" -> {
                    score += 95
                    reasons += "documentacion oficial Gradle"
                }
                "developer.android.com" -> {
                    score += 70
                    reasons += "documentacion oficial Android"
                }
                "stackoverflow.com" -> {
                    score += 35
                    reasons += "error similar resuelto"
                }
                "github.com" -> {
                    score += 30
                    reasons += "issue o codigo relacionado"
                }
            }
            WebSearchIntent.GITHUB_PROJECT -> when (host) {
                "docs.github.com" -> {
                    score += 100
                    reasons += "documentacion oficial GitHub"
                }
                "github.com" -> {
                    score += 45
                    reasons += "resultado GitHub directo"
                }
            }
            WebSearchIntent.DOCUMENTATION -> {
                if (host.startsWith("docs.") || "/docs" in path || "documentation" in lower) {
                    score += 70
                    reasons += "fuente documental"
                }
            }
            WebSearchIntent.GENERAL -> Unit
        }

        if (host.startsWith("docs.") || "/docs" in path) {
            score += 20
            reasons += "ruta de documentacion"
        }
        if ("official" in lower || "oficial" in lower) {
            score += 12
            reasons += "marcada como oficial"
        }
        if (title.length < 12 && host !in PRIORITY_HOSTS) {
            score -= 18
            reasons += "texto visible corto"
        }
        if (host in SOFT_BAD_HOSTS || host.endsWith(".medium.com")) {
            score -= 25
            reasons += "fuente secundaria no oficial"
        }
        if (host == "stackoverflow.com") {
            score -= 8
            reasons += "comunidad, no fuente primaria"
        }
        if ("sponsored" in lower || "anuncio" in lower) {
            score -= 45
            reasons += "posible anuncio"
        }
        return ScoredCandidate(score = score, reasons = reasons.distinct())
    }

    private fun decodeHtmlText(value: String, maxChars: Int): String {
        if (value.isBlank()) return ""
        return SafeWebDocumentTextExtractor.extract(
            document = SafeWebDocument(
                finalUrl = "https://search.brave.com/",
                html = "<html><body>$value</body></html>"
            ),
            maxTextChars = maxChars
        ).text.trim().take(maxChars)
    }

    private fun tokenize(value: String): Set<String> {
        return value
            .lowercase()
            .replace(Regex("[^a-z0-9áéíóúñ_.-]+"), " ")
            .split(' ')
            .asSequence()
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .toSet()
    }

    private data class ScoredCandidate(
        val score: Int,
        val reasons: List<String>
    )

    private const val MAX_ANCHORS = 160
    private const val MAX_CANDIDATES = 5
    private const val MIN_CANDIDATE_SCORE = 28
    private const val MAX_URL_CHARS = 600
    private const val MAX_URL_SOURCE_CHARS = 2_400
    private const val MAX_TITLE_CHARS = 160
    private const val MAX_TITLE_SOURCE_CHARS = 4_000
    private const val MAX_HOST_CHARS = 120
    private const val MAX_REASON_CHARS = 360
    private val ANCHOR_REGEX = Regex(
        "<a\\b[^>]*href\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))[^>]*>(.*?)</a\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val BLOCKED_HOSTS = setOf(
        "youtube.com",
        "youtu.be",
        "facebook.com",
        "instagram.com",
        "tiktok.com",
        "x.com",
        "twitter.com",
        "pinterest.com"
    )
    private val SOFT_BAD_HOSTS = setOf(
        "medium.com",
        "dev.to",
        "hashnode.dev",
        "quora.com",
        "reddit.com"
    )
    private val PRIORITY_HOSTS = setOf(
        "developer.android.com",
        "docs.github.com",
        "docs.gradle.org"
    )
    private val STOP_WORDS = setOf(
        "the",
        "and",
        "for",
        "con",
        "que",
        "una",
        "para",
        "como",
        "documentation",
        "official",
        "documentacion",
        "documentación",
        "busca",
        "buscar"
    )
}
