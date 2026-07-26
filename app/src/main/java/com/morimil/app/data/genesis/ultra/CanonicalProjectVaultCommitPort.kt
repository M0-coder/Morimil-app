package com.morimil.app.data.genesis.ultra

import com.morimil.app.data.repository.ProjectVaultCommitCommand
import com.morimil.app.data.repository.ProjectVaultCommitPort
import com.morimil.app.data.repository.ProjectVaultCommitReceipt
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.json.JSONObject

/**
 * Canonical side of the ProjectVault outbox protocol.
 *
 * A deterministic event id may be observed after a process restart. It is
 * reusable only when signed content and provenance match the staged operation
 * exactly; an id collision never counts as a successful delivery.
 */
internal class CanonicalProjectVaultCommitPort(
    private val repository: CanonicalMemoryRepository
) : ProjectVaultCommitPort {
    override suspend fun ensureCommitted(
        command: ProjectVaultCommitCommand
    ): ProjectVaultCommitReceipt {
        findVerified(command)?.let { existing ->
            return existing.copy(reusedExistingEvent = true)
        }

        val appended = runCatching {
            repository.appendText(command.toCanonicalAppendCommand())
        }.getOrElse { failure ->
            val recovered = findVerified(command)
            if (recovered != null) {
                return recovered.copy(reusedExistingEvent = true)
            }
            throw failure
        }
        verifyRecord(command, appended)

        return requireNotNull(findVerified(command)) {
            "project_vault_canonical_event_missing_after_append"
        }.copy(reusedExistingEvent = false)
    }

    private suspend fun findVerified(
        command: ProjectVaultCommitCommand
    ): ProjectVaultCommitReceipt? {
        val records = repository.readVerifiedSnapshot().records
            .filter { record -> record.event.eventId == command.eventId }
        require(records.size <= 1) { "project_vault_canonical_event_id_duplicate" }
        val record = records.singleOrNull() ?: return null
        return verifyRecord(command, record)
    }

    private fun verifyRecord(
        command: ProjectVaultCommitCommand,
        record: CanonicalMemoryRecord
    ): ProjectVaultCommitReceipt {
        val expectedObservedAt = observedAt(command.occurredAtMillis)
        require(record.hasPayload) { "project_vault_canonical_payload_missing" }
        require(
            record.event.eventId == command.eventId &&
                record.event.eventType == command.eventType &&
                record.event.actor == ACTOR &&
                record.event.observedAt == expectedObservedAt &&
                record.textContent == command.eventBody.trim()
        ) { "project_vault_canonical_event_mismatch" }

        val provenanceBytes = requireNotNull(record.copyProvenanceBytes()) {
            "project_vault_canonical_provenance_missing"
        }
        val provenance = JSONObject(provenanceBytes.toString(StandardCharsets.UTF_8))
        require(
            provenance.getString("source") == SOURCE &&
                provenance.getString("classification") == CLASSIFICATION &&
                !provenance.getBoolean("user_confirmed") &&
                provenance.getString("source_id") == command.operationId
        ) { "project_vault_canonical_provenance_mismatch" }

        val note = JSONObject(provenance.getString("note"))
        val expectedEvidenceDigest = GenesisUltraHashProfile.sha256(
            command.evidenceJson.toByteArray(StandardCharsets.UTF_8)
        )
        require(
            note.getString("schema") == NOTE_SCHEMA &&
                note.getString("operation_id") == command.operationId &&
                note.getString("vault_id") == command.vaultId &&
                note.getString("operation_type") == command.operationType &&
                note.getString("payload_digest") == command.payloadDigest &&
                note.getString("evidence_digest") == expectedEvidenceDigest
        ) { "project_vault_canonical_note_mismatch" }

        return ProjectVaultCommitReceipt(
            eventId = record.event.eventId,
            eventHash = record.event.eventHash,
            sequence = record.event.sequence,
            reusedExistingEvent = true
        )
    }

    private fun ProjectVaultCommitCommand.toCanonicalAppendCommand():
        CanonicalMemoryAppendCommand {
        val evidenceDigest = GenesisUltraHashProfile.sha256(
            evidenceJson.toByteArray(StandardCharsets.UTF_8)
        )
        val note = JSONObject()
            .put("schema", NOTE_SCHEMA)
            .put("operation_id", operationId)
            .put("vault_id", vaultId)
            .put("operation_type", operationType)
            .put("payload_digest", payloadDigest)
            .put("evidence_digest", evidenceDigest)
            .toString()
        return CanonicalMemoryAppendCommand(
            eventType = eventType,
            actor = ACTOR,
            content = eventBody,
            observedAt = observedAt(occurredAtMillis),
            provenance = CanonicalMemoryProvenance(
                source = SOURCE,
                classification = CLASSIFICATION,
                userConfirmed = false,
                sourceId = operationId,
                note = note
            ),
            eventId = eventId
        )
    }

    private fun observedAt(millis: Long): String {
        return Instant.ofEpochMilli(millis).truncatedTo(ChronoUnit.SECONDS).toString()
    }

    private companion object {
        const val ACTOR = "project_vault_outbox"
        const val SOURCE = "project_vault_outbox"
        const val CLASSIFICATION = "durable_project_vault_transition"
        const val NOTE_SCHEMA = "morimil.project_vault_outbox.canonical_commit.v1"
    }
}
