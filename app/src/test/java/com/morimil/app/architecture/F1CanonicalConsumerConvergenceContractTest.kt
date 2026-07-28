package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class F1CanonicalConsumerConvergenceContractTest {
    @Test
    fun inventoryIsCurrentVersionedAndStopGated() {
        val inventory = inventoryFile(repositoryRoot()).readText()

        assertTrue(inventory.startsWith("# Document status: CURRENT"))
        assertTrue(inventory.contains("Inventory version: `1`"))
        assertTrue(
            inventory.contains(
                "Audited baseline: `main@396e7af8a7329b100195dfa4f20c40506c51eacd`"
            )
        )
        assertTrue(inventory.contains("`#86`"))
        assertTrue(inventory.contains("`#87`"))
        assertTrue(inventory.contains("`STOP S5 remains open`"))
        assertTrue(
            inventory.contains(
                "This document is preparation and does not authorize functional runtime changes during STOP S5."
            )
        )
        assertTrue(inventory.contains("This document does not close `#86`"))
    }

    @Test
    fun remainingLegacyConsumersAndDependenciesAreExplicit() {
        val inventory = inventoryFile(repositoryRoot()).readText()

        REQUIRED_CONSUMERS.forEach { consumer ->
            assertTrue("Missing consumer $consumer", inventory.contains("`$consumer`"))
        }
        REQUIRED_LEGACY_DEPENDENCIES.forEach { dependency ->
            assertTrue("Missing legacy dependency $dependency", inventory.contains("`$dependency`"))
        }
        assertTrue(inventory.contains("`WAITING_FOR_CANONICAL_MEMORY_ADAPTER`"))
        assertTrue(inventory.contains("F1 / `#86` remains open"))
        assertTrue(inventory.contains("F2 / `#87` is closed"))
    }

    @Test
    fun canonicalAuthoritiesAndSovereigntyInvariantsAreMandatory() {
        val inventory = inventoryFile(repositoryRoot()).readText()

        assertTrue(inventory.contains("`CanonicalMemoryRepository`"))
        assertTrue(inventory.contains("`GenesisUltraRuntimeIdentityRepository`"))
        assertTrue(inventory.contains("`CanonicalLivingMemoryPort`"))
        assertTrue(inventory.contains("instanceId != bodyId"))
        assertTrue(inventory.contains("Compatibility rows are forbidden."))
        assertTrue(inventory.contains("No convergence step may create, copy, seed, or reconstruct rows in:"))
        assertTrue(inventory.contains("`genesis_core`"))
        assertTrue(inventory.contains("`local_instance_identity`"))
        assertTrue(inventory.contains("`memory_events`"))
        assertTrue(inventory.contains("No placeholder such as `local_instance_pending`"))
    }

    @Test
    fun convergenceOrderSeparatesReadinessRecallsRestHealthAndRetirement() {
        val inventory = inventoryFile(repositoryRoot()).readText()

        assertInOrder(
            inventory,
            listOf(
                "### STEP-1 — canonical read adapter",
                "### STEP-2 — recalls",
                "### STEP-3 — rest-cycle planning",
                "### STEP-4 — rest-cycle execution",
                "### STEP-5 — health",
                "### STEP-6 — remove legacy gates"
            )
        )
        assertTrue(inventory.contains("### Canonical durable authority"))
        assertTrue(inventory.contains("### Durable organ state"))
        assertTrue(inventory.contains("### Rebuildable projections"))
        assertTrue(inventory.contains("recall due time, interval, status, last action, and review time"))
        assertTrue(inventory.contains("Health is a derived report."))
    }

    @Test
    fun futureAcceptanceTestsAreFailClosedAndDoNotUseLegacyCompatibility() {
        val inventory = inventoryFile(repositoryRoot()).readText()

        REQUIRED_TEST_CONCEPTS.forEach { concept ->
            assertTrue("Missing future test concept $concept", inventory.contains(concept))
        }
        assertTrue(inventory.contains("a clean Ultra installation"))
        assertTrue(inventory.contains("repeated seeding is idempotent"))
        assertTrue(inventory.contains("corruption produces no plan and no organ mutation"))
        assertTrue(inventory.contains("failure after append but before local finalization is recoverable"))
        assertTrue(inventory.contains("no write occurs in `memory_events`"))
    }

    @Test
    fun auditedProductionDependenciesStillExist() {
        val root = repositoryRoot()
        val inventory = inventoryFile(root).readText()

        REQUIRED_SOURCE_TOKENS.forEach { (path, tokens) ->
            val sourceFile = File(root, path)
            assertTrue("Missing audited production source $path", sourceFile.isFile)
            val source = sourceFile.readText()
            tokens.forEach { token ->
                assertTrue("Missing audited token $token in $path", source.contains(token))
                assertTrue("Inventory must name audited token $token", inventory.contains("`$token`"))
            }
        }
    }

    private fun assertInOrder(text: String, markers: List<String>) {
        var previous = -1
        markers.forEach { marker ->
            val current = text.indexOf(marker)
            assertTrue("Missing ordered marker $marker", current >= 0)
            assertTrue("Marker $marker is out of order", current > previous)
            previous = current
        }
    }

    private fun inventoryFile(root: File): File {
        val file = File(root, INVENTORY_PATH)
        assertTrue("Missing F1 canonical consumer convergence inventory", file.isFile)
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
        const val INVENTORY_PATH = "docs/F1_CANONICAL_CONSUMER_CONVERGENCE.md"

        val REQUIRED_CONSUMERS = setOf(
            "GenesisUltraRuntimeBootstrapCoordinator",
            "RecallScheduleRepository",
            "RestCycleRepository",
            "LocalNervousSystemRepository",
            "AgentOrchestrationRepository",
            "MemoryRepository",
            "MemoryDao",
            "MorimilViewModel",
            "RunRestCycleUseCase",
            "RestCycleWorker"
        )

        val REQUIRED_LEGACY_DEPENDENCIES = setOf(
            "loadGenesisCore",
            "loadLocalIdentity",
            "loadMemoryContext",
            "getLivingMemorySnapshot",
            "hasCompleteBirth",
            "memory_events"
        )

        val REQUIRED_TEST_CONCEPTS = setOf(
            "### Clean Ultra installation",
            "### Recall idempotency",
            "### Verified rest cycle",
            "### Corruption and foreign-instance failure",
            "### Identity and Body separation",
            "### Projection rebuild"
        )

        val REQUIRED_SOURCE_TOKENS = mapOf(
            "app/src/main/java/com/morimil/app/runtime/GenesisUltraRuntimeBootstrapCoordinator.kt" to setOf(
                "WAITING_FOR_CANONICAL_MEMORY_ADAPTER"
            ),
            "app/src/main/java/com/morimil/app/data/repository/RecallScheduleRepository.kt" to setOf(
                "seedFromRecentMemoryIfNeeded",
                "loadGenesisCore",
                "loadLocalIdentity",
                "loadMemoryContext"
            ),
            "app/src/main/java/com/morimil/app/data/repository/RestCycleRepository.kt" to setOf(
                "runLocalRestCycleIfDue",
                "approvePlannedRestCycle",
                "loadGenesisCore",
                "loadLocalIdentity",
                "loadMemoryContext"
            ),
            "app/src/main/java/com/morimil/app/data/repository/LocalNervousSystemRepository.kt" to setOf(
                "recordHealthCheckIfDegraded",
                "countGenesisCore",
                "countLocalIdentity",
                "countMemoryEvents"
            ),
            "app/src/main/java/com/morimil/app/data/repository/AgentOrchestrationRepository.kt" to setOf(
                "hasCompleteBirth"
            ),
            "app/src/main/java/com/morimil/app/data/local/MemoryDao.kt" to setOf(
                "getLivingMemorySnapshot",
                "memory_events"
            )
        )
    }
}
