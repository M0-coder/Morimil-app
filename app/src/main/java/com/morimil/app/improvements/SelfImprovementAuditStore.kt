package com.morimil.app.improvements

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Local operational audit evidence. It is not canonical memory or identity authority. */
internal data class SelfChangeAuditRecord(
    val sequence: Long,
    val previousRecordDigest: String,
    val observationDigest: String,
    val stage: SelfChangeStage,
    val actor: SelfChangeActor,
    val candidateDigest: String?,
    val baseCommitSha: String?,
    val occurrenceCount: Int,
    val recordedAtMillis: Long,
    val recordDigest: String
)

/**
 * Durable append-only local audit for the self-improvement control plane.
 *
 * A separate head anchor prevents deletion, zero-truncation or rollback of only
 * the audit log from being accepted as a fresh history. The anchor is not a
 * substitute for a future remote/hardware monotonic witness: deleting or rolling
 * back the entire app-private storage remains outside this local guarantee.
 */
internal class SelfImprovementAuditStore(
    private val auditFile: File,
    private val anchorFile: File = File(
        auditFile.parentFile ?: File("."),
        DEFAULT_ANCHOR_FILENAME
    )
) {
    private val lock = Any()

    fun append(
        candidate: SelfChangeCandidate,
        actor: SelfChangeActor,
        recordedAtMillis: Long = System.currentTimeMillis(),
        occurrenceCount: Int = 1
    ): SelfChangeAuditRecord = synchronized(lock) {
        require(recordedAtMillis >= 0L) { "self_audit_time_invalid" }
        require(occurrenceCount > 0) { "self_audit_occurrence_count_invalid" }
        val existing = readVerifiedRecordsLocked()
        val previous = existing.lastOrNull()
        val record = buildRecord(
            sequence = (previous?.sequence ?: 0L) + 1L,
            previousRecordDigest = previous?.recordDigest ?: ZERO_SHA256,
            candidate = candidate,
            actor = actor,
            occurrenceCount = occurrenceCount,
            recordedAtMillis = recordedAtMillis
        )
        auditFile.parentFile?.let { parent ->
            require(parent.exists() || parent.mkdirs()) { "self_audit_parent_create_failed" }
        }
        val encoded = encode(record).toByteArray(StandardCharsets.UTF_8)
        FileOutputStream(auditFile, true).use { output ->
            output.write(encoded)
            output.flush()
            output.fd.sync()
        }
        writeAnchor(record.sequence, record.recordDigest)
        val verified = readVerifiedRecordsLocked()
        require(verified.lastOrNull() == record) { "self_audit_append_verification_failed" }
        record
    }

    fun readVerifiedRecords(): List<SelfChangeAuditRecord> = synchronized(lock) {
        readVerifiedRecordsLocked()
    }

    internal fun anchorPathForDiagnostics(): File = anchorFile

    private fun readVerifiedRecordsLocked(): List<SelfChangeAuditRecord> {
        val auditExists = auditFile.exists()
        val anchorExists = anchorFile.exists()
        if (!auditExists && !anchorExists) return emptyList()
        require(auditExists && anchorExists) { "self_audit_rollback_or_deletion_detected" }
        require(auditFile.isFile) { "self_audit_not_regular_file" }
        require(anchorFile.isFile) { "self_audit_anchor_not_regular_file" }

        val text = auditFile.readText(StandardCharsets.UTF_8)
        require(text.isNotEmpty()) { "self_audit_rollback_or_deletion_detected" }
        require(text.endsWith('\n')) { "self_audit_truncated_record" }
        val lines = text.split('\n').dropLast(1)
        var previousDigest = ZERO_SHA256
        var expectedSequence = 1L
        val records = lines.map { line ->
            val record = decode(line)
            require(record.sequence == expectedSequence) { "self_audit_sequence_invalid" }
            require(record.previousRecordDigest == previousDigest) { "self_audit_chain_invalid" }
            require(record.recordDigest == digestFor(record.copy(recordDigest = ZERO_SHA256))) {
                "self_audit_record_digest_invalid"
            }
            previousDigest = record.recordDigest
            expectedSequence += 1L
            record
        }
        require(records.isNotEmpty()) { "self_audit_rollback_or_deletion_detected" }

        val anchor = readAnchor()
        require(anchor.sequence > 0L && anchor.sequence <= records.last().sequence) {
            "self_audit_anchor_sequence_ahead_of_log"
        }
        val anchoredRecord = records[(anchor.sequence - 1L).toInt()]
        require(anchoredRecord.recordDigest == anchor.recordDigest) {
            "self_audit_anchor_digest_mismatch"
        }

        // A process interruption may occur after log fsync but before anchor replace.
        // Only a valid extension of the anchored prefix may advance the anchor.
        if (records.last().sequence > anchor.sequence) {
            writeAnchor(records.last().sequence, records.last().recordDigest)
        }
        return records
    }

    private fun buildRecord(
        sequence: Long,
        previousRecordDigest: String,
        candidate: SelfChangeCandidate,
        actor: SelfChangeActor,
        occurrenceCount: Int,
        recordedAtMillis: Long
    ): SelfChangeAuditRecord {
        val unsigned = SelfChangeAuditRecord(
            sequence = sequence,
            previousRecordDigest = previousRecordDigest,
            observationDigest = candidate.observationDigest,
            stage = candidate.stage,
            actor = actor,
            candidateDigest = candidate.candidateDigest,
            baseCommitSha = candidate.baseCommitSha,
            occurrenceCount = occurrenceCount,
            recordedAtMillis = recordedAtMillis,
            recordDigest = ZERO_SHA256
        )
        return unsigned.copy(recordDigest = digestFor(unsigned))
    }

    private fun digestFor(record: SelfChangeAuditRecord): String {
        val fields = listOf(
            AUDIT_DOMAIN,
            record.sequence.toString(),
            record.previousRecordDigest,
            record.observationDigest,
            record.stage.name,
            record.actor.name,
            record.candidateDigest.orEmpty(),
            record.baseCommitSha.orEmpty(),
            record.occurrenceCount.toString(),
            record.recordedAtMillis.toString()
        )
        return sha256(fields.joinToString(separator = "\u001f").toByteArray(StandardCharsets.UTF_8))
    }

    private fun encode(record: SelfChangeAuditRecord): String {
        return listOf(
            record.sequence.toString(),
            record.previousRecordDigest,
            record.observationDigest,
            record.stage.name,
            record.actor.name,
            record.candidateDigest.orEmpty(),
            record.baseCommitSha.orEmpty(),
            record.occurrenceCount.toString(),
            record.recordedAtMillis.toString(),
            record.recordDigest
        ).joinToString(separator = "\t", postfix = "\n")
    }

    private fun decode(line: String): SelfChangeAuditRecord {
        val fields = line.split('\t')
        require(fields.size == FIELD_COUNT) { "self_audit_field_count_invalid" }
        val sequence = fields[0].toLongOrNull() ?: error("self_audit_sequence_parse_failed")
        val previous = fields[1]
        val observation = fields[2]
        val stage = runCatching { SelfChangeStage.valueOf(fields[3]) }
            .getOrElse { throw IllegalStateException("self_audit_stage_invalid", it) }
        val actor = runCatching { SelfChangeActor.valueOf(fields[4]) }
            .getOrElse { throw IllegalStateException("self_audit_actor_invalid", it) }
        val candidateDigest = fields[5].ifEmpty { null }
        val baseCommitSha = fields[6].ifEmpty { null }
        val occurrenceCount = fields[7].toIntOrNull() ?: error("self_audit_occurrence_parse_failed")
        val recordedAtMillis = fields[8].toLongOrNull() ?: error("self_audit_time_parse_failed")
        val recordDigest = fields[9]
        require(SHA256_REF.matches(previous)) { "self_audit_previous_digest_invalid" }
        require(SHA256_REF.matches(observation)) { "self_audit_observation_digest_invalid" }
        require(candidateDigest == null || SHA256_REF.matches(candidateDigest)) {
            "self_audit_candidate_digest_invalid"
        }
        require(baseCommitSha == null || COMMIT_SHA.matches(baseCommitSha)) {
            "self_audit_base_sha_invalid"
        }
        require(occurrenceCount > 0) { "self_audit_occurrence_count_invalid" }
        require(recordedAtMillis >= 0L) { "self_audit_time_invalid" }
        require(SHA256_REF.matches(recordDigest)) { "self_audit_record_digest_format_invalid" }
        return SelfChangeAuditRecord(
            sequence = sequence,
            previousRecordDigest = previous,
            observationDigest = observation,
            stage = stage,
            actor = actor,
            candidateDigest = candidateDigest,
            baseCommitSha = baseCommitSha,
            occurrenceCount = occurrenceCount,
            recordedAtMillis = recordedAtMillis,
            recordDigest = recordDigest
        )
    }

    private fun writeAnchor(sequence: Long, recordDigest: String) {
        anchorFile.parentFile?.let { parent ->
            require(parent.exists() || parent.mkdirs()) { "self_audit_anchor_parent_create_failed" }
        }
        val anchorDigest = anchorDigest(sequence, recordDigest)
        val encoded = "$sequence\t$recordDigest\t$anchorDigest\n".toByteArray(StandardCharsets.UTF_8)
        val temp = File(anchorFile.parentFile ?: File("."), anchorFile.name + ".tmp")
        FileOutputStream(temp, false).use { output ->
            output.write(encoded)
            output.flush()
            output.fd.sync()
        }
        try {
            Files.move(
                temp.toPath(),
                anchorFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), anchorFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        require(anchorFile.isFile && anchorFile.length() > 0L) { "self_audit_anchor_replace_failed" }
    }

    private fun readAnchor(): AuditAnchor {
        val text = anchorFile.readText(StandardCharsets.UTF_8)
        require(text.endsWith('\n')) { "self_audit_anchor_truncated" }
        val fields = text.dropLast(1).split('\t')
        require(fields.size == ANCHOR_FIELD_COUNT) { "self_audit_anchor_field_count_invalid" }
        val sequence = fields[0].toLongOrNull() ?: error("self_audit_anchor_sequence_parse_failed")
        val recordDigest = fields[1]
        val digest = fields[2]
        require(sequence > 0L) { "self_audit_anchor_sequence_invalid" }
        require(SHA256_REF.matches(recordDigest)) { "self_audit_anchor_record_digest_invalid" }
        require(SHA256_REF.matches(digest)) { "self_audit_anchor_digest_invalid" }
        require(digest == anchorDigest(sequence, recordDigest)) { "self_audit_anchor_integrity_invalid" }
        return AuditAnchor(sequence, recordDigest)
    }

    private fun anchorDigest(sequence: Long, recordDigest: String): String {
        return sha256(
            listOf(ANCHOR_DOMAIN, sequence.toString(), recordDigest)
                .joinToString(separator = "\u001f")
                .toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return "sha256:" + digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private data class AuditAnchor(val sequence: Long, val recordDigest: String)

    internal companion object {
        const val DEFAULT_RELATIVE_PATH = "self-improvement/self-change-audit-v2.log"
        const val DEFAULT_ANCHOR_FILENAME = "self-change-audit-v2.anchor"
        private const val AUDIT_DOMAIN = "morimil.self_improvement.audit.v2"
        private const val ANCHOR_DOMAIN = "morimil.self_improvement.audit.anchor.v1"
        private const val FIELD_COUNT = 10
        private const val ANCHOR_FIELD_COUNT = 3
        private val ZERO_SHA256 = "sha256:" + "0".repeat(64)
        private val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
        private val COMMIT_SHA = Regex("^[a-f0-9]{40}$")
    }
}
