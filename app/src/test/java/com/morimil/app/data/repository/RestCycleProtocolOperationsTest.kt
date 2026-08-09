package com.morimil.app.data.repository

import com.morimil.app.core.memory.RestCycleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestCycleProtocolOperationsTest {
    @Test
    fun deterministicMigrationAndOperationDoNotDependOnClock() {
        val identity = identity()
        val sourceSetDigest = "sha256:${"a".repeat(64)}"
        val snapshotDigest = "sha256:${"b".repeat(64)}"
        val birthRoot = "evsha256:${"c".repeat(64)}"
        val migrationId = RestCycleOperationFactory.deterministicMigrationId(identity, sourceSetDigest, RestCycleMode.Normal)
        val source = source("d")
        val autobiography = AutobiographicalMemoryDraft("Morimil", "self", "traits", "goals", "constraints", "{}")

        val first = RestCycleOperationFactory.execute(
            identity, "Morimil", migrationId, RestCycleMode.Normal, sourceSetDigest, snapshotDigest,
            birthRoot, "REST_CYCLE_CANONICAL_V1\nsource_set_digest=$sourceSetDigest", listOf(source),
            autobiography, false, null
        )
        val second = RestCycleOperationFactory.execute(
            identity, "Morimil", migrationId, RestCycleMode.Normal, sourceSetDigest, snapshotDigest,
            birthRoot, "REST_CYCLE_CANONICAL_V1\nsource_set_digest=$sourceSetDigest", listOf(source),
            autobiography, false, null
        )

        assertEquals(first, second)
        assertEquals(RestCycleProtocolTypes.EXECUTE, first.operationType)
        assertEquals(RestCycleProtocolTypes.EXECUTED_EVENT, first.eventType)
        assertNotEquals(migrationId, RestCycleOperationFactory.deterministicMigrationId(identity, sourceSetDigest, RestCycleMode.Deep))
    }

    @Test
    fun repairProposalIsDeterministicProposalOnlyAndDistinctFromRest001() {
        val identity = identity()
        val report = RestRepairProposalPlanner.build(
            listOf(
                source("e", importance = 95, userConfirmed = false),
                source("f", importance = 75, confidence = 40, userConfirmed = false)
            )
        )
        val migrationId = RestCycleOperationFactory.deterministicRepairMigrationId(identity, report)
        val first = RestCycleOperationFactory.proposeRepair(
            identity = identity,
            migrationId = migrationId,
            sourceSetDigest = "sha256:${"1".repeat(64)}",
            snapshotDigest = "sha256:${"2".repeat(64)}",
            birthRootEventHash = "evsha256:${"3".repeat(64)}",
            report = report
        )
        val second = RestCycleOperationFactory.proposeRepair(
            identity = identity,
            migrationId = migrationId,
            sourceSetDigest = "sha256:${"1".repeat(64)}",
            snapshotDigest = "sha256:${"2".repeat(64)}",
            birthRootEventHash = "evsha256:${"3".repeat(64)}",
            report = report
        )

        assertEquals(first, second)
        assertEquals(RestCycleProtocolTypes.PROPOSE_REPAIR, first.operationType)
        assertEquals(RestCycleProtocolTypes.REPAIR_PROPOSED_EVENT, first.eventType)
        assertEquals(RestCycleProtocolSchemas.REST_002_PAYLOAD, first.payloadSchema)
        assertTrue(first.eventBody.contains("proposal_only_no_automatic_memory_mutation"))
        assertTrue(first.payloadJson.contains("\"automatic_changes\":false"))
        assertTrue(first.payloadJson.contains("\"approval_required\":true"))
    }

    private fun identity() = RestCycleProtocolIdentity(
        instanceId = "instance-001",
        writerBodyId = "body-001",
        writerEpoch = "epoch-001"
    )

    private fun source(
        suffix: String,
        importance: Int = 90,
        confidence: Int = 95,
        userConfirmed: Boolean = true
    ) = RestCycleSourceEvent(
        eventHash = "evsha256:${suffix.repeat(64).take(64)}",
        eventType = "conversation.user_message",
        actor = "user",
        source = "test",
        memoryKind = "decision",
        tagsJson = "[]",
        body = "Keep continuity canonical and review important memory carefully $suffix.",
        importance = importance,
        confidence = confidence,
        userConfirmed = userConfirmed,
        observedAtMillis = 1_000L
    )
}
