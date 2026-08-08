package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class F1CanonicalConsumerConvergenceContractTest {
    @Test
    fun inventoryRecordsCogOrchAndAgentWithoutClosingIssue86() {
        val inventory = inventoryFile().readText()
        assertTrue(inventory.startsWith("# Document status: CURRENT"))
        assertTrue(inventory.contains("Inventory version: `6`"))
        listOf(
            CONTENT_BASELINE_SHA,
            CONTENT_BASELINE_PARENT_SHA,
            "CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF",
            "MERGE_SHA_EVIDENCE=EXTERNAL",
            COG_AUDITED_SOURCE_HEAD,
            ORCH_AUDITED_SOURCE_HEAD,
            AGENT_AUDITED_SOURCE_HEAD,
            "PR_174=MERGED_BY_SQUASH_HISTORICAL",
            "F3_COG_CONSUMER_OF_F1_A=INTEGRATED_IN_MAIN",
            "ORCH_002_004_CANONICAL_WRITE_PATH=INTEGRATED_IN_MAIN",
            "AGENT_001_006_CANONICAL_WRITE_PATH=INTEGRATED_IN_MAIN",
            "F1_ORCH_001=OPEN",
            "ISSUE_86=OPEN"
        ).forEach { token -> assertTrue("Missing F1 token $token", inventory.contains(token)) }
        assertTrue(inventory.contains("This document does not close `#86`"))
    }

    @Test
    fun canonicalBoundaryRemainsSingleAndIncludesAgentCommitAdapter() {
        val inventory = inventoryFile().readText()
        val canonicalComposition = repositoryFile("app/src/main/java/com/morimil/app/MorimilAppContainerCanonicalConsumers.kt").readText()
        val protocolComposition = repositoryFile("app/src/main/java/com/morimil/app/MorimilAppContainerCognitiveMigrationProtocol.kt").readText()
        assertTrue(inventory.contains("GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository"))
        assertTrue(inventory.contains("-> CanonicalConsumerReadPort"))
        assertTrue(inventory.contains("CanonicalCognitiveMigrationCommitPort"))
        assertTrue(inventory.contains("CanonicalOrchestrationCommitPort"))
        assertTrue(inventory.contains("CanonicalAgentLifecycleCommitPort"))
        assertTrue(canonicalComposition.contains("GenesisUltraCanonicalConsumerReadAdapter.production"))
        assertTrue(protocolComposition.contains("CanonicalAgentLifecycleCommitPort"))
        assertFalse(protocolComposition.contains("GenesisUltraCanonicalConsumerReadAdapter.production"))
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
        ).forEach { token -> assertTrue("Missing remaining convergence token $token", inventory.contains(token)) }
        assertTrue(inventory.contains("PR #172 and PR #174 do not close this item"))
    }

    @Test
    fun compatibilityAuthorityRowsRemainForbiddenAndF33Open() {
        val inventory = inventoryFile().readText()
        listOf("genesis_core", "local_instance_identity", "memory_events", "F3_3=OPEN", "instanceId != bodyId").forEach {
            assertTrue("Missing prohibition/open token $it", inventory.contains(it))
        }
    }

    private fun inventoryFile(): File = repositoryFile("docs/F1_CANONICAL_CONSUMER_CONVERGENCE.md")

    private fun repositoryFile(relativePath: String): File =
        sequenceOf(File(relativePath), File("../$relativePath")).firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")

    private companion object {
        const val CONTENT_BASELINE_SHA = "CONTENT_BASELINE_SHA=d577a75290d70f423f6e83bf237a8a453f3a534e"
        const val CONTENT_BASELINE_PARENT_SHA = "CONTENT_BASELINE_PARENT_SHA=9da342f2c147105ea882076f4ebc6ab5f5494190"
        const val COG_AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        const val ORCH_AUDITED_SOURCE_HEAD = "0348dccb561e576d17c45e7f8b1e38717332772b"
        const val AGENT_AUDITED_SOURCE_HEAD = "74e072b911db692041d3716af9d0511b83ad70b7"
    }
}
