package com.morimil.app.core.memory

import com.morimil.app.data.local.RecallScheduleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalRecallReconciliationTest {
    private val reconciliation = MemoryOrganReconciliation()

    @Test
    fun canonicalDerivedRecallIsNotInvalidatedByLegacyMemoryEventSet() {
        val report = reconciliation.buildReport(
            validMemoryEventHashes = emptySet(),
            links = emptyList(),
            recalls = listOf(recall(source = "canonical_memory_event")),
            capsules = emptyList(),
            migrations = emptyList()
        )

        assertTrue(report.orphanedRecallIds.isEmpty())
    }

    @Test
    fun legacyRecallStillUsesLegacyMemoryEventOrphanCheck() {
        val report = reconciliation.buildReport(
            validMemoryEventHashes = emptySet(),
            links = emptyList(),
            recalls = listOf(recall(source = "local_memory_event")),
            capsules = emptyList(),
            migrations = emptyList()
        )

        assertEquals(listOf(7L), report.orphanedRecallIds)
    }

    private fun recall(source: String): RecallScheduleEntity {
        return RecallScheduleEntity(
            recallId = 7L,
            genesisCoreId = "canonical-birth-root",
            targetEventHash = "canonical-event-hash",
            targetMemoryKind = "preference",
            prompt = "Repasar preferencia",
            reason = "test",
            priority = 90,
            intervalDays = 1,
            dueAtMillis = 2L,
            status = "active",
            lastAction = "created",
            source = source,
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
            lastReviewedAtMillis = null
        )
    }
}
