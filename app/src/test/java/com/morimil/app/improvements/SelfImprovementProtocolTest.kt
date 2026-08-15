package com.morimil.app.improvements

import com.google.crypto.tink.subtle.Ed25519Sign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfImprovementProtocolTest {
    @Test
    fun criticalIdentityChangeRequiresSignedIndependentEvidenceAndSignedHumanAuthorization() {
        val authority = authorityVerifier()
        val observation = observation(
            setOf(SelfChangeSurface.INSTANCE_IDENTITY, SelfChangeSurface.GENESIS)
        )
        var candidate = patchCandidate(observation)
        assertEquals(SelfChangeRisk.CRITICAL, candidate.risk)

        candidate = SelfImprovementProtocol.verify(
            candidate,
            signedVerifierAttestation(observation, candidate, fullCriticalClaims()),
            authority
        )
        assertEquals(SelfChangeStage.VERIFIED, candidate.stage)

        assertThrows(IllegalArgumentException::class.java) {
            SelfImprovementProtocol.authorizeLowRiskFromIndependentVerification(candidate)
        }

        val humanAttestation = signedHumanAuthorization(
            observation = observation,
            candidate = candidate,
            verificationDigest = requireNotNull(candidate.evidence).attestationDigest
        )
        candidate = SelfImprovementProtocol.authorizeHighRisk(
            candidate,
            humanAttestation,
            authority
        )
        candidate = SelfImprovementProtocol.markMergeReady(candidate)

        assertEquals(SelfChangeStage.MERGE_READY, candidate.stage)
        assertEquals(SelfAuthorityRole.HUMAN_AUTHORIZER, candidate.authorization?.role)
        assertEquals(HUMAN_ID, candidate.authorization?.signerId)
    }

    @Test
    fun criticalChangeRejectsSignedEvidenceWithoutCrossLanguageVectors() {
        val authority = authorityVerifier()
        val observation = observation(setOf(SelfChangeSurface.CANONICAL_MEMORY))
        val candidate = patchCandidate(observation)
        val claims = fullCriticalClaims() - SelfVerificationClaim.CROSS_LANGUAGE_VECTORS

        val failure = assertThrows(IllegalArgumentException::class.java) {
            SelfImprovementProtocol.verify(
                candidate,
                signedVerifierAttestation(observation, candidate, claims),
                authority
            )
        }
        assertTrue(failure.message.orEmpty().contains("required_evidence_missing"))
    }

    @Test
    fun forgedOrTamperedVerifierSignatureCannotAdvanceStage() {
        val authority = authorityVerifier()
        val observation = observation(setOf(SelfChangeSurface.SECURITY_BOUNDARY))
        val candidate = patchCandidate(observation)
        val signed = signedVerifierAttestation(observation, candidate, fullHighClaims())
        val tampered = signed.copy(evidenceBundleDigest = "sha256:" + "f".repeat(64))

        val failure = assertThrows(IllegalArgumentException::class.java) {
            SelfImprovementProtocol.verify(candidate, tampered, authority)
        }
        assertTrue(failure.message.orEmpty().contains("signature"))
    }

    @Test
    fun humanAuthorizationMustReferenceExactVerificationAttestation() {
        val authority = authorityVerifier()
        val observation = observation(setOf(SelfChangeSurface.SECURITY_BOUNDARY))
        var candidate = patchCandidate(observation)
        candidate = SelfImprovementProtocol.verify(
            candidate,
            signedVerifierAttestation(observation, candidate, fullHighClaims()),
            authority
        )
        val wrongVerification = "sha256:" + "9".repeat(64)
        val wrong = signedHumanAuthorization(observation, candidate, wrongVerification)

        val failure = assertThrows(IllegalArgumentException::class.java) {
            SelfImprovementProtocol.authorizeHighRisk(candidate, wrong, authority)
        }
        assertTrue(failure.message.orEmpty().contains("authorization_not_bound"))
    }

    @Test
    fun lowRiskChangeCanUseAlreadyTrustedIndependentVerifierWithoutSelfAuthorization() {
        val authority = authorityVerifier()
        val observation = observation(setOf(SelfChangeSurface.PRESENTATION))
        var candidate = patchCandidate(observation)
        assertEquals(SelfChangeRisk.LOW, candidate.risk)
        candidate = SelfImprovementProtocol.verify(
            candidate,
            signedVerifierAttestation(observation, candidate, baseClaims()),
            authority
        )
        candidate = SelfImprovementProtocol.authorizeLowRiskFromIndependentVerification(candidate)

        assertEquals(SelfChangeStage.AUTHORIZED, candidate.stage)
        assertEquals(SelfAuthorityRole.INDEPENDENT_VERIFIER, candidate.authorization?.role)
        assertEquals(VERIFIER_ID, candidate.authorization?.signerId)
    }

    @Test
    fun observationDigestCannotBeDetachedFromProblemStatement() {
        val observation = SelfChangeObservation.create(
            changeId = "change_portability_001",
            problem = "Instance and Body boundaries must remain separable.",
            proposal = "Generate a verified candidate change without self-authorization.",
            surfaces = setOf(SelfChangeSurface.INSTANCE_IDENTITY)
        )

        assertThrows(IllegalArgumentException::class.java) {
            SelfChangeObservation(
                changeId = observation.changeId,
                problem = "A different problem statement.",
                proposal = observation.proposal,
                surfaces = observation.surfaces,
                observationDigest = observation.observationDigest
            )
        }
    }

    @Test
    fun signedEvidenceForAnotherBaseCannotBeAttached() {
        val authority = authorityVerifier()
        val observation = observation(setOf(SelfChangeSurface.INSTANCE_IDENTITY))
        val candidate = patchCandidate(observation)
        val wrongBaseCandidate = candidate.copy(baseCommitSha = "b".repeat(40))
        val attestation = signedVerifierAttestation(
            observation,
            wrongBaseCandidate,
            fullCriticalClaims()
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            SelfImprovementProtocol.verify(candidate, attestation, authority)
        }
        assertTrue(failure.message.orEmpty().contains("base_mismatch"))
    }

    private fun observation(surfaces: Set<SelfChangeSurface>): SelfChangeObservation {
        return SelfChangeObservation.create(
            changeId = "change_portability_001",
            problem = "Instance and Body boundaries must remain separable.",
            proposal = "Generate a verified candidate change without self-authorization.",
            surfaces = surfaces
        )
    }

    private fun patchCandidate(observation: SelfChangeObservation): SelfChangeCandidate {
        var candidate = SelfImprovementProtocol.detect(observation)
        candidate = SelfImprovementProtocol.diagnose(candidate, SelfChangeActor.MORIMIL)
        candidate = SelfImprovementProtocol.propose(candidate, SelfChangeActor.MORIMIL)
        return SelfImprovementProtocol.registerPatchCandidate(
            candidate,
            PATCH_DIGEST,
            BASE_SHA,
            SelfChangeActor.EXTERNAL_EXECUTOR
        )
    }

    private fun authorityVerifier(): SelfImprovementAuthorityVerifier {
        val verifier = verifierKeyPair()
        val human = humanKeyPair()
        return SelfImprovementAuthorityVerifier(
            listOf(
                SelfImprovementTrustedAuthorityKey(
                    SelfAuthorityRole.INDEPENDENT_VERIFIER,
                    VERIFIER_ID,
                    SelfImprovementAuthorityProfile.sha256(verifier.publicKey),
                    verifier.publicKey
                ),
                SelfImprovementTrustedAuthorityKey(
                    SelfAuthorityRole.HUMAN_AUTHORIZER,
                    HUMAN_ID,
                    SelfImprovementAuthorityProfile.sha256(human.publicKey),
                    human.publicKey
                )
            )
        )
    }

    private fun signedVerifierAttestation(
        observation: SelfChangeObservation,
        candidate: SelfChangeCandidate,
        claims: Set<SelfVerificationClaim>
    ): SelfSignedAuthorityAttestation {
        val pair = verifierKeyPair()
        val draft = SelfSignedAuthorityAttestation(
            role = SelfAuthorityRole.INDEPENDENT_VERIFIER,
            signerId = VERIFIER_ID,
            publicKeyRef = SelfImprovementAuthorityProfile.sha256(pair.publicKey),
            observationDigest = observation.observationDigest,
            candidateDigest = requireNotNull(candidate.candidateDigest),
            baseCommitSha = requireNotNull(candidate.baseCommitSha),
            evidenceBundleDigest = "sha256:" + "e".repeat(64),
            claims = claims,
            issuedAtMillis = 1000L,
            nonce = "verify-nonce-001",
            signatureValue = "0".repeat(128)
        )
        return sign(draft, pair)
    }

    private fun signedHumanAuthorization(
        observation: SelfChangeObservation,
        candidate: SelfChangeCandidate,
        verificationDigest: String
    ): SelfSignedAuthorityAttestation {
        val pair = humanKeyPair()
        val draft = SelfSignedAuthorityAttestation(
            role = SelfAuthorityRole.HUMAN_AUTHORIZER,
            signerId = HUMAN_ID,
            publicKeyRef = SelfImprovementAuthorityProfile.sha256(pair.publicKey),
            observationDigest = observation.observationDigest,
            candidateDigest = requireNotNull(candidate.candidateDigest),
            baseCommitSha = requireNotNull(candidate.baseCommitSha),
            evidenceBundleDigest = verificationDigest,
            claims = setOf(SelfVerificationClaim.HUMAN_AUTHORIZATION),
            issuedAtMillis = 1100L,
            nonce = "human-nonce-001",
            signatureValue = "0".repeat(128)
        )
        return sign(draft, pair)
    }

    private fun sign(
        draft: SelfSignedAuthorityAttestation,
        pair: Ed25519Sign.KeyPair
    ): SelfSignedAuthorityAttestation {
        val signature = Ed25519Sign(pair.privateKey)
            .sign(SelfImprovementAuthorityProfile.signingBytes(draft))
        return draft.copy(signatureValue = signature.toLowerHex())
    }

    private fun baseClaims(): Set<SelfVerificationClaim> = setOf(
        SelfVerificationClaim.PATCH_CONTENT_RECOMPUTED,
        SelfVerificationClaim.EXACT_BASE,
        SelfVerificationClaim.ARCHITECTURE_REVIEW,
        SelfVerificationClaim.COMPILATION,
        SelfVerificationClaim.UNIT_TESTS,
        SelfVerificationClaim.STATIC_ANALYSIS
    )

    private fun fullHighClaims(): Set<SelfVerificationClaim> = baseClaims() + setOf(
        SelfVerificationClaim.SECURITY_CHECKS,
        SelfVerificationClaim.REPRODUCIBILITY,
        SelfVerificationClaim.COVERAGE_REVIEW,
        SelfVerificationClaim.MUTATION_REVIEW,
        SelfVerificationClaim.SANDBOX_ISOLATION,
        SelfVerificationClaim.SECRET_ISOLATION,
        SelfVerificationClaim.BLAST_RADIUS_REVIEW,
        SelfVerificationClaim.ROLLBACK_PLAN_REVIEW,
        SelfVerificationClaim.AUDIT_TRAIL
    )

    private fun fullCriticalClaims(): Set<SelfVerificationClaim> = fullHighClaims() + setOf(
        SelfVerificationClaim.INSTRUMENTED_TESTS,
        SelfVerificationClaim.CROSS_LANGUAGE_VECTORS
    )

    private fun verifierKeyPair(): Ed25519Sign.KeyPair =
        Ed25519Sign.KeyPair.newKeyPairFromSeed(ByteArray(32) { 0x41.toByte() })

    private fun humanKeyPair(): Ed25519Sign.KeyPair =
        Ed25519Sign.KeyPair.newKeyPairFromSeed(ByteArray(32) { 0x42.toByte() })

    private fun ByteArray.toLowerHex(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        val PATCH_DIGEST = "sha256:" + "a".repeat(64)
        const val BASE_SHA = "2a9171874e4539de5ee8b8808f45fcc5a0e651b8"
        const val VERIFIER_ID = "verifier-01"
        const val HUMAN_ID = "human-authorizer-01"
    }
}
