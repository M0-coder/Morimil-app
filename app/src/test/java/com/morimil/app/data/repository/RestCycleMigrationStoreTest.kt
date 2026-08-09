package com.morimil.app.data.repository

import com.morimil.app.core.memory.RestCycleMode
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestCycleMigrationStoreTest {
    @Test
    fun candidateBindsCanonicalRootDigestsSourcesAndApprovalState() {
        val sources = listOf(
            "evsha256:${"a".repeat(64)}",
            "evsha256:${"b".repeat(64)}"
        )
        val candidate = RestCycleMigrationStore.buildCandidate(
            migrationId = "rest_${"c".repeat(64)}",
            instanceId = "instance_test",
            birthRootEventHash = "evsha256:${"d".repeat(64)}",
            sourceEventHashes = sources,
            preSnapshotId = "evsha256:${"e".repeat(64)}",
            snapshotDigest = "sha256:${"f".repeat(64)}",
            sourceSetDigest = "sha256:${"1".repeat(64)}",
            mode = RestCycleMode.Normal,
            approvalRequired = true,
            riskLevel = "medium",
            summary = "canonical rest summary",
            now = 1234L
        )

        assertEquals("instance_test", candidate.instanceId)
        assertEquals("evsha256:${"d".repeat(64)}", candidate.genesisCoreHash)
        assertEquals(RestCycleMigrationStore.REST_CYCLE_MIGRATION_TYPE, candidate.migrationType)
        assertEquals("canonical_snapshot:sha256:${"f".repeat(64)}", candidate.fromVersion)
        assertEquals("canonical_memory_after_rest_cycle", candidate.toVersion)
        assertEquals(sources, jsonArrayValues(candidate.affectedArtifactsJson))
        assertTrue(candidate.chainVerified)
        assertTrue(candidate.backupRequired)
        assertTrue(candidate.approvalRequired)
        assertFalse(candidate.approvedByUser)
        assertNull(candidate.approvalId)
        assertEquals(RestCycleMigrationStore.STATUS_PLANNED, candidate.status)
        assertTrue(candidate.rollbackAvailable)
        assertEquals(1234L, candidate.createdAtMillis)
        assertEquals(1234L, candidate.updatedAtMillis)
        assertTrue(candidate.expectedEffect.contains("mode=normal"))
        assertTrue(candidate.expectedEffect.contains("source_events=2"))
        assertTrue(candidate.expectedEffect.contains("approval_required=true"))
    }

    @Test
    fun exactReplayIsAcceptedButSemanticConflictIsRejected() {
        val original = candidate()
        val exactReplay = original.copy(createdAtMillis = 9999L, updatedAtMillis = 9999L)
        RestCycleMigrationStore.requireSamePlan(original, exactReplay)

        val conflicts = listOf(
            original.copy(instanceId = "foreign_instance"),
            original.copy(genesisCoreHash = "evsha256:${"9".repeat(64)}"),
            original.copy(affectedArtifactsJson = JSONArray(listOf("evsha256:${"8".repeat(64)}")).toString()),
            original.copy(riskLevel = "high"),
            original.copy(approvalRequired = !original.approvalRequired)
        )
        conflicts.forEach { conflict ->
            val failure = runCatching {
                RestCycleMigrationStore.requireSamePlan(original, conflict)
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertEquals("rest_cycle_migration_id_payload_conflict", failure?.message)
        }
    }

    private fun candidate() = RestCycleMigrationStore.buildCandidate(
        migrationId = "rest_${"2".repeat(64)}",
        instanceId = "instance_test",
        birthRootEventHash = "evsha256:${"3".repeat(64)}",
        sourceEventHashes = listOf("evsha256:${"4".repeat(64)}"),
        preSnapshotId = "evsha256:${"5".repeat(64)}",
        snapshotDigest = "sha256:${"6".repeat(64)}",
        sourceSetDigest = "sha256:${"7".repeat(64)}",
        mode = RestCycleMode.Deep,
        approvalRequired = false,
        riskLevel = "low",
        summary = "deterministic summary",
        now = 2222L
    )

    private fun jsonArrayValues(json: String): List<String> {
        val array = JSONArray(json)
        return (0 until array.length()).map { index -> array.getString(index) }
    }
}
