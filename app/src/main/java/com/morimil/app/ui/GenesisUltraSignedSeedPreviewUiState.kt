package com.morimil.app.ui

import com.morimil.app.data.genesis.ultra.GenesisUltraSignedSeedCandidatePreview

internal data class GenesisUltraSignedSeedPreviewUiState(
    val importing: Boolean = false,
    val preview: GenesisUltraSignedSeedCandidatePreview? = null,
    val errorMessage: String? = null
) {
    init {
        require(!importing || (preview == null && errorMessage == null)) {
            "seed_preview_importing_state_invalid"
        }
        require(preview == null || errorMessage == null) {
            "seed_preview_result_state_invalid"
        }
    }
}
