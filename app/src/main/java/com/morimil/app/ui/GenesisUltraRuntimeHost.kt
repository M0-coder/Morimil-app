package com.morimil.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.morimil.app.MorimilAppContainer
import com.morimil.app.genesisUltraRuntimeStartupGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class GenesisUltraRuntimeHostStatus {
    VERIFYING,
    READY,
    BLOCKED
}

internal data class GenesisUltraRuntimeHostUiState(
    val status: GenesisUltraRuntimeHostStatus = GenesisUltraRuntimeHostStatus.VERIFYING,
    val companionName: String? = null,
    val instanceId: String? = null,
    val errorMessage: String? = null
)

internal class GenesisUltraRuntimeGateViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val gate = MorimilAppContainer.from(application).genesisUltraRuntimeStartupGate
    private val _state = MutableStateFlow(GenesisUltraRuntimeHostUiState())
    val state: StateFlow<GenesisUltraRuntimeHostUiState> = _state.asStateFlow()

    init {
        inspectGate()
    }

    fun verify() {
        if (_state.value.status == GenesisUltraRuntimeHostStatus.VERIFYING) return
        inspectGate()
    }

    private fun inspectGate() {
        _state.value = GenesisUltraRuntimeHostUiState()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { gate.requireReady() }.fold(
                onSuccess = { identity ->
                    _state.value = GenesisUltraRuntimeHostUiState(
                        status = GenesisUltraRuntimeHostStatus.READY,
                        companionName = identity.companionName,
                        instanceId = identity.instanceId
                    )
                },
                onFailure = { error ->
                    _state.value = GenesisUltraRuntimeHostUiState(
                        status = GenesisUltraRuntimeHostStatus.BLOCKED,
                        errorMessage = error.message?.take(220)
                            ?: "genesis_ultra_runtime_gate_failed"
                    )
                }
            )
        }
    }
}

@Composable
internal fun GenesisUltraRuntimeHost(
    gateViewModel: GenesisUltraRuntimeGateViewModel = viewModel()
) {
    val state by gateViewModel.state.collectAsStateWithLifecycle()
    when (state.status) {
        GenesisUltraRuntimeHostStatus.READY -> {
            // Construction occurs only after the canonical identity was recovered and verified.
            val runtimeViewModel: MorimilViewModel = viewModel()
            MainTabsScaffold(runtimeViewModel)
        }
        GenesisUltraRuntimeHostStatus.VERIFYING -> {
            RuntimeGateMessage(
                title = "Verificando Genesis Ultra",
                detail = "Recuperando identidad, Body, Guardian y autorización comprometida."
            )
        }
        GenesisUltraRuntimeHostStatus.BLOCKED -> {
            RuntimeGateMessage(
                title = "Runtime bloqueado",
                detail = state.errorMessage
                    ?: "La identidad Genesis Ultra no pudo verificarse.",
                retry = gateViewModel::verify
            )
        }
    }
}

@Composable
private fun RuntimeGateMessage(
    title: String,
    detail: String,
    retry: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(detail, style = MaterialTheme.typography.bodyMedium)
        retry?.let { action ->
            Button(onClick = action) {
                Text("Reintentar verificación")
            }
        }
    }
}
