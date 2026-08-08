package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class F1CanonicalConsumerConvergenceContractTest {
    @Test
    fun inventoryRecordsOrchIntegrationWithoutClosingIssue86() {
        val inventory = inventoryFile().readText()
        assertTrue(inventory.startsWith("# Document status: CURRENT"))
        assertTrue(inventory.contains("Inventory version: `9`"))
        listOf(
            CONTENT_BASELINE_SHA,
            CONTENT_BASELINE_PARENT_SHA,
            "CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF",
            "MERGE_SHA_EVIDENCE=EXTERNAL",
            COG_AUDITED_SOURCE_HEAD,
            ORCH_AUDITED_SOURCE_HEAD,
            ORCH_001_AUDITED_SOURCE_HEAD,
            AGENT_AUDITED_SOURCE_HEAD,
            BOOT_AUDITED_SOURCE_HEAD,
            RECALL_AUDITED_SOURCE_HEAD,
            "PR_178=MERGED_BY_SQUASH_HISTORICAL",
            "PR_179=MERGED_BY_SQUASH_HISTORICAL",
            "PR_180=MERGED_BY_SQUASH_HISTORICAL",
            "F1_RECALL_001=INTEGRATED_IN_MAIN",
            "RECALL_CANONICAL_READ_PATH=INTEGRATED_IN_MAIN",
            "F1_ORCH_001=INTEGRATED_IN_MAIN",
            "ORCH_001_CANONICAL_IDENTITY_GATE=INTEGRATED_IN_MAIN",
            "RECALL_BOOT_READINESS=OPEN",
            "ISSUE_86=OPEN"
        ).forEach { token -> assertTrue("Missing F1 token $token", inventory.contains(token)) }
        assertTrue(inventory.contains("This document does not close `#86`"))
        assertFalse(inventory.contains("F1_RECALL_001=OPEN"))
        assertFalse(inventory.contains("F1_ORCH_001=OPEN"))
    }

    @Test
    fun canonicalBoundaryRemainsSingleAndIncludesRecallConsumer() {
        val inventory = inventoryFile().readText()
        val canonicalComposition = repositoryFile("app/src/main/java/com/morimil/app/MorimilAppContainerCanonicalConsumers.kt").readText()
        val recall = repositoryFile("app/src/main/java/com/morimil/app/data/repository/RecallScheduleRepository.kt").readText()
        assertTrue(inventory.contains("GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository"))
        assertTrue(inventory.contains("-> CanonicalConsumerReadPort"))
        assertTrue(canonicalComposition.contains("GenesisUltraCanonicalConsumerReadAdapter.production"))
        assertTrue(recall.contains("CanonicalConsumerReadPort"))
        assertTrue(recall.contains("readRecallCandidates"))
        listOf("loadGenesisCore(", "loadLocalIdentity(", "loadMemoryContext(", "local_instance_pending").forEach {
            assertFalse("Legacy recall dependency returned: $it", recall.contains(it))
        }
    }

    @Test
    fun orchSeedUsesCanonicalIdentityAndRemainingLegacyDependenciesStayVisible() {
        val inventory = inventoryFile().readText()
        val orch = repositoryFile("app/src/main/java/com/morimil/app/data/repository/AgentOrchestrationRepository.kt").readText()
        listOf(
            "RestCycleRepository",
            "LocalNervousSystemRepository",
            "RECALL_BOOT_READINESS=OPEN",
            "REST_PLANNING_CONVERGED=false",
            "HEALTH_CONVERGED=false"
        ).forEach { token -> assertTrue("Missing remaining convergence token $token", inventory.contains(token)) }
        assertTrue(orch.contains("identityRepository.readCommittedIdentity() ?: return"))
        assertFalse(orch.contains("memoryRepository.hasCompleteBirth()"))
        assertFalse(orch.contains("private val memoryRepository: MemoryRepository"))
        assertTrue(inventory.contains("BOOT still reports `recallState=WAITING_FOR_CANONICAL_MEMORY_ADAPTER`"))
    }

    @Test
    fun compatibilityAuthorityRowsRemainForbiddenAndF33Open() {
        val inventory = inventoryFile().readText()
        listOf(
            "genesis_core",
            "local_instance_identity",
            "memory_events",
            "F3_3=OPEN",
            "instanceId != bodyId",
            "writer authorization != ownership"
        ).forEach { assertTrue("Missing prohibition/open token $it", inventory.contains(it)) }
    }

    private fun inventoryFile(): File = repositoryFile("docs/F1_CANONICAL_CONSUMER_CONVERGENCE.md")

    private fun repositoryFile(relativePath: String): File =
        sequenceOf(File(relativePath), File("../$relativePath")).firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")

    private companion object {
        const val CONTENT_BASELINE_SHA = "CONTENT_BASELINE_SHA=6e0444b698bdc5c557ec3ea83f48d7980da1a36b"
        const val CONTENT_BASELINE_PARENT_SHA = "CONTENT_BASELINE_PARENT_SHA=bdbb5b2a040b728508948cd3cfbd8807b40a12f6"
        const val COG_AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        const val ORCH_AUDITED_SOURCE_HEAD = "0348dccb561e576d17c45e7f8b1e38717332772b"
        const val ORCH_001_AUDITED_SOURCE_HEAD = "fe188fdee8eae901434a255051b6fa4f852b929b"
        const val AGENT_AUDITED_SOURCE_HEAD = "74e072b911db692041d3716af9d0511b83ad70b7"
        const val BOOT_AUDITED_SOURCE_HEAD = "c7710635fa172108cce87b3f7a76d6e037095864"
        const val RECALL_AUDITED_SOURCE_HEAD = "fae8a0df3c29775317986877bce2b8eda8593d27"
    }
}
