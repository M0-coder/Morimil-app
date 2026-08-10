package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossDatabaseOperationProtocolAdrContractTest {
    private val adr by lazy { repositoryFile("docs/adr/ADR-0002-cross-database-operation-protocol.md").readText() }

    @Test
    fun adrIsCurrentAcceptedAndRecordsPostRecallReadinessDisposition() {
        assertTrue(adr.startsWith("# Document status: CURRENT"))
        assertTrue(adr.contains("Status: Accepted and implemented for COG-001..004, ORCH-002..004, AGENT-001..006, BOOT-001, REST-001, and REST-002 proposal convergence"))
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
            REST_002_AUDITED_SOURCE_HEAD,
            BOOTSTRAP_HEALTH_AUDITED_SOURCE_HEAD,
            REST_BOOT_001_AUDITED_SOURCE_HEAD,
            HEALTH_AUDITED_SOURCE_HEAD,
            RECALL_BOOT_001_AUDITED_SOURCE_HEAD,
            "PR_184=MERGED_BY_SQUASH_HISTORICAL",
            "PR_186=MERGED_BY_SQUASH_HISTORICAL",
            "PR_187=MERGED_BY_SQUASH_HISTORICAL",
            "PR_188=MERGED_BY_SQUASH_HISTORICAL",
            "PR_189=MERGED_BY_SQUASH_HISTORICAL",
            "PR_190=MERGED_BY_SQUASH_HISTORICAL",
            "PR_191=MERGED_BY_SQUASH_HISTORICAL",
            "ADR_0002=ACCEPTED_AND_IMPLEMENTED_FOR_COG_ORCH_AGENT_BOOT_REST001_AND_REST002_PROPOSAL_BOUNDED_SCOPES",
            "RECALL_DISPOSITION=INTEGRATED_DERIVED_REBUILD_NOT_XOP_OWNER",
            "REST_001=INTEGRATED",
            "REST_002=INTEGRATED",
            "REST_REPAIR_PROPOSAL_CONVERGED=true",
            "REST_REPAIR_EXECUTION_IMPLEMENTED=false",
            "REST_BOOT_READINESS=INTEGRATED",
            "RECALL_BOOT_READINESS=INTEGRATED",
            "BOOTSTRAP_HEALTH_DERIVATION=INTEGRATED",
            "HEALTH_LEGACY_CONSUMER_CONVERGENCE=INTEGRATED",
            "HEALTH_CAN_READ_CANONICAL_MEMORY=true",
            "HEALTH_CAN_WRITE_CANONICAL_MEMORY=false",
            "HEALTH_CAN_WRITE_LEGACY_MEMORY_EVENTS=false",
            "HEALTH_CONVERGENCE=OPEN",
            "HEALTH_CONVERGED=false",
            "HEALTH_STATE=DEPENDENCY_DERIVED",
            "F1_F3_2_FULL_REAUDIT=REQUIRED",
            "TRACKER_88=OPEN_FOR_REAUDIT_AND_LEGACY_RETIREMENT"
        ).forEach { token -> assertTrue("Missing ADR token $token", adr.contains(token)) }
        assertFalse(adr.contains("LocalNervousSystemRepository.recordHealthCheckIfDegraded"))
        assertFalse(adr.contains("REST_BOOT_READINESS=OPEN"))
        assertFalse(adr.contains("RECALL_BOOT_READINESS=OPEN"))
        assertFalse(adr.contains("HEALTH_STATE=WAITING_FOR_DEPENDENCIES"))
        assertFalse(adr.contains("HEALTH_CONVERGENCE=INTEGRATED"))
        assertFalse(adr.contains("REST_002=OPEN"))
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
            "CanonicalRestCycleCommitPort",
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
    fun mappingsIncludeRestRecallReadinessAndCanonicalHealthWithoutAuthorityTransfer() {
        listOf(
            "COG-001",
            "COG-004",
            "ORCH-001",
            "ORCH-004",
            "AGENT-001",
            "AGENT-006",
            "BOOT-001",
            "RECALL-001",
            "REST-001",
            "REST-002"
        ).forEach { assertTrue("Missing mapping $it", adr.contains(it)) }
        listOf(
            "GenesisUltraRuntimeIdentityRepository.readCommittedIdentity()",
            "CanonicalConsumerReadPort.readRecallCandidates",
            "RecallScheduleRepository.isBootstrapReady(identity)",
            "CanonicalConsumerReadPort.readRestCyclePlanningInput",
            "CanonicalConsumerReadPort.readHealthInput",
            "LocalNervousSystemRepository.observeHealth",
            "rest_cycle.execute",
            "rest_cycle.local_consolidation",
            "rest_cycle.propose_repair",
            "memory.repair_proposed",
            "repair_execution=not_implemented",
            "canonical_memory_event",
            "autobiographical snapshot is a rebuildable local projection",
            "future F5 successor Body",
            "same `instanceId`"
        ).forEach { token -> assertTrue("Missing ADR mapping token $token", adr.contains(token, true)) }
    }

    @Test
    fun normativeConcurrencyRecoveryAndRecallDetailsRemainExplicit() {
        listOf(
            "Approve/reject decisions serialize by `taskId`",
            "conditional Room transitions as a second defense",
            "striped `agentInstanceId` mutexes",
            "Multiprocess Android would require durable cross-process serialization",
            "`MorimilDatabase` projection preparation is idempotent and replayable",
            "validates Instance/Body/signer/epoch bindings",
            "canonical `targetEventHash` for idempotency",
            "fails closed on invalid canonical evidence",
            "accepts a verified empty candidate batch as READY",
            "does not call `seedFromRecentMemoryIfNeeded`"
        ).forEach { token -> assertTrue("Missing preserved ADR guarantee $token", adr.contains(token, true)) }
    }

    @Test
    fun healthAndRecallReadinessAreIntegratedWhileGlobalHealthAwaitsReaudit() {
        listOf(
            "RECALL_001=INTEGRATED",
            "REST_BOOT_READINESS=INTEGRATED",
            "RECALL_BOOT_READINESS=INTEGRATED",
            "ORCH_001=INTEGRATED",
            "REST_001=INTEGRATED",
            "REST_002=INTEGRATED",
            "REST_REPAIR_PROPOSAL_CONVERGED=true",
            "REST_REPAIR_EXECUTION_IMPLEMENTED=false",
            "BOOTSTRAP_HEALTH_DERIVATION=INTEGRATED",
            "HEALTH_LEGACY_CONSUMER_CONVERGENCE=INTEGRATED",
            "HEALTH_CAN_READ_CANONICAL_MEMORY=true",
            "HEALTH_CAN_WRITE_CANONICAL_MEMORY=false",
            "HEALTH_CAN_WRITE_LEGACY_MEMORY_EVENTS=false",
            "HEALTH_CONVERGENCE=OPEN",
            "HEALTH_CONVERGED=false",
            "HEALTH_STATE=DEPENDENCY_DERIVED",
            "F1_F3_2_FULL_REAUDIT=REQUIRED",
            "F3_3=OPEN"
        ).forEach { assertTrue("Missing state $it", adr.contains(it)) }
        listOf(
            "Health-specific mutation testing",
            "REST-specific mutation testing",
            "RECALL-specific mutation testing",
            "BOOT/AGENT-specific mutation testing",
            "full F1/F3.2 protected-main reaudit",
            "physical ARM64"
        ).forEach { assertTrue("Missing residual $it", adr.contains(it, true)) }
        assertFalse(adr.contains("RECALL startup-readiness wiring remains open", true))
        assertFalse(adr.contains("REST_001_002=OPEN"))
        assertFalse(adr.contains("REST_001=OPEN"))
        assertFalse(adr.contains("REST_002=OPEN"))
        assertFalse(adr.contains("RECALL_001=OPEN"))
        assertFalse(adr.contains("BOOT_001=OPEN"))
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
        const val CONTENT_BASELINE_SHA = "CONTENT_BASELINE_SHA=c4b192b8f54b2422ce816dc3542d55adfd44510c"
        const val CONTENT_BASELINE_PARENT_SHA = "CONTENT_BASELINE_PARENT_SHA=9c7325e6f1a21d79b1c3fb58f0b5f81a828fc304"
        const val COG_AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        const val ORCH_AUDITED_SOURCE_HEAD = "0348dccb561e576d17c45e7f8b1e38717332772b"
        const val ORCH_001_AUDITED_SOURCE_HEAD = "fe188fdee8eae901434a255051b6fa4f852b929b"
        const val AGENT_AUDITED_SOURCE_HEAD = "74e072b911db692041d3716af9d0511b83ad70b7"
        const val BOOT_AUDITED_SOURCE_HEAD = "c7710635fa172108cce87b3f7a76d6e037095864"
        const val RECALL_AUDITED_SOURCE_HEAD = "fae8a0df3c29775317986877bce2b8eda8593d27"
        const val REST_001_AUDITED_SOURCE_HEAD = "3661450325237fcadb86098ec16ee45cd039bc0b"
        const val REST_002_AUDITED_SOURCE_HEAD = "2ecca3f48d5e0ef27bd927da3986292daf7f7e2c"
        const val BOOTSTRAP_HEALTH_AUDITED_SOURCE_HEAD = "f1697227241459f316bd562756e15ae3ce02c90d"
        const val REST_BOOT_001_AUDITED_SOURCE_HEAD = "dd7a92a011fd4c453775df6ec307638b05313ec9"
        const val HEALTH_AUDITED_SOURCE_HEAD = "6735e2d1febccf7da560d026d6ddd88f6ad82845"
        const val RECALL_BOOT_001_AUDITED_SOURCE_HEAD = "20d834e1d438fd5883a76e9b45bcf21860e7db42"
    }
}
