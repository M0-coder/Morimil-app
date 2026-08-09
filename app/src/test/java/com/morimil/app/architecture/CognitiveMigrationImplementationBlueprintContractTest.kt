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
    fun blueprintIsCurrentAndRecordsPostBootstrapHealthRestReadinessTruth() {
        assertTrue(blueprint.startsWith("# Document status: CURRENT"))
        assertTrue(blueprint.contains("implemented and audited design", true))
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
            "PR_184=MERGED_BY_SQUASH_HISTORICAL",
            "PR_186=MERGED_BY_SQUASH_HISTORICAL",
            "PR_187=MERGED_BY_SQUASH_HISTORICAL",
            "PR_188=MERGED_BY_SQUASH_HISTORICAL",
            "COG_001_004=INTEGRATED_IN_MAIN",
            "ORCH_001=INTEGRATED_IN_MAIN",
            "ORCH_002_004=INTEGRATED_IN_MAIN",
            "AGENT_001_006=INTEGRATED_IN_MAIN",
            "BOOT_001=INTEGRATED_IN_MAIN",
            "RECALL_001=INTEGRATED_IN_MAIN",
            "REST_001=INTEGRATED_IN_MAIN",
            "REST_002=INTEGRATED_IN_MAIN",
            "REST_REPAIR_PROPOSAL_CONVERGED=true",
            "REST_REPAIR_EXECUTION_IMPLEMENTED=false",
            "REST_BOOT_READINESS=INTEGRATED",
            "RECALL_BOOT_READINESS=OPEN",
            "BOOTSTRAP_HEALTH_DERIVATION=INTEGRATED",
            "HEALTH_CONVERGENCE=OPEN",
            "HEALTH_CONVERGED=false",
            "HEALTH_STATE=WAITING_FOR_DEPENDENCIES"
        ).forEach { token -> assertTrue("Missing blueprint token $token", blueprint.contains(token)) }
        assertFalse(blueprint.contains("REST_BOOT_READINESS=OPEN"))
        assertFalse(blueprint.contains("HEALTH_CONVERGENCE=INTEGRATED"))
        assertFalse(blueprint.contains("REST_002=OPEN"))
    }

    @Test
    fun authorityFrontierAndBoundedScopeRemainExplicit() {
        val identityAuthority = blueprint.indexOf("GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository")
        val commonBoundary = blueprint.indexOf("-> CanonicalConsumerReadPort", identityAuthority)
        val specializedBoundary = blueprint.indexOf("-> CognitiveMigrationCanonicalReadPort", commonBoundary)
        assertTrue(identityAuthority >= 0 && commonBoundary > identityAuthority && specializedBoundary > commonBoundary)
        assertTrue(blueprint.contains("ProjectVault remains separate"))
        assertTrue(blueprint.contains("PR #178 integrated RECALL-001"))
        assertTrue(blueprint.contains("PR #180 integrated ORCH-001 seed convergence"))
        assertTrue(blueprint.contains("PR #182 integrated REST-001 canonical planning and durable execution"))
        assertTrue(blueprint.contains("PR #184 integrated REST-002 proposal-only canonical convergence"))
        assertTrue(blueprint.contains("PR #187 integrated dependency-derived bootstrap Health"))
        assertTrue(blueprint.contains("PR #188 integrated REST startup readiness"))
        listOf(
            "LocalNervousSystemRepository.recordHealthCheckIfDegraded",
            "F1 health convergence itself remains open",
            "RECALL startup-readiness convergence",
            "full F1/F3.2 reaudit",
            "F3.3 legacy removal remains open",
            "automatic repair execution remains unimplemented"
        ).forEach { assertTrue("Missing remaining scope $it", blueprint.contains(it, true)) }
        assertFalse(blueprint.contains("REST_001_002=OPEN"))
        assertFalse(blueprint.contains("REST_001=OPEN"))
        assertFalse(blueprint.contains("REST_002=OPEN"))
        assertFalse(blueprint.contains("RECALL_001=OPEN"))
    }

    @Test
    fun deterministicCogProtocolRemainsComplete() {
        listOf("COG-001", "COG-002", "COG-003", "COG-004").forEach {
            assertTrue("Missing operation $it", blueprint.contains(it))
        }
        listOf("cognitive_migration.proposed", "cognitive_migration.approved", "cognitive_migration.executed", "cognitive_migration.rollback").forEach {
            assertTrue("Missing event $it", blueprint.contains(it))
        }
        val state = blueprint.substringAfter("## 5. Durable journal and state machine")
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
        assertTrue(blueprint.contains("Clock is metadata only", true))
    }

    @Test
    fun ownerScopedRecoveryAndResidualHardeningRemainVisible() {
        listOf(
            "serializes advancement by deterministic `operationId`",
            "reloads after lost CAS",
            "rejects stale blocking",
            "COG recovery cannot consume ORCH or AGENT rows",
            "ORCH, AGENT and BOOT likewise remain owner-scoped",
            "REST recovery is owner-scoped",
            "BOOT has an additional idempotent MorimilDatabase preparation",
            "REST-002 recovery may finalize a proposal receipt but never execute a repair",
            "Room-backed multi-coordinator concurrency",
            "rollback snapshot",
            "UPDATE-trigger replacement",
            "REST-specific mutation testing is not established"
        ).forEach { token -> assertTrue("Missing blueprint token $token", blueprint.contains(token, true)) }
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
        const val CONTENT_BASELINE_SHA = "CONTENT_BASELINE_SHA=32a183e7821de49a4958c52d75693c43ee99b2e1"
        const val CONTENT_BASELINE_PARENT_SHA = "CONTENT_BASELINE_PARENT_SHA=0e06cd99c72db66a72d6f36345a2dae6d63c4c1f"
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
    }
}