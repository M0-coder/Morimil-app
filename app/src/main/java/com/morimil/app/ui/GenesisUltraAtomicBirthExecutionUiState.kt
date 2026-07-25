package com.morimil.app.ui

import com.morimil.app.data.genesis.ultra.GenesisUltraAtomicBirthExecutionCeremonyResult
import com.morimil.app.data.genesis.ultra.GenesisUltraAtomicBirthExecutionOutcome

internal data class GenesisUltraAtomicBirthExecutionSummary(
    val outcome: GenesisUltraAtomicBirthExecutionOutcome,
    val birthId: String,
    val instanceId: String,
    val companionName: String,
    val authorizationDigest: String,
    val birthStateDigest: String,
    val receiptDigest: String,
    val firstPostBirthEventHash: String?,
    val committedAt: String,
    val maintenanceError: String?
) {
    val birthCommitted: Boolean = true

    init {
        require(birthId.isNotBlank()) { "birth_execution_ui_birth_id_invalid" }
        require(instanceId.isNotBlank()) { "birth_execution_ui_instance_id_invalid" }
        require(companionName.isNotBlank()) { "birth_execution_ui_companion_name_invalid" }
        require(SHA256_REF.matches(authorizationDigest)) {
            "birth_execution_ui_authorization_digest_invalid"
        }
        require(SHA256_REF.matches(birthStateDigest)) {
            "birth_execution_ui_state_digest_invalid"
        }
        require(SHA256_REF.matches(receiptDigest)) {
            "birth_execution_ui_receipt_digest_invalid"
        }
        require(firstPostBirthEventHash == null || EVENT_HASH.matches(firstPostBirthEventHash)) {
            "birth_execution_ui_event_hash_invalid"
        }
        when (outcome) {
            GenesisUltraAtomicBirthExecutionOutcome.COMMITTED_CLEAN -> {
                require(maintenanceError == null && firstPostBirthEventHash != null) {
                    "birth_execution_ui_clean_state_invalid"
                }
            }
            GenesisUltraAtomicBirthExecutionOutcome.COMMITTED_MAINTENANCE_PENDING -> {
                require(!maintenanceError.isNullOrBlank()) {
                    "birth_execution_ui_pending_error_missing"
                }
            }
        }
        require(birthCommitted) { "birth_execution_ui_not_committed" }
    }

    companion object {
        private val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
        private val EVENT_HASH = Regex("^evsha256:[a-f0-9]{64}$")

        fun from(
            result: GenesisUltraAtomicBirthExecutionCeremonyResult
        ): GenesisUltraAtomicBirthExecutionSummary {
            return GenesisUltraAtomicBirthExecutionSummary(
                outcome = result.outcome,
                birthId = result.birthId,
                instanceId = result.instanceId,
                companionName = result.companionName,
                authorizationDigest = result.authorizationDigest,
                birthStateDigest = result.birthStateDigest,
                receiptDigest = result.receiptDigest,
                firstPostBirthEventHash = result.firstPostBirthEventHash,
                committedAt = result.committedAt,
                maintenanceError = result.maintenanceError
            )
        }
    }
}

internal data class GenesisUltraAtomicBirthExecutionUiState(
    val executing: Boolean = false,
    val committed: GenesisUltraAtomicBirthExecutionSummary? = null,
    val errorMessage: String? = null
) {
    val birthCommitted: Boolean
        get() = committed != null

    val retryAllowed: Boolean
        get() = !executing && committed == null

    init {
        require(!executing || committed == null) {
            "birth_execution_ui_executing_with_commit"
        }
        require(!executing || errorMessage == null) {
            "birth_execution_ui_executing_with_error"
        }
        require(committed == null || errorMessage == null) {
            "birth_execution_ui_committed_with_retry_error"
        }
        require(!birthCommitted || !retryAllowed) {
            "birth_execution_ui_committed_cannot_retry"
        }
    }
}
