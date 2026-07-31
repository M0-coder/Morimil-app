package com.morimil.app.architecture

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MorimilCanvasAssetProvenanceContractTest {
    private val bundlePath =
        "app/vendor/morimil-canvas/morimil-canvas-0.3.1-runtime-recovery-v1.zip"
    private val provenancePath =
        "app/vendor/morimil-canvas/morimil-canvas-0.3.1-runtime-recovery-v1.provenance.json"
    private val documentationPath = "docs/MORIMIL_CANVAS_RUNTIME_RECOVERY_PROVENANCE.md"
    private val expectedBundleSha256 =
        "6bbc1a5127f6db742db87a3cb6af9631bba387e7c0ff543309d48ffb5eac4835"
    private val expectedProvenanceSha256 =
        "cf57eff71ac919cc59a18e1815d49dd97702b3fe8e4864bb101f016f7147a542"

    @Test
    fun vendoredBundleHasExactNormativeBytesAndRuntimeInventory() {
        val bundle = repositoryFile(bundlePath)
        assertTrue(bundle.isFile)
        assertEquals(3_931_846L, bundle.length())
        assertEquals(expectedBundleSha256, sha256(bundle))

        ZipFile(bundle).use { zip ->
            val entries = zip.entries().asSequence().toList()
            assertEquals(48, entries.size)
            assertTrue(entries.none { it.isDirectory })
            assertTrue(entries.all { it.method == java.util.zip.ZipEntry.STORED })
            assertEquals(3_922_742L, entries.sumOf { it.size })
            assertNotNull(zip.getEntry("index.html"))
            assertNotNull(zip.getEntry("morimil-canvas.manifest.json"))
            assertTrue(entries.all { entry ->
                val name = entry.name
                name.isNotBlank() &&
                    !name.startsWith('/') &&
                    '\\' !in name &&
                    name.split('/').none { segment ->
                        segment.isBlank() || segment == "." || segment == ".."
                    }
            })
        }
    }

    @Test
    fun provenanceJsonIsCanonicalAndContainsOnlyTheNormativeFields() {
        val provenanceFile = repositoryFile(provenancePath)
        val bytes = provenanceFile.readBytes()
        assertEquals(964, bytes.size)
        assertEquals(expectedProvenanceSha256, sha256(bytes))
        assertFalse(
            bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte()
        )
        assertFalse(bytes.last() == '\n'.code.toByte() || bytes.last() == '\r'.code.toByte())

        val provenance = JSONObject(bytes.toString(Charsets.UTF_8))
        val expectedKeys = setOf(
            "apkEntry",
            "apkSha256",
            "canonicalTreeSha256",
            "originalBundleRecovered",
            "originalBundleSha256",
            "recoveryId",
            "runtimeFileCount",
            "runtimeTotalBytes",
            "schema",
            "sourceArtifactDigest",
            "sourceArtifactExpiresAt",
            "sourceArtifactId",
            "sourceHead",
            "sourceWorkflowRunId",
            "successorBundleName",
            "successorBundleSha256",
            "successorBundleSizeBytes"
        )
        val actualKeys = mutableSetOf<String>()
        val keyIterator = provenance.keys()
        while (keyIterator.hasNext()) {
            actualKeys += keyIterator.next()
        }
        assertEquals(expectedKeys, actualKeys)
        assertEquals("morimil.canvas.runtime-recovery.provenance.v1", provenance.getString("schema"))
        assertEquals("morimil.canvas.runtime-recovery.v1", provenance.getString("recoveryId"))
        assertFalse(provenance.getBoolean("originalBundleRecovered"))
        assertEquals(
            "73b061406d9fff999a859025f497bece4680a896ad19eccb6a391cdb50cd0507",
            provenance.getString("originalBundleSha256")
        )
        assertEquals(30_592_451_855L, provenance.getLong("sourceWorkflowRunId"))
        assertEquals(8_779_073_588L, provenance.getLong("sourceArtifactId"))
        assertEquals(
            "sha256:72c00b39491d4ba8b46478f9749e5e09d936718795bd314ce15e17df8a166c54",
            provenance.getString("sourceArtifactDigest")
        )
        assertEquals("2026-10-29T00:04:24Z", provenance.getString("sourceArtifactExpiresAt"))
        assertEquals(
            "7bdbda2aa4b7568695ba8e98be54d506d42c99d5",
            provenance.getString("sourceHead")
        )
        assertEquals(
            "app/build/outputs/apk/debug/app-debug.apk",
            provenance.getString("apkEntry")
        )
        assertEquals(
            "314b99a5a67d60f8d2d379d8efc1d7ef52caeacdc24d7dd1b32eb7b448cab623",
            provenance.getString("apkSha256")
        )
        assertEquals(48, provenance.getInt("runtimeFileCount"))
        assertEquals(3_922_742L, provenance.getLong("runtimeTotalBytes"))
        assertEquals(
            "e3d58636c98987d41f57409cc91e473564207eacd0e81e385108a0f54ddd6985",
            provenance.getString("canonicalTreeSha256")
        )
        assertEquals(
            "morimil-canvas-0.3.1-runtime-recovery-v1.zip",
            provenance.getString("successorBundleName")
        )
        assertEquals(3_931_846L, provenance.getLong("successorBundleSizeBytes"))
        assertEquals(expectedBundleSha256, provenance.getString("successorBundleSha256"))
    }

    @Test
    fun activeGradlePathIsVendoredFailClosedAndPreservesExtractionGuards() {
        val gradle = repositoryFile("app/build.gradle.kts").readText()

        assertFalse(gradle.contains("raw.githubusercontent.com"))
        assertFalse(gradle.contains("java.net.URI"))
        assertFalse(gradle.contains("URLConnection"))
        assertFalse(gradle.contains("openConnection"))
        assertFalse(gradle.contains("morimilCanvasBundleUrl"))
        assertFalse(gradle.contains("morimilCanvasSourceCommit"))

        assertTrue(gradle.contains(bundlePath.removePrefix("app/")))
        assertTrue(gradle.contains(provenancePath.removePrefix("app/")))
        assertTrue(gradle.contains("MORIMIL_CANVAS_ZIP"))
        assertTrue(gradle.contains("sourceArchive.copyTo(archive, overwrite = true)"))
        assertTrue(gradle.contains("actualArchiveHash == morimilCanvasBundleSha256"))
        assertTrue(gradle.contains("provenanceString(\"recoveryId\") == morimilCanvasRecoveryId"))
        assertTrue(gradle.contains("entry.name"))
        assertTrue(gradle.contains("target.path.startsWith(canonicalRoot.path + File.separator)"))
        assertTrue(gradle.contains("extractedFiles <= 200"))
        assertTrue(gradle.contains("extractedBytes <= 6L * 1024L * 1024L"))
        assertTrue(gradle.contains("index.html"))
        assertTrue(gradle.contains("morimil-canvas.manifest.json"))
        assertTrue(gradle.contains("actualTreeHash == morimilCanvasTreeSha256"))
    }

    @Test
    fun provenanceDocumentStatesRecoveryLimitsWithoutUnauthorizedClaims() {
        val document = repositoryFile(documentationPath).readText()
        val normalized = document.lowercase()

        assertTrue(document.contains("originalBundleRecovered=false"))
        assertTrue(document.contains("exact equivalence of the runtime tree"))
        assertTrue(document.contains("does **not** establish byte-for-byte identity"))
        assertTrue(document.contains("MORIMIL_COMMERCIAL_STATUS=NON_COMMERCIAL"))
        assertTrue(document.contains("READY_FOR_REVIEW_AUTHORIZED=false"))
        assertTrue(document.contains("MERGE_AUTHORIZED=false"))

        assertFalse(normalized.contains("production-ready"))
        assertFalse(normalized.contains("public beta"))
        assertFalse(normalized.contains("commercial release"))
        assertFalse(normalized.contains("f3 is fully closed"))
        assertFalse(normalized.contains("f3 is complete"))
        assertFalse(normalized.contains("pr #149 was authorized in github"))
    }

    private fun sha256(file: File): String = sha256(file.readBytes())

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun repositoryFile(relativePath: String): File {
        return sequenceOf(
            File(relativePath),
            File("../$relativePath")
        ).firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")
    }
}
