package com.morimil.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morimil.app.web.NativeWebContextStore
import com.morimil.app.web.NativeWebRequest
import com.morimil.app.web.NativeWebRequestStore
import com.morimil.app.web.NativeWebSearchAuditEntry
import com.morimil.app.web.NativeWebSearchAuditStore
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NativeWebBridgePanel(
    modifier: Modifier = Modifier,
    onPageReady: (String) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val pendingRequest by NativeWebRequestStore.pendingRequest.collectAsStateWithLifecycle()
    val researchEngine = remember { NativeWebResearchEngine() }
    var status by remember { mutableStateOf("Sin salida web activa.") }
    var isLoading by remember { mutableStateOf(false) }
    var lastRun by remember { mutableStateOf<NativeWebResearchRun?>(null) }

    LaunchedEffect(pendingRequest?.requestedAtMillis) {
        val request = pendingRequest ?: return@LaunchedEffect
        isLoading = true
        lastRun = null
        status = "Salida web solicitada por el usuario: ${request.searchQuery.take(120)}"

        val run = runCatching { researchEngine.research(request) }
            .getOrElse { error ->
                NativeWebResearchRun(
                    primary = null,
                    secondary = null,
                    verifier = NativeMultiSourceVerification(
                        status = "research_engine_failed",
                        confidence = "LOW",
                        reason = "${error::class.java.simpleName}:${error.message.orEmpty()}".take(240)
                    ),
                    retryCount = 0,
                    candidates = emptyList(),
                    navigationEvents = NativeWebNavigationTrace.started(
                        request = request,
                        searchUrl = "https://search.brave.com/"
                    ),
                    error = "research_engine_failed"
                )
            }

        lastRun = run
        NativeWebContextPublisher.publishWebContext(
            request = request,
            primary = run.primary,
            secondary = run.secondary,
            navigationTrace = NativeWebNavigationTrace.text(request, run.navigationEvents),
            verifier = run.verifier,
            retryCount = run.retryCount
        )
        withContext(Dispatchers.IO) {
            runCatching { writeSearchAudit(context, request, run) }
        }
        NativeWebRequestStore.markHandled(request)
        status = when {
            run.primary != null && run.secondary != null ->
                "Investigacion lista: dos fuentes capturadas sin JavaScript."
            run.primary != null ->
                "Investigacion lista: una fuente capturada sin JavaScript."
            else ->
                "Sin evidencia estatica suficiente; Morimil debe abstenerse o pedir otra fuente."
        }
        isLoading = false
        onPageReady(request.query)
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NativeWebWindowColor)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isLoading) "WEB · consultando" else "WEB · transporte filtrado",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = status,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Egreso: solo por solicitud del usuario · DNS/SSRF filtrado · 2 MiB max · JavaScript remoto desactivado · memoria permanente bloqueada",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            lastRun?.let { run ->
                ResearchSourceLine(label = "Primaria", capture = run.primary)
                if (run.secondary != null) {
                    ResearchSourceLine(label = "Secundaria", capture = run.secondary)
                }
                Text(
                    text = "verificador=${run.verifier.confidence.lowercase()} · candidatos=${run.candidates.size} · reintentos=${run.retryCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isLoading) {
                TextButton(
                    onClick = {
                        NativeWebContextStore.clear()
                        lastRun = null
                        status = "Contexto web temporal limpiado."
                    }
                ) {
                    Text("Limpiar contexto web")
                }
            }
        }
    }
}

@Composable
private fun ResearchSourceLine(
    label: String,
    capture: NativeWebCapture?
) {
    if (capture == null) return
    Text(
        text = "$label: ${capture.source?.host ?: capture.url} · ${capture.textChars} caracteres",
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun writeSearchAudit(
    context: android.content.Context,
    request: NativeWebRequest,
    run: NativeWebResearchRun
) {
    val fallbackCount = run.navigationEvents.count { it.type == "SOURCE_FALLBACK" }
    val result = when {
        run.primary != null && run.secondary != null -> "primary_secondary_captured"
        run.primary != null -> "primary_captured"
        else -> "capture_failed"
    }
    val entry = NativeWebSearchAuditEntry(
        auditId = "web-${request.requestedAtMillis}-${UUID.randomUUID()}",
        queryOriginal = request.query,
        querySearch = request.searchQuery,
        intent = request.intent.name,
        strategy = request.strategy,
        primaryUrl = run.primary?.url,
        primaryHost = run.primary?.source?.host,
        primaryScore = run.primary?.source?.score,
        primaryReason = run.primary?.source?.reason,
        secondaryUrl = run.secondary?.url,
        secondaryHost = run.secondary?.source?.host,
        secondaryScore = run.secondary?.source?.score,
        secondaryReason = run.secondary?.source?.reason,
        verifierStatus = run.verifier.status,
        verifierConfidence = run.verifier.confidence,
        verifierReason = run.verifier.reason,
        retryCount = run.retryCount,
        fallbackCount = fallbackCount,
        navigationEventCount = run.navigationEvents.size,
        result = result,
        createdAtMillis = System.currentTimeMillis()
    )
    NativeWebSearchAuditStore.append(context, entry)
}
