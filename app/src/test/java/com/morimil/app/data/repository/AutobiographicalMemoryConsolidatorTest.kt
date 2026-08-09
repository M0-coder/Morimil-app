package com.morimil.app.data.repository

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutobiographicalMemoryConsolidatorTest {
    @Test
    fun buildsAutobiographicalDraftFromMeaningfulMemoryEvents() {
        val events = listOf(
            event(
                eventHash = "evsha256:${"a".repeat(64)}",
                memoryKind = "identity",
                body = "Morimil nacio como instancia local con memoria privada.",
                importance = 100,
                userConfirmed = false
            ),
            event(
                eventHash = "evsha256:${"b".repeat(64)}",
                memoryKind = "decision",
                body = "Regla: decision importante debe entrar al torrente firmado.",
                importance = 92,
                userConfirmed = true
            ),
            event(
                eventHash = "evsha256:${"c".repeat(64)}",
                memoryKind = "conversation",
                eventType = "project.vault_created",
                tagsJson = "[\"project\"]",
                body = "Boveda de proyecto creada: Morimil-app.",
                importance = 88
            ),
            event(
                eventHash = "evsha256:${"d".repeat(64)}",
                memoryKind = "correction",
                body = "Correccion: no crear organos desconectados del torrente.",
                importance = 92,
                userConfirmed = true
            ),
            event(
                eventHash = "evsha256:${"e".repeat(64)}",
                memoryKind = "chat_noise",
                body = "dale",
                importance = 8
            )
        )

        val draft = AutobiographicalMemoryConsolidator.build(
            alias = "Morimil",
            sourceRestCycleRef = "rest_candidate_001",
            events = events,
            generatedAtMillis = 1234L
        )
        val evidence = JSONObject(draft.evidenceJson)

        assertTrue(draft.selfSummary.contains("Morimil (Morimil)"))
        assertTrue(draft.selfSummary.contains("decision importante"))
        assertTrue(draft.activeGoals.contains("Boveda de proyecto"))
        assertTrue(draft.importantConstraints.contains("organos desconectados"))
        assertFalse(draft.selfSummary.contains("dale"))
        assertEquals("morimil.autobiographical_consolidation.v2", evidence.getString("schema"))
        assertEquals("rest_candidate_001", evidence.getString("source_rest_cycle_ref"))
        assertEquals(5, evidence.getInt("source_event_count"))
        assertEquals(1, evidence.getInt("project_signal_count"))
    }

    @Test
    fun eventBodyIncludesSnapshotSections() {
        val draft = AutobiographicalMemoryDraft(
            alias = "Morimil",
            selfSummary = "self summary",
            stableTraits = "traits",
            activeGoals = "active goals",
            importantConstraints = "important constraints",
            evidenceJson = "{}"
        )

        val body = AutobiographicalMemoryConsolidator.eventBody(draft)

        assertTrue(body.contains("Autobiografia local consolidada"))
        assertTrue(body.contains("self summary"))
        assertTrue(body.contains("active goals"))
        assertTrue(body.contains("important constraints"))
    }

    private fun event(
        eventHash: String,
        eventType: String = "conversation.user_message",
        memoryKind: String,
        tagsJson: String = "[]",
        body: String,
        importance: Int,
        confidence: Int = 90,
        userConfirmed: Boolean = false,
        observedAtMillis: Long = 1000L
    ): RestCycleSourceEvent {
        return RestCycleSourceEvent(
            eventHash = eventHash,
            eventType = eventType,
            actor = "system",
            source = "test",
            memoryKind = memoryKind,
            tagsJson = tagsJson,
            body = body,
            importance = importance,
            confidence = confidence,
            userConfirmed = userConfirmed,
            observedAtMillis = observedAtMillis
        )
    }
}
