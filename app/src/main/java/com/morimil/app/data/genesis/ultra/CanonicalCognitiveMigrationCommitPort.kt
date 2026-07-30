package com.morimil.app.data.genesis.ultra

import com.morimil.app.data.repository.CognitiveMigrationProtocolTypes
import com.morimil.app.data.repository.CrossDatabaseCanonicalCommand
import com.morimil.app.data.repository.CrossDatabaseCanonicalEnsurePort
import com.morimil.app.data.repository.CrossDatabaseCanonicalReceipt
import com.morimil.app.data.repository.CrossDatabaseProtocolErrors
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.json.JSONObject

internal class CanonicalCognitiveMigrationCommitPort(
    private val repository: CanonicalMemoryRepository
) : CrossDatabaseCanonicalEnsurePort {
    override suspend fun ensureCommitted(
        command: CrossDatabaseCanonicalCommand
    ): CrossDatabaseCanonicalReceipt {
        findVerified(command)?.let { existing ->
            return existing.copy(reusedExistingEvent = true)
        }

        val appended = try {
            repository.appendText(command.toAppendCommand())
        } catch (failure: Throwable) {
            CrossDatabaseProtocolErrors.rethrowCancellation(failure)
            val recovered = findVerified(command)
            if (recovered != null) {
                return recovered.copy(reusedExistingEvent = true)
            }
            throw failure
        }
        verifyRecord(command, appended)

        return requireNotNull(findVerified(command)) {
            "cognitive_migration_canonical_event_missing_after_append"
        }.copy(reusedExistingEvent = false)
    }

    private suspend fun findVerified(
        command: CrossDatabaseCanonicalCommand
    ): CrossDatabaseCanonicalReceipt? {
        val snapshot = repository.readVerifiedSnapshot()
        if (snapshot.instanceId != command.instanceId) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.WRONG_INSTANCE
            )
        }
        val records = snapshot.records.filter { record ->
            record.event.eventId == command.eventId
        }
        if (records.size > 1) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.EVENT_ID_CONFLICT
            )
        }
        val record = records.singleOrNull() ?: return null
        return verifyRecord(command, record)
    }

    private fun verifyRecord(
        command: CrossDatabaseCanonicalCommand,
        record: CanonicalMemoryRecord
    ): CrossDatabaseCanonicalReceipt {
        if (!record.hasPayload) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.CANONICAL_EVENT_MISMATCH
            )
        }
        val exactEvent = record.event.eventId == command.eventId &&
            record.event.eventType == command.eventType &&
            record.event.actor == ACTOR &&
            record.event.instanceId == command.instanceId &&
            record.event.bodyId == command.writerBodyId &&
            record.event.signature.keyEpochId == command.writerEpoch &&
            record.event.observedAt == observedAt(command.occurredAtMillis) &&
            record.textContent == command.eventBody
        if (!exactEvent) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.CANONICAL_EVENT_MISMATCH
            )
        }

        val provenanceBytes = record.copyProvenanceBytes()
            ?: throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.CANONICAL_PROVENANCE_MISMATCH
            )
        val provenance = JSONObject(provenanceBytes.toString(StandardCharsets.UTF_8))
        val expectedUserConfirmed =
            command.operationType != CognitiveMigrationProtocolTypes.PROPOSE
        val exactProvenance = provenance.getString("source") == SOURCE &&
            provenance.getString("classification") == CLASSIFICATION &&
            provenance.getBoolean("user_confirmed") == expectedUserConfirmed &&
            provenance.getString("source_id") == command.operationId
        if (!exactProvenance) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.CANONICAL_PROVENANCE_MISMATCH
            )
        }

        val note = JSONObject(provenance.getString("note"))
        val exactNote = note.getString("schema") == NOTE_SCHEMA &&
            note.getString("operation_id") == command.operationId &&
            note.getString("owner_type") == CognitiveMigrationProtocolTypes.OWNER_TYPE &&
            note.getString("operation_type") == command.operationType &&
            note.getInt("operation_version") == command.operationVersion &&
            note.getString("instance_id") == command.instanceId &&
            note.getString("writer_body_id") == command.writerBodyId &&
            note.getString("writer_epoch") == command.writerEpoch &&
            note.getString("subject_id") == command.subjectId &&
            note.getString("payload_digest") == command.payloadDigest &&
            note.getString("evidence_digest") == command.evidenceDigest
        if (!exactNote) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.CANONICAL_PROVENANCE_MISMATCH
            )
        }

        return CrossDatabaseCanonicalReceipt(
            eventId = record.event.eventId,
            eventHash = record.event.eventHash,
            sequence = record.event.sequence,
            provenanceDigest = record.event.provenanceDigest,
            reusedExistingEvent = true
        )
    }

    private fun CrossDatabaseCanonicalCommand.toAppendCommand():
        CanonicalMemoryAppendCommand {
        val note = JSONObject()
            .put("schema", NOTE_SCHEMA)
            .put("operation_id", operationId)
            .put("owner_type", CognitiveMigrationProtocolTypes.OWNER_TYPE)
            .put("operation_type", operationType)
            .put("operation_version", operationVersion)
            .put("instance_id", instanceId)
            .put("writer_body_id", writerBodyId)
            .put("writer_epoch", writerEpoch)
            .put("subject_id", subjectId)
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
                userConfirmed = operationType != CognitiveMigrationProtocolTypes.PROPOSE,
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
        const val ACTOR = "cognitive_migration_protocol"
        const val SOURCE = "cross_database_operations"
        const val CLASSIFICATION = "durable_cognitive_migration_transition"
        const val NOTE_SCHEMA = "morimil.cross_database_operation.canonical_commit.v1"
    }
}
