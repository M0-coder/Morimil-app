package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossDatabaseOperationInventoryContractTest {
    @Test
    fun inventoryRecordsPostRecallReadinessCurrentSemantics() {
        val inventory = inventoryFile(repositoryRoot()).readText()
        assertTrue(inventory.startsWith("# Document status: CURRENT"))
        assertTrue(inventory.contains("Inventory version: `14`"))
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
            BOOTSTRAP_HEALTH_AUDITED_SOURCE_HEAD,
            REST_BOOT_001_AUDITED_SOURCE_HEAD,
            HEALTH_AUDITED_SOURCE_HEAD,
            RECALL_BOOT_001_AUDITED_SOURCE_HEAD,
            "PR_184=MERGED_BY_SQUASH_HISTORICAL",
            "PR_186=MERGED_BY_SQUASH_HISTORICAL",
            "PR_187=MERGED_BY_SQUASH_HISTORICAL",
            "PR_188=MERGED_BY_SQUASH_HISTORICAL",
            "PR_189=MERGED_BY_SQUASH_HISTORICAL",
            "PR_190=MERGED_BY_SQUASH_HISTORICAL",
            "PR_191=MERGED_BY_SQUASH_HISTORICAL"
        ).forEach { token -> assertTrue("Missing inventory token $token", inventory.contains(token)) }

        assertTrue(inventory.contains("RestCycleRepository.kt` | `INTEGRATED_PROTOCOL` | REST-001 local consolidation and REST-002 repair-proposal convergence"))
        assertTrue(inventory.contains("RecallScheduleRepository.kt` | `DERIVED_REBUILD` | RECALL-001 canonical derived rebuild and RECALL-BOOT-001 read-only startup readiness are integrated"))
        assertTrue(inventory.contains("`REST-001` | `runLocalRestCycleIfDue`, `approvePlannedRestCycle` | Integrated canonical protocol:"))
        assertTrue(inventory.contains("`REST-002` | `planRestRepairProposalIfNeeded` | Integrated proposal-only canonical protocol:"))
        assertTrue(inventory.contains("rest_cycle.propose_repair"))
        assertTrue(inventory.contains("memory.repair_proposed"))
        assertTrue(inventory.contains("repair_execution=not_implemented"))
        assertTrue(inventory.contains("REST-BOOT-001 reuses `CanonicalConsumerReadPort.readRestCyclePlanningInput` read-only"))
        assertTrue(inventory.contains("RECALL-BOOT-001 adds only a read-only startup disposition"))
        assertTrue(inventory.contains("LocalNervousSystemRepository` is not reclassified as an XOP owner"))
        assertTrue(inventory.contains("CanonicalConsumerReadPort.readHealthInput"))
        val remaining = inventory.substringAfter("## Remaining operations").substringBefore("## Integrated guarantees")
        assertFalse(remaining.contains("REST-001"))
        assertFalse(remaining.contains("REST-002"))
        assertFalse(remaining.contains("RECALL startup readiness"))
        assertFalse(remaining.contains("legacy health consumer boundary"))
        assertTrue(remaining.contains("full F1/F3.2 protected-main reaudit"))
    }

    @Test
    fun everyProductionBoundaryOwnerRemainsInventoried() {
        val root = repositoryRoot()
        val inventory = inventoryFile(root).readText()
        val candidates = discoverBoundaryCandidates(root)
        val owners = candidates - SCANNER_EXCLUDED_PATHS
        assertEquals(EXPECTED_OWNER_PATHS + SCANNER_EXCLUDED_PATHS, candidates)
        assertEquals(EXPECTED_OWNER_PATHS, owners)
        EXPECTED_OWNER_PATHS.forEach { path -> assertTrue(inventory.contains("`$path`")) }
    }

    @Test
    fun integratedHealthAndRecallReadinessWithPendingReauditAreExplicit() {
        val root = repositoryRoot()
        val inventory = inventoryFile(root).readText()
        REQUIRED_ENTRY_POINTS.forEach { (path, entryPoints) ->
            val source = File(root, path).readText()
            entryPoints.forEach { entryPoint ->
                assertTrue(Regex("\\bfun\\s+${Regex.escape(entryPoint)}\\s*\\(").containsMatchIn(source))
                assertTrue(inventory.contains("`$entryPoint`"))
            }
        }
        listOf("COG-001", "ORCH-001", "ORCH-002", "AGENT-001", "AGENT-006", "BOOT-001", "RECALL-001", "REST-001", "REST-002").forEach {
            assertTrue("Missing integrated owner/disposition $it", inventory.contains("`$it`"))
        }
        listOf(
            "REST_REPAIR_PROPOSAL_CONVERGED=true",
            "REST_REPAIR_EXECUTION_IMPLEMENTED=false",
            "REST_BOOT_READINESS=INTEGRATED",
            "RECALL_BOOT_READINESS=INTEGRATED",
            "BOOTSTRAP_HEALTH_DERIVATION=INTEGRATED",
            "HEALTH_LEGACY_CONSUMER_CONVERGENCE=INTEGRATED",
            "HEALTH_CAN_READ_CANONICAL_MEMORY=true",
            "HEALTH_CAN_WRITE_CANONICAL_MEMORY=false",
            "HEALTH_CAN_WRITE_LEGACY_MEMORY_EVENTS=false",
            "HEALTH_CONVERGENCE=OPEN",
            "HEALTH_CONVERGED=false",
            "HEALTH_STATE=DEPENDENCY_DERIVED",
            "F1_F3_2_FULL_REAUDIT=REQUIRED",
            "F3.3"
        ).forEach { token -> assertTrue("Missing readiness/debt token $token", inventory.contains(token)) }
        assertFalse(inventory.contains("LocalNervousSystemRepository.recordHealthCheckIfDegraded"))
        assertFalse(inventory.contains("REST_BOOT_READINESS=OPEN"))
        assertFalse(inventory.contains("RECALL_BOOT_READINESS=OPEN"))
        assertFalse(inventory.contains("HEALTH_STATE=WAITING_FOR_DEPENDENCIES"))
        assertFalse(inventory.contains("HEALTH_CONVERGENCE=INTEGRATED"))
    }

    @Test
    fun previouslyIntegratedGuaranteesRemainExplicit() {
        val inventory = inventoryFile(repositoryRoot()).readText()
        listOf(
            "ORCH-001 does not add an XOP event",
            "targetEventHash` as idempotency key",
            "schedule + local graph link in one `MemoryOrganDatabase` transaction",
            "blocked verification",
            "process-death recovery reuses the exact receipt",
            "autobiographical snapshot remains a rebuildable local projection",
            "a verified empty batch can be READY",
            "readiness never calls recall seeding"
        ).forEach { token -> assertTrue("Missing preserved guarantee $token", inventory.contains(token, true)) }
    }

    private fun discoverBoundaryCandidates(root: File): Set<String> {
        val sourceRoot = File(root, "app/src/main/java")
        return sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val source = file.readText()
                MEMORY_ORGAN_DATABASE_PATTERN.containsMatchIn(source) &&
                    (MORIMIL_DATABASE_PATTERN.containsMatchIn(source) ||
                        MEMORY_REPOSITORY_PATTERN.containsMatchIn(source) ||
                        PROJECT_VAULT_COMMIT_PORT_PATTERN.containsMatchIn(source) ||
                        CROSS_DATABASE_COORDINATOR_DEPENDENCY_PATTERN.containsMatchIn(source) ||
                        CANONICAL_CONSUMER_READ_PORT_PATTERN.containsMatchIn(source))
            }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toSet()
    }

    private fun inventoryFile(root: File): File = File(root, INVENTORY_PATH).also { assertTrue(it.isFile) }

    private fun repositoryRoot(): File =
        sequenceOf(File("."), File("..")).map(File::getCanonicalFile)
            .firstOrNull { File(it, "README.md").isFile && File(it, "app/build.gradle.kts").isFile }
            ?: error("Repository root not found")

    private companion object {
        const val INVENTORY_PATH = "docs/F3_CROSS_DATABASE_OPERATION_INVENTORY.md"
        const val CONTENT_BASELINE_SHA = "CONTENT_BASELINE_SHA=c4b192b8f54b2422ce816dc3542d55adfd44510c"
        const val CONTENT_BASELINE_PARENT_SHA = "CONTENT_BASELINE_PARENT_SHA=9c7325e6f1a21d79b1c3fb58f0b5f81a828fc304"
        const val COG_AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        const val ORCH_AUDITED_SOURCE_HEAD = "0348dccb561e576d17c45e7f8b1e38717332772b"
        const val ORCH_001_AUDITED_SOURCE_HEAD = "fe188fdee8eae901434a255051b6fa4f852b929b"
        const val AGENT_AUDITED_SOURCE_HEAD = "74e072b911db692041d3716af9d0511b83ad70b7"
        const val BOOT_AUDITED_SOURCE_HEAD = "c7710635fa172108cce87b3f7a76d6e037095864"
        const val RECALL_AUDITED_SOURCE_HEAD = "fae8a0df3c29775317986877bce2b8eda8593d27"
        const val REST_001_AUDITED_SOURCE_HEAD = "3661450325237fcadb86098ec16ee45cd039bc0b"
        const val REST_002_AUDITED_SOURCE_HEAD = "2ecca3f48d5e0ef27bd927da3986292daf7f7e2c"
        const val BOOTSTRAP_HEALTH_AUDITED_SOURCE_HEAD = "f1697227241459f316bd562756e15ae3ce02c90d"
        const val REST_BOOT_001_AUDITED_SOURCE_HEAD = "dd7a92a011fd4c453775df6ec307638b05313ec9"
        const val HEALTH_AUDITED_SOURCE_HEAD = "6735e2d1febccf7da560d026d6ddd88f6ad82845"
        const val RECALL_BOOT_001_AUDITED_SOURCE_HEAD = "20d834e1d438fd5883a76e9b45bcf21860e7db42"
        const val BOOTSTRAP_PATH = "app/src/main/java/com/morimil/app/runtime/GenesisUltraRuntimeBootstrapCoordinator.kt"
        const val BOOTSTRAP_FINALIZER_PATH = "app/src/main/java/com/morimil/app/data/repository/RuntimeBootstrapProtocolFinalizer.kt"

        val MEMORY_ORGAN_DATABASE_PATTERN = Regex("\\bMemoryOrganDatabase\\b")
        val MORIMIL_DATABASE_PATTERN = Regex("\\bMorimilDatabase\\b")
        val MEMORY_REPOSITORY_PATTERN = Regex("\\bMemoryRepository\\b")
        val PROJECT_VAULT_COMMIT_PORT_PATTERN = Regex("\\bProjectVaultCommitPort\\b")
        val CROSS_DATABASE_COORDINATOR_DEPENDENCY_PATTERN = Regex("\\bprivate\\s+val\\s+protocol\\s*:\\s*CrossDatabaseOperationCoordinator\\b")
        val CANONICAL_CONSUMER_READ_PORT_PATTERN = Regex("\\bCanonicalConsumerReadPort\\b")

        val EXPECTED_OWNER_PATHS = setOf(
            "app/src/main/java/com/morimil/app/data/repository/ProjectVaultRepository.kt",
            BOOTSTRAP_PATH,
            BOOTSTRAP_FINALIZER_PATH,
            "app/src/main/java/com/morimil/app/data/repository/RecallScheduleRepository.kt",
            "app/src/main/java/com/morimil/app/data/repository/RestCycleRepository.kt",
            "app/src/main/java/com/morimil/app/data/repository/CognitiveMigrationRepository.kt",
            "app/src/main/java/com/morimil/app/data/repository/AgentOrchestrationRepository.kt",
            "app/src/main/java/com/morimil/app/data/repository/AgentInstanceLifecycleRepository.kt",
            "app/src/main/java/com/morimil/app/data/repository/MigrationRecordRepository.kt"
        )

        val REQUIRED_ENTRY_POINTS = mapOf(
            "app/src/main/java/com/morimil/app/data/repository/ProjectVaultRepository.kt" to setOf("createProjectVaultFromIntent", "completeProjectVault", "archiveProjectVault"),
            BOOTSTRAP_PATH to setOf("bootstrap"),
            "app/src/main/java/com/morimil/app/data/repository/RecallScheduleRepository.kt" to setOf("seedFromRecentMemoryIfNeeded"),
            "app/src/main/java/com/morimil/app/data/repository/RestCycleRepository.kt" to setOf("runLocalRestCycleIfDue", "approvePlannedRestCycle", "planRestRepairProposalIfNeeded"),
            "app/src/main/java/com/morimil/app/data/repository/CognitiveMigrationRepository.kt" to setOf("proposeCognitiveMigration", "approveCognitiveMigration", "executeCognitiveMigration", "rollbackCognitiveMigration"),
            "app/src/main/java/com/morimil/app/data/repository/AgentOrchestrationRepository.kt" to setOf("seedDefaultOrchestrationIfNeeded", "proposeDelegatedTask", "approveDelegatedTask", "rejectDelegatedTask"),
            "app/src/main/java/com/morimil/app/data/repository/AgentInstanceLifecycleRepository.kt" to setOf("createAgentForVault", "assignTaskToAgent", "submitAgentResult", "evaluateAgent", "retireAgent", "quarantineAgent", "promoteAgent"),
            "app/src/main/java/com/morimil/app/data/repository/MigrationRecordRepository.kt" to setOf("planMigration", "markMigrationApproved", "markMigrationCompleted", "markMigrationFailed", "markMigrationRolledBack")
        )

        val SCANNER_EXCLUDED_PATHS = setOf("app/src/main/java/com/morimil/app/MorimilAppContainer.kt")
    }
}
