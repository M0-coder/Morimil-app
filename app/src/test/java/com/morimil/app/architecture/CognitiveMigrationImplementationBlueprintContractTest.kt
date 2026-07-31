package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CognitiveMigrationImplementationBlueprintContractTest {
    private val blueprint by lazy {
        repositoryFile("docs/F3_COGNITIVE_MIGRATION_IMPLEMENTATION_BLUEPRINT.md").readText()
    }

    @Test
    fun blueprintIsCurrentImplementedAndHistoricallyTraceable() {
        assertTrue(blueprint.startsWith("# Document status: CURRENT"))
        assertTrue(blueprint.contains("implemented and audited design", true))
        assertTrue(blueprint.contains(CURRENT_MAIN))
        assertTrue(blueprint.contains(AUDITED_SOURCE_HEAD))
        assertTrue(blueprint.contains("PR `#149`: closed and merged by squash"))
        assertTrue(blueprint.contains("COG_001_004=INTEGRATED_IN_MAIN"))

        STALE_PHRASES.forEach { phrase ->
            assertFalse("Blueprint contains stale phrase $phrase", blueprint.contains(phrase, true))
        }
    }

    @Test
    fun authorityFrontierAndBoundedScopeRemainExplicit() {
        val identityAuthority = blueprint.indexOf(
            "GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository"
        )
        val commonBoundary = blueprint.indexOf("-> CanonicalConsumerReadPort", identityAuthority)
        val specializedBoundary = blueprint.indexOf(
            "-> CognitiveMigrationCanonicalReadPort",
            commonBoundary
        )

        assertTrue(identityAuthority >= 0)
        assertTrue(commonBoundary > identityAuthority)
        assertTrue(specializedBoundary > commonBoundary)
        assertTrue(blueprint.contains("does not open a second direct identity or memory authority"))
        assertTrue(blueprint.contains("ProjectVault remains separate and preserved"))
        assertTrue(blueprint.contains("F3.3 legacy removal"))
        assertTrue(blueprint.contains("F3_3=OPEN"))
    }

    @Test
    fun deterministicProtocolStatesAndOperationsRemainComplete() {
        listOf("COG-001", "COG-002", "COG-003", "COG-004").forEach { operation ->
            assertTrue("Missing operation $operation", blueprint.contains(operation))
        }
        listOf(
            "cognitive_migration.proposed",
            "cognitive_migration.approved",
            "cognitive_migration.executed",
            "cognitive_migration.rollback"
        ).forEach { eventType ->
            assertTrue("Missing event $eventType", blueprint.contains(eventType))
        }

        val stateBlock = blueprint.substringAfter("The only normal forward order is:")
            .substringBefore("`BLOCKED` is terminal")
        assertInOrder(
            stateBlock,
            listOf(
                "STAGED",
                "PENDING_CANONICAL",
                "CANONICAL_COMMITTED",
                "PENDING_LOCAL_COMMIT",
                "COMMITTED",
                "BLOCKED"
            )
        )
        assertTrue(blueprint.contains("The clock is metadata only"))
        assertTrue(blueprint.contains("approvalId = operationId"))
    }

    @Test
    fun mergedCorrectionsAndResidualHardeningRemainVisible() {
        listOf(
            "process-wide advancement by deterministic `operationId`",
            "reloads durable state after a lost CAS",
            "prevents stale snapshots from writing `BLOCKED`",
            "NULL-safe",
            "postSnapshotId",
            "Room-backed concurrent regression",
            "redundant `rollbackEventHash`",
            "UPDATE-trigger replacement"
        ).forEach { token ->
            assertTrue("Missing blueprint token $token", blueprint.contains(token, true))
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

    private fun repositoryFile(relativePath: String): File {
        return sequenceOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")
    }

    private companion object {
        const val CURRENT_MAIN = "ba6ffa4f9ddc9189ded47e231ad1f8bc962e612d"
        const val AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        val STALE_PHRASES = listOf(
            "isolated implementation candidate",
            "draft pr `#149`",
            "not integrated in protected `main`",
            "production_integrated=false",
            "f3.2 open candidate"
        )
    }
}
