package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CognitiveMigrationProtocolContractTest {
    @Test
    fun cogRepositoryHasNoLegacyCanonicalInput() {
        val source = source("data/repository/CognitiveMigrationRepository.kt").readText()

        listOf(
            "MemoryEventEntity",
            "memoryDatabase",
            "memoryDao",
            "loadGenesisCore",
            "loadLocalIdentity",
            "recordSystemMemoryEvent",
            "auditLivingMemoryChain"
        ).forEach { forbidden ->
            assertFalse("Legacy cognitive input leaked: $forbidden", source.contains(forbidden))
        }
        assertTrue(source.contains("CognitiveMigrationCanonicalReadPort"))
        assertTrue(source.contains("GenesisUltraRuntimeIdentityRepository"))
        assertTrue(source.contains("CrossDatabaseOperationCoordinator"))
    }

    @Test
    fun coordinatorPersistsReceiptBeforeTypedOwnerFinalization() {
        val source = source("data/repository/CrossDatabaseOperationCoordinator.kt").readText()
        val receipt = source.indexOf("persistCanonicalReceipt")
        val pendingLocal = source.indexOf("transitionCanonicalCommitted")
        val finalizer = source.indexOf("finalizeCommitted")

        assertTrue(receipt >= 0)
        assertTrue(pendingLocal > receipt)
        assertTrue(finalizer > pendingLocal)
        assertTrue(source.contains("database.withTransaction"))
        assertTrue(source.contains("markCommittedWithLocalResult"))
    }

    @Test
    fun runtimeGateRecoversCognitiveProtocolBeforeProjectVaultAndBootstrap() {
        val source = sourceAtRoot("MorimilAppContainerRuntimeGate.kt").readText()
        val canonicalRead = source.indexOf("readVerifiedPlanningInput")
        val cognitiveRecovery = source.indexOf("recoverAtStartup")
        val vaultRecovery = source.indexOf("recoverPendingOperations")
        val bootstrap = source.indexOf("bootstrap.bootstrap")

        assertTrue(canonicalRead >= 0)
        assertTrue(cognitiveRecovery > canonicalRead)
        assertTrue(vaultRecovery > cognitiveRecovery)
        assertTrue(bootstrap > vaultRecovery)
    }

    @Test
    fun operationRegistryIsClosedToCog001ThroughCog004() {
        val source = source("data/repository/CrossDatabaseOperationContracts.kt").readText()
        listOf(
            "cognitive_migration.propose",
            "cognitive_migration.approve",
            "cognitive_migration.execute",
            "cognitive_migration.rollback"
        ).forEach { operation -> assertTrue(source.contains(operation)) }
        assertTrue(source.contains("CLOSED_REGISTRY"))
        assertFalse(source.contains("ProjectVault"))
    }

    private fun source(relative: String): File {
        return File(repositoryRoot(), "app/src/main/java/com/morimil/app/$relative")
    }

    private fun sourceAtRoot(filename: String): File {
        return File(repositoryRoot(), "app/src/main/java/com/morimil/app/$filename")
    }

    private fun repositoryRoot(): File {
        return sequenceOf(File("."), File(".."))
            .map(File::getCanonicalFile)
            .firstOrNull { candidate ->
                File(candidate, "README.md").isFile &&
                    File(candidate, "app/build.gradle.kts").isFile
            }
            ?: error("Repository root not found")
    }
}
