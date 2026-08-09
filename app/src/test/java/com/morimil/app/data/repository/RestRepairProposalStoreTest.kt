package com.morimil.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestRepairProposalStoreTest {
    @Test
    fun candidateRemainsPlannedProposalOnlyAndCanonicalBound() {
        val report = report()
        val record = RestRepairProposalStore.buildCandidate(
            migrationId = "repair_${"a".repeat(64)}",
            instanceId = "instance-001",
            birthRootEventHash = "evsha256:${"b".repeat(64)}",
            preSnapshotId = "evsha256:${"c".repeat(64)}",
            snapshotDigest = "sha256:${"d".repeat(64)}",
            sourceSetDigest = "sha256:${"e".repeat(64)}",
            proposalDigest = "sha256:${"f".repeat(64)}",
            report = report,
            now = 123L
        )

        assertEquals(RestRepairProposalStore.MIGRATION_TYPE, record.migrationType)
        assertEquals(RestRepairProposalStore.STATUS_PLANNED, record.status)
        assertTrue(record.approvalRequired)
        assertFalse(record.approvedByUser)
        assertNull(record.approvalId)
        assertNull(record.postSnapshotId)
        assertNull(record.proposalId)
        assertTrue(record.chainVerified)
        assertTrue(record.expectedEffect.contains("automatic_changes=false"))
        assertTrue(record.rollbackStrategy.contains("no memory mutation occurs"))
        RestRepairProposalStore.requireSamePlan(record, record.copy(updatedAtMillis = 999L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun conflictingCandidateForSameIdFailsClosed() {
        val report = report()
        val first = RestRepairProposalStore.buildCandidate(
            "repair_${"1".repeat(64)}", "instance-001", "evsha256:${"2".repeat(64)}",
            "evsha256:${"3".repeat(64)}", "sha256:${"4".repeat(64)}", "sha256:${"5".repeat(64)}",
            "sha256:${"6".repeat(64)}", report, 1L
        )
        RestRepairProposalStore.requireSamePlan(first, first.copy(riskLevel = "critical"))
    }

    private fun report(): RestRepairProposalReport {
        return RestRepairProposalReport(
            candidates = listOf(
                RestRepairCandidate(
                    kind = "important_unconfirmed_memory",
                    riskLevel = "medium",
                    eventHashes = listOf("evsha256:${"7".repeat(64)}"),
                    reason = "Important memory is not user-confirmed.",
                    suggestedAction = "Ask user to confirm or correct it."
                )
            ),
            scannedEventCount = 10
        )
    }
}
