package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossDatabaseOperationInventoryContractTest {
    @Test
    fun inventoryRecordsPostRest001CurrentSemantics() {
        val inventory = inventoryFile(repositoryRoot()).readText()
        assertTrue(inventory.startsWith("# Document status: CURRENT"))
        assertTrue(inventory.contains("Inventory version: `10`"))
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
            "PR_181=MERGED_BY_SQUASH_HISTORICAL",
            "PR_182=MERGED_BY_SQUASH_HISTORICAL"
        ).forEach { token -> assertTrue("Missing inventory token $token", inventory.contains(token)) }

        assertTrue(inventory.contains("RestCycleRepository.kt` | `INTEGRATED_PROTOCOL` | REST-001 integrated"))
        assertTrue(inventory.contains("`REST-001` | `runLocalRestCycleIfDue`, `approvePlannedRestCycle` | Integrated canonical protocol:"))
        assertTrue(inventory.contains("`REST-001` — integrated canonical planning and owner-scoped durable XOP"))
        assertTrue(inventory.contains("`REST-002`"))
        assertFalse(inventory.substringAfter("## Remaining operations").substringBefore("## Integrated guarantees").contains("REST-001"))
        assertTrue(inventory.substringAfter("## Remaining operations").substringBefore("## Integrated guarantees").contains("REST-002"))
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
    fun integratedRest001AndRemainingOwnersAreExplicit() {
        val root = repositoryRoot()
        val inventory = inventoryFile(root).readText()
        REQUIRED_ENTRY_POINTS.forEach { (path, entryPoints) ->
            val source = File(root, path).readText()
            entryPoints.forEach { entryPoint ->
                assertTrue(Regex("\\bfun\\s+${Regex.escape(entryPoint)}\\s*\\(").containsMatchIn(source))
                assertTrue(inventory.contains("`$entryPoint`"))
            }
        }
        listOf("COG-001", "ORCH-001", "ORCH-002", "AGENT-001", "AGENT-006", "BOOT-001", "RECALL-001", "REST-001").forEach {
            assertTrue("Missing integrated owner/disposition $it", inventory.contains("`$it`"))
        }
        assertTrue("Missing remaining REST-002", inventory.contains("`REST-002`"))
        assertTrue(inventory.contains("RECALL_BOOT_READINESS") || inventory.contains("startup-level recall readiness"))
        assertTrue(inventory.contains("HEALTH_CONVERGENCE") || inventory.contains("health convergence", true))
        assertTrue(inventory.contains("F3.3"))
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
        const val CONTENT_BASELINE_SHA = "CONTENT_BASELINE_SHA=2d16c5c3197d492d5daed3707e97a68caa0011a6"
        const val CONTENT_BASELINE_PARENT_SHA = "CONTENT_BASELINE_PARENT_SHA=d7e679b9f8e0b34d44a5e702c02c436f21e4eaee"
        const val COG_AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        const val ORCH_AUDITED_SOURCE_HEAD = "0348dccb561e576d17c45e7f8b1e38717332772b"
        const val ORCH_001_AUDITED_SOURCE_HEAD = "fe188fdee8eae901434a255051b6fa4f852b929b"
        const val AGENT_AUDITED_SOURCE_HEAD = "74e072b911db692041d3716af9d0511b83ad70b7"
        const val BOOT_AUDITED_SOURCE_HEAD = "c7710635fa172108cce87b3f7a76d6e037095864"
        const val RECALL_AUDITED_SOURCE_HEAD = "fae8a0df3c29775317986877bce2b8eda8593d27"
        const val REST_001_AUDITED_SOURCE_HEAD = "3661450325237fcadb86098ec16ee45cd039bc0b"
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
            "app/src/main/java/com/morimil/app/data/repository/RestCycleRepository.kt" to setOf("runLocalRestCycleIfDue", "approvePlannedRestCycle"),
            "app/src/main/java/com/morimil/app/data/repository/CognitiveMigrationRepository.kt" to setOf("proposeCognitiveMigration", "approveCognitiveMigration", "executeCognitiveMigration", "rollbackCognitiveMigration"),
            "app/src/main/java/com/morimil/app/data/repository/AgentOrchestrationRepository.kt" to setOf("seedDefaultOrchestrationIfNeeded", "proposeDelegatedTask", "approveDelegatedTask", "rejectDelegatedTask"),
            "app/src/main/java/com/morimil/app/data/repository/AgentInstanceLifecycleRepository.kt" to setOf("createAgentForVault", "assignTaskToAgent", "submitAgentResult", "evaluateAgent", "retireAgent", "quarantineAgent", "promoteAgent"),
            "app/src/main/java/com/morimil/app/data/repository/MigrationRecordRepository.kt" to setOf("planMigration", "markMigrationApproved", "markMigrationCompleted", "markMigrationFailed", "markMigrationRolledBack")
        )

        val SCANNER_EXCLUDED_PATHS = setOf("app/src/main/java/com/morimil/app/MorimilAppContainer.kt")
    }
}
