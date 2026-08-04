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
import com.morimil.app.data.genesis.ultra.GenesisUltraAtomicBirthExecutionCeremonyRequest
import com.morimil.app.data.genesis.ultra.GenesisUltraAtomicBirthExecutionOutcome
import com.morimil.app.data.genesis.ultra.GenesisUltraBirthPreparationStatus
import com.morimil.app.data.genesis.ultra.GenesisUltraBodyProvisioningReceipt
import com.morimil.app.data.genesis.ultra.GenesisUltraGuardianProvisioningReceipt
import com.morimil.app.data.genesis.ultra.GenesisUltraHostBirthConsentState
import com.morimil.app.data.genesis.ultra.GenesisUltraSignedSeedCandidatePreview

@Composable
internal fun OnboardingScreen(viewModel: GenesisUltraOnboardingViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val provisioningState by viewModel.preBirthProvisioning.collectAsStateWithLifecycle()
    val signedSeedState by viewModel.signedSeedPreview.collectAsStateWithLifecycle()
    val consentState by viewModel.hostBirthConsent.collectAsStateWithLifecycle()
    val authorizationState by viewModel.atomicBirthAuthorization.collectAsStateWithLifecycle()
    val executionState by viewModel.atomicBirthExecution.collectAsStateWithLifecycle()
    var companionName by remember { mutableStateOf("") }
    var bodyPresenceConfirmed by remember { mutableStateOf(false) }
    var guardianId by remember { mutableStateOf("") }
    var guardianKeyEpochId by remember { mutableStateOf("") }
    var guardianConfirmedFingerprint by remember { mutableStateOf("") }
    var guardianIndependentConfirmation by remember { mutableStateOf(false) }
    var guardianPresenceConfirmed by remember { mutableStateOf(false) }
    var consentCodeInput by remember { mutableStateOf("") }
    var consentPresenceConfirmed by remember { mutableStateOf(false) }
    var executionCodeInput by remember { mutableStateOf("") }
    var executionPresenceConfirmed by remember { mutableStateOf(false) }
    val nameValidation = viewModel.validateCanonicalCompanionName(companionName)
    val interactionLocked = provisioningState.busy || signedSeedState.importing || consentState.busy ||
        consentState.hasPersistedConsent || authorizationState.verifying ||
        authorizationState.authorizedInMemory || executionState.executing ||
        executionState.birthCommitted

    val signedSeedLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            consentCodeInput = ""
            consentPresenceConfirmed = false
            executionCodeInput = ""
            executionPresenceConfirmed = false
            viewModel.previewSignedSeed(uri, companionName)
        }
    }
    val guardianPublicKeyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            guardianConfirmedFingerprint = ""
            guardianIndependentConfirmation = false
            guardianPresenceConfirmed = false
            viewModel.previewGuardianPublicKey(uri)
        }
    }
    val witnessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            executionCodeInput = ""
            executionPresenceConfirmed = false
            viewModel.authorizeWitnessArchive(uri)
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

            val bodyReceipt = provisioningState.bodyReceipt
            if (bodyReceipt != null) {
                BodyProvisioningReceiptCard(receipt = bodyReceipt)
            }

            val guardianReceipt = provisioningState.guardianReceipt
            if (guardianReceipt != null) {
                GuardianProvisioningReceiptCard(receipt = guardianReceipt)
            }

            when (state.preparationStatus) {
                GenesisUltraBirthPreparationStatus.BODY_IDENTITY_REQUIRED ->
                    BodyProvisioningCard(
                        userPresenceConfirmed = bodyPresenceConfirmed,
                        busy = provisioningState.bodyProvisioning,
                        interactionLocked = interactionLocked,
                        onPresenceChanged = { bodyPresenceConfirmed = it },
                        onProvision = {
                            viewModel.provisionBodyIdentity(bodyPresenceConfirmed)
                        }
                    )

                GenesisUltraBirthPreparationStatus.GUARDIAN_TRUST_REQUIRED ->
                    GuardianProvisioningCard(
                        guardianId = guardianId,
                        keyEpochId = guardianKeyEpochId,
                        confirmedFingerprint = guardianConfirmedFingerprint,
                        importedFingerprint = provisioningState.guardianPublicKeyRef,
                        independentConfirmationAcknowledged =
                            guardianIndependentConfirmation,
                        userPresenceConfirmed = guardianPresenceConfirmed,
                        importing = provisioningState.guardianKeyImporting,
                        pinning = provisioningState.guardianPinning,
                        interactionLocked = interactionLocked,
                        onGuardianIdChanged = { guardianId = it },
                        onKeyEpochIdChanged = { guardianKeyEpochId = it },
                        onConfirmedFingerprintChanged = {
                            guardianConfirmedFingerprint = it
                        },
                        onIndependentConfirmationChanged = {
                            guardianIndependentConfirmation = it
                        },
                        onPresenceChanged = { guardianPresenceConfirmed = it },
                        onSelectKey = {
                            guardianPublicKeyLauncher.launch(GUARDIAN_PUBLIC_KEY_MIME_TYPES)
                        },
                        onClearKey = {
                            guardianConfirmedFingerprint = ""
                            guardianIndependentConfirmation = false
                            guardianPresenceConfirmed = false
                            viewModel.clearGuardianPublicKeyPreview()
                        },
                        onPin = {
                            viewModel.provisionGuardianTrustAnchor(
                                guardianId = guardianId,
                                keyEpochId = guardianKeyEpochId,
                                confirmedPublicKeyRef = guardianConfirmedFingerprint,
                                independentConfirmationAcknowledged =
                                    guardianIndependentConfirmation,
                                userPresenceConfirmed = guardianPresenceConfirmed
                            )
                        }
                    )

                else -> Unit
            }

            provisioningState.errorMessage?.let { message ->
                ErrorText("Aprovisionamiento rechazado: $message")
            }

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
                            consentCodeInput = ""
                            consentPresenceConfirmed = false
                            executionCodeInput = ""
                            executionPresenceConfirmed = false
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
                ErrorText(companionNameError(nameValidation.errorCode))
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
                    confirmationCodeInput = consentCodeInput,
                    userPresenceConfirmed = consentPresenceConfirmed,
                    busy = consentState.busy || authorizationState.verifying || executionState.executing,
                    onCodeChanged = { next ->
                        if (isConfirmationCodePrefix(next)) consentCodeInput = next
                    },
                    onPresenceChanged = { consentPresenceConfirmed = it },
                    onConfirm = {
                        viewModel.recordExplicitHostConsent(
                            presentedConfirmationCode = consentCodeInput,
                            userPresenceConfirmed = consentPresenceConfirmed
                        )
                    }
                )
            }

            if (consentState.hasPersistedConsent) {
                PersistedConsentCard(
                    state = consentState,
                    authorizationVerifying = authorizationState.verifying,
                    executionBusyOrCommitted = executionState.executing || executionState.birthCommitted,
                    onRevoke = {
                        consentCodeInput = ""
                        consentPresenceConfirmed = false
                        executionCodeInput = ""
                        executionPresenceConfirmed = false
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
                !authorizationState.authorizedInMemory &&
                !executionState.birthCommitted
            ) {
                OutlinedButton(
                    enabled = !authorizationState.verifying &&
                        !consentState.busy &&
                        !executionState.executing &&
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
                if (!executionState.birthCommitted) {
                    val expectedExecutionCode =
                        GenesisUltraAtomicBirthExecutionCeremonyRequest.confirmationCode(
                            summary.authorizationDigest
                        )
                    ExecutionCeremonyCard(
                        expectedCode = expectedExecutionCode,
                        expiresAt = summary.expiresAt,
                        confirmationCodeInput = executionCodeInput,
                        userPresenceConfirmed = executionPresenceConfirmed,
                        executing = executionState.executing,
                        onCodeChanged = { next ->
                            if (isConfirmationCodePrefix(next)) executionCodeInput = next
                        },
                        onPresenceChanged = { executionPresenceConfirmed = it },
                        onExecute = {
                            viewModel.executeAuthorizedBirth(
                                presentedConfirmationCode = executionCodeInput,
                                userPresenceConfirmed = executionPresenceConfirmed
                            )
                        }
                    )
                }
            }
            authorizationState.errorMessage?.let { message ->
                ErrorText("Testimonio rechazado: $message")
            }

            executionState.committed?.let { summary ->
                CommittedBirthCard(summary)
            }
            executionState.errorMessage?.let { message ->
                ErrorText("Ejecución rechazada antes del commit: $message")
            }

            OutlinedButton(
                enabled = !state.loading && !interactionLocked,
                modifier = Modifier.fillMaxWidth(),
                onClick = viewModel::refresh
            ) {
                Text("Revisar estado local")
            }

            state.errorMessage?.let { message ->
                ErrorText(message)
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
            MonospaceText(status, Color(0xFF245C37))
        }
    }
}

@Composable
private fun BodyProvisioningCard(
    userPresenceConfirmed: Boolean,
    busy: Boolean,
    interactionLocked: Boolean,
    onPresenceChanged: (Boolean) -> Unit,
    onProvision: () -> Unit
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
            Text("Preparar el primer Body", style = MaterialTheme.typography.titleMedium)
            Text(
                "Esta acción genera una identidad Ed25519 nueva para este Android. " +
                    "La clave privada quedará cifrada por Android Keystore y no se exportará.",
                style = MaterialTheme.typography.bodySmall
            )
            PresenceRow(
                checked = userPresenceConfirmed,
                enabled = !interactionLocked,
                label = "Estoy presente y ordeno crear la raíz criptográfica de este Body.",
                onChanged = onPresenceChanged
            )
            Button(
                enabled = !interactionLocked && userPresenceConfirmed,
                modifier = Modifier.fillMaxWidth(),
                onClick = onProvision
            ) {
                Text(if (busy) "Preparando Body" else "Crear raíz criptográfica del Body")
            }
            Text(
                "Esto prepara el dispositivo; todavía no crea la Instance ni ejecuta el nacimiento.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8A4B0F)
            )
        }
    }
}

@Composable
private fun GuardianProvisioningCard(
    guardianId: String,
    keyEpochId: String,
    confirmedFingerprint: String,
    importedFingerprint: String?,
    independentConfirmationAcknowledged: Boolean,
    userPresenceConfirmed: Boolean,
    importing: Boolean,
    pinning: Boolean,
    interactionLocked: Boolean,
    onGuardianIdChanged: (String) -> Unit,
    onKeyEpochIdChanged: (String) -> Unit,
    onConfirmedFingerprintChanged: (String) -> Unit,
    onIndependentConfirmationChanged: (Boolean) -> Unit,
    onPresenceChanged: (Boolean) -> Unit,
    onSelectKey: () -> Unit,
    onClearKey: () -> Unit,
    onPin: () -> Unit
) {
    val inputEnabled = !interactionLocked
    val identifiersValid = guardianId.length in 1..128 &&
        keyEpochId.length in 16..128 &&
        guardianId == guardianId.trim() &&
        keyEpochId == keyEpochId.trim() &&
        guardianId.none(Char::isISOControl) &&
        keyEpochId.none(Char::isISOControl)
    val fingerprintMatches = importedFingerprint != null &&
        isSha256Ref(confirmedFingerprint) &&
        confirmedFingerprint == importedFingerprint
    val pinEnabled = inputEnabled && identifiersValid && fingerprintMatches &&
        independentConfirmationAcknowledged && userPresenceConfirmed

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFE9E7F4)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Fijar la custodia del Guardián", style = MaterialTheme.typography.titleMedium)
            Text(
                "Selecciona una clave pública Ed25519 RAW de exactamente 32 bytes. " +
                    "La huella de confirmación debe llegar por un canal independiente del Seed.",
                style = MaterialTheme.typography.bodySmall
            )
            ProvisioningTextField(
                value = guardianId,
                label = "Guardian ID",
                enabled = inputEnabled,
                maxLength = 128,
                onValueChanged = onGuardianIdChanged
            )
            ProvisioningTextField(
                value = keyEpochId,
                label = "Guardian key epoch ID",
                enabled = inputEnabled,
                maxLength = 128,
                onValueChanged = onKeyEpochIdChanged
            )
            OutlinedButton(
                enabled = inputEnabled,
                modifier = Modifier.fillMaxWidth(),
                onClick = onSelectKey
            ) {
                Text(
                    when {
                        importing -> "Leyendo clave pública"
                        importedFingerprint != null -> "Seleccionar otra clave pública RAW"
                        else -> "Seleccionar clave pública RAW (32 bytes)"
                    }
                )
            }
            importedFingerprint?.let { fingerprint ->
                Text("Huella calculada del archivo", style = MaterialTheme.typography.bodySmall)
                MonospaceText(fingerprint)
                ProvisioningTextField(
                    value = confirmedFingerprint,
                    label = "Huella confirmada por canal independiente",
                    enabled = inputEnabled,
                    maxLength = SHA256_REF_LENGTH,
                    onValueChanged = onConfirmedFingerprintChanged
                )
                if (confirmedFingerprint.isNotEmpty() && !fingerprintMatches) {
                    ErrorText("La huella independiente no coincide exactamente con el archivo.")
                }
                PresenceRow(
                    checked = independentConfirmationAcknowledged,
                    enabled = inputEnabled,
                    label = "Recibí esta huella por un canal independiente del archivo y del Seed.",
                    onChanged = onIndependentConfirmationChanged
                )
                PresenceRow(
                    checked = userPresenceConfirmed,
                    enabled = inputEnabled,
                    label = "Estoy presente y ordeno fijar exactamente esta clave del Guardián.",
                    onChanged = onPresenceChanged
                )
                Button(
                    enabled = pinEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5B3B82),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onPin
                ) {
                    Text(if (pinning) "Fijando Guardián" else "Fijar Guardian trust anchor")
                }
                OutlinedButton(
                    enabled = inputEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onClearKey
                ) {
                    Text("Descartar clave seleccionada")
                }
            }
            Text(
                "El pin es de una sola vez antes del nacimiento. No existe reemplazo silencioso.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8A4B0F)
            )
        }
    }
}

@Composable
private fun ProvisioningTextField(
    value: String,
    label: String,
    enabled: Boolean,
    maxLength: Int,
    onValueChanged: (String) -> Unit
) {
    TextField(
        value = value,
        onValueChange = { next ->
            if (next.length <= maxLength && next.none(Char::isISOControl)) {
                onValueChanged(next)
            }
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true
    )
}

@Composable
private fun BodyProvisioningReceiptCard(receipt: GenesisUltraBodyProvisioningReceipt) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFDFF2E4)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Body criptográfico preparado", style = MaterialTheme.typography.titleMedium)
            MonospaceText("Body: ${receipt.bodyId}")
            MonospaceText("Epoch: ${receipt.keyEpochId}")
            MonospaceText("Public key: ${receipt.publicKeyRef}")
            MonospaceText("Receipt: ${receipt.receiptDigest}", Color(0xFF245C37))
        }
    }
}

@Composable
private fun GuardianProvisioningReceiptCard(receipt: GenesisUltraGuardianProvisioningReceipt) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFDFF2E4)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Guardián fijado", style = MaterialTheme.typography.titleMedium)
            Text("Guardian: ${receipt.guardianId}", style = MaterialTheme.typography.bodySmall)
            MonospaceText("Epoch: ${receipt.keyEpochId}")
            MonospaceText("Public key: ${receipt.publicKeyRef}")
            MonospaceText("Anchor: ${receipt.anchorDigest}")
            MonospaceText("Receipt: ${receipt.receiptDigest}", Color(0xFF245C37))
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
            ConfirmationCodeField(
                expectedCode = expectedCode,
                value = confirmationCodeInput,
                enabled = !busy,
                onValueChanged = onCodeChanged
            )
            Text("Confirma antes de $sessionExpiresAt.", style = MaterialTheme.typography.bodySmall)
            PresenceRow(
                checked = userPresenceConfirmed,
                enabled = !busy,
                label = "Estoy presente y apruebo este candidato exacto.",
                onChanged = onPresenceChanged
            )
            Button(
                enabled = !busy && userPresenceConfirmed && confirmationCodeInput == expectedCode,
                modifier = Modifier.fillMaxWidth(),
                onClick = onConfirm
            ) {
                Text(if (busy) "Registrando consentimiento" else "Registrar consentimiento exacto")
            }
            Text(
                "Esto todavía no ejecuta el nacimiento.",
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
    executionBusyOrCommitted: Boolean,
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
                enabled = !state.busy && !authorizationVerifying && !executionBusyOrCommitted &&
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
                "La autorización existe solo en memoria y requiere una segunda ceremonia para ejecutarse.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8A4B0F)
            )
        }
    }
}

@Composable
private fun ExecutionCeremonyCard(
    expectedCode: String,
    expiresAt: String,
    confirmationCodeInput: String,
    userPresenceConfirmed: Boolean,
    executing: Boolean,
    onCodeChanged: (String) -> Unit,
    onPresenceChanged: (Boolean) -> Unit,
    onExecute: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFE4D8)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Ceremonia final de nacimiento", style = MaterialTheme.typography.titleMedium)
            Text(
                "Esta acción compromete de forma atómica la Instance, el Body activo, la autorización durable " +
                    "y la memoria canónica secuencia 1. No es una vista previa.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8A2E0F)
            )
            ConfirmationCodeField(
                expectedCode = expectedCode,
                value = confirmationCodeInput,
                enabled = !executing,
                onValueChanged = onCodeChanged
            )
            Text("La autorización expira: $expiresAt", style = MaterialTheme.typography.bodySmall)
            PresenceRow(
                checked = userPresenceConfirmed,
                enabled = !executing,
                label = "Estoy presente y ordeno comprometer este nacimiento exacto.",
                onChanged = onPresenceChanged
            )
            Button(
                enabled = !executing && userPresenceConfirmed && confirmationCodeInput == expectedCode,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9A3412),
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth(),
                onClick = onExecute
            ) {
                Text(if (executing) "Comprometiendo nacimiento" else "Comprometer nacimiento Genesis Ultra")
            }
            Text(
                "Un fallo antes del commit permite corregir y volver a confirmar. " +
                    "Después del commit nunca se ofrece una segunda ejecución.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CommittedBirthCard(summary: GenesisUltraAtomicBirthExecutionSummary) {
    val maintenancePending =
        summary.outcome == GenesisUltraAtomicBirthExecutionOutcome.COMMITTED_MAINTENANCE_PENDING
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (maintenancePending) Color(0xFFFFF0D5) else Color(0xFFDFF2E4)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Nacimiento Genesis Ultra comprometido", style = MaterialTheme.typography.titleMedium)
            Text("Compañero: ${summary.companionName}", style = MaterialTheme.typography.bodySmall)
            Text("Instance: ${summary.instanceId}", style = MaterialTheme.typography.bodySmall)
            Text("Birth: ${summary.birthId}", style = MaterialTheme.typography.bodySmall)
            MonospaceText("Authorization: ${summary.authorizationDigest}")
            MonospaceText("Birth state: ${summary.birthStateDigest}")
            MonospaceText("Receipt: ${summary.receiptDigest}")
            MonospaceText("Memory sequence 1: ${summary.firstPostBirthEventHash}")
            Text("Comprometido: ${summary.committedAt}", style = MaterialTheme.typography.bodySmall)
            if (maintenancePending) {
                Text(
                    "El nacimiento ya está comprometido. La limpieza externa sigue pendiente y se " +
                        "reintentará mediante inspección durable; no ejecutes otro nacimiento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8A4B0F)
                )
                MonospaceText("Mantenimiento: ${summary.maintenanceError.orEmpty()}")
            } else {
                Text(
                    "Commit, memoria canónica y retiro del consentimiento verificados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF245C37)
                )
            }
            MonospaceText("birthCommitted = true", Color(0xFF245C37))
            MonospaceText("retryAllowed = false", Color(0xFF245C37))
        }
    }
}

@Composable
private fun ConfirmationCodeField(
    expectedCode: String,
    value: String,
    enabled: Boolean,
    onValueChanged: (String) -> Unit
) {
    Text(
        "Código: $expectedCode",
        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
    )
    TextField(
        value = value,
        onValueChange = onValueChanged,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Código de 12 caracteres") },
        singleLine = true
    )
}

@Composable
private fun PresenceRow(
    checked: Boolean,
    enabled: Boolean,
    label: String,
    onChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChanged,
            enabled = enabled
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
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

private fun isConfirmationCodePrefix(value: String): Boolean {
    return value.length <= 12 && value.all { character ->
        character in '0'..'9' || character in 'a'..'f'
    }
}

private fun isSha256Ref(value: String): Boolean = SHA256_REF.matches(value)

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

private val GUARDIAN_PUBLIC_KEY_MIME_TYPES = arrayOf(
    "*/*"
)

private const val SHA256_REF_LENGTH = 71
private val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
