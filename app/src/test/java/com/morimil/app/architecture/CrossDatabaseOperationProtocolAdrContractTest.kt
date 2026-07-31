package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossDatabaseOperationProtocolAdrContractTest {
    private val adr by lazy {
        repositoryFile("docs/adr/ADR-0002-cross-database-operation-protocol.md").readText()
    }

    @Test
    fun adrIsCurrentAcceptedImplementedAndMerged() {
        assertTrue(adr.startsWith("# Document status: CURRENT"))
        assertTrue(adr.contains("Status: Accepted and implemented for COG-001 through COG-004"))
        assertTrue(adr.contains(CURRENT_MAIN))
        assertTrue(adr.contains(AUDITED_SOURCE_HEAD))
        assertTrue(adr.contains("PR `#149`: closed and merged by squash"))
        assertTrue(adr.contains("ADR_0002=ACCEPTED_AND_IMPLEMENTED_FOR_COG_001_004"))

        STALE_PHRASES.forEach { phrase ->
            assertFalse("ADR contains stale phrase $phrase", adr.contains(phrase, true))
        }
    }

    @Test
    fun authorityDeterminismAndStateMachineRemainNormative() {
        assertTrue(adr.contains("instanceId != bodyId"))
        assertTrue(adr.contains("-> CanonicalConsumerReadPort"))
        assertTrue(adr.contains("-> CognitiveMigrationCanonicalReadPort"))
        assertTrue(adr.contains("must not reopen a second direct identity or memory authority"))
        assertTrue(adr.contains("Wall-clock time is metadata only"))
        assertTrue(adr.contains("MUST NOT participate in `operationId`, `eventId`"))

        val stateSection = adr.substringAfter("## State machine")
        assertInOrder(
            stateSection,
            listOf(
                "`STAGED`",
                "`PENDING_CANONICAL`",
                "`CANONICAL_COMMITTED`",
                "`PENDING_LOCAL_COMMIT`",
                "`COMMITTED`",
                "`BLOCKED`"
            )
        )
    }

    @Test
    fun implementedCogMappingAndCorrectionsRemainExplicit() {
        listOf("COG-001", "COG-002", "COG-003", "COG-004").forEach { id ->
            assertTrue("Missing $id", adr.contains(id))
        }
        listOf(
            "cognitive_migration.proposed",
            "cognitive_migration.approved",
            "cognitive_migration.executed",
            "cognitive_migration.rollback"
        ).forEach { event ->
            assertTrue("Missing $event", adr.contains(event))
        }
        listOf(
            "outside the Room write transaction",
            "real `sha256:*` snapshot",
            "preservation of the owner's existing `postSnapshotId`",
            "serializes process-wide advancement by `operationId`",
            "reloads after lost CAS",
            "rejects stale blocking",
            "without double counting"
        ).forEach { requirement ->
            assertTrue("Missing ADR requirement $requirement", adr.contains(requirement, true))
        }
    }

    @Test
    fun projectVaultF33AndResidualHardeningStaySeparate() {
        assertTrue(adr.contains("ProjectVault remains unchanged and separate"))
        assertTrue(adr.contains("F3_3=OPEN"))
        assertTrue(adr.contains("TRACKER_88=OPEN_FOR_REMAINING_OWNERS"))
        listOf(
            "Room-backed concurrent execution",
            "failed-rollback snapshot fixture",
            "redundant `rollbackEventHash`",
            "UPDATE-trigger replacement"
        ).forEach { finding ->
            assertTrue("Missing residual finding $finding", adr.contains(finding, true))
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
            "accepted design with audited candidate amendment",
            "draft pr `#149`",
            "not integrated in protected `main`",
            "production_integrated=false"
        )
    }
}
