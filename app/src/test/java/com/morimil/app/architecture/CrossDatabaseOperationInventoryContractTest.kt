package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossDatabaseOperationInventoryContractTest {
    @Test
    fun inventoryRecordsPostAgentCurrentSemantics() {
        val inventory = inventoryFile(repositoryRoot()).readText()
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
            "PR_174=MERGED_BY_SQUASH_HISTORICAL"
        ).forEach { token -> assertTrue("Missing inventory token $token", inventory.contains(token)) }

        assertTrue(inventory.contains("AgentInstanceLifecycleRepository.kt` | `INTEGRATED_PROTOCOL`"))
        assertTrue(inventory.contains("AGENT-001 through AGENT-006 integrated"))
        assertTrue(inventory.contains("BOOT-001 — next bounded owner"))
        assertTrue(inventory.contains("ORCH-001") && inventory.contains("open"))
        assertFalse(inventory.contains("AGENT-001 through AGENT-006 — next bounded owner family"))
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
    fun integratedAgentEntriesAndRemainingOwnersAreExplicit() {
        val root = repositoryRoot()
        val inventory = inventoryFile(root).readText()
        REQUIRED_ENTRY_POINTS.forEach { (path, entryPoints) ->
            val source = File(root, path).readText()
            entryPoints.forEach { entryPoint ->
                assertTrue(Regex("\\bfun\\s+${Regex.escape(entryPoint)}\\s*\\(").containsMatchIn(source))
                assertTrue(inventory.contains("`$entryPoint`"))
            }
        }
        listOf("AGENT-001", "AGENT-002", "AGENT-003", "AGENT-004", "AGENT-005", "AGENT-006").forEach {
            assertTrue("Missing $it", inventory.contains("`$it`"))
        }
        listOf("BOOT-001", "RECALL-001", "ORCH-001", "REST-001", "REST-002").forEach {
            assertTrue("Missing remaining owner $it", inventory.contains("`$it`"))
        }
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
                        CROSS_DATABASE_COORDINATOR_DEPENDENCY_PATTERN.containsMatchIn(source))
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
        const val CONTENT_BASELINE_SHA = "CONTENT_BASELINE_SHA=d577a75290d70f423f6e83bf237a8a453f3a534e"
        const val CONTENT_BASELINE_PARENT_SHA = "CONTENT_BASELINE_PARENT_SHA=9da342f2c147105ea882076f4ebc6ab5f5494190"
        const val COG_AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        const val ORCH_AUDITED_SOURCE_HEAD = "0348dccb561e576d17c45e7f8b1e38717332772b"
        const val AGENT_AUDITED_SOURCE_HEAD = "74e072b911db692041d3716af9d0511b83ad70b7"
        const val BOOTSTRAP_PATH = "app/src/main/java/com/morimil/app/runtime/GenesisUltraRuntimeBootstrapCoordinator.kt"

        val MEMORY_ORGAN_DATABASE_PATTERN = Regex("\\bMemoryOrganDatabase\\b")
        val MORIMIL_DATABASE_PATTERN = Regex("\\bMorimilDatabase\\b")
        val MEMORY_REPOSITORY_PATTERN = Regex("\\bMemoryRepository\\b")
        val PROJECT_VAULT_COMMIT_PORT_PATTERN = Regex("\\bProjectVaultCommitPort\\b")
        val CROSS_DATABASE_COORDINATOR_DEPENDENCY_PATTERN = Regex("\\bprivate\\s+val\\s+protocol\\s*:\\s*CrossDatabaseOperationCoordinator\\b")

        val EXPECTED_OWNER_PATHS = setOf(
            "app/src/main/java/com/morimil/app/data/repository/ProjectVaultRepository.kt",
            BOOTSTRAP_PATH,
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
