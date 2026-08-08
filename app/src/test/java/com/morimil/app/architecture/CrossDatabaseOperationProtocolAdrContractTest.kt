package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossDatabaseOperationProtocolAdrContractTest {
    private val adr by lazy { repositoryFile("docs/adr/ADR-0002-cross-database-operation-protocol.md").readText() }

    @Test
    fun adrIsCurrentAcceptedForCogOrchAgentAndBoot() {
        assertTrue(adr.startsWith("# Document status: CURRENT"))
        assertTrue(adr.contains("Status: Accepted and implemented for COG-001..004, ORCH-002..004, AGENT-001..006, and BOOT-001"))
        listOf(
            CONTENT_BASELINE_SHA,
            CONTENT_BASELINE_PARENT_SHA,
            "CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF",
            "MERGE_SHA_EVIDENCE=EXTERNAL",
            COG_AUDITED_SOURCE_HEAD,
            ORCH_AUDITED_SOURCE_HEAD,
            AGENT_AUDITED_SOURCE_HEAD,
            BOOT_AUDITED_SOURCE_HEAD,
            "PR_176=MERGED_BY_SQUASH_HISTORICAL",
            "ADR_0002=ACCEPTED_AND_IMPLEMENTED_FOR_COG_ORCH_AGENT_AND_BOOT_BOUNDED_SCOPES"
        ).forEach { token -> assertTrue("Missing ADR token $token", adr.contains(token)) }
    }

    @Test
    fun authorityDeterminismAndStateMachineRemainNormative() {
        listOf(
            "instanceId != bodyId",
            "agentInstanceId != instanceId",
            "writer authorization is not ownership",
            "CanonicalCognitiveMigrationCommitPort",
            "CanonicalOrchestrationCommitPort",
            "CanonicalAgentLifecycleCommitPort",
            "CanonicalRuntimeBootstrapCommitPort",
            "Wall clock is metadata only",
            "No implementation may expose new owner state before exact canonical receipt verification"
        ).forEach { token -> assertTrue("Missing authority/protocol token $token", adr.contains(token, true)) }

        val state = adr.substringAfter("## Deterministic identity and state machine")
        assertInOrder(
            state,
            listOf(
                "STAGED\n",
                "-> PENDING_CANONICAL",
                "-> CANONICAL_COMMITTED",
                "-> PENDING_LOCAL_COMMIT",
                "-> COMMITTED",
                "`BLOCKED` is terminal"
            )
        )
    }

    @Test
    fun implementedMappingsIncludeBootAndSuccessorBoundary() {
        listOf("COG-001", "COG-002", "COG-003", "COG-004", "ORCH-002", "ORCH-003", "ORCH-004", "AGENT-001", "AGENT-002", "AGENT-003", "AGENT-004", "AGENT-005", "AGENT-006", "BOOT-001").forEach {
            assertTrue("Missing mapping $it", adr.contains(it))
        }
        listOf(
            "agent_lifecycle.agent_created",
            "agent_lifecycle.task_assigned",
            "agent_lifecycle.result_submitted",
            "agent_lifecycle.agent_evaluated",
            "agent_lifecycle.agent_quarantined",
            "runtime.bootstrap_initialized",
            "ownershipConferred=false",
            "guardian.role=custodian_witness",
            "future F5 successor Body",
            "same `instanceId`"
        ).forEach { token -> assertTrue("Missing ADR mapping token $token", adr.contains(token, true)) }
    }

    @Test
    fun remainingOwnersAndResidualsStayOpen() {
        listOf("ORCH_001=OPEN", "RECALL_001=OPEN", "REST_001_002=OPEN", "HEALTH_CONVERGENCE=OPEN", "F3_3=OPEN", "TRACKER_88=OPEN_FOR_REMAINING_OWNERS").forEach {
            assertTrue("Missing open state $it", adr.contains(it))
        }
        listOf("BOOT-specific mutation testing", "AGENT-specific mutation testing", "direct instrumented line coverage", "single-process", "ORCH-specific mutation testing", "physical ARM64").forEach {
            assertTrue("Missing residual $it", adr.contains(it, true))
        }
        assertFalse(adr.contains("BOOT_001=OPEN"))
        assertFalse(adr.contains("AGENT_001_006=OPEN"))
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

    private fun repositoryFile(relativePath: String): File =
        sequenceOf(File(relativePath), File("../$relativePath")).firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")

    private companion object {
        const val CONTENT_BASELINE_SHA = "CONTENT_BASELINE_SHA=3a995232ce2a515e1ca9b9151f77e63805bad9d3"
        const val CONTENT_BASELINE_PARENT_SHA = "CONTENT_BASELINE_PARENT_SHA=5918b64ec83e69cbb3d9718943b25d1e1299d698"
        const val COG_AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        const val ORCH_AUDITED_SOURCE_HEAD = "0348dccb561e576d17c45e7f8b1e38717332772b"
        const val AGENT_AUDITED_SOURCE_HEAD = "74e072b911db692041d3716af9d0511b83ad70b7"
        const val BOOT_AUDITED_SOURCE_HEAD = "c7710635fa172108cce87b3f7a76d6e037095864"
    }
}
