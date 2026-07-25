package com.morimil.app.ui

import com.morimil.app.data.genesis.ultra.GenesisUltraAtomicBirthExecutionOutcome
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraAtomicBirthExecutionUiStateTest {
    @Test
    fun freshStateAllowsOneAttempt() {
        val state = GenesisUltraAtomicBirthExecutionUiState()

        assertFalse(state.executing)
        assertFalse(state.birthCommitted)
        assertTrue(state.retryAllowed)
        assertNull(state.errorMessage)
    }

    @Test
    fun executingStateCannotRetry() {
        val state = GenesisUltraAtomicBirthExecutionUiState(executing = true)

        assertTrue(state.executing)
        assertFalse(state.birthCommitted)
        assertFalse(state.retryAllowed)
    }

    @Test
    fun cleanCommitRequiresMemoryHashAndCannotRetry() {
        val state = GenesisUltraAtomicBirthExecutionUiState(
            committed = summary(
                outcome = GenesisUltraAtomicBirthExecutionOutcome.COMMITTED_CLEAN,
                eventHash = EVENT_HASH,
                maintenanceError = null
            )
        )

        assertTrue(state.birthCommitted)
        assertFalse(state.retryAllowed)
        assertNull(state.errorMessage)
    }

    @Test
    fun maintenancePendingMayOmitMemoryHashButCannotRetry() {
        val state = GenesisUltraAtomicBirthExecutionUiState(
            committed = summary(
                outcome = GenesisUltraAtomicBirthExecutionOutcome.COMMITTED_MAINTENANCE_PENDING,
                eventHash = null,
                maintenanceError = "post_commit_receipt_unavailable"
            )
        )

        assertTrue(state.birthCommitted)
        assertFalse(state.retryAllowed)
        assertNull(state.committed?.firstPostBirthEventHash)
    }

    @Test
    fun committedStateRejectsRetryError() {
        val failure = runCatching {
            GenesisUltraAtomicBirthExecutionUiState(
                committed = summary(
                    outcome = GenesisUltraAtomicBirthExecutionOutcome.COMMITTED_CLEAN,
                    eventHash = EVENT_HASH,
                    maintenanceError = null
                ),
                errorMessage = "retry"
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("committed_with_retry_error"))
    }

    private fun summary(
        outcome: GenesisUltraAtomicBirthExecutionOutcome,
        eventHash: String?,
        maintenanceError: String?
    ): GenesisUltraAtomicBirthExecutionSummary {
        return GenesisUltraAtomicBirthExecutionSummary(
            outcome = outcome,
            birthId = "birth_0123456789abcdef",
            instanceId =
                "inst_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            companionName = "Morimil",
            authorizationDigest = SHA,
            birthStateDigest = SHA,
            receiptDigest = SHA,
            firstPostBirthEventHash = eventHash,
            committedAt = "2026-07-25T12:10:00Z",
            maintenanceError = maintenanceError
        )
    }

    private companion object {
        const val SHA =
            "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val EVENT_HASH =
            "evsha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
