package com.morimil.app.data.genesis.ultra

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraSignedSeedPreviewBoundaryTest {
    @Test
    fun previewCoordinatorCannotRecordConsentAuthorizeOrExecuteBirth() {
        val source = sourceFile(
            "src/main/java/com/morimil/app/data/genesis/ultra/" +
                "GenesisUltraSignedSeedPreviewCoordinator.kt"
        ).readText()

        assertTrue(source.contains("GenesisUltraReleaseArchiveReader"))
        assertTrue(source.contains("guardianTrustAnchorStore.verifyRelease"))
        assertTrue(source.contains("candidateConstructionCoordinator.construct"))
        assertTrue(source.contains("GenesisUltraSignedSeedCandidateSession"))
        assertFalse(source.contains("GenesisUltraAndroidHostBirthConsentStore"))
        assertFalse(source.contains("GenesisUltraAtomicBirthAuthorizationCoordinator"))
        assertFalse(source.contains("GenesisUltraAtomicBirthExecutionCoordinator"))
        assertFalse(source.contains("withTransaction"))
        assertFalse(source.contains(".execute("))
    }

    @Test
    fun onboardingCanRecordConsentAndVerifyAuthorizationButCannotExecuteBirth() {
        val viewModel = sourceFile(
            "src/main/java/com/morimil/app/ui/GenesisUltraOnboardingViewModel.kt"
        ).readText()
        val screen = sourceFile(
            "src/main/java/com/morimil/app/ui/OnboardingScreen.kt"
        ).readText()

        assertTrue(viewModel.contains("recordExplicitConsent"))
        assertTrue(viewModel.contains("GenesisUltraHostBirthConsentRequest"))
        assertTrue(viewModel.contains("GenesisUltraAtomicBirthWitnessAuthorizationCoordinator"))
        assertTrue(viewModel.contains("authorizeWitnessArchive"))
        assertFalse(viewModel.contains("GenesisUltraAtomicBirthExecutionCoordinator"))
        assertFalse(viewModel.contains("genesisUltraAtomicBirthExecutionCoordinator"))
        assertFalse(viewModel.contains(".execute("))
        assertTrue(screen.contains("Consentimiento registrado; verifica el testimonio final"))
        assertTrue(screen.contains("birthCommitAuthorized = false"))
        assertTrue(screen.contains("birthCommitAuthorized = true"))
        assertTrue(screen.contains("Button(\n                enabled = false"))
    }

    @Test
    fun exactCandidateAndAuthorizationAreKeptOnlyAsPrivateViewModelMemory() {
        val viewModel = sourceFile(
            "src/main/java/com/morimil/app/ui/GenesisUltraOnboardingViewModel.kt"
        ).readText()
        val session = sourceFile(
            "src/main/java/com/morimil/app/data/genesis/ultra/" +
                "GenesisUltraSignedSeedPreviewCoordinator.kt"
        ).readText()

        assertTrue(viewModel.contains("private var candidateSession"))
        assertTrue(viewModel.contains("private var authorizedBirth"))
        assertTrue(viewModel.contains("override fun onCleared()"))
        assertTrue(session.contains("internal val constructedCandidate"))
        assertFalse(session.contains("SharedPreferences"))
        assertFalse(session.contains("MorimilDatabase"))
        assertFalse(session.contains("Serializable"))
    }

    @Test
    fun operationLocksArePublishedBeforeLaunchingBackgroundWork() {
        val source = sourceFile(
            "src/main/java/com/morimil/app/ui/GenesisUltraOnboardingViewModel.kt"
        ).readText()

        val previewFunction = source
            .substringAfter("internal fun previewSignedSeed")
            .substringBefore("internal fun recordExplicitHostConsent")
        assertBeforeLaunch(
            previewFunction,
            "GenesisUltraSignedSeedPreviewUiState(importing = true)"
        )

        val recordFunction = source
            .substringAfter("internal fun recordExplicitHostConsent")
            .substringBefore("internal fun authorizeWitnessArchive")
        assertBeforeLaunch(recordFunction, "recording = true")

        val authorizationFunction = source
            .substringAfter("internal fun authorizeWitnessArchive")
            .substringBefore("internal fun revokeHostConsent")
        assertBeforeLaunch(
            authorizationFunction,
            "GenesisUltraAtomicBirthAuthorizationUiState(verifying = true)"
        )

        val revokeFunction = source
            .substringAfter("internal fun revokeHostConsent")
            .substringBefore("internal fun clearSignedSeedPreview")
        assertBeforeLaunch(revokeFunction, "revoking = true")
    }

    private fun assertBeforeLaunch(functionSource: String, lockMarker: String) {
        val lockIndex = functionSource.indexOf(lockMarker)
        val launchIndex = functionSource.indexOf("viewModelScope.launch")
        assertTrue("Missing operation lock marker: $lockMarker", lockIndex >= 0)
        assertTrue("Missing coroutine launch", launchIndex >= 0)
        assertTrue("Operation lock must be published before launch", lockIndex < launchIndex)
    }

    private fun sourceFile(relativePath: String): File {
        return sequenceOf(
            File(relativePath),
            File("app/$relativePath")
        ).firstOrNull(File::isFile)
            ?: error("Source file not found: $relativePath")
    }
}
