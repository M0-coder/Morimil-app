package com.morimil.app.improvements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfImprovementProtocolTest {
    @Test
    fun criticalIdentityChangeRequiresIndependentEvidenceAndHumanAuthorization() {
        var candidate = detected(
            surfaces = setOf(
                SelfChangeSurface.INSTANCE_IDENTITY,
                SelfChangeSurface.GENESIS
            )
        )
        assertEquals(SelfChangeRisk.CRITICAL, candidate.risk)

        candidate = SelfImprovementProtocol.diagnose(candidate, SelfChangeActor.MORIMIL)
        candidate = SelfImprovementProtocol.propose(candidate, SelfChangeActor.MORIMIL)
        candidate = SelfImprovementProtocol.registerPatchCandidate(
            candidate = candidate,
            candidateDigest = PATCH_DIGEST,
            baseCommitSha = BASE_SHA,
            actor = SelfChangeActor.MORIMIL
        )

        assertThrows(IllegalArgumentException::class.java) {
            SelfImprovementProtocol.verify(
                candidate,
                fullCriticalEvidence(),
                SelfChangeActor.MORIMIL
            )
        }

        candidate = SelfImprovementProtocol.verify(
            candidate,
            fullCriticalEvidence(),
            SelfChangeActor.INDEPENDENT_VERIFIER
        )

        assertThrows(IllegalArgumentException::class.java) {
            SelfImprovementProtocol.authorize(candidate, SelfChangeActor.INDEPENDENT_VERIFIER)
        }
        assertThrows(IllegalArgumentException::class.java) {
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
        var candidate = detected(setOf(SelfChangeSurface.CANONICAL_MEMORY))
        candidate = SelfImprovementProtocol.diagnose(candidate, SelfChangeActor.MORIMIL)
        candidate = SelfImprovementProtocol.propose(candidate, SelfChangeActor.MORIMIL)
        candidate = SelfImprovementProtocol.registerPatchCandidate(
            candidate = candidate,
            candidateDigest = PATCH_DIGEST,
            baseCommitSha = BASE_SHA,
            actor = SelfChangeActor.EXTERNAL_EXECUTOR
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            SelfImprovementProtocol.verify(
                candidate,
                fullCriticalEvidence().copy(crossLanguageVectorsPassed = false),
                SelfChangeActor.INDEPENDENT_VERIFIER
            )
        }
        assertTrue(failure.message.orEmpty().contains("cross_language"))
    }

    @Test
    fun presentationChangeStillCannotSelfAuthorize() {
        var candidate = detected(setOf(SelfChangeSurface.PRESENTATION))
        assertEquals(SelfChangeRisk.LOW, candidate.risk)
        candidate = SelfImprovementProtocol.diagnose(candidate, SelfChangeActor.MORIMIL)
        candidate = SelfImprovementProtocol.propose(candidate, SelfChangeActor.MORIMIL)
        candidate = SelfImprovementProtocol.registerPatchCandidate(
            candidate = candidate,
            candidateDigest = PATCH_DIGEST,
            baseCommitSha = BASE_SHA,
            actor = SelfChangeActor.MORIMIL
        )
        candidate = SelfImprovementProtocol.verify(
            candidate,
            SelfChangeEvidence(
                candidateDigest = PATCH_DIGEST,
                baseCommitSha = BASE_SHA,
                architectureReviewed = true,
                compilationPassed = true,
                unitTestsPassed = true,
                staticAnalysisPassed = true,
                exactBaseVerified = true
            ),
            SelfChangeActor.INDEPENDENT_VERIFIER
        )

        assertThrows(IllegalArgumentException::class.java) {
            SelfImprovementProtocol.authorize(candidate, SelfChangeActor.MORIMIL)
        }
    }

    @Test
    fun patchEvidenceCannotBeAttachedBeforePatchExistsOrToAnotherBase() {
        val detected = detected(setOf(SelfChangeSurface.INSTANCE_IDENTITY))
        assertEquals(null, detected.candidateDigest)
        assertEquals(null, detected.baseCommitSha)

        var candidate = SelfImprovementProtocol.diagnose(detected, SelfChangeActor.MORIMIL)
        candidate = SelfImprovementProtocol.propose(candidate, SelfChangeActor.MORIMIL)
        candidate = SelfImprovementProtocol.registerPatchCandidate(
            candidate,
            PATCH_DIGEST,
            BASE_SHA,
            SelfChangeActor.EXTERNAL_EXECUTOR
        )

        assertThrows(IllegalArgumentException::class.java) {
            SelfImprovementProtocol.verify(
                candidate,
                fullCriticalEvidence().copy(baseCommitSha = "b".repeat(40)),
                SelfChangeActor.INDEPENDENT_VERIFIER
            )
        }
    }

    private fun detected(surfaces: Set<SelfChangeSurface>): SelfChangeCandidate {
        return SelfImprovementProtocol.detect(
            SelfChangeObservation(
                changeId = "change_portability_001",
                problem = "Instance and Body boundaries must remain separable.",
                proposal = "Generate a verified candidate change without self-authorization.",
                surfaces = surfaces,
                observationDigest = OBSERVATION_DIGEST
            )
        )
    }

    private fun fullCriticalEvidence(): SelfChangeEvidence {
        return SelfChangeEvidence(
            candidateDigest = PATCH_DIGEST,
            baseCommitSha = BASE_SHA,
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
            exactBaseVerified = true
        )
    }

    private companion object {
        val OBSERVATION_DIGEST = "sha256:" + "9".repeat(64)
        val PATCH_DIGEST = "sha256:" + "a".repeat(64)
        const val BASE_SHA = "2a9171874e4539de5ee8b8808f45fcc5a0e651b8"
    }
}
