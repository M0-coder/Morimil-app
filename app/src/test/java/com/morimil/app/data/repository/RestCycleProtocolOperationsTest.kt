package com.morimil.app.data.repository

import com.morimil.app.core.memory.RestCycleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RestCycleProtocolOperationsTest {
    @Test
    fun deterministicMigrationAndOperationDoNotDependOnClock() {
        val identity = RestCycleProtocolIdentity(
            instanceId = "instance-001",
            writerBodyId = "body-001",
            writerEpoch = "epoch-001"
        )
        val sourceSetDigest = "sha256:${"a".repeat(64)}"
        val snapshotDigest = "sha256:${"b".repeat(64)}"
        val birthRoot = "evsha256:${"c".repeat(64)}"
        val migrationId = RestCycleOperationFactory.deterministicMigrationId(
            identity = identity,
            sourceSetDigest = sourceSetDigest,
            mode = RestCycleMode.Normal
        )
        val source = RestCycleSourceEvent(
            eventHash = "evsha256:${"d".repeat(64)}",
            eventType = "conversation.user_message",
            actor = "user",
            source = "test",
            memoryKind = "decision",
            tagsJson = "[]",
            body = "Keep continuity canonical.",
            importance = 90,
            confidence = 95,
            userConfirmed = true,
            observedAtMillis = 1_000L
        )
        val autobiography = AutobiographicalMemoryDraft(
            alias = "Morimil",
            selfSummary = "self",
            stableTraits = "traits",
            activeGoals = "goals",
            importantConstraints = "constraints",
            evidenceJson = "{}"
        )

        val first = RestCycleOperationFactory.execute(
            identity = identity,
            companionName = "Morimil",
            migrationId = migrationId,
            mode = RestCycleMode.Normal,
            sourceSetDigest = sourceSetDigest,
            snapshotDigest = snapshotDigest,
            birthRootEventHash = birthRoot,
            summary = "REST_CYCLE_CANONICAL_V1\nsource_set_digest=$sourceSetDigest",
            sourceEvents = listOf(source),
            autobiography = autobiography,
            approvalRequired = false,
            approvalId = null
        )
        val second = RestCycleOperationFactory.execute(
            identity = identity,
            companionName = "Morimil",
            migrationId = migrationId,
            mode = RestCycleMode.Normal,
            sourceSetDigest = sourceSetDigest,
            snapshotDigest = snapshotDigest,
            birthRootEventHash = birthRoot,
            summary = "REST_CYCLE_CANONICAL_V1\nsource_set_digest=$sourceSetDigest",
            sourceEvents = listOf(source),
            autobiography = autobiography,
            approvalRequired = false,
            approvalId = null
        )

        assertEquals(first, second)
        assertEquals(migrationId, first.subjectId)
        assertEquals(RestCycleProtocolTypes.EXECUTE, first.operationType)
        assertEquals(RestCycleProtocolTypes.EXECUTED_EVENT, first.eventType)
        assertNotEquals(
            migrationId,
            RestCycleOperationFactory.deterministicMigrationId(
                identity = identity,
                sourceSetDigest = sourceSetDigest,
                mode = RestCycleMode.Deep
            )
        )
    }
}
