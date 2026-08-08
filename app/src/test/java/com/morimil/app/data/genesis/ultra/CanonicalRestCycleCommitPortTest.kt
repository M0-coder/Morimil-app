package com.morimil.app.data.genesis.ultra

import com.morimil.app.data.repository.CrossDatabaseCanonicalCommand
import com.morimil.app.data.repository.CrossDatabaseOperationIdentity
import com.morimil.app.data.repository.CrossDatabaseProtocolErrors
import com.morimil.app.data.repository.CrossDatabaseProtocolFailure
import com.morimil.app.data.repository.RestCycleProtocolTypes
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalRestCycleCommitPortTest {
    @Test
    fun exactExistingRestEventIsReusedWithoutAppend() = runBlocking {
        val command = command()
        val exact = record(command)
        var appendCalls = 0
        val port = CanonicalRestCycleCommitPort.testing(
            appendText = {
                appendCalls += 1
                error("append_must_not_run")
            },
            readVerifiedSnapshot = { snapshot(command.instanceId, exact) }
        )

        val receipt = port.ensureCommitted(command)

        assertEquals(0, appendCalls)
        assertEquals(command.eventId, receipt.eventId)
        assertEquals(exact.event.eventHash, receipt.eventHash)
        assertTrue(receipt.reusedExistingEvent)
    }

    @Test
    fun foreignSnapshotDuplicateAndEnvelopeMismatchFailClosed() = runBlocking {
        val command = command()
        val exact = record(command)

        assertFailure(
            CrossDatabaseProtocolErrors.WRONG_INSTANCE,
            runCatching {
                CanonicalRestCycleCommitPort.testing(
                    appendText = { error("append_must_not_run") },
                    readVerifiedSnapshot = { snapshot("foreign_instance", exact) }
                ).ensureCommitted(command)
            }.exceptionOrNull()
        )
        assertFailure(
            CrossDatabaseProtocolErrors.EVENT_ID_CONFLICT,
            runCatching {
                CanonicalRestCycleCommitPort.testing(
                    appendText = { error("append_must_not_run") },
                    readVerifiedSnapshot = { snapshot(command.instanceId, exact, exact) }
                ).ensureCommitted(command)
            }.exceptionOrNull()
        )
        listOf(
            record(command, content = "different"),
            record(command, actor = "wrong_actor"),
            record(command, bodyId = "foreign_body")
        ).forEach { conflict ->
            assertFailure(
                CrossDatabaseProtocolErrors.CANONICAL_EVENT_MISMATCH,
                runCatching { portFor(command, conflict).ensureCommitted(command) }.exceptionOrNull()
            )
        }
    }

    @Test
    fun provenanceMismatchFailsClosed() = runBlocking {
        val command = command()
        listOf(
            record(command, classification = "wrong_classification"),
            record(command, source = "wrong_source"),
            record(command, noteOwnerType = "wrong_owner"),
            record(command, userConfirmed = true)
        ).forEach { conflict ->
            assertFailure(
                CrossDatabaseProtocolErrors.CANONICAL_PROVENANCE_MISMATCH,
                runCatching { portFor(command, conflict).ensureCommitted(command) }.exceptionOrNull()
            )
        }
    }

    @Test
    fun interruptedAppendRecoversAndSuccessfulAppendIsVerified() = runBlocking {
        val command = command()
        val exact = record(command)
        var visible: CanonicalMemoryRecord? = null
        val interrupted = CanonicalRestCycleCommitPort.testing(
            appendText = {
                visible = exact
                error("simulated_interrupt_after_append")
            },
            readVerifiedSnapshot = {
                snapshot(command.instanceId, *listOfNotNull(visible).toTypedArray())
            }
        )
        val recovered = interrupted.ensureCommitted(command)
        assertTrue(recovered.reusedExistingEvent)

        visible = null
        val successful = CanonicalRestCycleCommitPort.testing(
            appendText = {
                visible = exact
                exact
            },
            readVerifiedSnapshot = {
                snapshot(command.instanceId, *listOfNotNull(visible).toTypedArray())
            }
        )
        val appended = successful.ensureCommitted(command)
        assertFalse(appended.reusedExistingEvent)
        assertEquals(exact.event.eventHash, appended.eventHash)
    }

    private fun portFor(
        command: CrossDatabaseCanonicalCommand,
        record: CanonicalMemoryRecord
    ): CanonicalRestCycleCommitPort {
        return CanonicalRestCycleCommitPort.testing(
            appendText = { error("append_must_not_run_for_existing_event") },
            readVerifiedSnapshot = { snapshot(command.instanceId, record) }
        )
    }

    private fun assertFailure(expectedCode: String, error: Throwable?) {
        val failure = error as CrossDatabaseProtocolFailure
        assertEquals(expectedCode, failure.stableCode)
        assertTrue(failure.permanent)
    }

    private fun snapshot(instanceId: String, vararg records: CanonicalMemoryRecord): CanonicalMemorySnapshot {
        val root = record(command()).event.copy(
            eventId = "evt_birth",
            instanceId = instanceId,
            sequence = 0,
            previousEventHash = "GENESIS",
            eventType = "instance.birth"
        )
        return CanonicalMemorySnapshot(
            instanceId = instanceId,
            companionName = "Morimil",
            birthRoot = root,
            records = records.toList()
        )
    }

    private fun record(
        command: CrossDatabaseCanonicalCommand,
        content: String = command.eventBody,
        actor: String = CanonicalRestCycleCommitPort.ACTOR,
        bodyId: String = command.writerBodyId,
        classification: String = CanonicalRestCycleCommitPort.CLASSIFICATION,
        source: String = CanonicalRestCycleCommitPort.SOURCE,
        noteOwnerType: String = RestCycleProtocolTypes.OWNER_TYPE,
        userConfirmed: Boolean = false
    ): CanonicalMemoryRecord {
        val note = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "evidence_digest" to command.evidenceDigest,
                "instance_id" to command.instanceId,
                "operation_id" to command.operationId,
                "operation_type" to command.operationType,
                "operation_version" to command.operationVersion,
                "owner_type" to noteOwnerType,
                "payload_digest" to command.payloadDigest,
                "schema" to CanonicalRestCycleCommitPort.NOTE_SCHEMA,
                "subject_id" to command.subjectId,
                "writer_body_id" to command.writerBodyId,
                "writer_epoch" to command.writerEpoch
            )
        )
        val provenance = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "body_id" to command.writerBodyId,
                "classification" to classification,
                "instance_id" to command.instanceId,
                "note" to note,
                "schema" to CanonicalRestCycleCommitPort.PROVENANCE_SCHEMA,
                "source" to source,
                "source_id" to command.operationId,
                "user_confirmed" to userConfirmed
            )
        ).toByteArray(StandardCharsets.UTF_8)
        return CanonicalMemoryRecord(
            event = GenesisUltraFirstMemoryEvent(
                schemaVersion = "genesis.memory.event.v0.1",
                hashProfile = GenesisUltraHashProfile.FIELD_PROFILE,
                eventId = command.eventId,
                instanceId = command.instanceId,
                bodyId = bodyId,
                sequence = 9,
                previousEventHash = "evsha256:" + "0".repeat(64),
                eventType = command.eventType,
                actor = actor,
                contentDigest = GenesisUltraHashProfile.sha256(content.toByteArray(StandardCharsets.UTF_8)),
                contentType = CanonicalRestCycleCommitPort.CONTENT_TYPE,
                contentRef = null,
                observedAt = "1970-01-01T00:00:01Z",
                provenanceDigest = GenesisUltraHashProfile.sha256(provenance),
                provenanceRef = null,
                privacy = CanonicalRestCycleCommitPort.PRIVACY,
                eventHash = "evsha256:" + "1".repeat(64),
                signature = signature(command)
            ),
            contentBytes = content.toByteArray(StandardCharsets.UTF_8),
            provenanceBytes = provenance,
            provenanceType = "application/json"
        )
    }

    private fun signature(command: CrossDatabaseCanonicalCommand): GenesisUltraSignatureEnvelope {
        return GenesisUltraSignatureEnvelope(
            schemaVersion = "genesis.signature.v0.1",
            signatureProfile = "test",
            signerType = "body",
            signerId = command.writerBodyId,
            keyEpochId = command.writerEpoch,
            signedDomain = "test",
            signedDigest = "sha256:" + "2".repeat(64),
            signatureValue = "test",
            createdAt = "1970-01-01T00:00:01Z",
            publicKeyRef = "sha256:" + "3".repeat(64)
        )
    }

    private fun command(): CrossDatabaseCanonicalCommand {
        val operationId = "xop_" + "a".repeat(64)
        return CrossDatabaseCanonicalCommand(
            operationId = operationId,
            operationType = RestCycleProtocolTypes.EXECUTE,
            operationVersion = RestCycleProtocolTypes.VERSION,
            instanceId = "instance_test",
            writerBodyId = "body_test",
            writerEpoch = "epoch_test",
            subjectId = "rest_" + "d".repeat(64),
            payloadDigest = "sha256:" + "e".repeat(64),
            evidenceDigest = "sha256:" + "f".repeat(64),
            eventId = CrossDatabaseOperationIdentity.eventId(
                operationId,
                RestCycleProtocolTypes.EXECUTED_EVENT
            ),
            eventType = RestCycleProtocolTypes.EXECUTED_EVENT,
            eventBody = "deterministic rest cycle transition",
            evidenceJson = "{}",
            occurredAtMillis = 1_000L
        )
    }
}
