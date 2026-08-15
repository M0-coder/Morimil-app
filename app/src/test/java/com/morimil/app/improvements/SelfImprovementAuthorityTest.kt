package com.morimil.app.improvements

import com.google.crypto.tink.subtle.Ed25519Sign
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
}
