package com.morimil.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Checkbox
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
import com.morimil.app.data.genesis.ultra.GenesisUltraHostBirthConsentState
import com.morimil.app.data.genesis.ultra.GenesisUltraSignedSeedCandidatePreview

@Composable
internal fun OnboardingScreen(viewModel: GenesisUltraOnboardingViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedSeedState by viewModel.signedSeedPreview.collectAsStateWithLifecycle()
    val consentState by viewModel.hostBirthConsent.collectAsStateWithLifecycle()
    val authorizationState by viewModel.atomicBirthAuthorization.collectAsStateWithLifecycle()
    var companionName by remember { mutableStateOf("") }
    var confirmationCodeInput by remember { mutableStateOf("") }
    var userPresenceConfirmed by remember { mutableStateOf(false) }
    val nameValidation = viewModel.validateCanonicalCompanionName(companionName)
    val interactionLocked = signedSeedState.importing || consentState.busy ||
        consentState.hasPersistedConsent || authorizationState.verifying ||
        authorizationState.authorizedInMemory

    val signedSeedLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            confirmationCodeInput = ""
            userPresenceConfirmed = false
            viewModel.previewSignedSeed(uri, companionName)
        }
    }
    val witnessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.authorizeWitnessArchive(uri)
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
                            confirmationCodeInput = ""
                            userPresenceConfirmed = false
                            viewModel.clearSignedSeedPreview()
                        }
                    },
                    enabled = state.canonicalNameInputEnabled && !interactionLocked,
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
                    !interactionLocked &&
                    consentState.persistedState == GenesisUltraHostBirthConsentState.ABSENT,
                modifier = Modifier.fillMaxWidth(),
                onClick = { signedSeedLauncher.launch(ZIP_MIME_TYPES) }
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
                SignedSeedPreviewCard(
                    preview = preview,
                    expiresAt = requireNotNull(signedSeedState.sessionExpiresAt)
                )
            }
            signedSeedState.errorMessage?.let { message ->
                ErrorText("Seed rechazado: $message")
            }

            if (signedSeedState.sessionAvailable && !consentState.hasPersistedConsent) {
                ConsentCeremonyCard(
                    expectedCode = requireNotNull(signedSeedState.confirmationCode),
                    sessionExpiresAt = requireNotNull(signedSeedState.sessionExpiresAt),
                    confirmationCodeInput = confirmationCodeInput,
                    userPresenceConfirmed = userPresenceConfirmed,
                    busy = consentState.busy || authorizationState.verifying,
                    onCodeChanged = { next ->
                        if (next.length <= 12 && next.all { it in '0'..'9' || it in 'a'..'f' }) {
                            confirmationCodeInput = next
                        }
                    },
                    onPresenceChanged = { userPresenceConfirmed = it },
                    onConfirm = {
                        viewModel.recordExplicitHostConsent(
                            presentedConfirmationCode = confirmationCodeInput,
                            userPresenceConfirmed = userPresenceConfirmed
                        )
                    }
                )
            }

            if (consentState.hasPersistedConsent) {
                PersistedConsentCard(
                    state = consentState,
                    authorizationVerifying = authorizationState.verifying,
                    onRevoke = {
                        confirmationCodeInput = ""
                        userPresenceConfirmed = false
                        viewModel.revokeHostConsent()
                    }
                )
            }
            consentState.errorMessage?.let { message ->
                ErrorText("Consentimiento rechazado: $message")
            }

            if (
                consentState.summary != null &&
                signedSeedState.sessionAvailable &&
                !authorizationState.authorizedInMemory
            ) {
                OutlinedButton(
                    enabled = !authorizationState.verifying &&
                        !consentState.busy &&
                        consentState.persistedState == GenesisUltraHostBirthConsentState.READY,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { witnessLauncher.launch(ZIP_MIME_TYPES) }
                ) {
                    Text(
                        if (authorizationState.verifying) {
                            "Verificando testimonio Body y Guardián"
                        } else {
                            "Seleccionar testimonio final (.zip)"
                        }
                    )
                }
            }

            authorizationState.summary?.let { summary ->
                AtomicAuthorizationCard(summary)
            }
            authorizationState.errorMessage?.let { message ->
                ErrorText("Testimonio rechazado: $message")
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
                        authorizationState.authorizedInMemory ->
                            "Autorización verificada; ejecución aún bloqueada"
                        authorizationState.verifying -> "Verificando evidencia final"
                        consentState.summary != null ->
                            "Consentimiento registrado; verifica el testimonio final"
                        consentState.hasPersistedConsent ->
                            "Consentimiento previo sin candidato; revócalo para continuar"
                        signedSeedState.sessionAvailable -> "Confirma el candidato exacto"
                        state.candidateConstructionReady -> "Selecciona y verifica un Seed firmado"
                        else -> "Nacimiento Genesis Ultra bloqueado"
                    },
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            OutlinedButton(
                enabled = !state.loading && !interactionLocked,
                modifier = Modifier.fillMaxWidth(),
                onClick = viewModel::refresh
            ) {
                Text("Revisar estado local")
            }

            state.errorMessage?.let(::ErrorText)
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
private fun SignedSeedPreviewCard(
    preview: GenesisUltraSignedSeedCandidatePreview,
    expiresAt: String
) {
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
            MonospaceText("Root: ${preview.seedRootHash}")
            MonospaceText("Candidate: ${preview.candidateDigest}")
            Text("Sesión válida hasta: $expiresAt", style = MaterialTheme.typography.bodySmall)
            Text(
                "El candidato completo existe solo en memoria y aún no está autorizado.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF245C37)
            )
        }
    }
}

@Composable
private fun ConsentCeremonyCard(
    expectedCode: String,
    sessionExpiresAt: String,
    confirmationCodeInput: String,
    userPresenceConfirmed: Boolean,
    busy: Boolean,
    onCodeChanged: (String) -> Unit,
    onPresenceChanged: (Boolean) -> Unit,
    onConfirm: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFF4DE)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Consentimiento exacto", style = MaterialTheme.typography.titleMedium)
            Text(
                "Comprueba el candidato mostrado. Este consentimiento quedará ligado solo a ese digest.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Código: $expectedCode",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
            )
            Text(
                "Escribe el código exactamente antes de $sessionExpiresAt.",
                style = MaterialTheme.typography.bodySmall
            )
            TextField(
                value = confirmationCodeInput,
                onValueChange = onCodeChanged,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Código de 12 caracteres") },
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = userPresenceConfirmed,
                    onCheckedChange = onPresenceChanged,
                    enabled = !busy
                )
                Text(
                    "Estoy presente y apruebo este candidato exacto.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                enabled = !busy && userPresenceConfirmed && confirmationCodeInput == expectedCode,
                modifier = Modifier.fillMaxWidth(),
                onClick = onConfirm
            ) {
                Text(if (busy) "Registrando consentimiento" else "Registrar consentimiento exacto")
            }
            Text(
                "Esto no autoriza ni ejecuta el nacimiento.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8A4B0F)
            )
        }
    }
}

@Composable
private fun PersistedConsentCard(
    state: GenesisUltraHostBirthConsentUiState,
    authorizationVerifying: Boolean,
    onRevoke: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFE9E7F4)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val summary = state.summary
            Text(
                if (summary != null) "Consentimiento registrado" else "Consentimiento previo detectado",
                style = MaterialTheme.typography.titleMedium
            )
            if (summary != null) {
                Text("Compañero: ${summary.companionName}", style = MaterialTheme.typography.bodySmall)
                Text("Instance: ${summary.instanceId}", style = MaterialTheme.typography.bodySmall)
                Text("Válido hasta: ${summary.expiresAt}", style = MaterialTheme.typography.bodySmall)
                MonospaceText("Consent: ${summary.consentDigest}")
            } else {
                Text(
                    "El proceso anterior terminó y el candidato exacto ya no está en memoria. " +
                        "Debe revocarse este consentimiento antes de importar otro Seed.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            MonospaceText("Estado: ${state.persistedState}")
            OutlinedButton(
                enabled = !state.busy && !authorizationVerifying &&
                    state.persistedState != GenesisUltraHostBirthConsentState.INCONSISTENT,
                modifier = Modifier.fillMaxWidth(),
                onClick = onRevoke
            ) {
                Text(if (state.revoking) "Revocando" else "Revocar consentimiento antes del nacimiento")
            }
            MonospaceText("birthCommitAuthorized = false", Color(0xFF245C37))
        }
    }
}

@Composable
private fun AtomicAuthorizationCard(summary: GenesisUltraAtomicBirthAuthorizationSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFE3EEF5)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Autorización atómica verificada", style = MaterialTheme.typography.titleMedium)
            MonospaceText("Candidate: ${summary.candidateDigest}")
            MonospaceText("Consent: ${summary.consentDigest}")
            MonospaceText("Birth state: ${summary.birthStateDigest}")
            MonospaceText("Receipt: ${summary.receiptDigest}")
            MonospaceText("Authorization: ${summary.authorizationDigest}")
            Text("Autorizada: ${summary.authorizedAt}", style = MaterialTheme.typography.bodySmall)
            Text("Expira: ${summary.expiresAt}", style = MaterialTheme.typography.bodySmall)
            MonospaceText("birthCommitAuthorized = true", Color(0xFF245C37))
            Text(
                "La autorización existe solo en memoria. La ejecución y el runtime continúan desconectados.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8A4B0F)
            )
        }
    }
}

@Composable
private fun MonospaceText(text: String, color: Color = Color.Unspecified) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = color
    )
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center
    )
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

private val ZIP_MIME_TYPES = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/octet-stream"
)
