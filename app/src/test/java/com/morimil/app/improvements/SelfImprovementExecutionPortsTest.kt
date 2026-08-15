package com.morimil.app.improvements

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SelfImprovementExecutionPortsTest {
    @Test
    fun orchestratorSeparatesExecutorAndVerifierAndStopsAtVerified() = runBlocking {
        val patch = SelfPatchArtifact(
            candidateDigest = PATCH_DIGEST,
            baseCommitSha = BASE_SHA,
            changedPaths = listOf("app/src/main/example.kt"),
            patchRef = "branch:self-change-test"
        )
        val executor = object : SelfPatchExecutorPort {
            override val executorId: String = "executor-01"
            override suspend fun generatePatch(request: SelfPatchGenerationRequest): SelfPatchArtifact {
                assertEquals(BASE_SHA, request.baseCommitSha)
                return patch
            }
        }
        val verifier = object : SelfIndependentVerifierPort {
            override val verifierId: String = "verifier-01"
            override suspend fun verify(
                observation: SelfChangeObservation,
                patch: SelfPatchArtifact
            ): SelfChangeEvidence {
                return criticalEvidence(patch)
            }
        }

        val candidate = SelfImprovementOrchestrator(executor, verifier)
            .prepareVerifiedCandidate(observation(), BASE_SHA)

        assertEquals(SelfChangeStage.VERIFIED, candidate.stage)
        assertEquals(SelfChangeRisk.CRITICAL, candidate.risk)
        assertEquals(PATCH_DIGEST, candidate.candidateDigest)
        assertEquals(BASE_SHA, candidate.baseCommitSha)
        assertNull(candidate.authorizedBy)
    }

    @Test
    fun sameExecutorAndVerifierIdentityIsRejected() {
        val executor = object : SelfPatchExecutorPort {
            override val executorId: String = "same-actor"
            override suspend fun generatePatch(request: SelfPatchGenerationRequest): SelfPatchArtifact {
                error("not called")
            }
        }
        val verifier = object : SelfIndependentVerifierPort {
            override val verifierId: String = "same-actor"
            override suspend fun verify(
                observation: SelfChangeObservation,
                patch: SelfPatchArtifact
            ): SelfChangeEvidence = error("not called")
        }

        assertThrows(IllegalArgumentException::class.java) {
            SelfImprovementOrchestrator(executor, verifier)
        }
    }

    @Test
    fun executorCannotSilentlyChangeBaseCommit() = runBlocking {
        val executor = object : SelfPatchExecutorPort {
            override val executorId: String = "executor-01"
            override suspend fun generatePatch(request: SelfPatchGenerationRequest): SelfPatchArtifact {
                return SelfPatchArtifact(
                    candidateDigest = PATCH_DIGEST,
                    baseCommitSha = "b".repeat(40),
                    changedPaths = listOf("app/src/main/example.kt"),
                    patchRef = "branch:wrong-base"
                )
            }
        }
        val verifier = object : SelfIndependentVerifierPort {
            override val verifierId: String = "verifier-01"
            override suspend fun verify(
                observation: SelfChangeObservation,
                patch: SelfPatchArtifact
            ): SelfChangeEvidence = criticalEvidence(patch)
        }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                SelfImprovementOrchestrator(executor, verifier)
                    .prepareVerifiedCandidate(observation(), BASE_SHA)
            }
        }
        Unit
    }

    private fun observation(): SelfChangeObservation {
        return SelfChangeObservation(
            changeId = "change-portability-001",
            problem = "Permanent Instance identity must not depend on its current Body.",
            proposal = "Generate and verify a Body-independent identity patch.",
            surfaces = setOf(SelfChangeSurface.INSTANCE_IDENTITY, SelfChangeSurface.GENESIS),
            observationDigest = OBSERVATION_DIGEST
        )
    }

    private fun criticalEvidence(patch: SelfPatchArtifact): SelfChangeEvidence {
        return SelfChangeEvidence(
            candidateDigest = patch.candidateDigest,
            baseCommitSha = patch.baseCommitSha,
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
            sandboxIsolationPassed = true,
            secretIsolationPassed = true,
            blastRadiusReviewed = true,
            rollbackPlanReviewed = true,
            auditTrailRecorded = true,
            exactBaseVerified = true
        )
    }

    private companion object {
        val OBSERVATION_DIGEST = "sha256:" + "9".repeat(64)
        val PATCH_DIGEST = "sha256:" + "a".repeat(64)
        const val BASE_SHA = "2a9171874e4539de5ee8b8808f45fcc5a0e651b8"
    }
}
