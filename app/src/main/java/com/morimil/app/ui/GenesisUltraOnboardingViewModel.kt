package com.morimil.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.morimil.app.MorimilAppContainer
import com.morimil.app.data.genesis.ultra.GenesisUltraAtomicBirthWitnessAuthorizationCoordinator
import com.morimil.app.data.genesis.ultra.GenesisUltraAuthorizedAtomicBirth
import com.morimil.app.data.genesis.ultra.GenesisUltraCompanionNamePolicy
import com.morimil.app.data.genesis.ultra.GenesisUltraCompanionNameValidation
import com.morimil.app.data.genesis.ultra.GenesisUltraHostBirthConsentRequest
import com.morimil.app.data.genesis.ultra.GenesisUltraHostBirthConsentState
import com.morimil.app.data.genesis.ultra.GenesisUltraSignedSeedCandidateSession
import com.morimil.app.data.genesis.ultra.GenesisUltraSignedSeedPreviewCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Pre-birth inspection, candidate preview, host consent and witness verification.
 *
 * The exact candidate and any issued authorization remain process-local. This
 * ViewModel cannot invoke atomic execution, persist authorization or open runtime.
 */
class GenesisUltraOnboardingViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val container = MorimilAppContainer.from(application)
    private val preparationCoordinator = container.genesisUltraBirthPreparationCoordinator
    private val hostBirthConsentStore = container.genesisUltraHostBirthConsentStore
    private val hostBirthConsentRecoveryCoordinator =
        container.genesisUltraHostBirthConsentRecoveryCoordinator
    private val signedSeedPreviewCoordinator = GenesisUltraSignedSeedPreviewCoordinator(
        context = application,
        preparationCoordinator = preparationCoordinator,
        guardianTrustAnchorStore = container.genesisUltraGuardianTrustAnchorStore,
        candidateConstructionCoordinator = container.genesisUltraBirthCandidateConstructionCoordinator
    )
    private val witnessAuthorizationCoordinator =
        GenesisUltraAtomicBirthWitnessAuthorizationCoordinator(
            context = application,
            authorizationCoordinator = container.genesisUltraAtomicBirthAuthorizationCoordinator
        )

    private var candidateSession: GenesisUltraSignedSeedCandidateSession? = null
    private var authorizedBirth: GenesisUltraAuthorizedAtomicBirth? = null

    private val _state = MutableStateFlow(GenesisUltraOnboardingUiStateMapper.loading())
    internal val state: StateFlow<GenesisUltraOnboardingUiState> = _state.asStateFlow()

    private val _signedSeedPreview = MutableStateFlow(GenesisUltraSignedSeedPreviewUiState())
    internal val signedSeedPreview: StateFlow<GenesisUltraSignedSeedPreviewUiState> =
        _signedSeedPreview.asStateFlow()

    private val _hostBirthConsent = MutableStateFlow(GenesisUltraHostBirthConsentUiState())
    internal val hostBirthConsent: StateFlow<GenesisUltraHostBirthConsentUiState> =
        _hostBirthConsent.asStateFlow()

    private val _atomicBirthAuthorization =
        MutableStateFlow(GenesisUltraAtomicBirthAuthorizationUiState())
    internal val atomicBirthAuthorization: StateFlow<GenesisUltraAtomicBirthAuthorizationUiState> =
        _atomicBirthAuthorization.asStateFlow()

    init {
        refresh()
    }

    internal fun refresh() {
        if (
            _hostBirthConsent.value.busy ||
            _hostBirthConsent.value.hasPersistedConsent ||
            _atomicBirthAuthorization.value.verifying ||
            authorizedBirth != null
        ) return
        clearCandidateSession()
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = GenesisUltraOnboardingUiStateMapper.loading()
            _hostBirthConsent.value = GenesisUltraHostBirthConsentUiState(checking = true)

            _state.value = runCatching { preparationCoordinator.inspect() }
                .fold(
                    onSuccess = GenesisUltraOnboardingUiStateMapper::from,
                    onFailure = GenesisUltraOnboardingUiStateMapper::failure
                )

            _hostBirthConsent.value = runCatching {
                hostBirthConsentRecoveryCoordinator.inspect()
            }.fold(
                onSuccess = { persistedState ->
                    GenesisUltraHostBirthConsentUiState(persistedState = persistedState)
                },
                onFailure = { error ->
                    GenesisUltraHostBirthConsentUiState(
                        persistedState = GenesisUltraHostBirthConsentState.INCONSISTENT,
                        errorMessage = error.message?.take(220) ?: error::class.java.simpleName
                    )
                }
            )
        }
    }

    internal fun validateCanonicalCompanionName(
        value: String
    ): GenesisUltraCompanionNameValidation {
        return GenesisUltraCompanionNamePolicy.validate(value)
    }

    internal fun previewSignedSeed(uri: Uri, companionName: String) {
        if (
            _signedSeedPreview.value.importing ||
            _hostBirthConsent.value.busy ||
            _atomicBirthAuthorization.value.verifying
        ) return
        if (_hostBirthConsent.value.persistedState != GenesisUltraHostBirthConsentState.ABSENT) {
            _signedSeedPreview.value = GenesisUltraSignedSeedPreviewUiState(
                errorMessage = "host_birth_consent_must_be_absent_before_new_candidate"
            )
            return
        }
        val validation = GenesisUltraCompanionNamePolicy.validate(companionName)
        if (!validation.isValid) {
            _signedSeedPreview.value = GenesisUltraSignedSeedPreviewUiState(
                errorMessage = validation.errorCode ?: "companion_name_invalid"
            )
            return
        }

        clearCandidateSession()
        _signedSeedPreview.value = GenesisUltraSignedSeedPreviewUiState(importing = true)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                signedSeedPreviewCoordinator.prepareSession(
                    uri = uri,
                    companionName = requireNotNull(validation.canonicalName)
                )
            }.fold(
                onSuccess = { session ->
                    check(session.isValidAt(canonicalNow())) {
                        "seed_candidate_session_expired_before_presentation"
                    }
                    candidateSession = session
                    _signedSeedPreview.value = GenesisUltraSignedSeedPreviewUiState(
                        preview = session.preview,
                        confirmationCode = session.confirmationCode,
                        sessionExpiresAt = session.expiresAt
                    )
                    _hostBirthConsent.value = GenesisUltraHostBirthConsentUiState(
                        persistedState = GenesisUltraHostBirthConsentState.ABSENT,
                        candidateSessionAvailable = true
                    )
                },
                onFailure = { error ->
                    clearCandidateSession()
                    _signedSeedPreview.value = GenesisUltraSignedSeedPreviewUiState(
                        errorMessage = error.message?.take(220) ?: error::class.java.simpleName
                    )
                }
            )
        }
    }

    internal fun recordExplicitHostConsent(
        presentedConfirmationCode: String,
        userPresenceConfirmed: Boolean
    ) {
        if (_hostBirthConsent.value.busy || _atomicBirthAuthorization.value.verifying) return
        val session = candidateSession
        if (session == null || !_signedSeedPreview.value.sessionAvailable) {
            _hostBirthConsent.value = _hostBirthConsent.value.copy(
                errorMessage = "host_birth_consent_candidate_session_missing"
            )
            return
        }
        if (!session.isValidAt(canonicalNow())) {
            clearCandidateSession()
            _hostBirthConsent.value = GenesisUltraHostBirthConsentUiState(
                errorMessage = "host_birth_consent_candidate_session_expired"
            )
            return
        }

        _hostBirthConsent.value = GenesisUltraHostBirthConsentUiState(
            recording = true,
            persistedState = GenesisUltraHostBirthConsentState.ABSENT,
            candidateSessionAvailable = true
        )
        viewModelScope.launch(Dispatchers.IO) {
            val preview = session.preview
            runCatching {
                hostBirthConsentStore.recordExplicitConsent(
                    candidate = session.constructedCandidate,
                    request = GenesisUltraHostBirthConsentRequest(
                        presentedCandidateDigest = preview.candidateDigest,
                        presentedInstanceId = preview.instanceId,
                        presentedCompanionName = preview.companionName,
                        presentedConfirmationCode = presentedConfirmationCode,
                        decision = GenesisUltraHostBirthConsentRequest.APPROVE_DECISION,
                        confirmationMode =
                            GenesisUltraHostBirthConsentRequest.INTERACTIVE_CONFIRMATION_MODE,
                        confirmationPurpose =
                            GenesisUltraHostBirthConsentRequest.BIRTH_CONFIRMATION_PURPOSE,
                        userPresenceConfirmed = userPresenceConfirmed
                    )
                )
            }.fold(
                onSuccess = { consent ->
                    check(!consent.birthCommitAuthorized) {
                        "host_birth_consent_unexpectedly_authorized_birth"
                    }
                    _hostBirthConsent.value = GenesisUltraHostBirthConsentUiState(
                        persistedState = GenesisUltraHostBirthConsentState.READY,
                        summary = GenesisUltraHostBirthConsentSummary.from(consent),
                        candidateSessionAvailable = true
                    )
                    scheduleConsentExpiry(consent.consentId, consent.expiresAt)
                },
                onFailure = { error ->
                    val persistedState = runCatching {
                        hostBirthConsentRecoveryCoordinator.inspect()
                    }.getOrDefault(GenesisUltraHostBirthConsentState.INCONSISTENT)
                    _hostBirthConsent.value = GenesisUltraHostBirthConsentUiState(
                        persistedState = persistedState,
                        candidateSessionAvailable = candidateSession != null,
                        errorMessage = error.message?.take(220) ?: error::class.java.simpleName
                    )
                }
            )
        }
    }

    internal fun authorizeWitnessArchive(uri: Uri) {
        if (
            _atomicBirthAuthorization.value.verifying ||
            _atomicBirthAuthorization.value.authorizedInMemory ||
            authorizedBirth != null ||
            _hostBirthConsent.value.busy ||
            _signedSeedPreview.value.importing
        ) return

        val session = candidateSession
        val consent = _hostBirthConsent.value.summary
        if (
            session == null ||
            consent == null ||
            _hostBirthConsent.value.persistedState != GenesisUltraHostBirthConsentState.READY
        ) {
            _atomicBirthAuthorization.value = GenesisUltraAtomicBirthAuthorizationUiState(
                errorMessage = "witness_authorization_candidate_or_consent_missing"
            )
            return
        }
        if (
            consent.candidateDigest != session.preview.candidateDigest ||
            !session.isValidAt(canonicalNow())
        ) {
            clearAtomicBirthAuthorization()
            _atomicBirthAuthorization.value = GenesisUltraAtomicBirthAuthorizationUiState(
                errorMessage = "witness_authorization_candidate_or_consent_expired"
            )
            return
        }

        _atomicBirthAuthorization.value = GenesisUltraAtomicBirthAuthorizationUiState(verifying = true)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                witnessAuthorizationCoordinator.authorize(
                    uri = uri,
                    candidate = session.constructedCandidate,
                    expectedConsentDigest = consent.consentDigest
                )
            }.fold(
                onSuccess = { authorization ->
                    check(candidateSession === session) {
                        "witness_authorization_candidate_session_changed"
                    }
                    check(_hostBirthConsent.value.summary?.consentDigest == consent.consentDigest) {
                        "witness_authorization_consent_session_changed"
                    }
                    authorizedBirth = authorization
                    _atomicBirthAuthorization.value = GenesisUltraAtomicBirthAuthorizationUiState(
                        summary = GenesisUltraAtomicBirthAuthorizationSummary.from(authorization)
                    )
                    scheduleAuthorizationExpiry(
                        authorization.authorizationDigest,
                        authorization.expiresAt
                    )
                },
                onFailure = { error ->
                    authorizedBirth = null
                    _atomicBirthAuthorization.value = GenesisUltraAtomicBirthAuthorizationUiState(
                        errorMessage = error.message?.take(220) ?: error::class.java.simpleName
                    )
                }
            )
        }
    }

    internal fun revokeHostConsent() {
        if (_hostBirthConsent.value.busy || _atomicBirthAuthorization.value.verifying) return
        val current = _hostBirthConsent.value
        if (!current.hasPersistedConsent) return

        clearAtomicBirthAuthorization()
        _hostBirthConsent.value = current.copy(
            recording = false,
            revoking = true,
            errorMessage = null
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val summary = current.summary
                if (summary != null && candidateSession != null) {
                    hostBirthConsentStore.revokeBeforeBirth(summary.candidateDigest)
                } else {
                    hostBirthConsentRecoveryCoordinator.revokeExistingBeforeBirth()
                }
            }.fold(
                onSuccess = {
                    clearCandidateSession()
                    _hostBirthConsent.value = GenesisUltraHostBirthConsentUiState(
                        persistedState = GenesisUltraHostBirthConsentState.ABSENT
                    )
                },
                onFailure = { error ->
                    _hostBirthConsent.value = GenesisUltraHostBirthConsentUiState(
                        persistedState = runCatching {
                            hostBirthConsentRecoveryCoordinator.inspect()
                        }.getOrDefault(GenesisUltraHostBirthConsentState.INCONSISTENT),
                        candidateSessionAvailable = candidateSession != null,
                        errorMessage = error.message?.take(220) ?: error::class.java.simpleName
                    )
                }
            )
        }
    }

    internal fun clearSignedSeedPreview() {
        if (_hostBirthConsent.value.hasPersistedConsent) return
        clearCandidateSession()
        _hostBirthConsent.value = GenesisUltraHostBirthConsentUiState(
            persistedState = GenesisUltraHostBirthConsentState.ABSENT
        )
    }

    override fun onCleared() {
        authorizedBirth = null
        candidateSession = null
        super.onCleared()
    }

    private fun clearCandidateSession() {
        clearAtomicBirthAuthorization()
        candidateSession = null
        _signedSeedPreview.value = GenesisUltraSignedSeedPreviewUiState()
        val consent = _hostBirthConsent.value
        if (!consent.hasPersistedConsent && !consent.busy) {
            _hostBirthConsent.value = consent.copy(candidateSessionAvailable = false)
        }
    }

    private fun clearAtomicBirthAuthorization() {
        authorizedBirth = null
        _atomicBirthAuthorization.value = GenesisUltraAtomicBirthAuthorizationUiState()
    }

    private fun scheduleConsentExpiry(consentId: String, expiresAt: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val waitMillis = Duration.between(Instant.now(), Instant.parse(expiresAt))
                .toMillis()
                .coerceAtLeast(0L)
            delay(waitMillis + EXPIRY_SETTLE_MILLIS)
            val current = _hostBirthConsent.value
            if (current.summary?.consentId != consentId) return@launch
            val persistedState = runCatching {
                hostBirthConsentRecoveryCoordinator.inspect()
            }.getOrDefault(GenesisUltraHostBirthConsentState.INCONSISTENT)
            if (persistedState == GenesisUltraHostBirthConsentState.EXPIRED) {
                clearAtomicBirthAuthorization()
                _hostBirthConsent.value = GenesisUltraHostBirthConsentUiState(
                    persistedState = persistedState,
                    candidateSessionAvailable = candidateSession != null
                )
            }
        }
    }

    private fun scheduleAuthorizationExpiry(authorizationDigest: String, expiresAt: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val waitMillis = Duration.between(Instant.now(), Instant.parse(expiresAt))
                .toMillis()
                .coerceAtLeast(0L)
            delay(waitMillis + EXPIRY_SETTLE_MILLIS)
            val current = _atomicBirthAuthorization.value.summary ?: return@launch
            if (current.authorizationDigest != authorizationDigest) return@launch
            if (authorizedBirth?.authorizationDigest != authorizationDigest) return@launch
            authorizedBirth = null
            _atomicBirthAuthorization.value = GenesisUltraAtomicBirthAuthorizationUiState(
                expired = true,
                errorMessage = "witness_authorization_expired"
            )
        }
    }

    private fun canonicalNow(): String =
        Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()

    private companion object {
        const val EXPIRY_SETTLE_MILLIS = 250L
    }
}
