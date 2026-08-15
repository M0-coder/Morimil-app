package com.morimil.app.improvements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFailsWith
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfImprovementProtocolTest {
    @Test
    fun criticalIdentityChangeRequiresIndependentEvidenceAndHumanAuthorization() {
        var candidate = candidate(
            surfaces = setOf(
                SelfChangeSurface.INSTANCE_IDENTITY,
                SelfChangeSurface.GENESIS
            )
        )
        assertEquals(SelfChangeRisk.CRITICAL, candidate.risk)

        candidate = SelfImprovementProtocol.diagnose(candidate, SelfChangeActor.MORIMIL)
        candidate = SelfImprovementProtocol.propose(candidate, SelfChangeActor.MORIMIL)
        candidate = SelfImprovementProtocol.registerPatchCandidate(candidate, SelfChangeActor.MORIMIL)

        assertFailsWith<IllegalArgumentException> {
            SelfImprovementProtocol.verify(
                candidate,
                fullCriticalEvidence(candidate.candidateDigest),
                SelfChangeActor.MORIMIL
            )
        }

        candidate = SelfImprovementProtocol.verify(
            candidate,
            fullCriticalEvidence(candidate.candidateDigest),
            SelfChangeActor.INDEPENDENT_VERIFIER
        )

        assertFailsWith<IllegalArgumentException> {
            SelfImprovementProtocol.authorize(candidate, SelfChangeActor.INDEPENDENT_VERIFIER)
        }
        assertFailsWith<IllegalArgumentException> {
            SelfImprovementProtocol.authorize(candidate, SelfChangeActor.MORIMIL)
        }

        candidate = SelfImprovementProtocol.authorize(
            candidate,
            SelfChangeActor.HUMAN_AUTHORIZER
        )
        candidate = SelfImprovementProtocol.markMergeReady(candidate)

        assertEquals(SelfChangeStage.MERGE_READY, candidate.stage)
        assertEquals(SelfChangeActor.HUMAN_AUTHORIZER, candidate.authorizedBy)
    }

    @Test
    fun criticalChangeRejectsEvidenceWithoutCrossLanguageVectors() {
        var candidate = candidate(setOf(SelfChangeSurface.CANONICAL_MEMORY))
        candidate = SelfImprovementProtocol.diagnose(candidate, SelfChangeActor.MORIMIL)
        candidate = SelfImprovementProtocol.propose(candidate, SelfChangeActor.MORIMIL)
        candidate = SelfImprovementProtocol.registerPatchCandidate(candidate, SelfChangeActor.EXTERNAL_EXECUTOR)

        val failure = assertFailsWith<IllegalArgumentException> {
            SelfImprovementProtocol.verify(
                candidate,
                fullCriticalEvidence(candidate.candidateDigest).copy(
                    crossLanguageVectorsPassed = false
                ),
                SelfChangeActor.INDEPENDENT_VERIFIER
            )
        }
        assertTrue(failure.message.orEmpty().contains("cross_language"))
    }

    @Test
    fun presentationChangeStillCannotSelfAuthorize() {
        var candidate = candidate(setOf(SelfChangeSurface.PRESENTATION))
        assertEquals(SelfChangeRisk.LOW, candidate.risk)
        candidate = SelfImprovementProtocol.diagnose(candidate, SelfChangeActor.MORIMIL)
        candidate = SelfImprovementProtocol.propose(candidate, SelfChangeActor.MORIMIL)
        candidate = SelfImprovementProtocol.registerPatchCandidate(candidate, SelfChangeActor.MORIMIL)
        candidate = SelfImprovementProtocol.verify(
            candidate,
            SelfChangeEvidence(
                candidateDigest = candidate.candidateDigest,
                architectureReviewed = true,
                compilationPassed = true,
                unitTestsPassed = true,
                staticAnalysisPassed = true,
                exactMainBaseVerified = true
            ),
            SelfChangeActor.INDEPENDENT_VERIFIER
        )

        assertFailsWith<IllegalArgumentException> {
            SelfImprovementProtocol.authorize(candidate, SelfChangeActor.MORIMIL)
        }
    }

    private fun candidate(surfaces: Set<SelfChangeSurface>): SelfChangeCandidate {
        return SelfChangeCandidate(
            changeId = "change_portability_001",
            problem = "Instance and Body boundaries must remain separable.",
            proposal = "Generate a verified candidate change without self-authorization.",
            surfaces = surfaces,
            candidateDigest = "sha256:" + "a".repeat(64),
            stage = SelfChangeStage.DETECTED
        )
    }

    private fun fullCriticalEvidence(candidateDigest: String): SelfChangeEvidence {
        return SelfChangeEvidence(
            candidateDigest = candidateDigest,
            architectureReviewed = true,
            compilationPassed = true,
            unitTestsPassed = true,
            instrumentedTestsPassed = true,
            staticAnalysisPassed = true,
            securityChecksPassed = true,
            reproducibilityPassed = true,
            coverageReviewed = true,
            mutationReviewed = true,
            crossLanguageVectorsPassed = true,
            exactMainBaseVerified = true
        )
    }
}
