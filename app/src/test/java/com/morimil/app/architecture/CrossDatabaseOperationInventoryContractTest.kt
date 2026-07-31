package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossDatabaseOperationInventoryContractTest {
    @Test
    fun inventoryRecordsPostMergeCurrentTruth() {
        val inventory = inventoryFile(repositoryRoot()).readText()

        assertTrue(inventory.startsWith("# Document status: CURRENT"))
        assertTrue(inventory.contains("Inventory version: `3`"))
        assertTrue(inventory.contains(CURRENT_MAIN))
        assertTrue(inventory.contains(AUDITED_SOURCE_HEAD))
        assertTrue(inventory.contains("PR `#149`: closed and merged by squash"))
        assertTrue(inventory.contains("`INTEGRATED_PROTOCOL`"))
        assertTrue(inventory.contains("COG-001 through COG-004 integrated in protected main"))
        assertTrue(inventory.contains("F3.3"))
        assertTrue(inventory.contains("ProjectVault remains a separate protected reference"))

        STALE_PHRASES.forEach { phrase ->
            assertFalse("Inventory contains stale phrase $phrase", inventory.contains(phrase, true))
        }
    }

    @Test
    fun everyProductionCrossDatabaseBoundaryOwnerIsInventoried() {
        val root = repositoryRoot()
        val inventory = inventoryFile(root).readText()
        val candidates = discoverBoundaryCandidates(root)
        val owners = candidates - SCANNER_EXCLUDED_PATHS

        assertEquals(
            "Cross-database candidate set changed; classify the new path",
            EXPECTED_OWNER_PATHS + SCANNER_EXCLUDED_PATHS,
            candidates
        )
        assertEquals(
            "Cross-database owner set changed; update the inventory",
            EXPECTED_OWNER_PATHS,
            owners
        )
        EXPECTED_OWNER_PATHS.forEach { path ->
            assertTrue("Inventory is missing owner $path", inventory.contains("`$path`"))
        }
    }

    @Test
    fun integratedCogEntryPointsAndRemainingOwnersAreExplicit() {
        val root = repositoryRoot()
        val inventory = inventoryFile(root).readText()

        REQUIRED_ENTRY_POINTS.forEach { (path, entryPoints) ->
            val source = File(root, path).readText()
            entryPoints.forEach { entryPoint ->
                assertTrue(
                    "Missing audited entry point $entryPoint in $path",
                    Regex("\\bfun\\s+${Regex.escape(entryPoint)}\\s*\\(").containsMatchIn(source)
                )
                assertTrue("Inventory is missing entry point $entryPoint", inventory.contains("`$entryPoint`"))
            }
        }

        listOf("COG-001", "COG-002", "COG-003", "COG-004").forEach { id ->
            assertTrue(inventory.contains("`$id`"))
        }
        assertTrue(inventory.contains("F3_3", true) || inventory.contains("F3.3"))
        assertTrue(inventory.contains("Room-backed two-coordinator", true))
    }

    private fun discoverBoundaryCandidates(root: File): Set<String> {
        val sourceRoot = File(root, "app/src/main/java")
        return sourceRoot.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .filter { file ->
                val source = file.readText()
                MEMORY_ORGAN_DATABASE_PATTERN.containsMatchIn(source) &&
                    (
                        MORIMIL_DATABASE_PATTERN.containsMatchIn(source) ||
                            MEMORY_REPOSITORY_PATTERN.containsMatchIn(source) ||
                            PROJECT_VAULT_COMMIT_PORT_PATTERN.containsMatchIn(source) ||
                            CROSS_DATABASE_COORDINATOR_DEPENDENCY_PATTERN.containsMatchIn(source)
                        )
            }
            .map { file -> file.relativeTo(root).invariantSeparatorsPath }
            .toSet()
    }

    private fun inventoryFile(root: File): File {
        val file = File(root, INVENTORY_PATH)
        assertTrue("Missing cross-database inventory", file.isFile)
        return file
    }

    private fun repositoryRoot(): File {
        return sequenceOf(File("."), File(".."))
            .map(File::getCanonicalFile)
            .firstOrNull { candidate ->
                File(candidate, "README.md").isFile &&
                    File(candidate, "app/build.gradle.kts").isFile
            }
            ?: error("Repository root not found")
    }

    private companion object {
        const val INVENTORY_PATH = "docs/F3_CROSS_DATABASE_OPERATION_INVENTORY.md"
        const val CURRENT_MAIN = "ba6ffa4f9ddc9189ded47e231ad1f8bc962e612d"
        const val AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        const val BOOTSTRAP_PATH =
            "app/src/main/java/com/morimil/app/runtime/GenesisUltraRuntimeBootstrapCoordinator.kt"

        val MEMORY_ORGAN_DATABASE_PATTERN = Regex("\\bMemoryOrganDatabase\\b")
        val MORIMIL_DATABASE_PATTERN = Regex("\\bMorimilDatabase\\b")
        val MEMORY_REPOSITORY_PATTERN = Regex("\\bMemoryRepository\\b")
        val PROJECT_VAULT_COMMIT_PORT_PATTERN = Regex("\\bProjectVaultCommitPort\\b")
        val CROSS_DATABASE_COORDINATOR_DEPENDENCY_PATTERN = Regex(
            "\\bprivate\\s+val\\s+protocol\\s*:\\s*CrossDatabaseOperationCoordinator\\b"
        )

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
            "app/src/main/java/com/morimil/app/data/repository/ProjectVaultRepository.kt" to setOf(
                "createProjectVaultFromIntent", "completeProjectVault", "archiveProjectVault"
            ),
            BOOTSTRAP_PATH to setOf("bootstrap"),
            "app/src/main/java/com/morimil/app/data/repository/RecallScheduleRepository.kt" to
                setOf("seedFromRecentMemoryIfNeeded"),
            "app/src/main/java/com/morimil/app/data/repository/RestCycleRepository.kt" to
                setOf("runLocalRestCycleIfDue", "approvePlannedRestCycle"),
            "app/src/main/java/com/morimil/app/data/repository/CognitiveMigrationRepository.kt" to
                setOf(
                    "proposeCognitiveMigration",
                    "approveCognitiveMigration",
                    "executeCognitiveMigration",
                    "rollbackCognitiveMigration"
                ),
            "app/src/main/java/com/morimil/app/data/repository/AgentOrchestrationRepository.kt" to
                setOf(
                    "seedDefaultOrchestrationIfNeeded",
                    "proposeDelegatedTask",
                    "approveDelegatedTask",
                    "rejectDelegatedTask"
                ),
            "app/src/main/java/com/morimil/app/data/repository/AgentInstanceLifecycleRepository.kt" to
                setOf(
                    "createAgentForVault",
                    "assignTaskToAgent",
                    "submitAgentResult",
                    "evaluateAgent",
                    "retireAgent",
                    "quarantineAgent",
                    "promoteAgent"
                ),
            "app/src/main/java/com/morimil/app/data/repository/MigrationRecordRepository.kt" to
                setOf(
                    "planMigration",
                    "markMigrationApproved",
                    "markMigrationCompleted",
                    "markMigrationFailed",
                    "markMigrationRolledBack"
                )
        )

        val SCANNER_EXCLUDED_PATHS = setOf(
            "app/src/main/java/com/morimil/app/MorimilAppContainer.kt"
        )

        val STALE_PHRASES = listOf(
            "draft pr `#149`",
            "candidate does not close",
            "not integrated",
            "draft candidate closure"
        )
    }
}
