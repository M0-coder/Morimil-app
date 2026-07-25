package com.morimil.app.data.genesis.ultra

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.zip.ZipInputStream

/**
 * Reads the transport envelope for one final atomic-birth witness package.
 *
 * The transport manifest is not a trust root. It only binds the archive to the
 * exact candidate and consent already held locally and declares the bytes that
 * must later pass the full Body and Guardian signature verifier. No entry is
 * extracted to disk and no caller-controlled path is resolved locally.
 */
internal class GenesisUltraAtomicBirthWitnessArchiveReader(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxEntryBytes: Int = DEFAULT_MAX_ENTRY_BYTES,
    private val maxTotalBytes: Int = DEFAULT_MAX_TOTAL_BYTES
) {
    init {
        require(maxEntries > 0) { "witness_archive_entry_limit_invalid" }
        require(maxEntryBytes > 0) { "witness_archive_entry_size_limit_invalid" }
        require(maxTotalBytes >= maxEntryBytes) { "witness_archive_total_size_limit_invalid" }
    }

    fun read(
        input: InputStream,
        expectedCandidateDigest: String,
        expectedConsentDigest: String
    ): GenesisUltraAtomicBirthWitnessPackage {
        require(SHA256_REF.matches(expectedCandidateDigest)) {
            "witness_archive_expected_candidate_digest_invalid"
        }
        require(SHA256_REF.matches(expectedConsentDigest)) {
            "witness_archive_expected_consent_digest_invalid"
        }

        val files = readArchiveFiles(input)
        val manifestBytes = files.remove(MANIFEST_ENTRY)
            ?: error("witness_archive_manifest_missing")
        val manifest = parseManifest(
            decodeStrictUtf8(manifestBytes, "witness_archive_manifest_utf8_invalid")
        )

        require(manifest.candidateDigest == expectedCandidateDigest) {
            "witness_archive_candidate_digest_mismatch"
        }
        require(manifest.consentDigest == expectedConsentDigest) {
            "witness_archive_consent_digest_mismatch"
        }

        val declaredPaths = buildList {
            addAll(manifest.artifacts.map(ArtifactRecord::path))
            addAll(manifest.journal.map(JournalRecord::path))
        }
        require(declaredPaths.distinct().size == declaredPaths.size) {
            "witness_archive_declared_path_duplicate"
        }
        require(files.keys == declaredPaths.toSet()) {
            val missing = declaredPaths.toSet().minus(files.keys).sorted()
            val unexpected = files.keys.minus(declaredPaths.toSet()).sorted()
            "witness_archive_file_set_mismatch:missing=$missing:unexpected=$unexpected"
        }

        val artifacts = manifest.artifacts.map { record ->
            val bytes = requireNotNull(files[record.path]).copyOf()
            require(bytes.isNotEmpty()) { "witness_archive_artifact_empty:${record.path}" }
            require(GenesisUltraHashProfile.sha256(bytes) == record.digest) {
                "witness_archive_artifact_digest_mismatch:${record.path}"
            }
            GenesisUltraBirthArtifact(
                relativePath = record.path,
                artifactKind = record.kind,
                payload = bytes
            )
        }

        val journal = manifest.journal.map { record ->
            val bytes = requireNotNull(files[record.path]).copyOf()
            require(bytes.isNotEmpty()) { "witness_archive_journal_empty:${record.path}" }
            require(GenesisUltraHashProfile.sha256(bytes) == record.digest) {
                "witness_archive_journal_digest_mismatch:${record.path}"
            }
            val entry = GenesisUltraAtomicBirthDocumentParser.parseJournalEntry(
                decodeStrictUtf8(bytes, "witness_archive_journal_utf8_invalid:${record.path}")
            )
            GenesisUltraBirthJournalEvidence(entry = entry, sourceBytes = bytes)
        }
        require(journal.map { evidence -> evidence.entry.sequence }.distinct().size == journal.size) {
            "witness_archive_journal_sequence_duplicate"
        }

        return GenesisUltraAtomicBirthWitnessPackage(
            artifacts = artifacts,
            journal = journal,
            evaluatedAt = manifest.evaluatedAt
        )
    }

    private fun readArchiveFiles(input: InputStream): LinkedHashMap<String, ByteArray> {
        val files = linkedMapOf<String, ByteArray>()
        var entryCount = 0
        var totalBytes = 0

        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount += 1
                require(entryCount <= maxEntries) { "witness_archive_too_many_entries" }
                require(!entry.isDirectory) { "witness_archive_directory_entry_forbidden:${entry.name}" }

                val path = entry.name
                GenesisUltraHashProfile.requireSafeRelativePath(path)
                require(files[path] == null) { "witness_archive_duplicate_entry:$path" }

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
                            "witness_archive_entry_too_large:$path"
                        }
                        require(totalBytes <= maxTotalBytes) {
                            "witness_archive_total_too_large"
                        }
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
                files[path] = bytes
                zip.closeEntry()
            }
        }
        require(entryCount > 0) { "witness_archive_empty" }
        return files
    }

    private fun parseManifest(jsonText: String): TransportManifest {
        val root = GenesisUltraStrictJson.parseObject(jsonText)
        root.requireExactKeys(MANIFEST_FIELDS, "witness_archive_manifest_fields_invalid")
        val schemaVersion = root.requiredText("schema_version", 1, 128)
        require(schemaVersion == MANIFEST_SCHEMA) { "witness_archive_manifest_schema_invalid" }
        val candidateDigest = root.requiredSha256("candidate_digest")
        val consentDigest = root.requiredSha256("consent_digest")
        val evaluatedAt = root.requiredCanonicalTimestamp("evaluated_at")
        val artifacts = parseArtifacts(root.getJSONArray("artifacts"))
        val journal = parseJournal(root.getJSONArray("journal"))

        require(artifacts.map(ArtifactRecord::path).distinct().size == artifacts.size) {
            "witness_archive_artifact_path_duplicate"
        }
        require(journal.map(JournalRecord::path).distinct().size == journal.size) {
            "witness_archive_journal_path_duplicate"
        }
        GenesisUltraAtomicBirthPersistenceValidator.mandatoryArtifactKinds.forEach { kind ->
            require(artifacts.count { record -> record.kind == kind } == 1) {
                "witness_archive_artifact_kind_invalid:$kind"
            }
        }

        return TransportManifest(
            candidateDigest = candidateDigest,
            consentDigest = consentDigest,
            evaluatedAt = evaluatedAt,
            artifacts = artifacts,
            journal = journal
        )
    }

    private fun parseArtifacts(array: JSONArray): List<ArtifactRecord> {
        require(array.length() >= GenesisUltraAtomicBirthPersistenceValidator.mandatoryArtifactKinds.size) {
            "witness_archive_artifacts_too_short"
        }
        return List(array.length()) { index ->
            val root = array.get(index)
            require(root is JSONObject) { "witness_archive_artifact_record_invalid" }
            root.requireExactKeys(ARTIFACT_FIELDS, "witness_archive_artifact_fields_invalid")
            val path = root.requiredPath("path")
            require(path != MANIFEST_ENTRY) { "witness_archive_reserved_path:$path" }
            val kind = root.requiredText("kind", 1, 128)
            require(kind == kind.trim()) { "witness_archive_artifact_kind_invalid" }
            ArtifactRecord(path = path, kind = kind, digest = root.requiredSha256("digest"))
        }
    }

    private fun parseJournal(array: JSONArray): List<JournalRecord> {
        require(array.length() >= 1) { "witness_archive_journal_missing" }
        return List(array.length()) { index ->
            val root = array.get(index)
            require(root is JSONObject) { "witness_archive_journal_record_invalid" }
            root.requireExactKeys(JOURNAL_FIELDS, "witness_archive_journal_fields_invalid")
            val path = root.requiredPath("path")
            require(path != MANIFEST_ENTRY) { "witness_archive_reserved_path:$path" }
            JournalRecord(path = path, digest = root.requiredSha256("digest"))
        }
    }

    private fun JSONObject.requireExactKeys(expected: Set<String>, errorCode: String) {
        val actual = keys().asSequence().toSet()
        require(actual == expected) { "$errorCode:expected=${expected.sorted()}:actual=${actual.sorted()}" }
    }

    private fun JSONObject.requiredText(name: String, minLength: Int, maxLength: Int): String {
        val value = get(name)
        require(value is String) { "witness_archive_invalid_$name" }
        GenesisUltraHashProfile.requireNfc(value)
        require(value.length in minLength..maxLength) { "witness_archive_invalid_$name" }
        return value
    }

    private fun JSONObject.requiredSha256(name: String): String {
        val value = requiredText(name, 71, 71)
        require(SHA256_REF.matches(value)) { "witness_archive_invalid_$name" }
        return value
    }

    private fun JSONObject.requiredPath(name: String): String {
        val value = requiredText(name, 1, 512)
        GenesisUltraHashProfile.requireSafeRelativePath(value)
        return value
    }

    private fun JSONObject.requiredCanonicalTimestamp(name: String): String {
        val value = requiredText(name, 20, 20)
        val parsed = runCatching { Instant.parse(value) }
            .getOrElse { failure -> throw IllegalArgumentException("witness_archive_invalid_$name", failure) }
        require(parsed.toString() == value) { "witness_archive_invalid_$name" }
        return value
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

    private data class TransportManifest(
        val candidateDigest: String,
        val consentDigest: String,
        val evaluatedAt: String,
        val artifacts: List<ArtifactRecord>,
        val journal: List<JournalRecord>
    )

    private data class ArtifactRecord(val path: String, val kind: String, val digest: String)
    private data class JournalRecord(val path: String, val digest: String)

    internal companion object {
        const val MANIFEST_ENTRY = "genesis.atomic.birth.witness.manifest.json"
        const val MANIFEST_SCHEMA = "genesis.atomic.birth.witness.transport.v0.1"
        const val DEFAULT_MAX_ENTRIES = 1024
        const val DEFAULT_MAX_ENTRY_BYTES = 4 * 1024 * 1024
        const val DEFAULT_MAX_TOTAL_BYTES = 64 * 1024 * 1024

        private const val BUFFER_BYTES = 8 * 1024
        private val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
        private val MANIFEST_FIELDS = setOf(
            "schema_version", "candidate_digest", "consent_digest", "evaluated_at", "artifacts", "journal"
        )
        private val ARTIFACT_FIELDS = setOf("path", "kind", "digest")
        private val JOURNAL_FIELDS = setOf("path", "digest")
    }
}
