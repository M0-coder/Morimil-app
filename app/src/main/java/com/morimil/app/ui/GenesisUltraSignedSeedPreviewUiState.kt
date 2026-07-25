package com.morimil.app.ui

import com.morimil.app.data.genesis.ultra.GenesisUltraSignedSeedCandidatePreview

internal data class GenesisUltraSignedSeedPreviewUiState(
    val importing: Boolean = false,
    val preview: GenesisUltraSignedSeedCandidatePreview? = null,
    val confirmationCode: String? = null,
    val sessionExpiresAt: String? = null,
    val errorMessage: String? = null
) {
    val sessionAvailable: Boolean
        get() = preview != null && confirmationCode != null && sessionExpiresAt != null

    init {
        require(!importing ||
            (preview == null && confirmationCode == null && sessionExpiresAt == null &&
                errorMessage == null)
        ) { "seed_preview_importing_state_invalid" }
        require(preview == null || errorMessage == null) {
            "seed_preview_result_state_invalid"
        }
        require(
            listOf(preview, confirmationCode, sessionExpiresAt).all { value -> value == null } ||
                listOf(preview, confirmationCode, sessionExpiresAt).all { value -> value != null }
        ) { "seed_preview_session_metadata_incomplete" }
        require(confirmationCode == null || Regex("^[a-f0-9]{12}$").matches(confirmationCode)) {
            "seed_preview_confirmation_code_invalid"
        }
    }
}
