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
    fun adrIsCurrentAcceptedImplementedAndHistoricallyTraceable() {
        assertTrue(adr.startsWith("# Document status: CURRENT"))
        assertTrue(
            adr.contains(
                "Status: Accepted and implemented for COG-001 through COG-004 and ORCH-002 through ORCH-004"
            )
        )
        assertTrue(adr.contains(CONTENT_BASELINE_SHA))
        assertTrue(adr.contains(CONTENT_BASELINE_PARENT_SHA))
        assertTrue(adr.contains(CURRENT_MAIN_RESOLUTION))
        assertTrue(adr.contains(MERGE_SHA_EVIDENCE))
        assertTrue(adr.contains(COG_AUDITED_SOURCE_HEAD))
        assertTrue(adr.contains(ORCH_AUDITED_SOURCE_HEAD))
        assertTrue(adr.contains("PR `#149`: closed and merged by squash"))
        assertTrue(adr.contains("PR `#150`: closed and merged by squash"))
        assertTrue(adr.contains("PR `#153`: closed and merged by squash"))
        assertTrue(adr.contains("PR `#172`: closed and merged by squash"))
        assertTrue(adr.contains("PR_153=MERGED_BY_SQUASH_HISTORICAL"))
        assertTrue(adr.contains("PR_172=MERGED_BY_SQUASH_HISTORICAL"))
        assertTrue(adr.contains("ADR_0002=ACCEPTED_AND_IMPLEMENTED_FOR_COG_001_004_AND_ORCH_002_004"))

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
        assertTrue(adr.contains("CanonicalOrchestrationCommitPort"))
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
    fun implementedCogAndOrchMappingsRemainExplicit() {
        listOf("COG-001", "COG-002", "COG-003", "COG-004").forEach { id ->
            assertTrue("Missing $id", adr.contains(id))
        }
        listOf("ORCH-002", "ORCH-003", "ORCH-004").forEach { id ->
            assertTrue("Missing $id", adr.contains(id))
        }
        listOf(
            "cognitive_migration.proposed",
            "cognitive_migration.approved",
            "cognitive_migration.executed",
            "cognitive_migration.rollback",
            "orchestration.delegated_task.proposed",
            "orchestration.delegated_task.approved",
            "orchestration.delegated_task.rejected"
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
            "without double counting",
            "serialized by `taskId`",
            "approvalId IS NULL"
        ).forEach { requirement ->
            assertTrue("Missing ADR requirement $requirement", adr.contains(requirement, true))
        }
    }

    @Test
    fun projectVaultF33AndResidualHardeningStaySeparate() {
        assertTrue(adr.contains("ProjectVault remains unchanged and separate"))
        assertTrue(adr.contains("ORCH_001=OPEN"))
        assertTrue(adr.contains("AGENT_001_006=OPEN"))
        assertTrue(adr.contains("F3_3=OPEN"))
        assertTrue(adr.contains("TRACKER_88=OPEN_FOR_REMAINING_OWNERS"))
        listOf(
            "Room-backed concurrent execution",
            "failed-rollback snapshot fixture",
            "redundant `rollbackEventHash`",
            "UPDATE-trigger replacement",
            "ORCH-specific mutation testing"
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
        const val CONTENT_BASELINE_SHA =
            "CONTENT_BASELINE_SHA=c6a6b0ca998d053c31c75977c5b6d4d9ae166e96"
        const val CONTENT_BASELINE_PARENT_SHA =
            "CONTENT_BASELINE_PARENT_SHA=c22920f68f8820bbec676a6cbc74b60548e43d29"
        const val CURRENT_MAIN_RESOLUTION = "CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF"
        const val MERGE_SHA_EVIDENCE = "MERGE_SHA_EVIDENCE=EXTERNAL"
        const val COG_AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        const val ORCH_AUDITED_SOURCE_HEAD = "0348dccb561e576d17c45e7f8b1e38717332772b"
        val STALE_PHRASES = listOf(
            "accepted design with audited candidate amendment",
            "draft pr `#149`",
            "not integrated in protected `main`",
            "production_integrated=false",
            "accepted and implemented for cog-001 through cog-004."
        )
    }
}
