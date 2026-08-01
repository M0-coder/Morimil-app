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
    fun contractTracksExactPostMergeMainAndStoreVersions() {
        assertTrue(contract.contains(CURRENT_MAIN))
        assertTrue(contract.contains(PREVIOUS_MAIN))
        assertTrue(contract.contains(AUDITED_SOURCE_HEAD))
        assertTrue(contract.contains("PR #149", ignoreCase = true))
        assertTrue(contract.contains("PR #150", ignoreCase = true))
        assertTrue(contract.contains("closed and merged by squash", ignoreCase = true))
        assertTrue(contract.contains("post-merge CURRENT reconciliation", ignoreCase = true))
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
        val cognitiveComposition = productionFile(
            "com/morimil/app/MorimilAppContainerCognitiveMigrationProtocol.kt"
        ).readText()
        val database = productionFile(
            "com/morimil/app/data/local/MemoryOrganDatabase.kt"
        ).readText()

        assertTrue(cognitiveComposition.contains("GenesisUltraCanonicalConsumerReadAdapter.production"))
        assertTrue(cognitiveComposition.contains("CanonicalCognitiveMigrationReadPort.production"))
        assertTrue(contract.contains("CanonicalConsumerReadPort"))
        assertTrue(contract.contains("CanonicalCognitiveMigrationCommitPort"))
        assertTrue(contract.contains("does not compose a second direct identity or memory repository"))
        assertTrue(database.contains("CrossDatabaseOperationEntity::class"))
    }

    @Test
    fun startupRecoveryAndCogGuaranteesAreCurrent() {
        listOf(
            "Startup and recovery",
            "process-wide advancement serialization by `operationId`",
            "reloads after lost CAS",
            "rejects stale blocking",
            "COG-001 through COG-004",
            "postSnapshotId",
            "ProjectVault remains a separate protocol"
        ).forEach { requirement ->
            assertTrue("Missing runtime requirement $requirement", contract.contains(requirement, true))
        }
    }

    @Test
    fun phaseTableClosesOnlyTheBoundedCogScope() {
        assertTrue(contract.contains("| F3.2 | Closed for the bounded COG-001 through COG-004 integration only."))
        assertTrue(contract.contains("| F3.3 | Open."))
        assertTrue(contract.contains("| F4 | Open:"))
        assertTrue(contract.contains("| F5 | Open:"))
        assertTrue(contract.contains("| F6 | Open:"))
        assertTrue(contract.contains("Issue `#86` remains open"))
        assertFalse(contract.contains("F3 complete", ignoreCase = true))
        assertFalse(contract.contains("production authorized", ignoreCase = true))
    }

    @Test
    fun residualHardeningRemainsVisibleAndNonBlocking() {
        listOf(
            "Room-backed two-coordinator concurrency",
            "failed-rollback snapshot fixture",
            "redundant rollback parameter cleanup",
            "vulnerable UPDATE-trigger replacement"
        ).forEach { finding ->
            assertTrue("Missing residual finding $finding", contract.contains(finding, true))
        }
        assertTrue(contract.contains("not represented as production defects", true))
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
        const val CURRENT_MAIN = "6250214bb6664a8fff851ed0afc2438bbc276931"
        const val PREVIOUS_MAIN = "5023981da7caf31c8f3679919f59205708b72823"
        const val AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        val STALE_PHRASES = listOf(
            "candidate not merged",
            "draft pr `#149`",
            "draft pr #149",
            "memoryorgandatabase` | `8` | `9`",
            "validation only; not in protected `main`",
            "f3.2 | open. draft pr"
        )
    }
}
