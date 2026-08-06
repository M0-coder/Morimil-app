package com.morimil.app.data.genesis

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.MessageDigest

class GenesisManifestVerifierCoreTest {
    @Test
    fun verifiesValidInMemoryBundle() {
        val fixture = fixture()

        val result = fixture.verify()

        assertEquals("manifest_test", result.manifestId)
        assertEquals(fixture.approvedCoreHash, result.genesisCoreHash)
        assertEquals(3, result.fileCount)
    }

    @Test
    fun rejectsMalformedManifest() {
        val fixture = fixture()
        fixture.source.writeText(MANIFEST_PATH, "not-json")

        assertThrows(JSONException::class.java) {
            fixture.core.verify()
        }
    }

    @Test
    fun rejectsInvalidSchema() {
        val fixture = fixture()
        fixture.manifest.put("schema_version", "morimil.genesis_manifest.v0")

        fixture.assertRejected("Invalid Genesis manifest schema.")
    }

    @Test
    fun rejectsUnapprovedManifestHash() {
        val fixture = fixture()
        fixture.manifest.put("genesis_core_hash", "sha256:" + "f".repeat(64))

        fixture.assertRejected("Genesis manifest hash is not the approved mobile seed.")
    }

    @Test
    fun rejectsDisabledStartupVerification() {
        val fixture = fixture()
        fixture.manifest.getJSONObject("mobile_installation")
            .put("startup_verification_required", false)

        fixture.assertRejected("Genesis startup verification must be required.")
    }

    @Test
    fun rejectsIncorrectFileCount() {
        val fixture = fixture()
        fixture.files.remove(fixture.files.length() - 1)

        fixture.assertRejected("Genesis manifest must declare exactly 3 seed files.")
    }

    @Test
    fun rejectsOptionalFile() {
        val fixture = fixture()
        fixture.files.getJSONObject(0).put("required", false)
        val path = fixture.files.getJSONObject(0).getString("path")

        fixture.assertRejected("Genesis file must be required: $path")
    }

    @Test
    fun rejectsBlankPath() {
        val fixture = fixture()
        fixture.files.getJSONObject(0).put("path", "")

        fixture.assertRejected("Genesis asset path must not be blank.")
    }

    @Test
    fun rejectsAbsolutePath() {
        val fixture = fixture()
        fixture.files.getJSONObject(0).put("path", "/identity/orchestrator.json")

        fixture.assertRejected("Genesis asset path must be relative.")
    }

    @Test
    fun rejectsTraversalPath() {
        val fixture = fixture()
        fixture.files.getJSONObject(0).put("path", "identity/../outside.json")

        fixture.assertRejected("Genesis asset path cannot escape bundle.")
    }

    @Test
    fun rejectsDuplicateDeclaredPath() {
        val fixture = fixture()
        val duplicatePath = fixture.files.getJSONObject(0).getString("path")
        fixture.files.getJSONObject(1).put("path", duplicatePath)

        fixture.assertRejected("Duplicate Genesis manifest path: $duplicatePath")
    }

    @Test
    fun rejectsTamperedAssetBytes() {
        val fixture = fixture()
        val path = fixture.files.getJSONObject(0).getString("path")
        fixture.source.writeBytes("$GENESIS_ROOT/$path", "tampered".toByteArray())

        fixture.assertRejected("Genesis asset hash mismatch: $path")
    }

    @Test
    fun rejectsMissingFileFromEnumeratedBundle() {
        val fixture = fixture()
        val missingPath = fixture.files.getJSONObject(0).getString("path")
        fixture.source.listedFilesOverride = fixture.source.currentFiles()
            .filterNot { it == "$GENESIS_ROOT/$missingPath" }

        fixture.assertRejected(
            "Genesis bundle file set mismatch. missing=[$missingPath] unexpected=[]"
        )
    }

    @Test
    fun rejectsUnexpectedFileInEnumeratedBundle() {
        val fixture = fixture()
        fixture.source.writeBytes("$GENESIS_ROOT/unexpected.txt", byteArrayOf(1, 2, 3))

        fixture.assertRejected(
            "Genesis bundle file set mismatch. missing=[] unexpected=[unexpected.txt]"
        )
    }

    @Test
    fun rejectsCanonicalCoreHashMismatch() {
        val fixture = fixture()
        fixture.files.getJSONObject(0).put("kind", "mutated_kind")

        fixture.assertRejected("Genesis core hash does not match declared file set.")
    }

    private fun fixture(): Fixture {
        val payloads = linkedMapOf(
            "policy/recovery.json" to "{\"mode\":\"manual\"}".toByteArray(),
            "identity/orchestrator.json" to "{\"agent_id\":\"morimil\"}".toByteArray(),
            "doctrine/free-birth.md" to "Freedom without ownership.\n".toByteArray()
        )
        val files = JSONArray().apply {
            payloads.forEach { (path, bytes) ->
                put(
                    JSONObject()
                        .put("path", path)
                        .put("kind", path.substringBefore('/'))
                        .put("sha256", sha256(bytes))
                        .put("required", true)
                )
            }
        }
        val approvedCoreHash = computeCoreHash(files)
        val manifest = JSONObject()
            .put("schema_version", "morimil.genesis_manifest.v1")
            .put("manifest_id", "manifest_test")
            .put("genesis_core_hash", approvedCoreHash)
            .put(
                "mobile_installation",
                JSONObject().put("startup_verification_required", true)
            )
            .put("files", files)

        val source = InMemoryGenesisAssetSource().apply {
            payloads.forEach { (path, bytes) ->
                writeBytes("$GENESIS_ROOT/$path", bytes)
            }
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
            files = files,
            approvedCoreHash = approvedCoreHash
        )
    }

    private data class Fixture(
        val source: InMemoryGenesisAssetSource,
        val core: GenesisManifestVerifierCore,
        val manifest: JSONObject,
        val files: JSONArray,
        val approvedCoreHash: String
    ) {
        fun verify(): GenesisManifestVerification {
            source.writeText(MANIFEST_PATH, manifest.toString())
            return core.verify()
        }

        fun assertRejected(expectedMessage: String) {
            source.writeText(MANIFEST_PATH, manifest.toString())
            val error = assertThrows(IllegalArgumentException::class.java) {
                core.verify()
            }
            assertEquals(expectedMessage, error.message)
        }
    }

    private class InMemoryGenesisAssetSource : GenesisAssetSource {
        private val entries = linkedMapOf<String, ByteArray>()
        var listedFilesOverride: List<String>? = null

        override fun readText(path: String): String {
            return readBytes(path).toString(Charsets.UTF_8)
        }

        override fun readBytes(path: String): ByteArray {
            return requireNotNull(entries[path]) { "Missing in-memory asset: $path" }.copyOf()
        }

        override fun listFiles(path: String): List<String> {
            return listedFilesOverride?.toList()
                ?: currentFiles().filter { file -> file == path || file.startsWith("$path/") }
        }

        fun writeText(path: String, value: String) {
            writeBytes(path, value.toByteArray(Charsets.UTF_8))
        }

        fun writeBytes(path: String, value: ByteArray) {
            entries[path] = value.copyOf()
        }

        fun currentFiles(): List<String> = entries.keys.sorted()
    }

    private fun computeCoreHash(files: JSONArray): String {
        val records = List(files.length()) { index ->
            val file = files.getJSONObject(index)
            mapOf(
                "kind" to file.getString("kind"),
                "path" to file.getString("path"),
                "required" to file.getBoolean("required"),
                "sha256" to file.getString("sha256")
            )
        }.sortedBy { record -> record.getValue("path") as String }
        return sha256(stableStringify(records).toByteArray(Charsets.UTF_8))
    }

    private fun stableStringify(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> JSONObject.quote(value)
            is Number, is Boolean -> value.toString()
            is List<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ",") {
                stableStringify(it)
            }
            is Map<*, *> -> value.keys
                .filterIsInstance<String>()
                .sorted()
                .joinToString(prefix = "{", postfix = "}", separator = ",") { key ->
                    "${JSONObject.quote(key)}:${stableStringify(value[key])}"
                }
            else -> error("Unsupported canonical fixture value: ${value::class.java.name}")
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return "sha256:" + digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private companion object {
        const val GENESIS_ROOT = "genesis"
        const val MANIFEST_PATH = "$GENESIS_ROOT/genesis_manifest.json"
    }
}
