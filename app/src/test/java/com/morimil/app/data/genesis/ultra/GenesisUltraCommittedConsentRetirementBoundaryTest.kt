package com.morimil.app.data.genesis.ultra

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraCommittedConsentRetirementBoundaryTest {
    @Test
    fun retirementRequiresDurableCommittedAuthorizationAndCannotExecute() {
        val source = sourceFile(
            "src/main/java/com/morimil/app/data/genesis/ultra/" +
                "GenesisUltraCommittedConsentRetirementCoordinator.kt"
        ).readText()

        assertTrue(source.contains("GenesisUltraAuthorizedBirthStateAudit"))
        assertTrue(source.contains("loadCommittedAuthorization"))
        assertTrue(source.contains("authorization.consentDigest"))
        assertTrue(source.contains("GenesisUltraPersistedBirthState.COMMITTED"))
        assertTrue(source.contains("GenesisUltraPersistedBirthState.ABSENT"))
        assertTrue(source.contains("GenesisUltraCommittedConsentRetirementResult.NOT_APPLICABLE"))
        assertFalse(source.contains("GenesisUltraAtomicBirthExecutionCoordinator"))
        assertFalse(source.contains("GenesisUltraAtomicBirthActivationCoordinator"))
        assertFalse(source.contains("GenesisUltraAuthorizedAtomicBirth"))
        assertFalse(source.contains(".execute("))
        assertFalse(source.contains(".activate("))
        assertFalse(source.contains("insertBirth"))
    }

    @Test
    fun preparationRunsRetirementBeforeReadingDurableFacts() {
        val coordinator = sourceFile(
            "src/main/java/com/morimil/app/data/genesis/ultra/" +
                "GenesisUltraBirthPreparationCoordinator.kt"
        ).readText()
        val container = sourceFile(
            "src/main/java/com/morimil/app/MorimilAppContainer.kt"
        ).readText()

        val inspect = coordinator
            .substringAfter("suspend fun inspect()")
            .substringBeforeLast("}")
        val maintenanceIndex = inspect.indexOf("beforeInspect()")
        val factsIndex = inspect.indexOf("GenesisUltraBirthPreparationFacts(")
        assertTrue(maintenanceIndex >= 0)
        assertTrue(factsIndex >= 0)
        assertTrue(maintenanceIndex < factsIndex)
        assertTrue(container.contains("genesisUltraCommittedConsentRetirementCoordinator.retireIfCommitted()"))
        assertFalse(coordinator.contains("GenesisUltraAtomicBirthExecutionCoordinator"))
        assertFalse(coordinator.contains(".execute("))
    }

    private fun sourceFile(relativePath: String): File {
        return sequenceOf(
            File(relativePath),
            File("app/$relativePath")
        ).firstOrNull(File::isFile)
            ?: error("Source file not found: $relativePath")
    }
}
