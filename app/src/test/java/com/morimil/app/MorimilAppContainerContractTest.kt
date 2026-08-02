package com.morimil.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MorimilAppContainerContractTest {
    @Test
    fun appContainerExposesCriticalRuntimeDependenciesFromOneRoot() {
        val methodNames = MorimilAppContainer::class.java.methods.map { method -> method.name }.toSet()

        assertTrue(methodNames.contains("getMemoryDatabase"))
        assertTrue(methodNames.contains("getOrganDatabase"))
        assertTrue(methodNames.contains("getMemorySignatureEpochPolicy"))
        assertTrue(methodNames.contains("getMemoryEventSigner"))
        assertTrue(methodNames.contains("getMemoryIntegrityCore"))
        assertTrue(methodNames.contains("getMemoryRepository"))
        assertTrue(methodNames.contains("getRestCycleRepository"))
        assertTrue(methodNames.contains("getMemoryOrganRepository"))
        assertTrue(methodNames.contains("getCognitiveMigrationRepository"))
        assertTrue(methodNames.contains("getRecallScheduleRepository"))
        assertTrue(methodNames.contains("getAppendLivingMemoryUseCase"))
        assertTrue(methodNames.contains("getRunRestCycleUseCase"))
        assertTrue(methodNames.contains("getProposeCognitiveMigrationUseCase"))
    }

    @Test
    fun canonicalConsumerCompositionOwnsOneAdapterConstructionAndCogReusesIt() {
        val root = repositoryRoot()
        val canonicalConsumers = File(
            root,
            "app/src/main/java/com/morimil/app/MorimilAppContainerCanonicalConsumers.kt"
        ).readText()
        val cognitiveMigration = File(
            root,
            "app/src/main/java/com/morimil/app/" +
                "MorimilAppContainerCognitiveMigrationProtocol.kt"
        ).readText()
        val productionConstructionCount = File(root, "app/src/main/java")
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .sumOf { file ->
                Regex("""\bGenesisUltraCanonicalConsumerReadAdapter\.production\s*\(""")
                    .findAll(file.readText())
                    .count()
            }

        assertEquals(1, productionConstructionCount)
        assertTrue(
            canonicalConsumers.contains(
                "internal val MorimilAppContainer.canonicalConsumerReadPort"
            )
        )
        assertTrue(canonicalConsumers.contains("CanonicalConsumerReadPort"))
        assertTrue(canonicalConsumers.contains("GenesisUltraCanonicalConsumerReadAdapter.production"))
        assertTrue(
            canonicalConsumers.contains(
                "identityRepository = genesisUltraRuntimeIdentityRepository"
            )
        )
        assertTrue(canonicalConsumers.contains("memoryRepository = canonicalMemoryRepository"))
        assertTrue(cognitiveMigration.contains("consumerReadPort = canonicalConsumerReadPort"))
        assertFalse(cognitiveMigration.contains("GenesisUltraCanonicalConsumerReadAdapter"))
        assertTrue(cognitiveMigration.contains("CanonicalCognitiveMigrationReadPort.production"))
        assertTrue(cognitiveMigration.contains("CanonicalCognitiveMigrationCommitPort"))
        assertTrue(cognitiveMigration.contains("CognitiveMigrationProtocolFinalizer"))
        assertTrue(cognitiveMigration.contains("CrossDatabaseOperationCoordinator.production"))
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
