package com.morimil.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraPreBirthProvisioningBoundaryTest {
    @Test
    fun onboardingExposesOnlyExplicitPresenceBoundProvisioningActions() {
        val viewModel = sourceFile(
            "src/main/java/com/morimil/app/ui/GenesisUltraOnboardingViewModel.kt"
        ).readText()
        val screen = sourceFile(
            "src/main/java/com/morimil/app/ui/OnboardingScreen.kt"
        ).readText()
        val preparation = sourceFile(
            "src/main/java/com/morimil/app/data/genesis/ultra/" +
                "GenesisUltraBirthPreparationCoordinator.kt"
        ).readText()

        assertTrue(viewModel.contains("internal fun provisionBodyIdentity("))
        assertTrue(viewModel.contains("internal fun provisionGuardianTrustAnchor("))
        assertTrue(viewModel.contains("independentConfirmationAcknowledged"))
        assertTrue(viewModel.contains("userPresenceConfirmed"))
        assertTrue(screen.contains("Crear raíz criptográfica del Body"))
        assertTrue(screen.contains("Huella confirmada por canal independiente"))
        assertTrue(screen.contains("Fijar Guardian trust anchor"))
        assertFalse(preparation.contains("provisionBeforeBirth("))
        assertFalse(viewModel.contains("birthLocalIdentity"))
        assertFalse(viewModel.contains("bornInstance("))
    }

    @Test
    fun rawGuardianBytesNeverEnterComposeUiState() {
        val state = sourceFile(
            "src/main/java/com/morimil/app/ui/GenesisUltraPreBirthProvisioningUiState.kt"
        ).readText()
        val screen = sourceFile(
            "src/main/java/com/morimil/app/ui/OnboardingScreen.kt"
        ).readText()

        assertTrue(state.contains("guardianPublicKeyRef"))
        assertFalse(state.contains("ByteArray"))
        assertFalse(state.contains("rawPublicKey"))
        assertFalse(screen.contains("copyRawPublicKey"))
        assertFalse(screen.contains("rawPublicKey"))
    }

    private fun sourceFile(relativePath: String): File {
        return sequenceOf(
            File(relativePath),
            File("app/$relativePath")
        ).firstOrNull(File::isFile)
            ?: error("Source file not found: $relativePath")
    }
}
