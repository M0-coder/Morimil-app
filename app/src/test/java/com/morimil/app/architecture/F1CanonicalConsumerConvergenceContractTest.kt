package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class F1CanonicalConsumerConvergenceContractTest {
    @Test
    fun inventoryRecordsRest002IntegrationWithoutClosingIssue86() {
        val inventory = inventoryFile().readText()
        assertTrue(inventory.startsWith("# Document status: CURRENT"))
        assertTrue(inventory.contains("Inventory version: `11`"))
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
            REST_001_AUDITED_SOURCE_HEAD,
            REST_002_AUDITED_SOURCE_HEAD,
            "PR_182=MERGED_BY_SQUASH_HISTORICAL",
            "PR_183=MERGED_BY_SQUASH_HISTORICAL",
            "PR_184=MERGED_BY_SQUASH_HISTORICAL",
            "F1_RECALL_001=INTEGRATED_IN_MAIN",
            "F1_ORCH_001=INTEGRATED_IN_MAIN",
            "F1_REST_001=INTEGRATED_IN_MAIN",
            "REST_001_CANONICAL_XOP=INTEGRATED_IN_MAIN",
            "F1_REST_002=INTEGRATED_IN_MAIN",
            "REST_002_CANONICAL_PROPOSAL_XOP=INTEGRATED_IN_MAIN",
            "REST_PLANNING_CONVERGED=true",
            "REST_EXECUTION_CONVERGED=true",
            "REST_REPAIR_PROPOSAL_CONVERGED=true",
            "REST_REPAIR_EXECUTION_IMPLEMENTED=false",
            "REST_BOOT_READINESS=OPEN",
            "RECALL_BOOT_READINESS=OPEN",
            "ISSUE_86=OPEN"
        ).forEach { token -> assertTrue("Missing F1 token $token", inventory.contains(token)) }
        assertTrue(inventory.contains("This document does not close `#86`"))
        assertFalse(inventory.contains("F1_RECALL_001=OPEN"))
        assertFalse(inventory.contains("F1_ORCH_001=OPEN"))
        assertFalse(inventory.contains("F1_REST_001=OPEN"))
        assertFalse(inventory.contains("REST_002=OPEN"))
        assertFalse(inventory.contains("REST_001_002=OPEN"))
    }

    @Test
    fun canonicalBoundaryRemainsSingleAndIncludesRestConsumer() {
        val inventory = inventoryFile().readText()
        val canonicalComposition = repositoryFile("app/src/main/java/com/morimil/app/MorimilAppContainerCanonicalConsumers.kt").readText()
        val rest = repositoryFile("app/src/main/java/com/morimil/app/data/repository/RestCycleRepository.kt").readText()
        assertTrue(inventory.contains("GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository"))
        assertTrue(inventory.contains("-> CanonicalConsumerReadPort"))
        assertTrue(inventory.contains("CanonicalRestCycleCommitPort"))
        assertTrue(canonicalComposition.contains("GenesisUltraCanonicalConsumerReadAdapter.production"))
        assertTrue(rest.contains("CanonicalConsumerReadPort"))
        assertTrue(rest.contains("readRestCyclePlanningInput"))
        assertTrue(rest.contains("RestCycleProtocolTypes.OWNER_TYPE"))
        listOf(
            "loadGenesisCore(",
            "loadLocalIdentity(",
            "loadMemoryContext(",
            "loadMemoryEventAuditChain(",
            "MemoryRepository",
            "recordSystemMemoryEvent("
        ).forEach { token -> assertFalse("Legacy REST dependency returned: $token", rest.contains(token)) }
    }

    @Test
    fun rest002ProposalIsIntegratedWhileHealthAndStartupReadinessRemainOpen() {
        val inventory = inventoryFile().readText()
        listOf(
            "LocalNervousSystemRepository",
            "REST_BOOT_READINESS=OPEN",
            "RECALL_BOOT_READINESS=OPEN",
            "HEALTH_CONVERGED=false",
            "REST_REPAIR_EXECUTION_IMPLEMENTED=false"
        ).forEach { token -> assertTrue("Missing remaining convergence token $token", inventory.contains(token)) }
        assertTrue(inventory.contains("REST_REPAIR_PROPOSAL_CONVERGED=true"))
        assertTrue(inventory.contains("rest_cycle.propose_repair"))
        assertTrue(inventory.contains("memory.repair_proposed"))
        assertTrue(inventory.contains("repair_execution=not_implemented"))
        assertTrue(inventory.contains("restCycleState=WAITING_FOR_CANONICAL_MEMORY_ADAPTER"))
        assertTrue(inventory.contains("recallState=WAITING_FOR_CANONICAL_MEMORY_ADAPTER"))
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
        const val CONTENT_BASELINE_SHA = "CONTENT_BASELINE_SHA=e05ae7a08b1a88d2fbc0d4f2dff8ff06d282c908"
        const val CONTENT_BASELINE_PARENT_SHA = "CONTENT_BASELINE_PARENT_SHA=9585e94a690d4f00d591f81d14e56aedefda3341"
        const val COG_AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        const val ORCH_AUDITED_SOURCE_HEAD = "0348dccb561e576d17c45e7f8b1e38717332772b"
        const val ORCH_001_AUDITED_SOURCE_HEAD = "fe188fdee8eae901434a255051b6fa4f852b929b"
        const val AGENT_AUDITED_SOURCE_HEAD = "74e072b911db692041d3716af9d0511b83ad70b7"
        const val BOOT_AUDITED_SOURCE_HEAD = "c7710635fa172108cce87b3f7a76d6e037095864"
        const val RECALL_AUDITED_SOURCE_HEAD = "fae8a0df3c29775317986877bce2b8eda8593d27"
        const val REST_001_AUDITED_SOURCE_HEAD = "3661450325237fcadb86098ec16ee45cd039bc0b"
        const val REST_002_AUDITED_SOURCE_HEAD = "2ecca3f48d5e0ef27bd927da3986292daf7f7e2c"
    }
}
