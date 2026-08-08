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
    fun blueprintIsCurrentAndUsesPostAgentBaseline() {
        assertTrue(blueprint.startsWith("# Document status: CURRENT"))
        assertTrue(blueprint.contains("implemented and audited design", true))
        listOf(
            CONTENT_BASELINE_SHA,
            CONTENT_BASELINE_PARENT_SHA,
            "CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF",
            "MERGE_SHA_EVIDENCE=EXTERNAL",
            COG_AUDITED_SOURCE_HEAD,
            ORCH_AUDITED_SOURCE_HEAD,
            AGENT_AUDITED_SOURCE_HEAD,
            "PR_174=MERGED_BY_SQUASH_HISTORICAL",
            "COG_001_004=INTEGRATED_IN_MAIN",
            "ORCH_002_004=INTEGRATED_IN_MAIN",
            "AGENT_001_006=INTEGRATED_IN_MAIN"
        ).forEach { token -> assertTrue("Missing blueprint token $token", blueprint.contains(token)) }
    }

    @Test
    fun authorityFrontierAndBoundedScopeRemainExplicit() {
        val identityAuthority = blueprint.indexOf("GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository")
        val commonBoundary = blueprint.indexOf("-> CanonicalConsumerReadPort", identityAuthority)
        val specializedBoundary = blueprint.indexOf("-> CognitiveMigrationCanonicalReadPort", commonBoundary)
        assertTrue(identityAuthority >= 0 && commonBoundary > identityAuthority && specializedBoundary > commonBoundary)
        assertTrue(blueprint.contains("ProjectVault remains separate"))
        assertTrue(blueprint.contains("PR #174 integrated AGENT-001..006"))
        listOf("BOOT-001", "RECALL-001", "ORCH-001", "REST-001/002", "F3.3 legacy removal remains open").forEach {
            assertTrue("Missing remaining scope $it", blueprint.contains(it))
        }
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
            "Room-backed multi-coordinator concurrency",
            "rollback snapshot",
            "UPDATE-trigger replacement"
        ).forEach { token -> assertTrue("Missing blueprint token $token", blueprint.contains(token, true)) }
        assertFalse(blueprint.contains("AGENT, BOOT, RECALL, ORCH-001, REST, and F3.3 legacy removal remain open", true))
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
        const val CONTENT_BASELINE_SHA = "CONTENT_BASELINE_SHA=d577a75290d70f423f6e83bf237a8a453f3a534e"
        const val CONTENT_BASELINE_PARENT_SHA = "CONTENT_BASELINE_PARENT_SHA=9da342f2c147105ea882076f4ebc6ab5f5494190"
        const val COG_AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        const val ORCH_AUDITED_SOURCE_HEAD = "0348dccb561e576d17c45e7f8b1e38717332772b"
        const val AGENT_AUDITED_SOURCE_HEAD = "74e072b911db692041d3716af9d0511b83ad70b7"
    }
}
