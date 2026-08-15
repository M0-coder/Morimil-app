package com.morimil.app.improvements

import com.google.crypto.tink.subtle.Ed25519Sign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfImprovementAuthorityTest {
    @Test
    fun oneTrustedPublicKeyCannotServeAsVerifierAndHumanAuthorizer() {
        val pair = Ed25519Sign.KeyPair.newKeyPairFromSeed(ByteArray(32) { 0x51.toByte() })
        val ref = SelfImprovementAuthorityProfile.sha256(pair.publicKey)

        val failure = assertThrows(IllegalArgumentException::class.java) {
            SelfImprovementAuthorityVerifier(
                listOf(
                    SelfImprovementTrustedAuthorityKey(
                        role = SelfAuthorityRole.INDEPENDENT_VERIFIER,
                        signerId = "verifier-01",
                        publicKeyRef = ref,
                        rawPublicKey = pair.publicKey
                    ),
                    SelfImprovementTrustedAuthorityKey(
                        role = SelfAuthorityRole.HUMAN_AUTHORIZER,
                        signerId = "human-01",
                        publicKeyRef = ref,
                        rawPublicKey = pair.publicKey
                    )
                )
            )
        }

        assertTrue(failure.message.orEmpty().contains("public_key_reuse"))
    }

    @Test
    fun oneSignerIdentityCannotBeReusedAcrossAuthorityRolesEvenWithDifferentKeys() {
        val verifier = Ed25519Sign.KeyPair.newKeyPairFromSeed(ByteArray(32) { 0x52.toByte() })
        val human = Ed25519Sign.KeyPair.newKeyPairFromSeed(ByteArray(32) { 0x53.toByte() })

        val failure = assertThrows(IllegalArgumentException::class.java) {
            SelfImprovementAuthorityVerifier(
                listOf(
                    SelfImprovementTrustedAuthorityKey(
                        role = SelfAuthorityRole.INDEPENDENT_VERIFIER,
                        signerId = "same-authority",
                        publicKeyRef = SelfImprovementAuthorityProfile.sha256(verifier.publicKey),
                        rawPublicKey = verifier.publicKey
                    ),
                    SelfImprovementTrustedAuthorityKey(
                        role = SelfAuthorityRole.HUMAN_AUTHORIZER,
                        signerId = "same-authority",
                        publicKeyRef = SelfImprovementAuthorityProfile.sha256(human.publicKey),
                        rawPublicKey = human.publicKey
                    )
                )
            )
        }

        assertTrue(failure.message.orEmpty().contains("signer_identity_reuse"))
    }

    @Test
    fun verifiedClaimsAreSnapshotAndCannotBeMutatedAfterSignatureVerification() {
        val pair = Ed25519Sign.KeyPair.newKeyPairFromSeed(ByteArray(32) { 0x54.toByte() })
        val publicKeyRef = SelfImprovementAuthorityProfile.sha256(pair.publicKey)
        val authority = SelfImprovementAuthorityVerifier(
            listOf(
                SelfImprovementTrustedAuthorityKey(
                    role = SelfAuthorityRole.INDEPENDENT_VERIFIER,
                    signerId = "verifier-immutable",
                    publicKeyRef = publicKeyRef,
                    rawPublicKey = pair.publicKey
                )
            )
        )
        val mutableClaims = linkedSetOf(
            SelfVerificationClaim.PATCH_CONTENT_RECOMPUTED,
            SelfVerificationClaim.EXACT_BASE,
            SelfVerificationClaim.ARCHITECTURE_REVIEW,
            SelfVerificationClaim.COMPILATION,
            SelfVerificationClaim.UNIT_TESTS,
            SelfVerificationClaim.STATIC_ANALYSIS
        )
        val draft = SelfSignedAuthorityAttestation(
            role = SelfAuthorityRole.INDEPENDENT_VERIFIER,
            signerId = "verifier-immutable",
            publicKeyRef = publicKeyRef,
            observationDigest = OBSERVATION_DIGEST,
            candidateDigest = CANDIDATE_DIGEST,
            baseCommitSha = BASE_SHA,
            evidenceBundleDigest = EVIDENCE_DIGEST,
            claims = mutableClaims,
            issuedAtMillis = 100L,
            nonce = "immutable-claims-nonce",
            signatureValue = "0".repeat(128)
        )
        val signature = Ed25519Sign(pair.privateKey)
            .sign(SelfImprovementAuthorityProfile.signingBytes(draft))
        val signed = draft.copy(signatureValue = signature.toLowerHex())

        val evidence = authority.verifyIndependent(
            signed,
            expectedObservationDigest = OBSERVATION_DIGEST,
            expectedCandidateDigest = CANDIDATE_DIGEST,
            expectedBaseCommitSha = BASE_SHA
        )
        val expectedClaims = evidence.claims.toSet()

        mutableClaims.clear()
        assertEquals(expectedClaims, evidence.claims)
        assertFalse(evidence.claims.isEmpty())
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (evidence.claims as MutableSet<SelfVerificationClaim>).clear()
        }
    }

    private fun ByteArray.toLowerHex(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        val OBSERVATION_DIGEST = "sha256:" + "1".repeat(64)
        val CANDIDATE_DIGEST = "sha256:" + "2".repeat(64)
        val EVIDENCE_DIGEST = "sha256:" + "3".repeat(64)
        const val BASE_SHA = "2a9171874e4539de5ee8b8808f45fcc5a0e651b8"
    }
}
