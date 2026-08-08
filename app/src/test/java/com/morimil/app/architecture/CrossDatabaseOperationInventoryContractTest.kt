package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossDatabaseOperationInventoryContractTest {
    @Test
    fun inventoryRecordsStableBaselineAndCurrentSemantics() {
        val inventory = inventoryFile(repositoryRoot()).readText()

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
        assertTrue(inventory.contains("historical CURRENT reconciliation", true))
        assertTrue(inventory.contains("`INTEGRATED_PROTOCOL`"))
        assertTrue(inventory.contains("`MIXED_DISPOSITION`"))
        assertTrue(inventory.contains("COG-001 through COG-004 integrated in protected main"))
        assertTrue(inventory.contains("ORCH-002 through ORCH-004 integrated"))
        assertTrue(inventory.contains("ORCH-001 remains open"))
        assertTrue(inventory.contains("F3.3"))
        assertTrue(inventory.contains("ProjectVault remains a separate protected reference"))
        assertTrue(inventory.contains("## Retired regression literals"))
        assertTrue(inventory.contains("not CURRENT facts"))

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
    fun integratedEntryPointsAndRemainingOwnersAreExplicit() {
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
        listOf("ORCH-002", "ORCH-003", "ORCH-004").forEach { id ->
            assertTrue(inventory.contains("`$id`"))
        }
        assertTrue(inventory.contains("`AGENT-001` through `AGENT-006` — next bounded owner family"))
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
        const val CONTENT_BASELINE_SHA =
            "CONTENT_BASELINE_SHA=c6a6b0ca998d053c31c75977c5b6d4d9ae166e96"
        const val CONTENT_BASELINE_PARENT_SHA =
            "CONTENT_BASELINE_PARENT_SHA=c22920f68f8820bbec676a6cbc74b60548e43d29"
        const val CURRENT_MAIN_RESOLUTION = "CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF"
        const val MERGE_SHA_EVIDENCE = "MERGE_SHA_EVIDENCE=EXTERNAL"
        const val COG_AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        const val ORCH_AUDITED_SOURCE_HEAD = "0348dccb561e576d17c45e7f8b1e38717332772b"
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
            "not integrated",
            "draft candidate closure",
            "`ORCH-002` | `proposeDelegatedTask` | `REQUIRES_PROTOCOL`; open",
            "`ORCH-003` | `approveDelegatedTask` | `REQUIRES_PROTOCOL`; open",
            "`ORCH-004` | `rejectDelegatedTask` | `REQUIRES_PROTOCOL`; open"
        )
    }
}
