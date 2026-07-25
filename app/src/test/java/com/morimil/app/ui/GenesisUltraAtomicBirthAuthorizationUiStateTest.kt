package com.morimil.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraAtomicBirthAuthorizationUiStateTest {
    @Test
    fun verifiedAuthorizationIsActiveOnlyInMemory() {
        val state = GenesisUltraAtomicBirthAuthorizationUiState(summary = summary())

        assertTrue(state.authorizedInMemory)
        assertTrue(requireNotNull(state.summary).birthCommitAuthorized)
        assertFalse(state.verifying)
        assertFalse(state.expired)
    }

    @Test
    fun verifyingStateCannotContainSummaryOrError() {
        assertThrows(IllegalArgumentException::class.java) {
            GenesisUltraAtomicBirthAuthorizationUiState(
                verifying = true,
                summary = summary()
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GenesisUltraAtomicBirthAuthorizationUiState(
                verifying = true,
                errorMessage = "failure"
            )
        }
    }

    @Test
    fun expiredStateCannotRetainAuthorizationSummary() {
        assertThrows(IllegalArgumentException::class.java) {
            GenesisUltraAtomicBirthAuthorizationUiState(
                summary = summary(),
                expired = true
            )
        }

        val expired = GenesisUltraAtomicBirthAuthorizationUiState(
            expired = true,
            errorMessage = "witness_authorization_expired"
        )
        assertFalse(expired.authorizedInMemory)
    }

    private fun summary(): GenesisUltraAtomicBirthAuthorizationSummary {
        return GenesisUltraAtomicBirthAuthorizationSummary(
            candidateDigest = digest('1'),
            consentDigest = digest('2'),
            birthStateDigest = digest('3'),
            receiptDigest = digest('4'),
            authorizationDigest = digest('5'),
            authorizedAt = "2026-07-25T12:00:00Z",
            expiresAt = "2026-07-25T12:02:00Z"
        )
    }

    private fun digest(character: Char): String = "sha256:" + character.toString().repeat(64)
}
