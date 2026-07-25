package com.morimil.app.ui

import com.morimil.app.data.genesis.ultra.GenesisUltraAuthorizedAtomicBirth

internal data class GenesisUltraAtomicBirthAuthorizationSummary(
    val candidateDigest: String,
    val consentDigest: String,
    val birthStateDigest: String,
    val receiptDigest: String,
    val authorizationDigest: String,
    val authorizedAt: String,
    val expiresAt: String
) {
    val birthCommitAuthorized: Boolean = true

    init {
        require(SHA256_REF.matches(candidateDigest)) { "atomic_authorization_ui_candidate_digest_invalid" }
        require(SHA256_REF.matches(consentDigest)) { "atomic_authorization_ui_consent_digest_invalid" }
        require(SHA256_REF.matches(birthStateDigest)) { "atomic_authorization_ui_state_digest_invalid" }
        require(SHA256_REF.matches(receiptDigest)) { "atomic_authorization_ui_receipt_digest_invalid" }
        require(SHA256_REF.matches(authorizationDigest)) { "atomic_authorization_ui_digest_invalid" }
        require(birthCommitAuthorized) { "atomic_authorization_ui_not_authorized" }
    }

    companion object {
        private val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")

        fun from(
            authorization: GenesisUltraAuthorizedAtomicBirth
        ): GenesisUltraAtomicBirthAuthorizationSummary {
            return GenesisUltraAtomicBirthAuthorizationSummary(
                candidateDigest = authorization.candidateDigest,
                consentDigest = authorization.consentDigest,
                birthStateDigest = authorization.birthStateDigest,
                receiptDigest = authorization.receiptDigest,
                authorizationDigest = authorization.authorizationDigest,
                authorizedAt = authorization.authorizedAt,
                expiresAt = authorization.expiresAt
            )
        }
    }
}

internal data class GenesisUltraAtomicBirthAuthorizationUiState(
    val verifying: Boolean = false,
    val summary: GenesisUltraAtomicBirthAuthorizationSummary? = null,
    val expired: Boolean = false,
    val errorMessage: String? = null
) {
    val authorizedInMemory: Boolean
        get() = summary != null && !expired

    init {
        require(!verifying || summary == null) {
            "atomic_authorization_ui_verifying_with_summary"
        }
        require(!verifying || errorMessage == null) {
            "atomic_authorization_ui_verifying_with_error"
        }
        require(!expired || summary == null) {
            "atomic_authorization_ui_expired_with_summary"
        }
    }
}
