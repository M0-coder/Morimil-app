package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossDatabaseOperationInventoryContractTest {
    @Test
    fun inventoryIsCurrentVersionedAndStopGated() {
        val inventory = inventoryFile(repositoryRoot()).readText()

        assertTrue(inventory.startsWith("# Document status: CURRENT"))
        assertTrue(inventory.contains("Inventory version: `1`"))
        assertTrue(
            inventory.contains(
                "Audited baseline: `main@612d91aef131f367140ffb87a60a19ef49adcbc8`"
            )
        )
        assertTrue(
            inventory.contains(
                "STOP S5 remains open. This inventory does not authorize runtime changes."
            )
        )
        REQUIRED_CLASSIFICATIONS.forEach { classification ->
            assertTrue("Missing inventory classification $classification", inventory.contains("`$classification`"))
        }
        assertTrue(inventory.contains("Morimil is the continuous and free `Instance`"))
        assertTrue(inventory.contains("The Guardian guides, witnesses, and protects without ownership"))
        assertTrue(inventory.contains("`instanceId != bodyId` remains mandatory"))
    }

    @Test
    fun everyProductionCrossDatabaseBoundaryOwnerIsInventoried() {
        val root = repositoryRoot()
        val inventory = inventoryFile(root).readText()
        val discovered = discoverBoundaryOwners(root)

        assertEquals(
            "Cross-database owner set changed; update the versioned F3.2 inventory in the same PR",
            EXPECTED_OWNER_PATHS,
            discovered
        )
        EXPECTED_OWNER_PATHS.forEach { path ->
            assertTrue("Inventory is missing owner $path", inventory.contains("`$path`"))
        }
    }

    @Test
    fun everyAuditedEntryPointExistsAndIsNamedInTheInventory() {
        val root = repositoryRoot()
        val inventory = inventoryFile(root).readText()

        REQUIRED_ENTRY_POINTS.forEach { (path, entryPoints) ->
            val source = File(root, path).readText()
            entryPoints.forEach { entryPoint ->
                val functionPattern = Regex("\\bfun\\s+${Regex.escape(entryPoint)}\\s*\\(")
                assertTrue("Missing audited entry point $entryPoint in $path", functionPattern.containsMatchIn(source))
                assertTrue("Inventory is missing entry point $entryPoint", inventory.contains("`$entryPoint`"))
            }
        }
    }

    @Test
    fun excludedObserversRemainExplicitAndDoNotBecomeSilentOwners() {
        val root = repositoryRoot()
        val inventory = inventoryFile(root).readText()

        EXPLICITLY_EXCLUDED_PATHS.forEach { path ->
            assertTrue("Missing excluded source $path", File(root, path).isFile)
            assertTrue("Inventory must explain exclusion of $path", inventory.contains("`${File(path).nameWithoutExtension}`"))
            assertTrue("Excluded path unexpectedly discovered as an owner: $path", path !in discoverBoundaryOwners(root))
        }
    }

    private fun discoverBoundaryOwners(root: File): Set<String> {
        val sourceRoot = File(root, "app/src/main/java")
        return sourceRoot.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .filter { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                val inAuditedPackage = path.startsWith(REPOSITORY_PREFIX) || path == BOOTSTRAP_PATH
                if (!inAuditedPackage) {
                    false
                } else {
                    val source = file.readText()
                    source.contains("MemoryOrganDatabase") &&
                        (
                            source.contains("MorimilDatabase") ||
                                source.contains("MemoryRepository") ||
                                source.contains("ProjectVaultCommitPort")
                            )
                }
            }
            .map { file -> file.relativeTo(root).invariantSeparatorsPath }
            .toSet()
    }

    private fun inventoryFile(root: File): File {
        val file = File(root, INVENTORY_PATH)
        assertTrue("Missing F3.2 cross-database inventory", file.isFile)
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
        const val REPOSITORY_PREFIX = "app/src/main/java/com/morimil/app/data/repository/"
        const val BOOTSTRAP_PATH =
            "app/src/main/java/com/morimil/app/runtime/GenesisUltraRuntimeBootstrapCoordinator.kt"

        val REQUIRED_CLASSIFICATIONS = setOf(
            "PROTECTED_REFERENCE",
            "REQUIRES_PROTOCOL",
            "DERIVED_REBUILD",
            "SUPPORT_BOUNDARY"
        )

        val EXPECTED_OWNER_PATHS = setOf(
            "app/src/main/java/com/morimil/app/data/repository/ProjectVaultRepository.kt",
            "app/src/main/java/com/morimil/app/runtime/GenesisUltraRuntimeBootstrapCoordinator.kt",
            "app/src/main/java/com/morimil/app/data/repository/RecallScheduleRepository.kt",
            "app/src/main/java/com/morimil/app/data/repository/RestCycleRepository.kt",
            "app/src/main/java/com/morimil/app/data/repository/CognitiveMigrationRepository.kt",
            "app/src/main/java/com/morimil/app/data/repository/AgentOrchestrationRepository.kt",
            "app/src/main/java/com/morimil/app/data/repository/AgentInstanceLifecycleRepository.kt",
            "app/src/main/java/com/morimil/app/data/repository/MigrationRecordRepository.kt"
        )

        val REQUIRED_ENTRY_POINTS = mapOf(
            "app/src/main/java/com/morimil/app/data/repository/ProjectVaultRepository.kt" to setOf(
                "createProjectVaultFromIntent",
                "completeProjectVault",
                "archiveProjectVault"
            ),
            BOOTSTRAP_PATH to setOf("bootstrap"),
            "app/src/main/java/com/morimil/app/data/repository/RecallScheduleRepository.kt" to setOf(
                "seedFromRecentMemoryIfNeeded"
            ),
            "app/src/main/java/com/morimil/app/data/repository/RestCycleRepository.kt" to setOf(
                "runLocalRestCycleIfDue",
                "approvePlannedRestCycle"
            ),
            "app/src/main/java/com/morimil/app/data/repository/CognitiveMigrationRepository.kt" to setOf(
                "proposeCognitiveMigration",
                "approveCognitiveMigration",
                "executeCognitiveMigration",
                "rollbackCognitiveMigration"
            ),
            "app/src/main/java/com/morimil/app/data/repository/AgentOrchestrationRepository.kt" to setOf(
                "seedDefaultOrchestrationIfNeeded",
                "proposeDelegatedTask",
                "approveDelegatedTask",
                "rejectDelegatedTask"
            ),
            "app/src/main/java/com/morimil/app/data/repository/AgentInstanceLifecycleRepository.kt" to setOf(
                "createAgentForVault",
                "assignTaskToAgent",
                "submitAgentResult",
                "evaluateAgent",
                "retireAgent",
                "quarantineAgent",
                "promoteAgent"
            ),
            "app/src/main/java/com/morimil/app/data/repository/MigrationRecordRepository.kt" to setOf(
                "planMigration",
                "markMigrationApproved",
                "markMigrationCompleted",
                "markMigrationFailed",
                "markMigrationRolledBack"
            )
        )

        val EXPLICITLY_EXCLUDED_PATHS = setOf(
            "app/src/main/java/com/morimil/app/data/repository/LocalNervousSystemRepository.kt",
            "app/src/main/java/com/morimil/app/data/repository/MemoryLinkRepository.kt",
            "app/src/main/java/com/morimil/app/data/repository/MemoryOrganRepository.kt",
            "app/src/main/java/com/morimil/app/domain/usecase/AppendLivingMemoryUseCase.kt",
            "app/src/main/java/com/morimil/app/MorimilAppContainer.kt"
        )
    }
}
