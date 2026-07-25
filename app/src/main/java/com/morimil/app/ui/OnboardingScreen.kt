package com.morimil.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morimil.app.data.genesis.ultra.GenesisUltraSignedSeedCandidatePreview

@Composable
internal fun OnboardingScreen(viewModel: GenesisUltraOnboardingViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedSeedState by viewModel.signedSeedPreview.collectAsStateWithLifecycle()
    var companionName by remember { mutableStateOf("") }
    val nameValidation = viewModel.validateCanonicalCompanionName(companionName)
    val signedSeedLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.previewSignedSeed(uri, companionName)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFF4F2EA),
                        Color(0xFFE9EEDF)
                    )
                )
            )
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "&",
                style = MaterialTheme.typography.displayLarge,
                color = Color(0xFF11140F),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Nombra a tu compañero",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF1C1B17),
                textAlign = TextAlign.Center
            )
            Text(
                text = "El nombre canónico formará parte de su identidad Genesis Ultra. " +
                    "No es un alias del teléfono y no crea el nacimiento por sí solo.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF44483F),
                textAlign = TextAlign.Center
            )

            StatusCard(
                title = state.title,
                detail = state.detail,
                status = state.preparationStatus?.name ?: "LOADING"
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                color = Color(0xFFFFFBF4),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                TextField(
                    value = companionName,
                    onValueChange = { next ->
                        if (next.length <= 128 && next.none(Char::isISOControl)) {
                            companionName = next
                            viewModel.clearSignedSeedPreview()
                        }
                    },
                    enabled = state.canonicalNameInputEnabled && !signedSeedState.importing,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Nombre canónico del compañero",
                            color = Color(0xFF8B8A82)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(32.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1C1B17),
                        unfocusedTextColor = Color(0xFF1C1B17),
                        disabledTextColor = Color(0xFF77766F),
                        cursorColor = Color(0xFF245C37),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
            }

            if (companionName.isNotEmpty() && !nameValidation.isValid) {
                Text(
                    text = companionNameError(nameValidation.errorCode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            if (state.blockers.isNotEmpty()) {
                Text(
                    text = "Bloqueos: ${state.blockers.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8A4B0F),
                    textAlign = TextAlign.Center
                )
            }

            if (state.remainingRequirements.isNotEmpty()) {
                Text(
                    text = "Pendiente: ${state.remainingRequirements.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF44483F),
                    textAlign = TextAlign.Center
                )
            }

            OutlinedButton(
                enabled = state.candidateConstructionReady &&
                    nameValidation.isValid &&
                    !signedSeedState.importing,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    signedSeedLauncher.launch(
                        arrayOf(
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream"
                        )
                    )
                }
            ) {
                Text(
                    when {
                        signedSeedState.importing -> "Verificando Seed firmado"
                        signedSeedState.preview != null -> "Verificar otro Seed firmado"
                        else -> "Seleccionar Seed firmado (.zip)"
                    }
                )
            }

            signedSeedState.preview?.let { preview ->
                SignedSeedPreviewCard(preview)
            }

            signedSeedState.errorMessage?.let { message ->
                Text(
                    text = "Seed rechazado: $message",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Button(
                enabled = false,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFBA7517),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0x33BA7517),
                    disabledContentColor = Color(0x88FFFFFF)
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
                onClick = { }
            ) {
                Text(
                    text = when {
                        state.loading -> "Revisando preparación"
                        !nameValidation.isValid -> "Confirma un nombre canónico"
                        signedSeedState.preview != null ->
                            "Candidato verificado; consentimiento aún bloqueado"
                        state.candidateConstructionReady -> "Selecciona y verifica un Seed firmado"
                        else -> "Nacimiento Genesis Ultra bloqueado"
                    },
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            OutlinedButton(
                enabled = !state.loading && !signedSeedState.importing,
                modifier = Modifier.fillMaxWidth(),
                onClick = viewModel::refresh
            ) {
                Text("Revisar estado local")
            }

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = "LOCAL / PRIVATE / GENESIS ULTRA",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFF44483F),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatusCard(title: String, detail: String, status: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFFBF4)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall)
            Text(
                status,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFF245C37)
            )
        }
    }
}

@Composable
private fun SignedSeedPreviewCard(preview: GenesisUltraSignedSeedCandidatePreview) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFEAF3E7)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Candidato firmado verificado", style = MaterialTheme.typography.titleMedium)
            Text("Compañero: ${preview.companionName}", style = MaterialTheme.typography.bodySmall)
            Text("Seed: ${preview.seedId}", style = MaterialTheme.typography.bodySmall)
            Text("Archivos verificados: ${preview.verifiedFileCount}", style = MaterialTheme.typography.bodySmall)
            Text("Guardián: ${preview.guardianId}", style = MaterialTheme.typography.bodySmall)
            Text("Instance: ${preview.instanceId}", style = MaterialTheme.typography.bodySmall)
            Text("Body: ${preview.bodyId}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Root: ${preview.seedRootHash}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
            )
            Text(
                "Candidate: ${preview.candidateDigest}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
            )
            Text(
                "Vista previa efímera: no es consentimiento, testimonio ni nacimiento.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF245C37)
            )
        }
    }
}

private fun companionNameError(errorCode: String?): String {
    return when (errorCode) {
        "companion_name_not_nfc" -> "El nombre debe usar una forma Unicode canónica."
        "companion_name_has_outer_whitespace" -> "Quita los espacios del inicio o del final."
        "companion_name_length_invalid" -> "El nombre debe tener entre 1 y 128 caracteres."
        "companion_name_control_character" -> "El nombre contiene caracteres de control."
        else -> "El nombre todavía no es canónico."
    }
}
