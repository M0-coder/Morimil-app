package com.morimil.app.data.genesis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class GenesisManifestVerification(
    val manifestId: String,
    val genesisCoreHash: String,
    val fileCount: Int
)

data class GenesisInstalledBundle(
    val verification: GenesisManifestVerification,
    val installPath: String
)

internal interface GenesisAssetSource {
    fun readText(path: String): String

    fun readBytes(path: String): ByteArray

    fun listFiles(path: String): List<String>
}

private class AndroidGenesisAssetSource(context: Context) : GenesisAssetSource {
    private val assets = context.assets

    override fun readText(path: String): String {
        return assets.open(path).bufferedReader().use { it.readText() }
    }

    override fun readBytes(path: String): ByteArray {
        return assets.open(path).use { it.readBytes() }
    }

    override fun listFiles(path: String): List<String> {
        val children = assets.list(path)?.toList().orEmpty()
        if (children.isEmpty()) return listOf(path)
        return children.flatMap { child -> listFiles("$path/$child") }
    }
}

class GenesisManifestVerifier(context: Context) {
    private val core = GenesisManifestVerifierCore(
        assets = AndroidGenesisAssetSource(context),
        approvedGenesisCoreHash = APPROVED_GENESIS_CORE_HASH,
        approvedFileCount = APPROVED_FILE_COUNT
    )

    fun verify(): GenesisManifestVerification = core.verify()

    companion object {
        const val APPROVED_GENESIS_CORE_HASH =
            "sha256:fb0e37ef719b2cb2607944b7738a7e8e1abdb0e437e9c5bce84117d6df597f5f"

        internal const val APPROVED_FILE_COUNT = 17
        internal const val GENESIS_ROOT = "genesis"
        internal const val MANIFEST_ASSET = "$GENESIS_ROOT/genesis_manifest.json"
    }
}

internal class GenesisManifestVerifierCore(
    private val assets: GenesisAssetSource,
    private val approvedGenesisCoreHash: String,
    private val approvedFileCount: Int
) {
    fun verify(): GenesisManifestVerification {
        val manifestText = assets.readText(GenesisManifestVerifier.MANIFEST_ASSET)
        val manifest = JSONObject(manifestText)

        require(manifest.getString("schema_version") == "morimil.genesis_manifest.v1") {
            "Invalid Genesis manifest schema."
        }
        require(manifest.getString("genesis_core_hash") == approvedGenesisCoreHash) {
            "Genesis manifest hash is not the approved mobile seed."
        }
        require(manifest.getJSONObject("mobile_installation").getBoolean("startup_verification_required")) {
            "Genesis startup verification must be required."
        }

        val files = manifest.getJSONArray("files")
        require(files.length() == approvedFileCount) {
            "Genesis manifest must declare exactly $approvedFileCount seed files."
        }

        val declaredPaths = mutableSetOf<String>()
        val fileRecords = List(files.length()) { index ->
            val file = files.getJSONObject(index)
            val path = file.getString("path")
            requireValidBundlePath(path)
            require(file.getBoolean("required")) { "Genesis file must be required: $path" }
            require(declaredPaths.add(path)) { "Duplicate Genesis manifest path: $path" }

            val bytes = assets.readBytes("${GenesisManifestVerifier.GENESIS_ROOT}/$path")
            val actual = sha256(bytes)
            val expected = file.getString("sha256")
            require(actual == expected) { "Genesis asset hash mismatch: $path" }

            ManifestFile(
                path = path,
                kind = file.getString("kind"),
                sha256 = expected,
                required = file.getBoolean("required")
            )
        }.sortedBy { it.path }

        val actualPaths = assets.listFiles(GenesisManifestVerifier.GENESIS_ROOT)
            .map { it.removePrefix("${GenesisManifestVerifier.GENESIS_ROOT}/") }
            .filter { it != "genesis_manifest.json" }
            .toSet()
        require(actualPaths == declaredPaths) {
            val missing = declaredPaths.minus(actualPaths).sorted()
            val unexpected = actualPaths.minus(declaredPaths).sorted()
            "Genesis bundle file set mismatch. missing=$missing unexpected=$unexpected"
        }

        val computedCoreHash = sha256(
            stableStringify(fileRecords.map { it.toCanonicalJson() })
                .toByteArray(Charsets.UTF_8)
        )
        require(computedCoreHash == manifest.getString("genesis_core_hash")) {
            "Genesis core hash does not match declared file set."
        }

        return GenesisManifestVerification(
            manifestId = manifest.getString("manifest_id"),
            genesisCoreHash = computedCoreHash,
            fileCount = files.length()
        )
    }

    private fun requireValidBundlePath(path: String) {
        require(path.isNotBlank()) { "Genesis asset path must not be blank." }
        require(!path.startsWith("/")) { "Genesis asset path must be relative." }
        require(!path.contains("..")) { "Genesis asset path cannot escape bundle." }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return "sha256:" + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun stableStringify(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> quoteJsonString(value)
            is Number, is Boolean -> value.toString()
            is List<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ",") {
                stableStringify(it)
            }
            is Map<*, *> -> value.keys
                .filterIsInstance<String>()
                .sorted()
                .joinToString(prefix = "{", postfix = "}", separator = ",") { key ->
                    "${quoteJsonString(key)}:${stableStringify(value[key])}"
                }
            is JSONArray -> List(value.length()) { index -> value.get(index) }.let(::stableStringify)
            is JSONObject -> value.keys().asSequence().toList()
                .associateWith { key -> value.get(key) }
                .let(::stableStringify)
            else -> quoteJsonString(value.toString())
        }
    }

    private fun quoteJsonString(value: String): String {
        val output = StringBuilder(value.length + 2)
        output.append('"')
        value.forEach { char ->
            when (char) {
                '"' -> output.append("\\\"")
                '\\' -> output.append("\\\\")
                '\b' -> output.append("\\b")
                '\u000C' -> output.append("\\f")
                '\n' -> output.append("\\n")
                '\r' -> output.append("\\r")
                '\t' -> output.append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        output.append("\\u")
                        output.append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        output.append(char)
                    }
                }
            }
        }
        output.append('"')
        return output.toString()
    }

    private data class ManifestFile(
        val path: String,
        val kind: String,
        val sha256: String,
        val required: Boolean
    ) {
        fun toCanonicalJson(): Map<String, Any> {
            return mapOf(
                "kind" to kind,
                "path" to path,
                "required" to required,
                "sha256" to sha256
            )
        }
    }
}
