package com.morimil.app.ui

import com.morimil.app.data.genesis.ultra.GenesisUltraHostBirthConsentState
import com.morimil.app.data.genesis.ultra.GenesisUltraVerifiedHostBirthConsent

internal data class GenesisUltraHostBirthConsentSummary(
    val consentId: String,
    val candidateDigest: String,
    val instanceId: String,
    val companionName: String,
    val consentDigest: String,
    val consentedAt: String,
    val expiresAt: String
) {
    val birthCommitAuthorized: Boolean = false

    init {
        require(consentId.startsWith("consent_")) { "host_consent_ui_consent_id_invalid" }
        require(candidateDigest.startsWith("sha256:")) { "host_consent_ui_candidate_digest_invalid" }
        require(instanceId.startsWith("inst_")) { "host_consent_ui_instance_id_invalid" }
        require(companionName.isNotBlank()) { "host_consent_ui_companion_name_invalid" }
        require(consentDigest.startsWith("sha256:")) { "host_consent_ui_digest_invalid" }
        require(!birthCommitAuthorized) { "host_consent_ui_cannot_authorize_birth" }
    }

    companion object {
        fun from(consent: GenesisUltraVerifiedHostBirthConsent): GenesisUltraHostBirthConsentSummary {
            return GenesisUltraHostBirthConsentSummary(
                consentId = consent.consentId,
                candidateDigest = consent.candidateDigest,
                instanceId = consent.instanceId,
                companionName = consent.companionName,
                consentDigest = consent.consentDigest,
                consentedAt = consent.consentedAt,
                expiresAt = consent.expiresAt
            )
        }
    }
}

internal data class GenesisUltraHostBirthConsentUiState(
    val checking: Boolean = false,
    val recording: Boolean = false,
    val revoking: Boolean = false,
    val persistedState: GenesisUltraHostBirthConsentState = GenesisUltraHostBirthConsentState.ABSENT,
    val summary: GenesisUltraHostBirthConsentSummary? = null,
    val candidateSessionAvailable: Boolean = false,
    val errorMessage: String? = null
) {
    val busy: Boolean
        get() = checking || recording || revoking

    val hasPersistedConsent: Boolean
        get() = persistedState == GenesisUltraHostBirthConsentState.READY ||
            persistedState == GenesisUltraHostBirthConsentState.EXPIRED

    init {
        require(listOf(checking, recording, revoking).count { it } <= 1) {
            "host_consent_ui_multiple_operations"
        }
        require(summary == null || persistedState == GenesisUltraHostBirthConsentState.READY) {
            "host_consent_ui_summary_requires_ready_state"
        }
        require(summary == null || candidateSessionAvailable) {
            "host_consent_ui_summary_requires_candidate_session"
        }
        require(!busy || errorMessage == null) {
            "host_consent_ui_busy_state_cannot_have_error"
        }
    }
}
