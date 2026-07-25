package com.morimil.app.data.genesis.ultra

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraAtomicBirthWitnessArchiveBoundaryTest {
    @Test
    fun transportReaderUsesLocalTimeAndCannotAuthorizeExecuteOrInjectTrust() {
        val source = sourceFile(
            "src/main/java/com/morimil/app/data/genesis/ultra/" +
                "GenesisUltraAtomicBirthWitnessArchiveReader.kt"
        ).readText()

        assertTrue(source.contains("GenesisUltraAtomicBirthWitnessPackage"))
        assertTrue(source.contains("expectedCandidateDigest"))
        assertTrue(source.contains("expectedConsentDigest"))
        assertTrue(source.contains("evaluatedAt: String"))
        assertFalse(source.contains("\"evaluated_at\""))
        assertFalse(source.contains("GenesisUltraAtomicBirthAuthorizationCoordinator"))
        assertFalse(source.contains("GenesisUltraAuthorizedAtomicBirth"))
        assertFalse(source.contains("GenesisUltraAtomicBirthExecutionCoordinator"))
        assertFalse(source.contains("GenesisUltraAndroidBodyIdentityRootStore"))
        assertFalse(source.contains("GenesisUltraAndroidGuardianTrustAnchorStore"))
        assertFalse(source.contains("GenesisUltraAndroidHostBirthConsentStore"))
        assertFalse(source.contains("MorimilDatabase"))
        assertFalse(source.contains("AndroidKeystore"))
        assertFalse(source.contains(".execute("))
        assertFalse(source.contains("withTransaction"))
    }

    @Test
    fun importCoordinatorMayAuthorizeButCannotExecuteOrPersist() {
        val source = sourceFile(
            "src/main/java/com/morimil/app/data/genesis/ultra/" +
                "GenesisUltraAtomicBirthWitnessAuthorizationCoordinator.kt"
        ).readText()

        assertTrue(source.contains("archiveReader.read"))
        assertTrue(source.contains("authorizationCoordinator.authorize"))
        assertTrue(source.contains("GenesisUltraAuthorizedAtomicBirth"))
        assertFalse(source.contains("GenesisUltraAtomicBirthExecutionCoordinator"))
        assertFalse(source.contains("genesisUltraAtomicBirthExecutionCoordinator"))
        assertFalse(source.contains("MorimilDatabase"))
        assertFalse(source.contains("withTransaction"))
        assertFalse(source.contains(".execute("))
    }

    @Test
    fun onboardingRetainsAuthorizationInMemoryAndExecutesOnlyThroughFinalCeremony() {
        val viewModel = sourceFile(
            "src/main/java/com/morimil/app/ui/GenesisUltraOnboardingViewModel.kt"
        ).readText()
        val screen = sourceFile(
            "src/main/java/com/morimil/app/ui/OnboardingScreen.kt"
        ).readText()

        assertTrue(viewModel.contains("private var authorizedBirth"))
        assertTrue(viewModel.contains("authorizeWitnessArchive"))
        assertTrue(viewModel.contains("authorizedBirth = null"))
        assertTrue(viewModel.contains("genesisUltraAtomicBirthExecutionCeremonyCoordinator"))
        assertTrue(viewModel.contains("executionCeremonyCoordinator.execute("))
        assertFalse(viewModel.contains("GenesisUltraAtomicBirthExecutionCoordinator"))
        assertFalse(viewModel.contains("genesisUltraAtomicBirthExecutionCoordinator"))
        assertFalse(viewModel.contains("GenesisUltraAtomicBirthActivationCoordinator"))
        assertTrue(screen.contains("Ceremonia final de nacimiento"))
        assertTrue(screen.contains("executeAuthorizedBirth("))
        assertTrue(screen.contains("retryAllowed = false"))
        assertFalse(screen.contains("Autorización verificada; ejecución aún bloqueada"))
        assertFalse(screen.contains("Button(\n                enabled = false"))
    }

    private fun sourceFile(relativePath: String): File {
        return sequenceOf(
            File(relativePath),
            File("app/$relativePath")
        ).firstOrNull(File::isFile)
            ?: error("Source file not found: $relativePath")
    }
}
