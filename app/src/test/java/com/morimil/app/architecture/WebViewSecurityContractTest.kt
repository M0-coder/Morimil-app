package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewSecurityContractTest {
    @Test
    fun webViewFileAndContentAccessStayFailClosed() {
        productionKotlinFiles().forEach { (path, source) ->
            assertTrue(
                "$path enables WebView file access",
                !FILE_ACCESS_TRUE.containsMatchIn(source)
            )
            assertTrue(
                "$path enables WebView content access",
                !CONTENT_ACCESS_TRUE.containsMatchIn(source)
            )
        }
    }

    @Test
    fun javascriptBoundariesStayExplicitAndHardened() {
        val files = productionKotlinFiles()
        val javascriptFiles = files
            .filterValues { source -> JAVASCRIPT_ENABLED.containsMatchIn(source) }
            .keys

        assertEquals(EXPLICIT_JAVASCRIPT_BOUNDARIES.keys, javascriptFiles)

        EXPLICIT_JAVASCRIPT_BOUNDARIES.forEach { (path, marker) ->
            val source = requireNotNull(files[path]) { "Missing JavaScript boundary: $path" }
            assertTrue("$path is missing its reviewed boundary marker", marker in source)
            REQUIRED_FAIL_CLOSED_SETTINGS.forEach { setting ->
                assertTrue("$path is missing `$setting`", setting in source)
            }
        }
    }

    @Test
    fun productionWebViewsDoNotExposeJavascriptInterfaces() {
        val offenders = productionKotlinFiles()
            .filterValues { source -> ADD_JAVASCRIPT_INTERFACE.containsMatchIn(source) }
            .keys

        assertTrue(
            "JavaScript interfaces require a dedicated security review: $offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun isolatedReadersDoNotExecuteJavascript() {
        val files = productionKotlinFiles()
        val screen = requireNotNull(files[ISOLATED_BROWSER_SCREEN])
        val runtime = requireNotNull(files[ISOLATED_BROWSER_RUNTIME])

        assertTrue(
            "$ISOLATED_BROWSER_SCREEN must explicitly disable JavaScript",
            "settings.setJavaScriptEnabled(false)" in screen
        )
        listOf(screen, runtime).forEach { source ->
            assertFalse("Isolated readers must not evaluate JavaScript", "evaluateJavascript" in source)
            assertFalse(
                "The temporary issue-126 JavaScript marker must not return",
                "TEMPORARY_ISOLATED_READER_ISSUE_126" in source
            )
        }
        assertFalse(
            "$ISOLATED_BROWSER_RUNTIME must not create a WebView",
            Regex("""\bWebView\s*\(""").containsMatchIn(runtime)
        )
    }

    @Test
    fun remoteResearchBridgeDoesNotCreateOrScriptAWebView() {
        val bridge = requireNotNull(productionKotlinFiles()[REMOTE_RESEARCH_BRIDGE])

        assertFalse(
            "$REMOTE_RESEARCH_BRIDGE must not create a WebView",
            Regex("""\bWebView\s*\(""").containsMatchIn(bridge)
        )
        assertFalse("Remote research must not evaluate JavaScript", "evaluateJavascript" in bridge)
        assertFalse(
            "The temporary issue-125 JavaScript marker must not return",
            "TEMPORARY_REMOTE_RESEARCH_ISSUE_125" in bridge
        )
    }

    private fun productionKotlinFiles(): Map<String, String> {
        val root = repositoryRoot()
        val sourceRoot = File(root, "app/src/main/java")
        return sourceRoot.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .associate { file ->
                file.relativeTo(root).invariantSeparatorsPath to file.readText()
            }
    }

    private fun repositoryRoot(): File {
        return sequenceOf(File("."), File(".."))
            .map(File::getCanonicalFile)
            .firstOrNull { candidate ->
                File(candidate, "README.md").isFile &&
                    File(candidate, "app/build.gradle.kts").isFile
            }
            ?: error("Repository root not found")
    }

    private companion object {
        val FILE_ACCESS_TRUE =
            Regex("""(?:allowFileAccess\s*=\s*true|setAllowFileAccess\(\s*true\s*\))""")
        val CONTENT_ACCESS_TRUE =
            Regex("""(?:allowContentAccess\s*=\s*true|setAllowContentAccess\(\s*true\s*\))""")
        val JAVASCRIPT_ENABLED =
            Regex("""(?:javaScriptEnabled\s*=\s*true|setJavaScriptEnabled\(\s*true\s*\))""")
        val ADD_JAVASCRIPT_INTERFACE = Regex("""\baddJavascriptInterface\s*\(""")

        val REQUIRED_FAIL_CLOSED_SETTINGS = setOf(
            "settings.setAllowFileAccess(false)",
            "settings.setAllowContentAccess(false)",
            "settings.setAllowFileAccessFromFileURLs(false)",
            "settings.setAllowUniversalAccessFromFileURLs(false)"
        )

        val EXPLICIT_JAVASCRIPT_BOUNDARIES = mapOf(
            "app/src/main/java/com/morimil/app/ui/MorimilCanvasScreen.kt" to
                "WEBVIEW_JS_BOUNDARY: LOCAL_CANVAS_ISSUE_127"
        )
        const val ISOLATED_BROWSER_SCREEN =
            "app/src/main/java/com/morimil/app/ui/NativeBrowserScreen.kt"
        const val ISOLATED_BROWSER_RUNTIME =
            "app/src/main/java/com/morimil/app/net/NativeBrowserRuntime.kt"
        const val REMOTE_RESEARCH_BRIDGE =
            "app/src/main/java/com/morimil/app/ui/NativeWebBridgePanel.kt"
    }
}
