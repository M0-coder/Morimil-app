package com.morimil.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Compatibility entry point. The canonical chat surface is
 * [ChatScreenPolished], which enforces the intrinsic-trimotor boundary and
 * presents external provider output only as unverified advisory text.
 *
 * The promotion panel remains outside the transcript surface so merely showing
 * or generating a turn can never append it to canonical memory.
 */
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            ChatScreenPolished(viewModel)
        }
        ConversationMemoryPromotionPanel(messages = uiState.messages)
    }
}
