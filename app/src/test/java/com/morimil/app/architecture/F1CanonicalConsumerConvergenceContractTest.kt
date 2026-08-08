package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class F1CanonicalConsumerConvergenceContractTest {
    @Test
    fun inventoryRecordsMergedCogAndOrchConsumersWithoutClosingIssue86() {
        val inventory = inventoryFile().readText()

        assertTrue(inventory.startsWith("# Document status: CURRENT"))
        assertTrue(inventory.contains("Inventory version: `5`"))
        assertTrue(inventory.contains(CONTENT_BASELINE_SHA))
        assertTrue(inventory.contains(CONTENT_BASELINE_PARENT_SHA))
        assertTrue(inventory.contains(CURRENT_MAIN_RESOLUTION))
        assertTrue(inventory.contains(MERGE_SHA_EVIDENCE))
        assertTrue(inventory.contains(COG_AUDITED_SOURCE_HEAD))
        assertTrue(inventory.contains(ORCH_AUDITED_SOURCE_HEAD))
        assertTrue(inventory.contains("PR `#149`: closed and merged by squash"))
        assertTrue(inventory.contains("PR `#150`: closed and merged by squash"))
        assertTrue(inventory.contains("PR `#153`: closed and merged by squash"))
        assertTrue(inventory.contains("PR `#172`: closed and merged by squash"))
        assertTrue(inventory.contains("PR_153=MERGED_BY_SQUASH_HISTORICAL"))
        assertTrue(inventory.contains("PR_172=MERGED_BY_SQUASH_HISTORICAL"))
        assertTrue(inventory.contains("F3_COG_CONSUMER_OF_F1_A=INTEGRATED_IN_MAIN"))
        assertTrue(inventory.contains("ORCH_002_004_CANONICAL_WRITE_PATH=INTEGRATED_IN_MAIN"))
        assertTrue(inventory.contains("F1_ORCH_001=OPEN"))
        assertTrue(inventory.contains("ISSUE_86=OPEN"))
        assertTrue(inventory.contains("This document does not close `#86`"))

        STALE_PHRASES.forEach { phrase ->
            assertFalse("F1 inventory contains stale phrase $phrase", inventory.contains(phrase, true))
        }
    }

    @Test
    fun canonicalBoundaryRemainsSingleAndIntegrated() {
        val inventory = inventoryFile().readText()
        val canonicalComposition = repositoryFile(
            "app/src/main/java/com/morimil/app/MorimilAppContainerCanonicalConsumers.kt"
        ).readText()
        val cognitiveComposition = repositoryFile(
            "app/src/main/java/com/morimil/app/MorimilAppContainerCognitiveMigrationProtocol.kt"
        ).readText()

        assertTrue(inventory.contains("GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository"))
        assertTrue(inventory.contains("-> CanonicalConsumerReadPort"))
        assertTrue(inventory.contains("CognitiveMigrationCanonicalReadPort"))
        assertTrue(inventory.contains("does not create a second identity or memory authority"))
        assertTrue(inventory.contains("CanonicalCognitiveMigrationCommitPort"))
        assertTrue(inventory.contains("CanonicalOrchestrationCommitPort"))
        assertTrue(canonicalComposition.contains("GenesisUltraCanonicalConsumerReadAdapter.production"))
        assertTrue(canonicalComposition.contains("identityRepository = genesisUltraRuntimeIdentityRepository"))
        assertTrue(canonicalComposition.contains("memoryRepository = canonicalMemoryRepository"))
        assertTrue(cognitiveComposition.contains("consumerReadPort = canonicalConsumerReadPort"))
        assertFalse(cognitiveComposition.contains("GenesisUltraCanonicalConsumerReadAdapter.production"))
        assertTrue(cognitiveComposition.contains("CanonicalCognitiveMigrationReadPort.production"))
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
        assertTrue(inventory.contains("PR #172 intentionally does not close this item"))
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
        const val CONTENT_BASELINE_SHA =
            "CONTENT_BASELINE_SHA=c6a6b0ca998d053c31c75977c5b6d4d9ae166e96"
        const val CONTENT_BASELINE_PARENT_SHA =
            "CONTENT_BASELINE_PARENT_SHA=c22920f68f8820bbec676a6cbc74b60548e43d29"
        const val CURRENT_MAIN_RESOLUTION = "CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF"
        const val MERGE_SHA_EVIDENCE = "MERGE_SHA_EVIDENCE=EXTERNAL"
        const val COG_AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        const val ORCH_AUDITED_SOURCE_HEAD = "0348dccb561e576d17c45e7f8b1e38717332772b"
        val STALE_PHRASES = listOf(
            "draft f3 candidate",
            "pr_149=draft_validation_only",
            "candidate not merged",
            "f3.2 open candidate",
            "one downstream consumer family has converged"
        )
    }
}
