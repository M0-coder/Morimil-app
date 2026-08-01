package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class F1CanonicalConsumerConvergenceContractTest {
    @Test
    fun inventoryRecordsMergedCogConsumerWithoutClosingIssue86() {
        val inventory = inventoryFile().readText()

        assertTrue(inventory.startsWith("# Document status: CURRENT"))
        assertTrue(inventory.contains("Inventory version: `3`"))
        assertTrue(inventory.contains(CURRENT_MAIN))
        assertTrue(inventory.contains(AUDITED_SOURCE_HEAD))
        assertTrue(inventory.contains("PR `#149`: closed and merged by squash"))
        assertTrue(inventory.contains("F3_COG_CONSUMER_OF_F1_A=INTEGRATED_IN_MAIN"))
        assertTrue(inventory.contains("ISSUE_86=OPEN"))
        assertTrue(inventory.contains("This document does not close `#86`"))

        STALE_PHRASES.forEach { phrase ->
            assertFalse("F1 inventory contains stale phrase $phrase", inventory.contains(phrase, true))
        }
    }

    @Test
    fun canonicalBoundaryRemainsSingleAndIntegrated() {
        val inventory = inventoryFile().readText()
        val composition = repositoryFile(
            "app/src/main/java/com/morimil/app/MorimilAppContainerCognitiveMigrationProtocol.kt"
        ).readText()

        assertTrue(inventory.contains("GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository"))
        assertTrue(inventory.contains("-> CanonicalConsumerReadPort"))
        assertTrue(inventory.contains("CognitiveMigrationCanonicalReadPort"))
        assertTrue(inventory.contains("does not create a second identity or memory authority"))
        assertTrue(inventory.contains("CanonicalCognitiveMigrationCommitPort"))
        assertTrue(composition.contains("GenesisUltraCanonicalConsumerReadAdapter.production"))
        assertTrue(composition.contains("CanonicalCognitiveMigrationReadPort.production"))
    }

    @Test
    fun remainingConsumersAndLegacyDependenciesStayVisible() {
        val inventory = inventoryFile().readText()

        listOf(
            "GenesisUltraRuntimeBootstrapCoordinator",
            "RecallScheduleRepository",
            "RestCycleRepository",
            "LocalNervousSystemRepository",
            "AgentOrchestrationRepository",
            "WAITING_FOR_CANONICAL_MEMORY_ADAPTER",
            "loadGenesisCore",
            "loadLocalIdentity",
            "loadMemoryContext",
            "hasCompleteBirth"
        ).forEach { token ->
            assertTrue("Missing remaining convergence token $token", inventory.contains(token))
        }
    }

    @Test
    fun compatibilityRowsRemainForbiddenAndF33Open() {
        val inventory = inventoryFile().readText()

        assertTrue(inventory.contains("Compatibility rows are forbidden"))
        assertTrue(inventory.contains("genesis_core"))
        assertTrue(inventory.contains("local_instance_identity"))
        assertTrue(inventory.contains("memory_events"))
        assertTrue(inventory.contains("local_instance_pending"))
        assertTrue(inventory.contains("F3_3=OPEN"))
        assertTrue(inventory.contains("instanceId != bodyId"))
    }

    private fun inventoryFile(): File =
        repositoryFile("docs/F1_CANONICAL_CONSUMER_CONVERGENCE.md")

    private fun repositoryFile(relativePath: String): File {
        return sequenceOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")
    }

    private companion object {
        const val CURRENT_MAIN = "5023981da7caf31c8f3679919f59205708b72823"
        const val AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        val STALE_PHRASES = listOf(
            "draft f3 candidate",
            "pr_149=draft_validation_only",
            "candidate not merged",
            "f3.2 open candidate"
        )
    }
}
