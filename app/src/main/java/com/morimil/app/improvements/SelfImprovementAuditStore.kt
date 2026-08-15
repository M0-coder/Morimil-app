package com.morimil.app.improvements

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
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
    val recordedAtMillis: Long,
    val recordDigest: String
)

/**
 * Durable append-only local audit for the self-improvement control plane.
 *
 * The file is hash chained and fsync'd after every append. Corruption, truncation
 * of a record, sequence gaps, or digest mismatch fail closed on read. This store
 * records control-plane evidence only and never becomes canonical Morimil memory.
 */
internal class SelfImprovementAuditStore(
    private val auditFile: File
) {
    private val lock = Any()

    fun append(
        candidate: SelfChangeCandidate,
        actor: SelfChangeActor,
        recordedAtMillis: Long = System.currentTimeMillis()
    ): SelfChangeAuditRecord = synchronized(lock) {
        require(recordedAtMillis >= 0L) { "self_audit_time_invalid" }
        val existing = readVerifiedRecordsLocked()
        val previous = existing.lastOrNull()
        val record = buildRecord(
            sequence = (previous?.sequence ?: 0L) + 1L,
            previousRecordDigest = previous?.recordDigest ?: ZERO_SHA256,
            candidate = candidate,
            actor = actor,
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
        val verified = readVerifiedRecordsLocked()
        require(verified.lastOrNull() == record) { "self_audit_append_verification_failed" }
        record
    }

    fun readVerifiedRecords(): List<SelfChangeAuditRecord> = synchronized(lock) {
        readVerifiedRecordsLocked()
    }

    private fun readVerifiedRecordsLocked(): List<SelfChangeAuditRecord> {
        if (!auditFile.exists()) return emptyList()
        require(auditFile.isFile) { "self_audit_not_regular_file" }
        val text = auditFile.readText(StandardCharsets.UTF_8)
        if (text.isEmpty()) return emptyList()
        require(text.endsWith('\n')) { "self_audit_truncated_record" }
        val lines = text.split('\n').dropLast(1)
        var previousDigest = ZERO_SHA256
        var expectedSequence = 1L
        return lines.map { line ->
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
    }

    private fun buildRecord(
        sequence: Long,
        previousRecordDigest: String,
        candidate: SelfChangeCandidate,
        actor: SelfChangeActor,
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
            record.recordedAtMillis.toString()
        )
        val preimage = fields.joinToString(separator = "\u001f").toByteArray(StandardCharsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(preimage)
        return "sha256:" + digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
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
        val recordedAtMillis = fields[7].toLongOrNull() ?: error("self_audit_time_parse_failed")
        val recordDigest = fields[8]
        require(SHA256_REF.matches(previous)) { "self_audit_previous_digest_invalid" }
        require(SHA256_REF.matches(observation)) { "self_audit_observation_digest_invalid" }
        require(candidateDigest == null || SHA256_REF.matches(candidateDigest)) {
            "self_audit_candidate_digest_invalid"
        }
        require(baseCommitSha == null || COMMIT_SHA.matches(baseCommitSha)) {
            "self_audit_base_sha_invalid"
        }
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
            recordedAtMillis = recordedAtMillis,
            recordDigest = recordDigest
        )
    }

    internal companion object {
        const val DEFAULT_RELATIVE_PATH = "self-improvement/self-change-audit-v1.log"
        private const val AUDIT_DOMAIN = "morimil.self_improvement.audit.v1"
        private const val FIELD_COUNT = 9
        private val ZERO_SHA256 = "sha256:" + "0".repeat(64)
        private val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
        private val COMMIT_SHA = Regex("^[a-f0-9]{40}$")
    }
}
