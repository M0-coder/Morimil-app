package com.morimil.app.data.repository

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestCycleLocalProjectionTest {
    @Test
    fun linksAreDeterministicCanonicalMemoryEdgesWithBoundedStrength() {
        val receiptHash = "evsha256:${"a".repeat(64)}"
        val birthRootHash = "evsha256:${"b".repeat(64)}"
        val sourceA = "evsha256:${"c".repeat(64)}"
        val sourceB = "evsha256:${"d".repeat(64)}"
        val refs = JSONArray()
            .put(sourceRef(sourceA, memoryKind = "decision", importance = 90, confidence = 80))
            .put(sourceRef(sourceB, memoryKind = "learning", importance = 120, confidence = -10))

        val first = RestCycleLocalProjection.buildLinks(
            instanceId = "instance_test",
            occurredAtMillis = 5000L,
            receiptEventHash = receiptHash,
            birthRootEventHash = birthRootHash,
            sourceRefs = refs
        )
        val replay = RestCycleLocalProjection.buildLinks(
            instanceId = "instance_test",
            occurredAtMillis = 5000L,
            receiptEventHash = receiptHash,
            birthRootEventHash = birthRootHash,
            sourceRefs = refs
        )

        assertEquals(first, replay)
        assertEquals(2, first.size)
        assertNotEquals(first[0].linkId, first[1].linkId)
        assertEquals(listOf(sourceA, sourceB), RestCycleLocalProjection.sourceEventHashes(refs))
        assertEquals(listOf(sourceA, sourceB), RestCycleLocalProjection.jsonArrayValues(JSONArray(listOf(sourceA, sourceB)).toString()))

        val decision = first[0]
        assertEquals("instance_test", decision.instanceId)
        assertEquals(birthRootHash, decision.genesisCoreHash)
        assertEquals(receiptHash, decision.sourceId)
        assertEquals("canonical_memory_event", decision.sourceType)
        assertEquals(sourceA, decision.targetId)
        assertEquals("canonical_memory_event", decision.targetType)
        assertEquals("derived_from", decision.relation)
        assertEquals(0.86, decision.strength, 0.000001)
        assertEquals("canonical_rest_cycle:decision/i90/c80", decision.reason)
        assertEquals("rest_cycle", decision.createdBy)
        assertEquals("private_local", decision.privacyVisibility)
        assertFalse(decision.cloudSyncAllowed)
        assertFalse(decision.exportAllowed)
        assertEquals("valid", decision.verificationState)
        assertEquals(5000L, decision.createdAtMillis)

        val clamped = first[1]
        assertEquals(0.60, clamped.strength, 0.000001)
        assertEquals("canonical_rest_cycle:learning/i100/c0", clamped.reason)
        assertEquals(5001L, clamped.createdAtMillis)
    }

    @Test
    fun autobiographicalSnapshotIsBoundToCanonicalReceiptAndBirthRoot() {
        val receiptHash = "evsha256:${"e".repeat(64)}"
        val birthRootHash = "evsha256:${"f".repeat(64)}"
        val autobiography = JSONObject()
            .put("alias", "Morimil")
            .put("self_summary", "self")
            .put("stable_traits", "traits")
            .put("active_goals", "goals")
            .put("important_constraints", "constraints")

        val snapshot = RestCycleLocalProjection.buildSelfSnapshot(
            birthRootEventHash = birthRootHash,
            receiptEventHash = receiptHash,
            occurredAtMillis = 7777L,
            autobiography = autobiography
        )

        assertEquals("current", snapshot.snapshotId)
        assertEquals(birthRootHash, snapshot.genesisCoreId)
        assertEquals("Morimil", snapshot.alias)
        assertEquals("self", snapshot.selfSummary)
        assertEquals("traits", snapshot.stableTraits)
        assertEquals("goals", snapshot.activeGoals)
        assertEquals("constraints", snapshot.importantConstraints)
        assertEquals(receiptHash, snapshot.sourceEventHash)
        assertEquals(7777L, snapshot.updatedAtMillis)
        assertTrue(snapshot.genesisCoreId.startsWith("evsha256:"))
    }

    private fun sourceRef(
        eventHash: String,
        memoryKind: String,
        importance: Int,
        confidence: Int
    ): JSONObject {
        return JSONObject()
            .put("event_hash", eventHash)
            .put("memory_kind", memoryKind)
            .put("importance", importance)
            .put("confidence", confidence)
    }
}
