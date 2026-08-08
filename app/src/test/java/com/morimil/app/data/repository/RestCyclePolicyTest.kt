package com.morimil.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestCyclePolicyTest {
    @Test
    fun lowRiskEventsDoNotRequireHumanApproval() {
        val events = listOf(
            event(memoryKind = "learning", importance = 62),
            event(memoryKind = "preference", importance = 70)
        )

        assertFalse(RestCyclePolicy.requiresHumanApproval(events))
        assertEquals("low", RestCyclePolicy.riskLevel(events))
    }

    @Test
    fun confirmedCriticalEventRequiresHumanApproval() {
        val events = listOf(
            event(memoryKind = "decision", importance = 86, userConfirmed = true)
        )

        assertTrue(RestCyclePolicy.requiresHumanApproval(events))
        assertEquals("medium", RestCyclePolicy.riskLevel(events))
    }

    @Test
    fun highImpactBatchRequiresHumanApproval() {
        val events = listOf(
            event(memoryKind = "learning", importance = 81),
            event(memoryKind = "preference", importance = 83),
            event(memoryKind = "correction", importance = 82)
        )

        assertTrue(RestCyclePolicy.requiresHumanApproval(events))
    }

    private fun event(
        memoryKind: String,
        importance: Int,
        userConfirmed: Boolean = false
    ): RestCycleSourceEvent {
        return RestCycleSourceEvent(
            eventHash = "evsha256:${"a".repeat(64)}",
            eventType = "test.event",
            actor = "user",
            source = "test",
            memoryKind = memoryKind,
            tagsJson = "[]",
            body = "test memory",
            importance = importance,
            confidence = 90,
            userConfirmed = userConfirmed,
            observedAtMillis = 123L
        )
    }
}
