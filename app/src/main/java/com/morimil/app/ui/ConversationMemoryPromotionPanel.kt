package com.morimil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morimil.app.data.local.ReasoningTurnAuthor
import com.morimil.app.data.local.ReasoningTurnEntity

@Composable
internal fun ConversationMemoryPromotionPanel(messages: List<ReasoningTurnEntity>) {
    val controller = rememberConversationMemoryPromotionController()
    val state by controller.state.collectAsStateWithLifecycle()
    val candidate = messages.lastOrNull { turn ->
        ReasoningTurnAuthor.isTrustedConversationAuthor(turn.author) &&
            turn.id > 0L &&
            turn.body.isNotBlank()
    }
    val busy = state is ConversationMemoryPromotionUiState.Loading ||
        state is ConversationMemoryPromotionUiState.Promoting

    if (candidate != null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Transcript ≠ memoria",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        "Último turno confiable: ${candidate.author}. " +
                            candidate.body.replace('\n', ' ').take(90),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    enabled = !busy,
                    onClick = { controller.requestPreview(candidate) }
                ) {
                    Text("Proponer como memoria")
                }
            }
        }
    }

    ConversationMemoryPromotionDialog(
        state = state,
        onApprove = controller::approveCurrent,
        onDismiss = controller::dismiss
    )
}
