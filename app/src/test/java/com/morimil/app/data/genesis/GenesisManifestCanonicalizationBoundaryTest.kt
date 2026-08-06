package com.morimil.app.data.genesis

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

class GenesisManifestCanonicalizationBoundaryTest {
    @Test
    fun verifiesOneCharacterCanonicalStrings() {
        val fixture = fixture(
            path = "a",
            kind = "k",
            canonicalKindJson = "\"k\""
        )

        val result = fixture.verify()

        assertEquals("manifest_boundary_test", result.manifestId)
        assertEquals(fixture.approvedCoreHash, result.genesisCoreHash)
        assertEquals(1, result.fileCount)
    }

    @Test
    fun preservesSpaceAtCanonicalControlBoundary() {
        val fixture = fixture(
            path = "a",
            kind = " ",
            canonicalKindJson = "\" \""
        )

        val result = fixture.verify()

        assertEquals(fixture.approvedCoreHash, result.genesisCoreHash)
    }

    @Test
    fun escapesUnitSeparatorBelowCanonicalControlBoundary() {
        val fixture = fixture(
            path = "a",
            kind = "\u001F",
            canonicalKindJson = "\"\\u001f\""
        )

        val result = fixture.verify()

        assertEquals(fixture.approvedCoreHash, result.genesisCoreHash)
    }

    private fun fixture(
        path: String,
        kind: String,
        canonicalKindJson: String
    ): Fixture {
        val payload = "boundary-payload".toByteArray(Charsets.UTF_8)
        val assetHash = sha256(payload)
        val files = JSONArray().put(
            JSONObject()
                .put("path", path)
                .put("kind", kind)
                .put("sha256", assetHash)
                .put("required", true)
        )
        val canonicalRecords =
            "[{\"kind\":$canonicalKindJson,\"path\":\"$path\",\"required\":true," +
                "\"sha256\":\"$assetHash\"}]"
        val approvedCoreHash = sha256(canonicalRecords.toByteArray(Charsets.UTF_8))
        val manifest = JSONObject()
            .put("schema_version", "morimil.genesis_manifest.v1")
            .put("manifest_id", "manifest_boundary_test")
            .put("genesis_core_hash", approvedCoreHash)
            .put(
                "mobile_installation",
                JSONObject().put("startup_verification_required", true)
            )
            .put("files", files)

        val source = InMemoryGenesisAssetSource().apply {
            writeBytes("$GENESIS_ROOT/$path", payload)
            writeText(MANIFEST_PATH, manifest.toString())
        }
        val core = GenesisManifestVerifierCore(
            assets = source,
            approvedGenesisCoreHash = approvedCoreHash,
            approvedFileCount = files.length()
        )
        return Fixture(
            source = source,
            core = core,
            manifest = manifest,
            approvedCoreHash = approvedCoreHash
        )
    }

    private data class Fixture(
        val source: InMemoryGenesisAssetSource,
        val core: GenesisManifestVerifierCore,
        val manifest: JSONObject,
        val approvedCoreHash: String
    ) {
        fun verify(): GenesisManifestVerification {
            source.writeText(MANIFEST_PATH, manifest.toString())
            return core.verify()
        }
    }

    private class InMemoryGenesisAssetSource : GenesisAssetSource {
        private val entries = linkedMapOf<String, ByteArray>()

        override fun readText(path: String): String {
            return readBytes(path).toString(Charsets.UTF_8)
        }

        override fun readBytes(path: String): ByteArray {
            return requireNotNull(entries[path]) { "Missing in-memory asset: $path" }.copyOf()
        }

        override fun listFiles(path: String): List<String> {
            return entries.keys
                .filter { file -> file == path || file.startsWith("$path/") }
                .sorted()
        }

        fun writeText(path: String, value: String) {
            writeBytes(path, value.toByteArray(Charsets.UTF_8))
        }

        fun writeBytes(path: String, value: ByteArray) {
            entries[path] = value.copyOf()
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return "sha256:" + digest.joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private companion object {
        const val GENESIS_ROOT = "genesis"
        const val MANIFEST_PATH = "$GENESIS_ROOT/genesis_manifest.json"
    }
}
