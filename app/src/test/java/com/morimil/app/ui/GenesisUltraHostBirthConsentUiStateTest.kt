package com.morimil.app.ui

import com.morimil.app.data.genesis.ultra.GenesisUltraHostBirthConsentState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraHostBirthConsentUiStateTest {
    @Test
    fun priorConsentWithoutCandidateSessionCanBeRepresentedForRevocation() {
        val state = GenesisUltraHostBirthConsentUiState(
            persistedState = GenesisUltraHostBirthConsentState.EXPIRED,
            candidateSessionAvailable = false
        )

        assertTrue(state.hasPersistedConsent)
        assertFalse(state.candidateSessionAvailable)
        assertNull(state.summary)
        assertFalse(state.busy)
    }

    @Test
    fun consentSummaryNeverAuthorizesBirth() {
        val summary = GenesisUltraHostBirthConsentSummary(
            consentId = "consent_" + "1".repeat(64),
            candidateDigest = "sha256:" + "2".repeat(64),
            instanceId = "inst_" + "3".repeat(64),
            companionName = "Morimil",
            consentDigest = "sha256:" + "4".repeat(64),
            consentedAt = "2026-07-25T00:00:00Z",
            expiresAt = "2026-07-25T00:02:00Z"
        )

        assertFalse(summary.birthCommitAuthorized)
        val state = GenesisUltraHostBirthConsentUiState(
            persistedState = GenesisUltraHostBirthConsentState.READY,
            summary = summary,
            candidateSessionAvailable = true
        )
        assertTrue(state.hasPersistedConsent)
        assertFalse(state.busy)
    }

    @Test
    fun summaryCannotSurviveWithoutTheExactCandidateSession() {
        val summary = GenesisUltraHostBirthConsentSummary(
            consentId = "consent_" + "1".repeat(64),
            candidateDigest = "sha256:" + "2".repeat(64),
            instanceId = "inst_" + "3".repeat(64),
            companionName = "Morimil",
            consentDigest = "sha256:" + "4".repeat(64),
            consentedAt = "2026-07-25T00:00:00Z",
            expiresAt = "2026-07-25T00:02:00Z"
        )

        assertThrows(IllegalArgumentException::class.java) {
            GenesisUltraHostBirthConsentUiState(
                persistedState = GenesisUltraHostBirthConsentState.READY,
                summary = summary,
                candidateSessionAvailable = false
            )
        }
    }

    @Test
    fun onlyOneConsentOperationMayRunAtOnce() {
        assertThrows(IllegalArgumentException::class.java) {
            GenesisUltraHostBirthConsentUiState(
                checking = true,
                recording = true
            )
        }
    }
}
