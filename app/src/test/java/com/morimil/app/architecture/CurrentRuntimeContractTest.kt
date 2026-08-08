package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentRuntimeContractTest {
    private val contract by lazy {
        repositoryFile("docs/CURRENT_RUNTIME_CONTRACT.md").readText()
    }

    @Test
    fun contractUsesStableBaselineResolutionAndStoreVersions() {
        assertTrue(contract.contains(CONTENT_BASELINE_SHA))
        assertTrue(contract.contains(CONTENT_BASELINE_PARENT_SHA))
        assertTrue(contract.contains(CURRENT_MAIN_RESOLUTION))
        assertTrue(contract.contains(MERGE_SHA_EVIDENCE))
        assertTrue(contract.contains(COG_AUDITED_SOURCE_HEAD))
        assertTrue(contract.contains(ORCH_AUDITED_SOURCE_HEAD))
        assertTrue(contract.contains("PR #149", ignoreCase = true))
        assertTrue(contract.contains("PR #150", ignoreCase = true))
        assertTrue(contract.contains("PR #153", ignoreCase = true))
        assertTrue(contract.contains("PR #172", ignoreCase = true))
        assertTrue(contract.contains("closed and merged by squash", ignoreCase = true))
        assertTrue(contract.contains("historical CURRENT reconciliation", ignoreCase = true))
        assertTrue(contract.contains("| `MorimilDatabase` | `15` |"))
        assertTrue(contract.contains("| `MemoryOrganDatabase` | `9` |"))
        assertTrue(contract.contains("`cross_database_operations`"))

        assertEquals(15, roomVersion(productionFile("com/morimil/app/data/local/MorimilDatabase.kt")))
        assertEquals(9, roomVersion(productionFile("com/morimil/app/data/local/MemoryOrganDatabase.kt")))

        STALE_PHRASES.forEach { phrase ->
            assertFalse("Runtime contract contains stale phrase: $phrase", contract.contains(phrase, true))
        }
    }

    @Test
    fun integratedCompositionPreservesTheF1AAuthorityFrontier() {
        val canonicalComposition = productionFile(
            "com/morimil/app/MorimilAppContainerCanonicalConsumers.kt"
        ).readText()
        val cognitiveComposition = productionFile(
            "com/morimil/app/MorimilAppContainerCognitiveMigrationProtocol.kt"
        ).readText()
        val database = productionFile(
            "com/morimil/app/data/local/MemoryOrganDatabase.kt"
        ).readText()

        assertTrue(canonicalComposition.contains("GenesisUltraCanonicalConsumerReadAdapter.production"))
        assertTrue(canonicalComposition.contains("identityRepository = genesisUltraRuntimeIdentityRepository"))
        assertTrue(canonicalComposition.contains("memoryRepository = canonicalMemoryRepository"))
        assertTrue(cognitiveComposition.contains("consumerReadPort = canonicalConsumerReadPort"))
        assertFalse(cognitiveComposition.contains("GenesisUltraCanonicalConsumerReadAdapter.production"))
        assertTrue(cognitiveComposition.contains("CanonicalCognitiveMigrationReadPort.production"))
        assertTrue(contract.contains("CanonicalConsumerReadPort"))
        assertTrue(contract.contains("CanonicalCognitiveMigrationCommitPort"))
        assertTrue(contract.contains("CanonicalOrchestrationCommitPort"))
        assertTrue(contract.contains("does not compose a second direct identity or memory repository"))
        assertTrue(database.contains("CrossDatabaseOperationEntity::class"))
    }

    @Test
    fun startupRecoveryCogAndOrchGuaranteesAreCurrent() {
        listOf(
            "Startup and recovery",
            "process-wide advancement serialization by `operationId`",
            "reloads after lost CAS",
            "rejects stale blocking",
            "COG-001 through COG-004",
            "ORCH-002 through ORCH-004",
            "postSnapshotId",
            "owner-scoped recovery",
            "ProjectVault remains a separate protocol"
        ).forEach { requirement ->
            assertTrue("Missing runtime requirement $requirement", contract.contains(requirement, true))
        }
    }

    @Test
    fun phaseTableClosesOnlyBoundedIntegratedF3Owners() {
        assertTrue(
            contract.contains(
                "| F3.2 | Closed for the bounded COG-001 through COG-004 and ORCH-002 through ORCH-004 integrations only."
            )
        )
        assertTrue(contract.contains("AGENT, BOOT, RECALL, ORCH-001, and REST remain separately open"))
        assertTrue(contract.contains("| F3.3 | Open."))
        assertTrue(contract.contains("| F4 | Open:"))
        assertTrue(contract.contains("| F5 | Open:"))
        assertTrue(contract.contains("| F6 | Open:"))
        assertTrue(contract.contains("Issue `#86` remains open"))
        assertTrue(contract.contains("F1-ORCH-001 is not closed"))
        assertFalse(contract.contains("F3 complete", ignoreCase = true))
        assertFalse(contract.contains("production authorized", ignoreCase = true))
    }

    @Test
    fun residualHardeningRemainsVisibleAndNonBlocking() {
        listOf(
            "Room-backed two-coordinator concurrency",
            "failed-rollback snapshot fixture",
            "redundant rollback parameter cleanup",
            "vulnerable UPDATE-trigger replacement",
            "ORCH-specific mutation-testing"
        ).forEach { finding ->
            assertTrue("Missing residual finding $finding", contract.contains(finding, true))
        }
        assertTrue(contract.contains("not represented as completed work", true))
    }

    private fun roomVersion(file: File): Int {
        val annotation = requireNotNull(
            Regex("""@Database\(([\s\S]*?)\)\s*abstract class""").find(file.readText())
        ) { "Room database annotation not found in ${file.path}" }.groupValues[1]
        return requireNotNull(Regex("""version\s*=\s*(\d+)""").find(annotation)) {
            "Room database version not found in ${file.path}"
        }.groupValues[1].toInt()
    }

    private fun productionFile(relativePath: String): File =
        repositoryFile("app/src/main/java/$relativePath")

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
            "candidate not merged",
            "draft pr `#149`",
            "draft pr #149",
            "orchestration gates, and final legacy retirement are not fully converged",
            "orch, agent, boot, recall, and rest owners remain outside",
            "f3.2 | open. draft pr"
        )
    }
}
