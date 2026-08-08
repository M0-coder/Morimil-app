package com.morimil.app.data.genesis.ultra

import com.morimil.app.data.repository.AgentLifecycleProtocolTypes
import com.morimil.app.data.repository.CrossDatabaseCanonicalCommand
import com.morimil.app.data.repository.CrossDatabaseOperationIdentity
import com.morimil.app.data.repository.CrossDatabaseProtocolErrors
import com.morimil.app.data.repository.CrossDatabaseProtocolFailure
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalAgentLifecycleCommitPortTest {
    @Test
    fun exactExistingCreateIsReusedWithoutAppend() = runBlocking {
        val command = command(AgentLifecycleProtocolTypes.CREATE)
        val exact = record(command)
        var appendCalls = 0
        val port = CanonicalAgentLifecycleCommitPort.testing(
            appendText = {
                appendCalls += 1
                error("append_must_not_run")
            },
            readVerifiedSnapshot = { snapshot(command.instanceId, exact) }
        )

        val receipt = port.ensureCommitted(command)

        assertEquals(0, appendCalls)
        assertEquals(command.eventId, receipt.eventId)
        assertTrue(receipt.reusedExistingEvent)
    }

    @Test
    fun humanLifecycleDecisionRequiresConfirmedProvenance() = runBlocking {
        val command = command(AgentLifecycleProtocolTypes.QUARANTINE)
        val exact = record(command, userConfirmed = true)
        val port = CanonicalAgentLifecycleCommitPort.testing(
            appendText = { error("append_must_not_run") },
            readVerifiedSnapshot = { snapshot(command.instanceId, exact) }
        )

        assertTrue(port.ensureCommitted(command).reusedExistingEvent)

        val unconfirmed = record(command, userConfirmed = false)
        assertFailure(
            CrossDatabaseProtocolErrors.CANONICAL_PROVENANCE_MISMATCH,
            runCatching {
                CanonicalAgentLifecycleCommitPort.testing(
                    appendText = { error("append_must_not_run") },
                    readVerifiedSnapshot = { snapshot(command.instanceId, unconfirmed) }
                ).ensureCommitted(command)
            }.exceptionOrNull()
        )
    }

    @Test
    fun foreignSnapshotDuplicateAndEnvelopeMismatchFailClosed() = runBlocking {
        val command = command(AgentLifecycleProtocolTypes.CREATE)
        val exact = record(command)

        assertFailure(
            CrossDatabaseProtocolErrors.WRONG_INSTANCE,
            runCatching {
                CanonicalAgentLifecycleCommitPort.testing(
                    appendText = { error("append_must_not_run") },
                    readVerifiedSnapshot = { snapshot("foreign_instance", exact) }
                ).ensureCommitted(command)
            }.exceptionOrNull()
        )
        assertFailure(
            CrossDatabaseProtocolErrors.EVENT_ID_CONFLICT,
            runCatching {
                CanonicalAgentLifecycleCommitPort.testing(
                    appendText = { error("append_must_not_run") },
                    readVerifiedSnapshot = { snapshot(command.instanceId, exact, exact) }
                ).ensureCommitted(command)
            }.exceptionOrNull()
        )
        assertFailure(
            CrossDatabaseProtocolErrors.CANONICAL_EVENT_MISMATCH,
            runCatching { portFor(command, record(command, actor = "wrong_actor")).ensureCommitted(command) }
                .exceptionOrNull()
        )
    }

    @Test
    fun interruptedAppendRecoversExactEventAndSuccessfulAppendIsVerified() = runBlocking {
        val command = command(AgentLifecycleProtocolTypes.CREATE)
        val exact = record(command)
        var visible: CanonicalMemoryRecord? = null
        val interrupted = CanonicalAgentLifecycleCommitPort.testing(
            appendText = {
                visible = exact
                error("simulated_interrupt")
            },
            readVerifiedSnapshot = {
                snapshot(command.instanceId, *listOfNotNull(visible).toTypedArray())
            }
        )
        assertTrue(interrupted.ensureCommitted(command).reusedExistingEvent)

        visible = null
        val successful = CanonicalAgentLifecycleCommitPort.testing(
            appendText = {
                visible = exact
                exact
            },
            readVerifiedSnapshot = {
                snapshot(command.instanceId, *listOfNotNull(visible).toTypedArray())
            }
        )
        assertFalse(successful.ensureCommitted(command).reusedExistingEvent)
    }

    private fun portFor(
        command: CrossDatabaseCanonicalCommand,
        record: CanonicalMemoryRecord
    ) = CanonicalAgentLifecycleCommitPort.testing(
        appendText = { error("append_must_not_run") },
        readVerifiedSnapshot = { snapshot(command.instanceId, record) }
    )

    private fun assertFailure(expectedCode: String, error: Throwable?) {
        val failure = error as CrossDatabaseProtocolFailure
        assertEquals(expectedCode, failure.stableCode)
        assertTrue(failure.permanent)
    }

    private fun snapshot(
        instanceId: String,
        vararg records: CanonicalMemoryRecord
    ): CanonicalMemorySnapshot {
        val seed = command(AgentLifecycleProtocolTypes.CREATE)
        val root = record(seed).event.copy(
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
        actor: String = CanonicalAgentLifecycleCommitPort.ACTOR,
        classification: String = CanonicalAgentLifecycleCommitPort.CLASSIFICATION,
        userConfirmed: Boolean = requiresConfirmation(command.operationType)
    ): CanonicalMemoryRecord {
        val note = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "evidence_digest" to command.evidenceDigest,
                "instance_id" to command.instanceId,
                "operation_id" to command.operationId,
                "operation_type" to command.operationType,
                "operation_version" to command.operationVersion,
                "owner_type" to AgentLifecycleProtocolTypes.OWNER_TYPE,
                "payload_digest" to command.payloadDigest,
                "schema" to CanonicalAgentLifecycleCommitPort.NOTE_SCHEMA,
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
                "schema" to CanonicalAgentLifecycleCommitPort.PROVENANCE_SCHEMA,
                "source" to CanonicalAgentLifecycleCommitPort.SOURCE,
                "source_id" to command.operationId,
                "user_confirmed" to userConfirmed
            )
        ).toByteArray(StandardCharsets.UTF_8)
        val content = command.eventBody.toByteArray(StandardCharsets.UTF_8)
        return CanonicalMemoryRecord(
            event = GenesisUltraFirstMemoryEvent(
                schemaVersion = "genesis.memory.event.v0.1",
                hashProfile = GenesisUltraHashProfile.FIELD_PROFILE,
                eventId = command.eventId,
                instanceId = command.instanceId,
                bodyId = command.writerBodyId,
                sequence = 9,
                previousEventHash = "evsha256:" + "0".repeat(64),
                eventType = command.eventType,
                actor = actor,
                contentDigest = GenesisUltraHashProfile.sha256(content),
                contentType = CanonicalAgentLifecycleCommitPort.CONTENT_TYPE,
                contentRef = null,
                observedAt = "1970-01-01T00:00:01Z",
                provenanceDigest = GenesisUltraHashProfile.sha256(provenance),
                provenanceRef = null,
                privacy = CanonicalAgentLifecycleCommitPort.PRIVACY,
                eventHash = "evsha256:" + "1".repeat(64),
                signature = signature(command)
            ),
            contentBytes = content,
            provenanceBytes = provenance,
            provenanceType = "application/json"
        )
    }

    private fun signature(command: CrossDatabaseCanonicalCommand) = GenesisUltraSignatureEnvelope(
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

    private fun command(operationType: String): CrossDatabaseCanonicalCommand {
        val eventType = AgentLifecycleProtocolTypes.CLOSED_REGISTRY.getValue(operationType)
        val discriminator = when (operationType) {
            AgentLifecycleProtocolTypes.CREATE -> "a"
            AgentLifecycleProtocolTypes.QUARANTINE -> "b"
            else -> "c"
        }
        val operationId = "xop_" + discriminator.repeat(64)
        return CrossDatabaseCanonicalCommand(
            operationId = operationId,
            operationType = operationType,
            operationVersion = AgentLifecycleProtocolTypes.VERSION,
            instanceId = "instance_test",
            writerBodyId = "body_test",
            writerEpoch = "epoch_test",
            subjectId = "agent_instance_" + "d".repeat(64),
            payloadDigest = "sha256:" + "e".repeat(64),
            evidenceDigest = "sha256:" + "f".repeat(64),
            eventId = CrossDatabaseOperationIdentity.eventId(operationId, eventType),
            eventType = eventType,
            eventBody = "deterministic agent lifecycle transition",
            evidenceJson = "{}",
            occurredAtMillis = 1_000
        )
    }

    private fun requiresConfirmation(operationType: String): Boolean = operationType in setOf(
        AgentLifecycleProtocolTypes.EVALUATE,
        AgentLifecycleProtocolTypes.RETIRE,
        AgentLifecycleProtocolTypes.PROMOTE,
        AgentLifecycleProtocolTypes.QUARANTINE
    )
}
