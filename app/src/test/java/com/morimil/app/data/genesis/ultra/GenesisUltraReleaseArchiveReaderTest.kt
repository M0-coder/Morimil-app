package com.morimil.app.data.genesis.ultra

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GenesisUltraReleaseArchiveReaderTest {
    @Test
    fun readsReservedMetadataAndExactPayloadBytes() {
        val identity = "{\"identity\":true}".toByteArray()
        val doctrine = "doctrine\n".toByteArray()
        val archive = zip(
            GenesisUltraReleaseArchiveReader.MANIFEST_ENTRY to "{\"manifest\":true}".toByteArray(),
            GenesisUltraReleaseArchiveReader.SIGNATURE_ENTRY to "{\"signature\":true}".toByteArray(),
            "identity/companion.identity.json" to identity,
            "doctrine/doctrine.md" to doctrine
        )

        val bundle = GenesisUltraReleaseArchiveReader().read(ByteArrayInputStream(archive))

        assertEquals("{\"manifest\":true}", bundle.manifestJson)
        assertEquals("{\"signature\":true}", bundle.signatureJson)
        assertEquals(
            setOf("identity/companion.identity.json", "doctrine/doctrine.md"),
            bundle.files.keys
        )
        assertArrayEquals(identity, bundle.files.getValue("identity/companion.identity.json"))
        assertArrayEquals(doctrine, bundle.files.getValue("doctrine/doctrine.md"))
    }

    @Test
    fun rejectsUnsafeRelativePathsBeforeReadingPayload() {
        val archive = zip(
            GenesisUltraReleaseArchiveReader.MANIFEST_ENTRY to "{}".toByteArray(),
            GenesisUltraReleaseArchiveReader.SIGNATURE_ENTRY to "{}".toByteArray(),
            "../escape.json" to byteArrayOf(1)
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            GenesisUltraReleaseArchiveReader().read(ByteArrayInputStream(archive))
        }

        assertEquals("invalid_relative_path", error.message)
    }

    @Test
    fun rejectsMissingDetachedSignature() {
        val archive = zip(
            GenesisUltraReleaseArchiveReader.MANIFEST_ENTRY to "{}".toByteArray(),
            "identity/companion.identity.json" to byteArrayOf(1)
        )

        val error = assertThrows(IllegalStateException::class.java) {
            GenesisUltraReleaseArchiveReader().read(ByteArrayInputStream(archive))
        }

        assertEquals("release_archive_signature_missing", error.message)
    }

    @Test
    fun rejectsMalformedUtf8Metadata() {
        val archive = zip(
            GenesisUltraReleaseArchiveReader.MANIFEST_ENTRY to byteArrayOf(0xC3.toByte()),
            GenesisUltraReleaseArchiveReader.SIGNATURE_ENTRY to "{}".toByteArray(),
            "identity/companion.identity.json" to byteArrayOf(1)
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            GenesisUltraReleaseArchiveReader().read(ByteArrayInputStream(archive))
        }

        assertEquals("release_archive_manifest_utf8_invalid", error.message)
    }

    @Test
    fun rejectsExpandedEntryBeyondConfiguredLimit() {
        val archive = zip(
            GenesisUltraReleaseArchiveReader.MANIFEST_ENTRY to "{}".toByteArray(),
            GenesisUltraReleaseArchiveReader.SIGNATURE_ENTRY to "{}".toByteArray(),
            "identity/companion.identity.json" to ByteArray(9) { 7 }
        )
        val reader = GenesisUltraReleaseArchiveReader(
            maxEntries = 8,
            maxEntryBytes = 8,
            maxTotalBytes = 32
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            reader.read(ByteArrayInputStream(archive))
        }

        assertEquals(
            "release_archive_entry_too_large:identity/companion.identity.json",
            error.message
        )
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        return ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                entries.forEach { (path, payload) ->
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(payload)
                    zip.closeEntry()
                }
            }
            bytes.toByteArray()
        }
    }
}
