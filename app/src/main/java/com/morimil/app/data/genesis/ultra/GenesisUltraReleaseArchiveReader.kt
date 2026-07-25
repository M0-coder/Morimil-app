package com.morimil.app.data.genesis.ultra

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

/**
 * Reads the Android import envelope for one signed Genesis Ultra Seed release.
 *
 * The ZIP is transport only. Its manifest, detached Guardian signature and exact
 * payload bytes are still verified by [GenesisUltraReleaseVerifier]. No archive
 * entry is written to disk and no caller-controlled path is resolved locally.
 */
internal class GenesisUltraReleaseArchiveReader(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxEntryBytes: Int = DEFAULT_MAX_ENTRY_BYTES,
    private val maxTotalBytes: Int = DEFAULT_MAX_TOTAL_BYTES
) {
    init {
        require(maxEntries > 0) { "release_archive_entry_limit_invalid" }
        require(maxEntryBytes > 0) { "release_archive_entry_size_limit_invalid" }
        require(maxTotalBytes >= maxEntryBytes) { "release_archive_total_size_limit_invalid" }
    }

    fun read(input: InputStream): GenesisUltraReleaseBundle {
        val files = linkedMapOf<String, ByteArray>()
        val seenEntries = linkedSetOf<String>()
        var entryCount = 0
        var totalBytes = 0

        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount += 1
                require(entryCount <= maxEntries) { "release_archive_too_many_entries" }

                val rawName = entry.name
                val validationName = if (entry.isDirectory) rawName.removeSuffix("/") else rawName
                GenesisUltraHashProfile.requireSafeRelativePath(validationName)
                require(seenEntries.add(rawName)) { "release_archive_duplicate_entry:$rawName" }

                if (!entry.isDirectory) {
                    val bytes = ByteArrayOutputStream().use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        var entryBytes = 0
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            entryBytes += read
                            totalBytes += read
                            require(entryBytes <= maxEntryBytes) {
                                "release_archive_entry_too_large:$rawName"
                            }
                            require(totalBytes <= maxTotalBytes) {
                                "release_archive_total_too_large"
                            }
                            output.write(buffer, 0, read)
                        }
                        output.toByteArray()
                    }
                    require(files.put(rawName, bytes) == null) {
                        "release_archive_duplicate_file:$rawName"
                    }
                }
                zip.closeEntry()
            }
        }

        require(entryCount > 0) { "release_archive_empty" }
        val manifestBytes = files.remove(MANIFEST_ENTRY)
            ?: error("release_archive_manifest_missing")
        val signatureBytes = files.remove(SIGNATURE_ENTRY)
            ?: error("release_archive_signature_missing")
        require(files.isNotEmpty()) { "release_archive_payload_missing" }

        return GenesisUltraReleaseBundle(
            manifestJson = decodeStrictUtf8(manifestBytes, "release_archive_manifest_utf8_invalid"),
            signatureJson = decodeStrictUtf8(signatureBytes, "release_archive_signature_utf8_invalid"),
            files = files.mapValues { (_, bytes) -> bytes.copyOf() }
        )
    }

    private fun decodeStrictUtf8(bytes: ByteArray, errorCode: String): String {
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (failure: Exception) {
            throw IllegalArgumentException(errorCode, failure)
        }
    }

    internal companion object {
        const val MANIFEST_ENTRY = "genesis.seed.manifest.json"
        const val SIGNATURE_ENTRY = "genesis.seed.signature.json"
        const val DEFAULT_MAX_ENTRIES = 512
        const val DEFAULT_MAX_ENTRY_BYTES = 4 * 1024 * 1024
        const val DEFAULT_MAX_TOTAL_BYTES = 32 * 1024 * 1024
        private const val BUFFER_BYTES = 8 * 1024
    }
}
