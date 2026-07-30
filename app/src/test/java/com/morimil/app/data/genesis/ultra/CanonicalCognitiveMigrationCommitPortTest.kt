package com.morimil.app.data.genesis.ultra

import com.morimil.app.data.repository.CognitiveMigrationProtocolTypes
import com.morimil.app.data.repository.CrossDatabaseCanonicalCommand
import com.morimil.app.data.repository.CrossDatabaseProtocolErrors
import com.morimil.app.data.repository.CrossDatabaseProtocolFailure
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalCognitiveMigrationCommitPortTest {
    @Test
    fun canonicalEnvelopeBindsOperationIdentityWriterAndDigests() {
        val source = sourceFile().readText()

        listOf(
            "cognitive_migration_protocol",
            "cross_database_operations",
            "durable_cognitive_migration_transition",
            "morimil.cross_database_operation.canonical_commit.v1",
            "operation_id",
            "operation_type",
            "operation_version",
            "instance_id",
            "writer_body_id",
            "writer_epoch",
            "subject_id",
            "payload_digest",
            "evidence_digest"
        ).forEach { binding ->
            assertTrue("Missing canonical binding: $binding", source.contains(binding))
        }
    }

    @Test
    fun ensureChecksExistingEventAndRecoversInterruptedAppend() {
        val source = sourceFile().readText()
        val firstLookup = source.indexOf("findVerified(command)?.let")
        val append = source.indexOf("appendText(command.toAppendCommand())")
        val recoveryLookup = source.indexOf("val recovered = findVerified(command)")
        val postAppendLookup = source.lastIndexOf("findVerified(command)")

        assertTrue(firstLookup >= 0)
        assertTrue(append > firstLookup)
        assertTrue(recoveryLookup > append)
        assertTrue(postAppendLookup > recoveryLookup)
        assertTrue(source.contains("records.size > 1"))
        assertTrue(source.contains("CANONICAL_EVENT_MISMATCH"))
        assertTrue(source.contains("CANONICAL_PROVENANCE_MISMATCH"))
    }

    @Test
    fun exactExistingCanonicalEventIsReusedWithoutAppend() = runBlocking {
        val command = command()
        val exact = record(command)
        var appendCalls = 0
        val port = CanonicalCognitiveMigrationCommitPort.testing(
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
    fun existingEventIdWithDifferentContentOrProvenanceFailsClosed() = runBlocking {
        val command = command()
        val envelopeConflicts = listOf(
            record(command, content = "different canonical content"),
            record(command, eventContentType = "application/json"),
            record(command, privacy = "public")
        )
        envelopeConflicts.forEach { conflict ->
            val failure = runCatching {
                portFor(conflict).ensureCommitted(command)
            }.exceptionOrNull() as CrossDatabaseProtocolFailure
            assertEquals(
                CrossDatabaseProtocolErrors.CANONICAL_EVENT_MISMATCH,
                failure.stableCode
            )
        }

        val provenanceConflicts = listOf(
            record(command, provenanceClassification = "conflicting_transition"),
            record(command, provenanceSchema = "conflicting.provenance.v1"),
            record(command, provenanceInstanceId = "foreign_instance"),
            record(command, provenanceBodyId = "foreign_body")
        )
        provenanceConflicts.forEach { conflict ->
            val failure = runCatching {
                portFor(conflict).ensureCommitted(command)
            }.exceptionOrNull() as CrossDatabaseProtocolFailure
            assertEquals(
                CrossDatabaseProtocolErrors.CANONICAL_PROVENANCE_MISMATCH,
                failure.stableCode
            )
        }
    }

    private fun portFor(
        record: CanonicalMemoryRecord
    ): CanonicalCognitiveMigrationCommitPort {
        return CanonicalCognitiveMigrationCommitPort.testing(
            appendText = { error("append_must_not_run_for_existing_event_id") },
            readVerifiedSnapshot = { snapshot(record) }
        )
    }

    private fun snapshot(record: CanonicalMemoryRecord): CanonicalMemorySnapshot {
        return CanonicalMemorySnapshot(
            instanceId = record.event.instanceId,
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

    private fun record(
        command: CrossDatabaseCanonicalCommand,
        content: String = command.eventBody,
        eventContentType: String = "text/plain",
        privacy: String = "private_local",
        provenanceClassification: String = "durable_cognitive_migration_transition",
        provenanceSchema: String = "morimil.canonical_memory.provenance.v1",
        provenanceInstanceId: String = command.instanceId,
        provenanceBodyId: String = command.writerBodyId
    ): CanonicalMemoryRecord {
        val note = JSONObject()
            .put("schema", "morimil.cross_database_operation.canonical_commit.v1")
            .put("operation_id", command.operationId)
            .put("owner_type", CognitiveMigrationProtocolTypes.OWNER_TYPE)
            .put("operation_type", command.operationType)
            .put("operation_version", command.operationVersion)
            .put("instance_id", command.instanceId)
            .put("writer_body_id", command.writerBodyId)
            .put("writer_epoch", command.writerEpoch)
            .put("subject_id", command.subjectId)
            .put("payload_digest", command.payloadDigest)
            .put("evidence_digest", command.evidenceDigest)
            .toString()
        val provenance = JSONObject()
            .put("schema", provenanceSchema)
            .put("instance_id", provenanceInstanceId)
            .put("body_id", provenanceBodyId)
            .put("source", "cross_database_operations")
            .put("classification", provenanceClassification)
            .put("user_confirmed", false)
            .put("source_id", command.operationId)
            .put("note", note)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
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
                actor = "cognitive_migration_protocol",
                contentDigest = GenesisUltraHashProfile.sha256(
                    content.toByteArray(StandardCharsets.UTF_8)
                ),
                contentType = eventContentType,
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

    private fun signature(
        command: CrossDatabaseCanonicalCommand
    ): GenesisUltraSignatureEnvelope {
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
            operationType = CognitiveMigrationProtocolTypes.PROPOSE,
            operationVersion = CognitiveMigrationProtocolTypes.VERSION,
            instanceId = "instance_test",
            writerBodyId = "body_test",
            writerEpoch = "epoch_test",
            subjectId = "cog_migration_" + "b".repeat(64),
            payloadDigest = "sha256:" + "c".repeat(64),
            evidenceDigest = "sha256:" + "d".repeat(64),
            eventId = "xevt_" + "e".repeat(64),
            eventType = CognitiveMigrationProtocolTypes.PROPOSED_EVENT,
            eventBody = "deterministic canonical transition",
            evidenceJson = "{}",
            occurredAtMillis = 1_000
        )
    }

    private fun sourceFile(): File {
        val root = sequenceOf(File("."), File(".."))
            .map(File::getCanonicalFile)
            .firstOrNull { candidate ->
                File(candidate, "README.md").isFile &&
                    File(candidate, "app/build.gradle.kts").isFile
            }
            ?: error("Repository root not found")
        return File(
            root,
            "app/src/main/java/com/morimil/app/data/genesis/ultra/" +
                "CanonicalCognitiveMigrationCommitPort.kt"
        )
    }
}
