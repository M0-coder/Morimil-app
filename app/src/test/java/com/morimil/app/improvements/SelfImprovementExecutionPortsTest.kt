package com.morimil.app.improvements

import com.google.crypto.tink.subtle.Ed25519Sign
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfImprovementExecutionPortsTest {
    @Test
    fun orchestratorSeparatesExecutorAndVerifierAndStopsAtVerified() = runBlocking {
        val patch = patchArtifact("app/src/main/java/com/morimil/app/data/genesis/ultra/Example.kt")
        val authority = authorityVerifier()
        val executor = object : SelfPatchExecutorPort {
            override val executorId: String = "executor-01"
            override suspend fun generatePatch(request: SelfPatchGenerationRequest): SelfPatchArtifact {
                assertEquals(BASE_SHA, request.baseCommitSha)
                return patch
            }
        }
        val verifier = object : SelfIndependentVerifierPort {
            override val verifierId: String = VERIFIER_ID
            override suspend fun verify(
                observation: SelfChangeObservation,
                patch: SelfPatchArtifact
            ): SelfSignedAuthorityAttestation {
                return signedVerifierAttestation(observation, patch)
            }
        }

        val candidate = SelfImprovementOrchestrator(executor, verifier, authority)
            .prepareVerifiedCandidate(observation(), BASE_SHA)

        assertEquals(SelfChangeStage.VERIFIED, candidate.stage)
        assertEquals(SelfChangeRisk.CRITICAL, candidate.risk)
        assertEquals(patch.candidateDigest, candidate.candidateDigest)
        assertEquals(BASE_SHA, candidate.baseCommitSha)
        assertNull(candidate.authorization)
        assertEquals(VERIFIER_ID, candidate.evidence?.verifierId)
    }

    @Test
    fun patchDigestAndPathsAreDerivedFromExactBytesAndBase() {
        val first = patchArtifact("app/src/main/java/com/morimil/app/ui/A.kt", replacement = "new-a")
        val second = patchArtifact("app/src/main/java/com/morimil/app/ui/A.kt", replacement = "new-b")
        val otherBase = SelfPatchArtifact.fromPatchBytes(
            baseCommitSha = "b".repeat(40),
            patchRef = "branch:other-base",
            patchBytes = patchBytes("app/src/main/java/com/morimil/app/ui/A.kt", "new-a")
        )

        assertNotEquals(first.candidateDigest, second.candidateDigest)
        assertNotEquals(first.candidateDigest, otherBase.candidateDigest)
        assertEquals(first.candidateDigest, first.recomputeCandidateDigest())
        assertEquals(
            listOf("app/src/main/java/com/morimil/app/ui/A.kt"),
            first.changedPaths
        )
        assertEquals(first.copyPatchBytes().size.toLong(), first.patchByteCount)
    }

    @Test
    fun patchDerivedMetadataCannotBeMutatedAfterValidation() {
        val patch = patchArtifact("app/src/main/java/com/morimil/app/ui/A.kt")
        val originalPaths = patch.changedPaths.toList()
        val originalSurfaces = patch.derivedSurfaces.toSet()

        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (patch.changedPaths as MutableList<String>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (patch.derivedSurfaces as MutableSet<SelfChangeSurface>).clear()
        }

        assertEquals(originalPaths, patch.changedPaths)
        assertEquals(originalSurfaces, patch.derivedSurfaces)
        assertEquals(patch.candidateDigest, patch.recomputeCandidateDigest())
    }

    @Test
    fun mixedKnownAndUnknownProductionPathsRetainCoreImplementationRisk() {
        val surfaces = SelfPatchSafetyPolicy.inferSurfaces(
            listOf(
                "app/src/main/java/com/morimil/app/ui/A.kt",
                "app/src/main/java/com/morimil/app/core/Unknown.kt"
            )
        )

        assertTrue(SelfChangeSurface.PRESENTATION in surfaces)
        assertTrue(SelfChangeSurface.CORE_IMPLEMENTATION in surfaces)
        assertEquals(SelfChangeRisk.HIGH, SelfImprovementPolicy.classify(surfaces))
    }

    @Test
    fun rootBuildTestToolAndUnknownRepositoryPathsNeverRemainLowRisk() {
        val paths = listOf(
            "build.gradle.kts",
            "settings.gradle.kts",
            "gradlew",
            "tools/governance/verify_policy.py",
            "app/src/test/java/com/morimil/app/ui/FakePassingTest.kt",
            "app/src/debug/AndroidManifest.xml",
            "docs/security-policy.md",
            "custom/unknown-script.sh"
        )

        paths.forEach { path ->
            val surfaces = SelfPatchSafetyPolicy.inferSurfaces(listOf(path))
            assertTrue("path must have derived surface: $path", surfaces.isNotEmpty())
            assertTrue(
                "path must be HIGH or CRITICAL: $path -> $surfaces",
                SelfImprovementPolicy.classify(surfaces) in
                    setOf(SelfChangeRisk.HIGH, SelfChangeRisk.CRITICAL)
            )
        }
        assertTrue(
            SelfChangeSurface.BUILD_AND_SUPPLY_CHAIN in
                SelfPatchSafetyPolicy.inferSurfaces(listOf("settings.gradle.kts"))
        )
        assertTrue(
            SelfChangeSurface.CORE_IMPLEMENTATION in
                SelfPatchSafetyPolicy.inferSurfaces(listOf("custom/unknown-script.sh"))
        )
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
            ): SelfSignedAuthorityAttestation = error("not called")
        }

        assertThrows(IllegalArgumentException::class.java) {
            SelfImprovementOrchestrator(executor, verifier, authorityVerifier())
        }
    }

    @Test
    fun executorCannotSilentlyChangeBaseCommit() {
        val executor = object : SelfPatchExecutorPort {
            override val executorId: String = "executor-01"
            override suspend fun generatePatch(request: SelfPatchGenerationRequest): SelfPatchArtifact {
                return SelfPatchArtifact.fromPatchBytes(
                    baseCommitSha = "b".repeat(40),
                    patchRef = "branch:wrong-base",
                    patchBytes = patchBytes(
                        "app/src/main/java/com/morimil/app/data/genesis/ultra/Example.kt"
                    )
                )
            }
        }
        val verifier = object : SelfIndependentVerifierPort {
            override val verifierId: String = VERIFIER_ID
            override suspend fun verify(
                observation: SelfChangeObservation,
                patch: SelfPatchArtifact
            ): SelfSignedAuthorityAttestation = signedVerifierAttestation(observation, patch)
        }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                SelfImprovementOrchestrator(executor, verifier, authorityVerifier())
                    .prepareVerifiedCandidate(observation(), BASE_SHA)
            }
        }
    }

    @Test
    fun patchCannotHideCredentialPathInsideRealDiffBytes() {
        listOf(
            "release/morimil-production.jks",
            "release/MORIMIL-PRODUCTION.JKS",
            "config/.env",
            "config/.env.production",
            "nested/local.properties",
            ".git/config",
            ".gitmodules"
        ).forEach { forbiddenPath ->
            assertThrows(IllegalArgumentException::class.java) {
                SelfPatchArtifact.fromPatchBytes(
                    baseCommitSha = BASE_SHA,
                    patchRef = "branch:credential-write",
                    patchBytes = patchBytes(forbiddenPath)
                )
            }
        }
    }

    @Test
    fun executorCannotWidenObservationSurfaceThroughPatchPaths() {
        val lowObservation = SelfChangeObservation.create(
            changeId = "change-ui-001",
            problem = "Presentation spacing is inconsistent.",
            proposal = "Prepare a bounded presentation-only patch.",
            surfaces = setOf(SelfChangeSurface.PRESENTATION)
        )
        val executor = object : SelfPatchExecutorPort {
            override val executorId: String = "executor-01"
            override suspend fun generatePatch(request: SelfPatchGenerationRequest): SelfPatchArtifact {
                return patchArtifact(
                    "app/src/main/java/com/morimil/app/data/genesis/ultra/GenesisOverride.kt"
                )
            }
        }
        val verifier = object : SelfIndependentVerifierPort {
            override val verifierId: String = VERIFIER_ID
            override suspend fun verify(
                observation: SelfChangeObservation,
                patch: SelfPatchArtifact
            ): SelfSignedAuthorityAttestation = signedVerifierAttestation(observation, patch)
        }

        val failure = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                SelfImprovementOrchestrator(executor, verifier, authorityVerifier())
                    .prepareVerifiedCandidate(lowObservation, BASE_SHA)
            }
        }
        assertTrue(failure.message.orEmpty().contains("surface_expansion"))
    }

    private fun observation(): SelfChangeObservation {
        return SelfChangeObservation.create(
            changeId = "change-portability-001",
            problem = "Permanent Instance identity must not depend on its current Body.",
            proposal = "Generate and verify a Body-independent identity patch.",
            surfaces = setOf(SelfChangeSurface.INSTANCE_IDENTITY, SelfChangeSurface.GENESIS)
        )
    }

    private fun patchArtifact(path: String, replacement: String = "new"): SelfPatchArtifact {
        return SelfPatchArtifact.fromPatchBytes(
            baseCommitSha = BASE_SHA,
            patchRef = "branch:self-change-test",
            patchBytes = patchBytes(path, replacement)
        )
    }

    private fun patchBytes(path: String, replacement: String = "new"): ByteArray {
        return (
            "diff --git a/$path b/$path\n" +
                "--- a/$path\n" +
                "+++ b/$path\n" +
                "@@ -1 +1 @@\n" +
                "-old\n" +
                "+$replacement\n"
            ).toByteArray(StandardCharsets.UTF_8)
    }

    private fun authorityVerifier(): SelfImprovementAuthorityVerifier {
        val pair = verifierKeyPair()
        return SelfImprovementAuthorityVerifier(
            listOf(
                SelfImprovementTrustedAuthorityKey(
                    role = SelfAuthorityRole.INDEPENDENT_VERIFIER,
                    signerId = VERIFIER_ID,
                    publicKeyRef = SelfImprovementAuthorityProfile.sha256(pair.publicKey),
                    rawPublicKey = pair.publicKey
                )
            )
        )
    }

    private fun signedVerifierAttestation(
        observation: SelfChangeObservation,
        patch: SelfPatchArtifact
    ): SelfSignedAuthorityAttestation {
        val pair = verifierKeyPair()
        val draft = SelfSignedAuthorityAttestation(
            role = SelfAuthorityRole.INDEPENDENT_VERIFIER,
            signerId = VERIFIER_ID,
            publicKeyRef = SelfImprovementAuthorityProfile.sha256(pair.publicKey),
            observationDigest = observation.observationDigest,
            candidateDigest = patch.candidateDigest,
            baseCommitSha = patch.baseCommitSha,
            evidenceBundleDigest = "sha256:" + "e".repeat(64),
            claims = fullCriticalClaims(),
            issuedAtMillis = 1000L,
            nonce = "verifier-nonce-001",
            signatureValue = "0".repeat(128)
        )
        val signature = Ed25519Sign(pair.privateKey)
            .sign(SelfImprovementAuthorityProfile.signingBytes(draft))
        return draft.copy(signatureValue = signature.toLowerHex())
    }

    private fun fullCriticalClaims(): Set<SelfVerificationClaim> = setOf(
        SelfVerificationClaim.PATCH_CONTENT_RECOMPUTED,
        SelfVerificationClaim.EXACT_BASE,
        SelfVerificationClaim.ARCHITECTURE_REVIEW,
        SelfVerificationClaim.COMPILATION,
        SelfVerificationClaim.UNIT_TESTS,
        SelfVerificationClaim.INSTRUMENTED_TESTS,
        SelfVerificationClaim.STATIC_ANALYSIS,
        SelfVerificationClaim.SECURITY_CHECKS,
        SelfVerificationClaim.REPRODUCIBILITY,
        SelfVerificationClaim.COVERAGE_REVIEW,
        SelfVerificationClaim.MUTATION_REVIEW,
        SelfVerificationClaim.CROSS_LANGUAGE_VECTORS,
        SelfVerificationClaim.SANDBOX_ISOLATION,
        SelfVerificationClaim.SECRET_ISOLATION,
        SelfVerificationClaim.BLAST_RADIUS_REVIEW,
        SelfVerificationClaim.ROLLBACK_PLAN_REVIEW,
        SelfVerificationClaim.AUDIT_TRAIL
    )

    private fun verifierKeyPair(): Ed25519Sign.KeyPair {
        return Ed25519Sign.KeyPair.newKeyPairFromSeed(ByteArray(32) { 0x31.toByte() })
    }

    private fun ByteArray.toLowerHex(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val BASE_SHA = "2a9171874e4539de5ee8b8808f45fcc5a0e651b8"
        const val VERIFIER_ID = "verifier-01"
    }
}
