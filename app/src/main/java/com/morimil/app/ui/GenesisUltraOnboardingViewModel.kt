package com.morimil.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.morimil.app.MorimilAppContainer
import com.morimil.app.data.genesis.ultra.GenesisUltraCompanionNamePolicy
import com.morimil.app.data.genesis.ultra.GenesisUltraCompanionNameValidation
import com.morimil.app.data.genesis.ultra.GenesisUltraSignedSeedPreviewCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Pre-birth inspection and candidate preview only. This ViewModel can verify a
 * user-selected signed Seed and construct an ephemeral candidate summary. It
 * cannot persist the candidate, record consent, accept Guardian testimony,
 * authorize birth or execute the atomic transaction.
 */
class GenesisUltraOnboardingViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val container = MorimilAppContainer.from(application)
    private val preparationCoordinator = container.genesisUltraBirthPreparationCoordinator
    private val signedSeedPreviewCoordinator = GenesisUltraSignedSeedPreviewCoordinator(
        context = application,
        preparationCoordinator = preparationCoordinator,
        guardianTrustAnchorStore = container.genesisUltraGuardianTrustAnchorStore,
        candidateConstructionCoordinator = container.genesisUltraBirthCandidateConstructionCoordinator
    )

    private val _state = MutableStateFlow(GenesisUltraOnboardingUiStateMapper.loading())
    internal val state: StateFlow<GenesisUltraOnboardingUiState> = _state.asStateFlow()

    private val _signedSeedPreview = MutableStateFlow(GenesisUltraSignedSeedPreviewUiState())
    internal val signedSeedPreview: StateFlow<GenesisUltraSignedSeedPreviewUiState> =
        _signedSeedPreview.asStateFlow()

    init {
        refresh()
    }

    internal fun refresh() {
        clearSignedSeedPreview()
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = GenesisUltraOnboardingUiStateMapper.loading()
            _state.value = runCatching { preparationCoordinator.inspect() }
                .fold(
                    onSuccess = GenesisUltraOnboardingUiStateMapper::from,
                    onFailure = GenesisUltraOnboardingUiStateMapper::failure
                )
        }
    }

    internal fun validateCanonicalCompanionName(
        value: String
    ): GenesisUltraCompanionNameValidation {
        return GenesisUltraCompanionNamePolicy.validate(value)
    }

    internal fun previewSignedSeed(uri: Uri, companionName: String) {
        if (_signedSeedPreview.value.importing) return
        val validation = GenesisUltraCompanionNamePolicy.validate(companionName)
        if (!validation.isValid) {
            _signedSeedPreview.value = GenesisUltraSignedSeedPreviewUiState(
                errorMessage = validation.errorCode ?: "companion_name_invalid"
            )
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _signedSeedPreview.value = GenesisUltraSignedSeedPreviewUiState(importing = true)
            _signedSeedPreview.value = runCatching {
                signedSeedPreviewCoordinator.preview(
                    uri = uri,
                    companionName = requireNotNull(validation.canonicalName)
                )
            }.fold(
                onSuccess = { preview -> GenesisUltraSignedSeedPreviewUiState(preview = preview) },
                onFailure = { error ->
                    GenesisUltraSignedSeedPreviewUiState(
                        errorMessage = error.message?.take(220) ?: error::class.java.simpleName
                    )
                }
            )
        }
    }

    internal fun clearSignedSeedPreview() {
        _signedSeedPreview.value = GenesisUltraSignedSeedPreviewUiState()
    }
}
