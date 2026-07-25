package com.morimil.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.morimil.app.MorimilAppContainer
import com.morimil.app.data.genesis.ultra.GenesisUltraCompanionNamePolicy
import com.morimil.app.data.genesis.ultra.GenesisUltraCompanionNameValidation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Pre-birth inspection only. This ViewModel cannot construct a candidate,
 * record consent, accept Guardian evidence or execute the atomic birth.
 */
internal class GenesisUltraOnboardingViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val preparationCoordinator =
        MorimilAppContainer.from(application).genesisUltraBirthPreparationCoordinator

    private val _state = MutableStateFlow(GenesisUltraOnboardingUiStateMapper.loading())
    val state: StateFlow<GenesisUltraOnboardingUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = GenesisUltraOnboardingUiStateMapper.loading()
            _state.value = runCatching { preparationCoordinator.inspect() }
                .fold(
                    onSuccess = GenesisUltraOnboardingUiStateMapper::from,
                    onFailure = GenesisUltraOnboardingUiStateMapper::failure
                )
        }
    }

    fun validateCanonicalCompanionName(
        value: String
    ): GenesisUltraCompanionNameValidation {
        return GenesisUltraCompanionNamePolicy.validate(value)
    }
}
