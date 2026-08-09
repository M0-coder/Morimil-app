package com.morimil.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestRepairProposalPlannerTest {
    @Test
    fun duplicateMemoriesBecomeRepairCandidates() {
        val text = "Morimil debe guardar decisiones importantes solo cuando el usuario las confirma claramente."
        val report = RestRepairProposalPlanner.build(
            listOf(
                event(hash = "evsha256:${"a".repeat(64)}", body = text, memoryKind = "decision", importance = 80),
                event(hash = "evsha256:${"b".repeat(64)}", body = text, memoryKind = "decision", importance = 76)
            )
        )

        assertTrue(report.hasCandidates)
        assertTrue(report.candidates.any { candidate -> candidate.kind == "duplicate_candidate" })
        assertTrue(report.expectedEffect().contains("automatic_changes=false"))
    }

    @Test
    fun importantUnconfirmedMemoryRequiresReview() {
        val report = RestRepairProposalPlanner.build(
            listOf(
                event(
                    hash = "evsha256:${"c".repeat(64)}",
                    body = "La arquitectura aprobada exige aprobacion humana antes de acciones externas.",
                    memoryKind = "decision",
                    importance = 92,
                    userConfirmed = false
                )
            )
        )

        assertEquals("medium", report.riskLevel)
        assertTrue(report.candidates.any { candidate -> candidate.kind == "important_unconfirmed_memory" })
        assertTrue(report.migrationSteps().contains("wait_for_human_approval_before_any_repair"))
    }

    @Test
    fun correctionOverlappingStableMemoryCreatesHighRiskContradictionCandidate() {
        val report = RestRepairProposalPlanner.build(
            listOf(
                event(
                    hash = "evsha256:${"d".repeat(64)}",
                    body = "IonPay sera una empresa billetera digital con agentes activos en la boveda.",
                    memoryKind = "decision",
                    importance = 88,
                    observedAtMillis = 10L
                ),
                event(
                    hash = "evsha256:${"e".repeat(64)}",
                    body = "Correccion: IonPay no sera una empresa billetera digital todavia; solo es una idea por revisar.",
                    memoryKind = "correction",
                    eventType = "memory_review.correction",
                    importance = 90,
                    observedAtMillis = 20L
                )
            )
        )

        assertEquals("high", report.riskLevel)
        assertTrue(report.candidates.any { candidate -> candidate.kind == "possible_contradiction" })
    }

    @Test
    fun cleanLowImportanceMemoriesDoNotCreateRepairProposal() {
        val report = RestRepairProposalPlanner.build(
            listOf(
                event(hash = "evsha256:${"f".repeat(64)}", body = "Hola, conversacion casual.", memoryKind = "conversation", importance = 20),
                event(hash = "evsha256:${"1".repeat(64)}", body = "Nota simple sin importancia estable.", memoryKind = "learning", importance = 45)
            )
        )

        assertFalse(report.hasCandidates)
        assertTrue(report.candidates.isEmpty())
    }

    @Test
    fun evidenceDeclaresProposalOnlyMode() {
        val report = RestRepairProposalPlanner.build(
            listOf(
                event(
                    hash = "evsha256:${"2".repeat(64)}",
                    body = "Una decision muy importante debe revisarse antes de consolidarse como verdad estable.",
                    memoryKind = "decision",
                    importance = 95,
                    userConfirmed = false
                )
            )
        )

        assertTrue(report.evidenceJson("repair_test").contains("morimil.rest_repair_proposal.v2"))
        assertTrue(report.canonicalProposalJson().contains("proposal_only"))
        assertTrue(report.eventBody("repair_test").contains("no_automatic_memory_mutation"))
    }

    private fun event(
        hash: String,
        body: String,
        memoryKind: String,
        importance: Int,
        eventType: String = "test.event",
        confidence: Int = 90,
        userConfirmed: Boolean = false,
        observedAtMillis: Long = 123L
    ): RestCycleSourceEvent {
        return RestCycleSourceEvent(
            eventHash = hash,
            eventType = eventType,
            actor = "user",
            source = "test",
            memoryKind = memoryKind,
            tagsJson = "[]",
            body = body,
            importance = importance,
            confidence = confidence,
            userConfirmed = userConfirmed,
            observedAtMillis = observedAtMillis
        )
    }
}
