package com.morimil.app.data.genesis.ultra

import com.morimil.app.data.repository.CrossDatabaseCanonicalCommand
import com.morimil.app.data.repository.CrossDatabaseOperationIdentity
import com.morimil.app.data.repository.CrossDatabaseProtocolErrors
import com.morimil.app.data.repository.CrossDatabaseProtocolFailure
import com.morimil.app.data.repository.OrchestrationProtocolTypes
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalOrchestrationCommitPortTest {
    @Test
    fun exactExistingProposalIsReusedWithoutAppend() = runBlocking {
        val command = command(OrchestrationProtocolTypes.PROPOSE)
        val exact = record(command)
        var appendCalls = 0
        val port = CanonicalOrchestrationCommitPort.testing(
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
    fun approvalProvenanceRequiresUserConfirmedTrue() = runBlocking {
        val command = command(OrchestrationProtocolTypes.APPROVE)
        val exact = record(command, userConfirmed = true)
        val port = CanonicalOrchestrationCommitPort.testing(
            appendText = { error("append_must_not_run") },
            readVerifiedSnapshot = { snapshot(command.instanceId, exact) }
        )

        val receipt = port.ensureCommitted(command)

        assertTrue(receipt.reusedExistingEvent)
        assertEquals(command.eventId, receipt.eventId)
    }

    @Test
    fun foreignSnapshotDuplicateEventAndEnvelopeMismatchFailClosed() = runBlocking {
        val command = command(OrchestrationProtocolTypes.PROPOSE)
        val exact = record(command)

        val foreign = CanonicalOrchestrationCommitPort.testing(
            appendText = { error("append_must_not_run") },
            readVerifiedSnapshot = { snapshot("foreign_instance", exact) }
        )
        assertFailure(
            CrossDatabaseProtocolErrors.WRONG_INSTANCE,
            runCatching { foreign.ensureCommitted(command) }.exceptionOrNull()
        )

        val duplicate = CanonicalOrchestrationCommitPort.testing(
            appendText = { error("append_must_not_run") },
            readVerifiedSnapshot = { snapshot(command.instanceId, exact, exact) }
        )
        assertFailure(
            CrossDatabaseProtocolErrors.EVENT_ID_CONFLICT,
            runCatching { duplicate.ensureCommitted(command) }.exceptionOrNull()
        )

        listOf(
            record(command, content = "different"),
            record(command, actor = "wrong_actor"),
            record(command, privacy = "public"),
            record(command, bodyId = "foreign_body")
        ).forEach { conflict ->
            val port = portFor(command, conflict)
            assertFailure(
                CrossDatabaseProtocolErrors.CANONICAL_EVENT_MISMATCH,
                runCatching { port.ensureCommitted(command) }.exceptionOrNull()
            )
        }
    }

    @Test
    fun provenanceMismatchFailsClosed() = runBlocking {
        val command = command(OrchestrationProtocolTypes.PROPOSE)
        listOf(
            record(command, classification = "wrong_classification"),
            record(command, source = "wrong_source"),
            record(command, noteOwnerType = "wrong_owner"),
            record(command, extraProvenanceField = "unexpected")
        ).forEach { conflict ->
            assertFailure(
                CrossDatabaseProtocolErrors.CANONICAL_PROVENANCE_MISMATCH,
                runCatching { portFor(command, conflict).ensureCommitted(command) }.exceptionOrNull()
            )
        }
    }

    @Test
    fun interruptedAppendRecoversExactEventAndSuccessfulAppendIsVerified() = runBlocking {
        val command = command(OrchestrationProtocolTypes.PROPOSE)
        val exact = record(command)
        var visible: CanonicalMemoryRecord? = null
        var reads = 0
        val interrupted = CanonicalOrchestrationCommitPort.testing(
            appendText = {
                visible = exact
                error("simulated_interrupt_after_append")
            },
            readVerifiedSnapshot = {
                reads += 1
                snapshot(command.instanceId, *listOfNotNull(visible).toTypedArray())
            }
        )

        val recovered = interrupted.ensureCommitted(command)
        assertTrue(recovered.reusedExistingEvent)
        assertTrue(reads >= 2)

        visible = null
        val successful = CanonicalOrchestrationCommitPort.testing(
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
    ): CanonicalOrchestrationCommitPort {
        return CanonicalOrchestrationCommitPort.testing(
            appendText = { error("append_must_not_run_for_existing_event") },
            readVerifiedSnapshot = { snapshot(command.instanceId, record) }
        )
    }

    private fun assertFailure(expectedCode: String, error: Throwable?) {
        val failure = error as CrossDatabaseProtocolFailure
        assertEquals(expectedCode, failure.stableCode)
        assertTrue(failure.permanent)
    }

    private fun snapshot(
        instanceId: String,
        vararg records: CanonicalMemoryRecord
    ): CanonicalMemorySnapshot {
        val command = command(OrchestrationProtocolTypes.PROPOSE)
        val root = record(command).event.copy(
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
        actor: String = "orchestration_protocol",
        privacy: String = "private_local",
        bodyId: String = command.writerBodyId,
        classification: String = "durable_orchestration_transition",
        source: String = "cross_database_operations",
        noteOwnerType: String = OrchestrationProtocolTypes.OWNER_TYPE,
        userConfirmed: Boolean = command.operationType != OrchestrationProtocolTypes.PROPOSE,
        extraProvenanceField: String? = null
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
                "schema" to "morimil.cross_database_operation.canonical_commit.v1",
                "subject_id" to command.subjectId,
                "writer_body_id" to command.writerBodyId,
                "writer_epoch" to command.writerEpoch
            )
        )
        val provenanceValues = linkedMapOf<String, Any?>(
            "body_id" to command.writerBodyId,
            "classification" to classification,
            "instance_id" to command.instanceId,
            "note" to note,
            "schema" to "morimil.canonical_memory.provenance.v1",
            "source" to source,
            "source_id" to command.operationId,
            "user_confirmed" to userConfirmed
        )
        if (extraProvenanceField != null) {
            provenanceValues["unexpected"] = extraProvenanceField
        }
        val provenance = CrossDatabaseOperationIdentity.canonicalJson(provenanceValues)
            .toByteArray(StandardCharsets.UTF_8)
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
                contentDigest = GenesisUltraHashProfile.sha256(
                    content.toByteArray(StandardCharsets.UTF_8)
                ),
                contentType = "text/plain",
                contentRef = null,
                observedAt = "1970-01-01T00:00:01Z",
                provenanceDigest = GenesisUltraHashProfile.sha256(provenance),
                provenanceRef = null,
                privacy = privacy,
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

    private fun command(operationType: String): CrossDatabaseCanonicalCommand {
        val eventType = when (operationType) {
            OrchestrationProtocolTypes.PROPOSE -> OrchestrationProtocolTypes.PROPOSED_EVENT
            OrchestrationProtocolTypes.APPROVE -> OrchestrationProtocolTypes.APPROVED_EVENT
            else -> OrchestrationProtocolTypes.REJECTED_EVENT
        }
        val discriminator = when (operationType) {
            OrchestrationProtocolTypes.PROPOSE -> "a"
            OrchestrationProtocolTypes.APPROVE -> "b"
            else -> "c"
        }
        return CrossDatabaseCanonicalCommand(
            operationId = "xop_" + discriminator.repeat(64),
            operationType = operationType,
            operationVersion = OrchestrationProtocolTypes.VERSION,
            instanceId = "instance_test",
            writerBodyId = "body_test",
            writerEpoch = "epoch_test",
            subjectId = "dtask_" + "d".repeat(64),
            payloadDigest = "sha256:" + "e".repeat(64),
            evidenceDigest = "sha256:" + "f".repeat(64),
            eventId = CrossDatabaseOperationIdentity.eventId(
                "xop_" + discriminator.repeat(64),
                eventType
            ),
            eventType = eventType,
            eventBody = "deterministic orchestration transition",
            evidenceJson = "{}",
            occurredAtMillis = 1_000
        )
    }
}
