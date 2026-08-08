package com.morimil.app.data.genesis.ultra

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.morimil.app.data.repository.CrossDatabaseCanonicalCommand
import com.morimil.app.data.repository.CrossDatabaseOperationIdentity
import com.morimil.app.data.repository.RuntimeBootstrapProtocolTypes
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalRuntimeBootstrapCommitPortInstrumentedTest {
    @Test
    fun exactCommittedBootstrapReceiptIsReusedWithoutSecondAppend() = runBlocking {
        val command = command()
        val exact = record(command)
        var appendCalls = 0
        val port = CanonicalRuntimeBootstrapCommitPort.testing(
            appendText = {
                appendCalls += 1
                error("append_must_not_run")
            },
            readVerifiedSnapshot = { snapshot(exact) }
        )

        val receipt = port.ensureCommitted(command)

        assertEquals(0, appendCalls)
        assertEquals(command.eventId, receipt.eventId)
        assertEquals(exact.event.eventHash, receipt.eventHash)
        assertTrue(receipt.reusedExistingEvent)
    }

    @Test
    fun missingBootstrapReceiptAppendsThenRequiresVerifiedReread() = runBlocking {
        val command = command()
        val appended = record(command)
        var reads = 0
        var appendCalls = 0
        val port = CanonicalRuntimeBootstrapCommitPort.testing(
            appendText = { appendCommand ->
                appendCalls += 1
                assertEquals(command.eventId, appendCommand.eventId)
                assertEquals(command.eventType, appendCommand.eventType)
                assertEquals(command.eventBody, appendCommand.content)
                appended
            },
            readVerifiedSnapshot = {
                reads += 1
                if (reads == 1) snapshot() else snapshot(appended)
            }
        )

        val receipt = port.ensureCommitted(command)

        assertEquals(1, appendCalls)
        assertEquals(2, reads)
        assertEquals(appended.event.eventHash, receipt.eventHash)
        assertEquals(false, receipt.reusedExistingEvent)
    }

    private fun snapshot(vararg records: CanonicalMemoryRecord): CanonicalMemorySnapshot {
        val reference = records.firstOrNull()?.event ?: record(command()).event
        return CanonicalMemorySnapshot(
            instanceId = reference.instanceId,
            companionName = "Morimil",
            birthRoot = reference.copy(
                eventId = "evt_birth",
                sequence = 0,
                previousEventHash = "GENESIS",
                eventType = "instance.birth"
            ),
            records = records.toList()
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
                "owner_type" to RuntimeBootstrapProtocolTypes.OWNER_TYPE,
                "payload_digest" to command.payloadDigest,
                "schema" to "morimil.cross_database_operation.canonical_commit.v1",
                "subject_id" to command.subjectId,
                "writer_body_id" to command.writerBodyId,
                "writer_epoch" to command.writerEpoch
            )
        )
        val provenanceText = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "body_id" to command.writerBodyId,
                "classification" to "durable_runtime_bootstrap_transition",
                "instance_id" to command.instanceId,
                "note" to note,
                "schema" to "morimil.canonical_memory.provenance.v1",
                "source" to "cross_database_operations",
                "source_id" to command.operationId,
                "user_confirmed" to false
            )
        )
        val content = command.eventBody.toByteArray(StandardCharsets.UTF_8)
        val provenance = provenanceText.toByteArray(StandardCharsets.UTF_8)
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
                actor = "runtime_bootstrap_protocol",
                contentDigest = GenesisUltraHashProfile.sha256(content),
                contentType = "text/plain",
                contentRef = null,
                observedAt = "1970-01-01T00:00:01Z",
                provenanceDigest = GenesisUltraHashProfile.sha256(provenance),
                provenanceRef = null,
                privacy = "private_local",
                eventHash = "evsha256:" + "1".repeat(64),
                signature = signature(command)
            ),
            contentBytes = content,
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
        return CrossDatabaseCanonicalCommand(
            operationId = "xop_" + "a".repeat(64),
            operationType = RuntimeBootstrapProtocolTypes.INITIALIZE,
            operationVersion = RuntimeBootstrapProtocolTypes.VERSION,
            instanceId = "instance_test",
            writerBodyId = "body_test",
            writerEpoch = "epoch_test",
            subjectId = "bootstrap:instance_test:body_test:epoch_test",
            payloadDigest = "sha256:" + "c".repeat(64),
            evidenceDigest = "sha256:" + "d".repeat(64),
            eventId = "xevt_" + "e".repeat(64),
            eventType = RuntimeBootstrapProtocolTypes.INITIALIZED_EVENT,
            eventBody = "deterministic runtime bootstrap transition",
            evidenceJson = "{}",
            occurredAtMillis = 1_000
        )
    }
}
