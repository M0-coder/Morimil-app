package com.morimil.app.ui

import com.morimil.app.data.genesis.ultra.GenesisUltraBirthPreparationAssessment
import com.morimil.app.data.genesis.ultra.GenesisUltraBirthPreparationStatus

internal enum class GenesisUltraAppRoute {
    ONBOARDING,
    RUNTIME
}

internal data class GenesisUltraOnboardingUiState(
    val loading: Boolean,
    val route: GenesisUltraAppRoute,
    val preparationStatus: GenesisUltraBirthPreparationStatus?,
    val title: String,
    val detail: String,
    val blockers: List<String>,
    val remainingRequirements: List<String>,
    val canonicalNameInputEnabled: Boolean,
    val candidateConstructionReady: Boolean,
    val birthCommitAuthorized: Boolean,
    val errorMessage: String?
) {
    init {
        require(!birthCommitAuthorized) {
            "onboarding_state_cannot_authorize_birth"
        }
        require(route != GenesisUltraAppRoute.RUNTIME ||
            preparationStatus == GenesisUltraBirthPreparationStatus.ALREADY_COMMITTED
        ) { "onboarding_runtime_route_requires_committed_birth" }
        require(!candidateConstructionReady ||
            preparationStatus == GenesisUltraBirthPreparationStatus.READY_FOR_SIGNED_CANDIDATE
        ) { "onboarding_candidate_readiness_status_mismatch" }
        require(!loading || preparationStatus == null) {
            "onboarding_loading_status_must_be_absent"
        }
    }
}

internal object GenesisUltraOnboardingUiStateMapper {
    fun loading(): GenesisUltraOnboardingUiState {
        return GenesisUltraOnboardingUiState(
            loading = true,
            route = GenesisUltraAppRoute.ONBOARDING,
            preparationStatus = null,
            title = "Revisando Genesis Ultra",
            detail = "Comprobando nacimiento, Body y custodia local.",
            blockers = emptyList(),
            remainingRequirements = emptyList(),
            canonicalNameInputEnabled = false,
            candidateConstructionReady = false,
            birthCommitAuthorized = false,
            errorMessage = null
        )
    }

    fun failure(error: Throwable): GenesisUltraOnboardingUiState {
        return GenesisUltraOnboardingUiState(
            loading = false,
            route = GenesisUltraAppRoute.ONBOARDING,
            preparationStatus = GenesisUltraBirthPreparationStatus.INCONSISTENT,
            title = "Estado Genesis Ultra no verificable",
            detail = "El runtime permanece bloqueado hasta recuperar un estado verificable.",
            blockers = listOf("onboarding_state_inspection_failed"),
            remainingRequirements = emptyList(),
            canonicalNameInputEnabled = false,
            candidateConstructionReady = false,
            birthCommitAuthorized = false,
            errorMessage = error.message?.take(180) ?: error::class.java.simpleName
        )
    }

    fun from(
        assessment: GenesisUltraBirthPreparationAssessment
    ): GenesisUltraOnboardingUiState {
        val presentation = presentationFor(assessment.status)
        return GenesisUltraOnboardingUiState(
            loading = false,
            route = if (assessment.status == GenesisUltraBirthPreparationStatus.ALREADY_COMMITTED) {
                GenesisUltraAppRoute.RUNTIME
            } else {
                GenesisUltraAppRoute.ONBOARDING
            },
            preparationStatus = assessment.status,
            title = presentation.first,
            detail = presentation.second,
            blockers = assessment.blockers,
            remainingRequirements = assessment.remainingRequirements,
            canonicalNameInputEnabled = assessment.status in setOf(
                GenesisUltraBirthPreparationStatus.BODY_IDENTITY_REQUIRED,
                GenesisUltraBirthPreparationStatus.GUARDIAN_TRUST_REQUIRED,
                GenesisUltraBirthPreparationStatus.READY_FOR_SIGNED_CANDIDATE
            ),
            candidateConstructionReady = assessment.candidateConstructionReady,
            birthCommitAuthorized = false,
            errorMessage = null
        )
    }

    private fun presentationFor(
        status: GenesisUltraBirthPreparationStatus
    ): Pair<String, String> {
        return when (status) {
            GenesisUltraBirthPreparationStatus.INCONSISTENT ->
                "Estado Genesis Ultra inconsistente" to
                    "No se permite nacimiento ni runtime hasta reparar la evidencia durable."

            GenesisUltraBirthPreparationStatus.ALREADY_COMMITTED ->
                "Nacimiento Genesis Ultra verificado" to
                    "La autorización, el nacimiento y la primera memoria canónica están comprometidos."

            GenesisUltraBirthPreparationStatus.LEGACY_CONFLICT ->
                "Identidad heredada detectada" to
                    "La ruta antigua no puede convertirse en un nacimiento Genesis Ultra."

            GenesisUltraBirthPreparationStatus.BODY_IDENTITY_REQUIRED ->
                "Body criptográfico pendiente" to
                    "Primero debe existir una raíz corporal local no exportable."

            GenesisUltraBirthPreparationStatus.GUARDIAN_TRUST_REQUIRED ->
                "Custodia del Guardián pendiente" to
                    "Debe fijarse la clave pública del Guardián mediante una ceremonia separada."

            GenesisUltraBirthPreparationStatus.READY_FOR_SIGNED_CANDIDATE ->
                "Preparado para candidato firmado" to
                    "Aún faltan Seed verificado, consentimiento exacto y testimonio atómico."
        }
    }
}
