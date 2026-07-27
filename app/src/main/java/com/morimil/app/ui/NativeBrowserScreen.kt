package com.morimil.app.ui

import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morimil.app.net.NetSourcePolicy
import com.morimil.app.net.SafeWebDocument
import com.morimil.app.net.SafeWebDocumentLoader
import com.morimil.app.net.SafeWebDocumentTextExtractor
import com.morimil.app.net.blockedWebResponse
import com.morimil.app.web.NativeWebContextStore
import com.morimil.app.web.NativeWebPageContext
import com.morimil.app.web.NativeWebRequestStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NativeBrowserScreen() {
    var input by remember { mutableStateOf("https://www.google.com/search?q=Morimil") }
    var loadTarget by remember { mutableStateOf(input) }
    var status by remember { mutableStateOf("Navegador nativo aislado listo.") }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var loadedDocument by remember { mutableStateOf<SafeWebDocument?>(null) }
    var initialLoadStarted by remember { mutableStateOf(false) }
    val documentLoader = remember { SafeWebDocumentLoader() }
    val scope = rememberCoroutineScope()
    val capturedPage by NativeWebContextStore.currentPage.collectAsStateWithLifecycle()
    val pendingRequest by NativeWebRequestStore.pendingRequest.collectAsStateWithLifecycle()

    fun loadPublicTarget(rawValue: String) {
        val target = normalizeUrlOrSearch(rawValue)
        val decision = NetSourcePolicy.validateUrl(target)
        if (!decision.allowed) {
            status = "Navegacion bloqueada: ${decision.reason}"
            return
        }
        loadedDocument = null
        status = "Validando DNS y descargando de forma aislada: ${target.take(120)}"
        scope.launch {
            val fetched = withContext(Dispatchers.IO) { documentLoader.fetch(target) }
            val document = fetched.document
            if (document == null) {
                status = "Navegacion bloqueada o fallida: ${fetched.error.orEmpty().take(160)}"
                return@launch
            }
            val finalDecision = NetSourcePolicy.validateUrl(document.finalUrl)
            if (!finalDecision.allowed) {
                status = "Destino final bloqueado: ${finalDecision.reason}"
                return@launch
            }
            input = document.finalUrl
            loadTarget = document.finalUrl
            loadedDocument = document
            activeWebView?.loadDataWithBaseURL(
                document.finalUrl,
                document.html,
                "text/html",
                Charsets.UTF_8.name(),
                document.finalUrl
            )
            status = "Pagina publica cargada en modo aislado. La WebView no tiene acceso directo a la red."
        }
    }

    LaunchedEffect(activeWebView) {
        if (activeWebView != null && !initialLoadStarted) {
            initialLoadStarted = true
            loadPublicTarget(loadTarget)
        }
    }

    LaunchedEffect(pendingRequest) {
        val request = pendingRequest ?: return@LaunchedEffect
        input = request.query
        status = "Morimil pidio buscar: ${request.query.take(120)}"
        loadPublicTarget(request.query)
        NativeWebRequestStore.markHandled(request)
    }

    DisposableEffect(Unit) {
        onDispose {
            activeWebView?.destroy()
            activeWebView = null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Web nativa", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Descarga paginas publicas mediante un transporte con DNS filtrado, elimina contenido activo y las muestra sin dar acceso directo de red a WebView. Capturar usa extraccion determinista y no ejecuta JavaScript remoto.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = { Text("URL HTTPS o busqueda") }
            )
            Button(onClick = { loadPublicTarget(input) }) {
                Text("Ir")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val view = activeWebView
                if (view?.canGoBack() == true) view.goBack() else status = "No hay pagina anterior."
            }) {
                Text("Atras")
            }
            Button(onClick = { loadPublicTarget(loadTarget) }) {
                Text("Recargar")
            }
            Button(onClick = {
                val document = loadedDocument
                if (document == null) {
                    status = "No hay un documento validado para capturar."
                } else {
                    captureDocument(document) { status = it }
                }
            }) {
                Text("Capturar")
            }
            Button(onClick = {
                NativeWebContextStore.clear()
                status = "Contexto web limpiado."
            }) {
                Text("Limpiar")
            }
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Contexto entregado al modelo", style = MaterialTheme.typography.titleSmall)
                Text(capturedPage?.title?.ifBlank { "Sin titulo" } ?: "Sin pagina capturada.")
                Text(capturedPage?.url ?: "Carga una pagina publica y pulsa Capturar.", style = MaterialTheme.typography.bodySmall)
                Text("caracteres=${capturedPage?.text?.length ?: 0}", style = MaterialTheme.typography.bodySmall)
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(520.dp),
            factory = { context ->
                WebView(context).apply {
                    settings.setJavaScriptEnabled(false)
                    settings.setDomStorageEnabled(false)
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.setLoadsImagesAutomatically(false)
                    settings.setBlockNetworkImage(true)
                    settings.setBlockNetworkLoads(true)
                    settings.setAllowFileAccess(false)
                    settings.setAllowContentAccess(false)
                    settings.setAllowFileAccessFromFileURLs(false)
                    settings.setAllowUniversalAccessFromFileURLs(false)
                    settings.setJavaScriptCanOpenWindowsAutomatically(false)
                    settings.setSupportMultipleWindows(false)
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.setSafeBrowsingEnabled(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    // Expose the WebView to Compose state only after every fail-closed
                    // setting has been applied.
                    activeWebView = this
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            if (!request.isForMainFrame) return true
                            val target = request.url.toString()
                            val decision = NetSourcePolicy.validateUrl(target)
                            if (!decision.allowed) {
                                status = "Navegacion bloqueada: ${decision.reason}"
                            } else {
                                loadPublicTarget(target)
                            }
                            return true
                        }

                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse {
                            return blockedWebResponse("interactive_browser_network_denied")
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            val current = url.orEmpty().ifBlank { loadTarget }
                            val decision = NetSourcePolicy.validateUrl(current)
                            status = if (decision.allowed) {
                                "Pagina cargada en modo aislado. Revisa el contenido y pulsa Capturar."
                            } else {
                                "Pagina no capturable: ${decision.reason}"
                            }
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                status = "Error web aislado: ${error.description}"
                            }
                        }
                    }
                }
            },
            update = { }
        )
    }
}

private fun normalizeUrlOrSearch(value: String): String {
    val clean = value.trim()
    if (clean.isBlank()) return "https://www.google.com"
    if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
    if (clean.contains(".") && !clean.contains(" ")) return "https://$clean"
    val encoded = java.net.URLEncoder.encode(clean, Charsets.UTF_8.name())
    return "https://www.google.com/search?q=$encoded"
}

private fun captureDocument(document: SafeWebDocument, onStatus: (String) -> Unit) {
    val currentDecision = NetSourcePolicy.validateUrl(document.finalUrl)
    if (!currentDecision.allowed) {
        onStatus("Captura bloqueada: ${currentDecision.reason}")
        return
    }

    runCatching {
        SafeWebDocumentTextExtractor.extract(
            document = document,
            maxTextChars = MAX_CAPTURE_CHARS
        )
    }.onSuccess { extracted ->
        if (extracted.text.isBlank()) {
            onStatus("Pagina cargada, pero no contiene texto estatico suficiente.")
        } else {
            NativeWebContextStore.update(
                NativeWebPageContext(
                    title = extracted.title,
                    url = document.finalUrl,
                    text = extracted.text
                )
            )
            onStatus("Pagina capturada sin JavaScript: ${extracted.text.length} caracteres.")
        }
    }.onFailure { error ->
        onStatus("No se pudo capturar pagina: ${error.message ?: error::class.java.simpleName}")
    }
}

private const val MAX_CAPTURE_CHARS = 12_000
