package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertEquals
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
        val FILE_ACCESS_TRUE = Regex("""allowFileAccess\s*=\s*true""")
        val CONTENT_ACCESS_TRUE = Regex("""allowContentAccess\s*=\s*true""")
        val JAVASCRIPT_ENABLED = Regex("""javaScriptEnabled\s*=\s*true""")
        val ADD_JAVASCRIPT_INTERFACE = Regex("""\baddJavascriptInterface\s*\(""")

        val REQUIRED_FAIL_CLOSED_SETTINGS = setOf(
            "settings.allowFileAccess = false",
            "settings.allowContentAccess = false",
            "settings.allowFileAccessFromFileURLs = false",
            "settings.allowUniversalAccessFromFileURLs = false"
        )

        val EXPLICIT_JAVASCRIPT_BOUNDARIES = mapOf(
            "app/src/main/java/com/morimil/app/net/NativeBrowserRuntime.kt" to
                "WEBVIEW_JS_BOUNDARY: TEMPORARY_ISOLATED_READER_ISSUE_126",
            "app/src/main/java/com/morimil/app/ui/MorimilCanvasScreen.kt" to
                "WEBVIEW_JS_BOUNDARY: LOCAL_CANVAS_ISSUE_127",
            "app/src/main/java/com/morimil/app/ui/NativeBrowserScreen.kt" to
                "WEBVIEW_JS_BOUNDARY: TEMPORARY_ISOLATED_READER_ISSUE_126",
            "app/src/main/java/com/morimil/app/ui/NativeWebBridgePanel.kt" to
                "WEBVIEW_JS_BOUNDARY: TEMPORARY_REMOTE_RESEARCH_ISSUE_125"
        )
    }
}
