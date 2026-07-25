package com.morimil.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraAtomicBirthExecutionCeremonyBoundaryTest {
    @Test
    fun onboardingReceivesOnlyTheFinalCeremonyFacade() {
        val viewModel = sourceFile(
            "src/main/java/com/morimil/app/ui/GenesisUltraOnboardingViewModel.kt"
        ).readText()
        val screen = sourceFile(
            "src/main/java/com/morimil/app/ui/OnboardingScreen.kt"
        ).readText()

        assertTrue(viewModel.contains("genesisUltraAtomicBirthExecutionCeremonyCoordinator"))
        assertTrue(viewModel.contains("executionCeremonyCoordinator.execute("))
        assertFalse(viewModel.contains("GenesisUltraAtomicBirthExecutionCoordinator"))
        assertFalse(viewModel.contains("GenesisUltraAtomicBirthActivationCoordinator"))
        assertFalse(viewModel.contains("birthLocalIdentity"))
        assertFalse(viewModel.contains("bornInstance("))

        assertTrue(screen.contains("executeAuthorizedBirth("))
        assertTrue(screen.contains("Ceremonia final de nacimiento"))
        assertFalse(screen.contains("GenesisUltraAtomicBirthExecutionCoordinator"))
        assertFalse(screen.contains("GenesisUltraAtomicBirthActivationCoordinator"))
        assertFalse(screen.contains(".activate("))
    }

    @Test
    fun ceremonyOwnsTheOnlyOnboardingExecutionCallAndPostCommitRetirement() {
        val ceremony = sourceFile(
            "src/main/java/com/morimil/app/data/genesis/ultra/" +
                "GenesisUltraAtomicBirthExecutionCeremonyCoordinator.kt"
        ).readText()
        val container = sourceFile(
            "src/main/java/com/morimil/app/MorimilAppContainer.kt"
        ).readText()

        assertTrue(ceremony.contains("executionCoordinator.execute("))
        assertTrue(ceremony.contains("retireCommittedConsent()"))
        assertTrue(ceremony.contains("GenesisUltraAtomicBirthCommittedReturn"))
        assertTrue(ceremony.contains("COMMITTED_MAINTENANCE_PENDING"))
        assertTrue(ceremony.contains("firstPostBirthRequest = memoryRequest"))
        assertFalse(ceremony.contains("birthLocalIdentity"))
        assertFalse(ceremony.contains("GenesisCore"))
        assertFalse(ceremony.contains("LocalInstanceIdentity"))

        assertTrue(
            container.contains(
                "GenesisUltraAtomicBirthExecutionCeremonyCoordinator.production("
            )
        )
        assertTrue(
            container.contains(
                "executionCoordinator = genesisUltraAtomicBirthExecutionCoordinator"
            )
        )
    }

    @Test
    fun viewModelPublishesExecutionLockBeforeLaunchingAndDiscardsTypeStateAfterCommit() {
        val viewModel = sourceFile(
            "src/main/java/com/morimil/app/ui/GenesisUltraOnboardingViewModel.kt"
        ).readText()
        val method = viewModel
            .substringAfter("internal fun executeAuthorizedBirth(")
            .substringBefore("internal fun revokeHostConsent()")

        val lockIndex = method.indexOf(
            "_atomicBirthExecution.value = GenesisUltraAtomicBirthExecutionUiState(executing = true)"
        )
        val launchIndex = method.indexOf("viewModelScope.launch(Dispatchers.IO)")
        val discardIndex = method.indexOf("discardPreBirthSessionAfterCommit()")
        val inspectIndex = method.indexOf("inspectDurableStateAfterCommit()")

        assertTrue(lockIndex >= 0)
        assertTrue(launchIndex > lockIndex)
        assertTrue(discardIndex > launchIndex)
        assertTrue(inspectIndex > discardIndex)
        assertTrue(viewModel.contains("authorizedBirth = null"))
        assertTrue(viewModel.contains("candidateSession = null"))
    }

    private fun sourceFile(relativePath: String): File {
        return sequenceOf(
            File(relativePath),
            File("app/$relativePath")
        ).firstOrNull(File::isFile)
            ?: error("Source file not found: $relativePath")
    }
}
