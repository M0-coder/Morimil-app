package com.morimil.app

import java.io.File
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
    fun cognitiveMigrationCompositionUsesCanonicalProtocolOnly() {
        val root = sequenceOf(File("."), File(".."))
            .map(File::getCanonicalFile)
            .firstOrNull { candidate ->
                File(candidate, "README.md").isFile &&
                    File(candidate, "app/build.gradle.kts").isFile
            }
            ?: error("Repository root not found")
        val source = File(
            root,
            "app/src/main/java/com/morimil/app/" +
                "MorimilAppContainerCognitiveMigrationProtocol.kt"
        ).readText()

        assertTrue(source.contains("GenesisUltraCanonicalConsumerReadAdapter.production"))
        assertTrue(source.contains("CanonicalCognitiveMigrationReadPort.production"))
        assertTrue(source.contains("CanonicalCognitiveMigrationCommitPort"))
        assertTrue(source.contains("CognitiveMigrationProtocolFinalizer"))
        assertTrue(source.contains("CrossDatabaseOperationCoordinator.production"))
    }
}
