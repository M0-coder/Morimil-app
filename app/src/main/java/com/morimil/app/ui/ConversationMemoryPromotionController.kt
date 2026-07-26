package com.morimil.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.morimil.app.MorimilAppContainer
import com.morimil.app.conversationMemoryPromotionCoordinator
import com.morimil.app.data.genesis.ultra.ConversationMemoryPromotionCoordinator
import com.morimil.app.data.genesis.ultra.ConversationMemoryPromotionPreview
import com.morimil.app.data.genesis.ultra.ConversationMemoryPromotionReceipt
import com.morimil.app.data.local.ReasoningTurnAuthor
import com.morimil.app.data.local.ReasoningTurnEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface ConversationMemoryPromotionUiState {
    data object Idle : ConversationMemoryPromotionUiState
    data class Loading(val turnId: Long) : ConversationMemoryPromotionUiState
    data class Preview(val value: ConversationMemoryPromotionPreview) :
        ConversationMemoryPromotionUiState
    data class Promoting(val value: ConversationMemoryPromotionPreview) :
        ConversationMemoryPromotionUiState
    data class Promoted(val receipt: ConversationMemoryPromotionReceipt) :
        ConversationMemoryPromotionUiState
    data class Error(
        val message: String,
        val preview: ConversationMemoryPromotionPreview? = null
    ) : ConversationMemoryPromotionUiState
}

internal class ConversationMemoryPromotionController(
    private val coordinator: ConversationMemoryPromotionCoordinator,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<ConversationMemoryPromotionUiState>(
        ConversationMemoryPromotionUiState.Idle
    )
    val state: StateFlow<ConversationMemoryPromotionUiState> = _state.asStateFlow()

    fun requestPreview(turn: ReasoningTurnEntity) {
        if (!ReasoningTurnAuthor.isTrustedConversationAuthor(turn.author)) {
            _state.value = ConversationMemoryPromotionUiState.Error(
                "Las salidas auxiliares no pueden convertirse en memoria."
            )
            return
        }
        if (_state.value is ConversationMemoryPromotionUiState.Loading ||
            _state.value is ConversationMemoryPromotionUiState.Promoting
        ) {
            return
        }
        _state.value = ConversationMemoryPromotionUiState.Loading(turn.id)
        scope.launch {
            _state.value = runCatching { coordinator.preview(turn) }
                .fold(
                    onSuccess = ConversationMemoryPromotionUiState::Preview,
                    onFailure = { error ->
                        ConversationMemoryPromotionUiState.Error(
                            error.message ?: "No se pudo preparar la vista previa."
                        )
                    }
                )
        }
    }

    fun approveCurrent() {
        val preview = when (val current = _state.value) {
            is ConversationMemoryPromotionUiState.Preview -> current.value
            is ConversationMemoryPromotionUiState.Error -> current.preview
            else -> null
        } ?: return
        _state.value = ConversationMemoryPromotionUiState.Promoting(preview)
        scope.launch {
            _state.value = runCatching {
                coordinator.approve(
                    previewId = preview.previewId,
                    expectedCandidateDigest = preview.candidateDigest
                )
            }.fold(
                onSuccess = ConversationMemoryPromotionUiState::Promoted,
                onFailure = { error ->
                    ConversationMemoryPromotionUiState.Error(
                        message = error.message ?: "La promoción fue rechazada.",
                        preview = null
                    )
                }
            )
        }
    }

    fun dismiss() {
        val previewId = when (val current = _state.value) {
            is ConversationMemoryPromotionUiState.Preview -> current.value.previewId
            is ConversationMemoryPromotionUiState.Promoting -> current.value.previewId
            is ConversationMemoryPromotionUiState.Error -> current.preview?.previewId
            else -> null
        }
        _state.value = ConversationMemoryPromotionUiState.Idle
        if (previewId != null) {
            scope.launch { coordinator.dismiss(previewId) }
        }
    }
}

@Composable
internal fun rememberConversationMemoryPromotionController():
    ConversationMemoryPromotionController {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val coordinator = remember(context) {
        MorimilAppContainer.from(context).conversationMemoryPromotionCoordinator
    }
    return remember(coordinator, scope) {
        ConversationMemoryPromotionController(coordinator = coordinator, scope = scope)
    }
}
