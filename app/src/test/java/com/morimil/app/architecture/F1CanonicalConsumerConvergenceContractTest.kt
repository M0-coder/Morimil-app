package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class F1CanonicalConsumerConvergenceContractTest {
    @Test
    fun inventoryRecordsBootIntegrationWithoutClosingIssue86() {
        val inventory = inventoryFile().readText()
        assertTrue(inventory.startsWith("# Document status: CURRENT"))
        assertTrue(inventory.contains("Inventory version: `7`"))
        listOf(
            CONTENT_BASELINE_SHA,
            CONTENT_BASELINE_PARENT_SHA,
            "CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF",
            "MERGE_SHA_EVIDENCE=EXTERNAL",
            COG_AUDITED_SOURCE_HEAD,
            ORCH_AUDITED_SOURCE_HEAD,
            AGENT_AUDITED_SOURCE_HEAD,
            BOOT_AUDITED_SOURCE_HEAD,
            "PR_176=MERGED_BY_SQUASH_HISTORICAL",
            "F3_COG_CONSUMER_OF_F1_A=INTEGRATED_IN_MAIN",
            "ORCH_002_004_CANONICAL_WRITE_PATH=INTEGRATED_IN_MAIN",
            "AGENT_001_006_CANONICAL_WRITE_PATH=INTEGRATED_IN_MAIN",
            "BOOT_001_CANONICAL_WRITE_PATH=INTEGRATED_IN_MAIN",
            "BOOT_CONVERGED=true",
            "F1_ORCH_001=OPEN",
            "ISSUE_86=OPEN"
        ).forEach { token -> assertTrue("Missing F1 token $token", inventory.contains(token)) }
        assertTrue(inventory.contains("This document does not close `#86`"))
        assertFalse(inventory.contains("BOOT_CONVERGED=false"))
    }

    @Test
    fun canonicalBoundaryRemainsSingleAndIncludesBootCommitAdapter() {
        val inventory = inventoryFile().readText()
        val canonicalComposition = repositoryFile("app/src/main/java/com/morimil/app/MorimilAppContainerCanonicalConsumers.kt").readText()
        val protocolComposition = repositoryFile("app/src/main/java/com/morimil/app/MorimilAppContainerCognitiveMigrationProtocol.kt").readText()
        val bootComposition = repositoryFile("app/src/main/java/com/morimil/app/MorimilAppContainerRuntimeBootstrapProtocol.kt").readText()
        assertTrue(inventory.contains("GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository"))
        assertTrue(inventory.contains("-> CanonicalConsumerReadPort"))
        assertTrue(inventory.contains("CanonicalCognitiveMigrationCommitPort"))
        assertTrue(inventory.contains("CanonicalOrchestrationCommitPort"))
        assertTrue(inventory.contains("CanonicalAgentLifecycleCommitPort"))
        assertTrue(inventory.contains("CanonicalRuntimeBootstrapCommitPort"))
        assertTrue(canonicalComposition.contains("GenesisUltraCanonicalConsumerReadAdapter.production"))
        assertTrue(protocolComposition.contains("CanonicalAgentLifecycleCommitPort"))
        assertTrue(bootComposition.contains("CanonicalRuntimeBootstrapCommitPort"))
        assertFalse(protocolComposition.contains("GenesisUltraCanonicalConsumerReadAdapter.production"))
    }

    @Test
    fun remainingConsumersAndLegacyDependenciesStayVisibleAfterBoot() {
        val inventory = inventoryFile().readText()
        listOf(
            "RecallScheduleRepository",
            "RestCycleRepository",
            "LocalNervousSystemRepository",
            "AgentOrchestrationRepository",
            "loadGenesisCore",
            "loadLocalIdentity",
            "loadMemoryContext",
            "hasCompleteBirth",
            "rest_cycle=canonical_adapter_pending",
            "recalls=canonical_adapter_pending"
        ).forEach { token -> assertTrue("Missing remaining convergence token $token", inventory.contains(token)) }
        assertTrue(inventory.contains("BOOT PR #176 do not close this item"))
        assertFalse(inventory.contains("F1-BOOT-001"))
    }

    @Test
    fun compatibilityAuthorityRowsRemainForbiddenAndF33Open() {
        val inventory = inventoryFile().readText()
        listOf("genesis_core", "local_instance_identity", "memory_events", "F3_3=OPEN", "instanceId != bodyId", "writer authorization != ownership").forEach {
            assertTrue("Missing prohibition/open token $it", inventory.contains(it))
        }
    }

    private fun inventoryFile(): File = repositoryFile("docs/F1_CANONICAL_CONSUMER_CONVERGENCE.md")

    private fun repositoryFile(relativePath: String): File =
        sequenceOf(File(relativePath), File("../$relativePath")).firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")

    private companion object {
        const val CONTENT_BASELINE_SHA = "CONTENT_BASELINE_SHA=3a995232ce2a515e1ca9b9151f77e63805bad9d3"
        const val CONTENT_BASELINE_PARENT_SHA = "CONTENT_BASELINE_PARENT_SHA=5918b64ec83e69cbb3d9718943b25d1e1299d698"
        const val COG_AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        const val ORCH_AUDITED_SOURCE_HEAD = "0348dccb561e576d17c45e7f8b1e38717332772b"
        const val AGENT_AUDITED_SOURCE_HEAD = "74e072b911db692041d3716af9d0511b83ad70b7"
        const val BOOT_AUDITED_SOURCE_HEAD = "c7710635fa172108cce87b3f7a76d6e037095864"
    }
}
