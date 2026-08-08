package com.morimil.app.data.genesis.ultra

import com.morimil.app.data.repository.CrossDatabaseCanonicalCommand
import com.morimil.app.data.repository.CrossDatabaseCanonicalEnsurePort
import com.morimil.app.data.repository.CrossDatabaseCanonicalReceipt
import com.morimil.app.data.repository.CrossDatabaseOperationIdentity
import com.morimil.app.data.repository.CrossDatabaseProtocolErrors
import com.morimil.app.data.repository.OrchestrationProtocolTypes
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.json.JSONObject

internal class CanonicalOrchestrationCommitPort private constructor(
    private val appendText:
        suspend (CanonicalMemoryAppendCommand) -> CanonicalMemoryRecord,
    private val readVerifiedSnapshot: suspend () -> CanonicalMemorySnapshot
) : CrossDatabaseCanonicalEnsurePort {
    internal constructor(repository: CanonicalMemoryRepository) : this(
        appendText = repository::appendText,
        readVerifiedSnapshot = repository::readVerifiedSnapshot
    )

    override suspend fun ensureCommitted(
        command: CrossDatabaseCanonicalCommand
    ): CrossDatabaseCanonicalReceipt {
        require(command.operationType in OrchestrationProtocolTypes.CLOSED_REGISTRY) {
            "orchestration_operation_type_unsupported"
        }
        findVerified(command)?.let { existing ->
            return existing.copy(reusedExistingEvent = true)
        }

        val appended = try {
            appendText(command.toAppendCommand())
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
            "orchestration_canonical_event_missing_after_append"
        }.copy(reusedExistingEvent = false)
    }

    private suspend fun findVerified(
        command: CrossDatabaseCanonicalCommand
    ): CrossDatabaseCanonicalReceipt? {
        val snapshot = readVerifiedSnapshot()
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
            record.event.contentType == CONTENT_TYPE &&
            record.event.privacy == PRIVACY &&
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
        val actualProvenance = try {
            CrossDatabaseOperationIdentity.canonicalJson(
                JSONObject(provenanceBytes.toString(StandardCharsets.UTF_8))
            )
        } catch (failure: Throwable) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.CANONICAL_PROVENANCE_MISMATCH,
                failure
            )
        }
        if (actualProvenance != expectedProvenance(command)) {
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
        return CanonicalMemoryAppendCommand(
            eventType = eventType,
            actor = ACTOR,
            content = eventBody,
            observedAt = observedAt(occurredAtMillis),
            provenance = CanonicalMemoryProvenance(
                source = SOURCE,
                classification = CLASSIFICATION,
                userConfirmed = operationType != OrchestrationProtocolTypes.PROPOSE,
                sourceId = operationId,
                note = expectedNote(this)
            ),
            eventId = eventId
        )
    }

    private fun expectedProvenance(command: CrossDatabaseCanonicalCommand): String {
        return CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "body_id" to command.writerBodyId,
                "classification" to CLASSIFICATION,
                "instance_id" to command.instanceId,
                "note" to expectedNote(command),
                "schema" to PROVENANCE_SCHEMA,
                "source" to SOURCE,
                "source_id" to command.operationId,
                "user_confirmed" to
                    (command.operationType != OrchestrationProtocolTypes.PROPOSE)
            )
        )
    }

    private fun expectedNote(command: CrossDatabaseCanonicalCommand): String {
        return CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "evidence_digest" to command.evidenceDigest,
                "instance_id" to command.instanceId,
                "operation_id" to command.operationId,
                "operation_type" to command.operationType,
                "operation_version" to command.operationVersion,
                "owner_type" to OrchestrationProtocolTypes.OWNER_TYPE,
                "payload_digest" to command.payloadDigest,
                "schema" to NOTE_SCHEMA,
                "subject_id" to command.subjectId,
                "writer_body_id" to command.writerBodyId,
                "writer_epoch" to command.writerEpoch
            )
        )
    }

    private fun observedAt(millis: Long): String {
        return Instant.ofEpochMilli(millis).truncatedTo(ChronoUnit.SECONDS).toString()
    }

    internal companion object {
        fun testing(
            appendText: suspend (CanonicalMemoryAppendCommand) -> CanonicalMemoryRecord,
            readVerifiedSnapshot: suspend () -> CanonicalMemorySnapshot
        ): CanonicalOrchestrationCommitPort {
            return CanonicalOrchestrationCommitPort(
                appendText = appendText,
                readVerifiedSnapshot = readVerifiedSnapshot
            )
        }

        const val ACTOR = "orchestration_protocol"
        const val CONTENT_TYPE = "text/plain"
        const val PRIVACY = "private_local"
        const val PROVENANCE_SCHEMA = "morimil.canonical_memory.provenance.v1"
        const val SOURCE = "cross_database_operations"
        const val CLASSIFICATION = "durable_orchestration_transition"
        const val NOTE_SCHEMA = "morimil.cross_database_operation.canonical_commit.v1"
    }
}
