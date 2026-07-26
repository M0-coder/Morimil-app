package com.morimil.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
internal fun ConversationMemoryPromotionDialog(
    state: ConversationMemoryPromotionUiState,
    onApprove: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        ConversationMemoryPromotionUiState.Idle -> Unit
        is ConversationMemoryPromotionUiState.Loading -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text("Preparando candidato") },
                text = {
                    Text(
                        "Se está verificando el transcript y la cadena canónica. " +
                            "Todavía no se ha escrito memoria."
                    )
                }
            )
        }
        is ConversationMemoryPromotionUiState.Preview -> {
            val preview = state.value
            AlertDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    Button(onClick = onApprove) { Text("Aprobar y firmar") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                },
                title = { Text("Vista previa de memoria") },
                text = {
                    Column {
                        Text(
                            "El transcript no es memoria por defecto. Esta acción confirma " +
                                "el candidato exacto como Guardian y permite que el Body lo firme."
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Clasificación: ${preview.classification}")
                        Text("Autor original: ${preview.sourceAuthor}")
                        Text("Turno: ${preview.sourceTurnId}")
                        Spacer(Modifier.height(10.dp))
                        Text(preview.content, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            preview.candidateDigest,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            )
        }
        is ConversationMemoryPromotionUiState.Promoting -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text("Firmando memoria") },
                text = {
                    Text(
                        "La aprobación ya fue consumida. El Body está firmando y " +
                            "verificando el evento dentro de la cadena canónica."
                    )
                }
            )
        }
        is ConversationMemoryPromotionUiState.Promoted -> {
            val receipt = state.receipt
            AlertDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                },
                title = { Text("Memoria firmada") },
                text = {
                    Column {
                        Text("El evento fue verificado en la cadena Genesis Ultra.")
                        Spacer(Modifier.height(8.dp))
                        Text("Secuencia: ${receipt.sequence}")
                        Text("Body: ${receipt.bodyId}")
                        Text("Guardian: ${receipt.guardianId}")
                        Text(
                            receipt.eventHash,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            )
        }
        is ConversationMemoryPromotionUiState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                },
                title = { Text("Promoción rechazada") },
                text = { Text(state.message) }
            )
        }
    }
}
