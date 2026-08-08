package com.morimil.app.data.genesis.ultra

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.morimil.app.data.repository.CrossDatabaseCanonicalCommand
import com.morimil.app.data.repository.CrossDatabaseOperationIdentity
import com.morimil.app.data.repository.OrchestrationProtocolTypes
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalOrchestrationCommitPortInstrumentedTest {
    @Test
    fun exactExistingOrchestrationEventIsVerifiedAndReused() = runBlocking {
        val command = command()
        val existing = record(command)
        var appendCalls = 0
        val port = CanonicalOrchestrationCommitPort.testing(
            appendText = {
                appendCalls += 1
                error("append_must_not_run_for_exact_existing_event")
            },
            readVerifiedSnapshot = { snapshot(command.instanceId, existing) }
        )

        val receipt = port.ensureCommitted(command)

        assertEquals(0, appendCalls)
        assertEquals(command.eventId, receipt.eventId)
        assertEquals(existing.event.eventHash, receipt.eventHash)
        assertTrue(receipt.reusedExistingEvent)
    }

    private fun snapshot(
        instanceId: String,
        record: CanonicalMemoryRecord
    ): CanonicalMemorySnapshot {
        return CanonicalMemorySnapshot(
            instanceId = instanceId,
            companionName = "Morimil",
            birthRoot = record.event.copy(
                eventId = "evt_birth",
                sequence = 0,
                previousEventHash = "GENESIS",
                eventType = "instance.birth"
            ),
            records = listOf(record)
        )
    }

    private fun record(command: CrossDatabaseCanonicalCommand): CanonicalMemoryRecord {
        val note = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "evidence_digest" to command.evidenceDigest,
                "instance_id" to command.instanceId,
                "operation_id" to command.operationId,
                "operation_type" to command.operationType,
                "operation_version" to command.operationVersion,
                "owner_type" to OrchestrationProtocolTypes.OWNER_TYPE,
                "payload_digest" to command.payloadDigest,
                "schema" to "morimil.cross_database_operation.canonical_commit.v1",
                "subject_id" to command.subjectId,
                "writer_body_id" to command.writerBodyId,
                "writer_epoch" to command.writerEpoch
            )
        )
        val provenance = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "body_id" to command.writerBodyId,
                "classification" to "durable_orchestration_transition",
                "instance_id" to command.instanceId,
                "note" to note,
                "schema" to "morimil.canonical_memory.provenance.v1",
                "source" to "cross_database_operations",
                "source_id" to command.operationId,
                "user_confirmed" to false
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
                actor = "orchestration_protocol",
                contentDigest = GenesisUltraHashProfile.sha256(content),
                contentType = "text/plain",
                contentRef = null,
                observedAt = "1970-01-01T00:00:01Z",
                provenanceDigest = GenesisUltraHashProfile.sha256(provenance),
                provenanceRef = null,
                privacy = "private_local",
                eventHash = "evsha256:" + "1".repeat(64),
                signature = GenesisUltraSignatureEnvelope(
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
            ),
            contentBytes = content,
            provenanceBytes = provenance,
            provenanceType = "application/json"
        )
    }

    private fun command(): CrossDatabaseCanonicalCommand {
        val operationId = "xop_" + "a".repeat(64)
        val eventType = OrchestrationProtocolTypes.PROPOSED_EVENT
        return CrossDatabaseCanonicalCommand(
            operationId = operationId,
            operationType = OrchestrationProtocolTypes.PROPOSE,
            operationVersion = OrchestrationProtocolTypes.VERSION,
            instanceId = "instance_test",
            writerBodyId = "body_test",
            writerEpoch = "epoch_test",
            subjectId = "dtask_" + "d".repeat(64),
            payloadDigest = "sha256:" + "e".repeat(64),
            evidenceDigest = "sha256:" + "f".repeat(64),
            eventId = CrossDatabaseOperationIdentity.eventId(operationId, eventType),
            eventType = eventType,
            eventBody = "deterministic orchestration transition",
            evidenceJson = "{}",
            occurredAtMillis = 1_000
        )
    }
}
