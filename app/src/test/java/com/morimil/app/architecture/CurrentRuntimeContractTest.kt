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
    fun contractUsesPostAgentBaselineAndStableMainResolution() {
        listOf(
            CONTENT_BASELINE_SHA,
            CONTENT_BASELINE_PARENT_SHA,
            "CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF",
            "MERGE_SHA_EVIDENCE=EXTERNAL",
            COG_AUDITED_SOURCE_HEAD,
            ORCH_AUDITED_SOURCE_HEAD,
            AGENT_AUDITED_SOURCE_HEAD,
            "PR_174=MERGED_BY_SQUASH_HISTORICAL"
        ).forEach { token -> assertTrue("Missing runtime token $token", contract.contains(token)) }

        assertTrue(contract.contains("| `MorimilDatabase` | `15` |"))
        assertTrue(contract.contains("| `MemoryOrganDatabase` | `9` |"))
        assertEquals(15, roomVersion(productionFile("com/morimil/app/data/local/MorimilDatabase.kt")))
        assertEquals(9, roomVersion(productionFile("com/morimil/app/data/local/MemoryOrganDatabase.kt")))
    }

    @Test
    fun canonicalAuthorityIncludesAgentPortWithoutExpandingIdentityAuthority() {
        val composition = productionFile("com/morimil/app/MorimilAppContainerCognitiveMigrationProtocol.kt").readText()
        assertTrue(contract.contains("GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository"))
        assertTrue(contract.contains("CanonicalCognitiveMigrationCommitPort"))
        assertTrue(contract.contains("CanonicalOrchestrationCommitPort"))
        assertTrue(contract.contains("CanonicalAgentLifecycleCommitPort"))
        assertTrue(contract.contains("No specialized port becomes an identity source"))
        assertTrue(composition.contains("CanonicalAgentLifecycleCommitPort"))
    }

    @Test
    fun startupRecoveryOrderAndOwnerIsolationAreCurrent() {
        val section = contract.substringAfter("## Startup and recovery")
        val cog = section.indexOf("COG recovery")
        val orch = section.indexOf("ORCH recovery")
        val agent = section.indexOf("AGENT recovery")
        val legacy = section.indexOf("remaining legacy convergence")
        assertTrue(cog >= 0 && orch > cog && agent > orch && legacy > agent)
        assertTrue(contract.contains("cannot consume one another's journal rows"))
    }

    @Test
    fun phaseTableClosesOnlyIntegratedBoundedScopes() {
        assertTrue(contract.contains("F3.2 | Closed only for ProjectVault, COG-001..004, ORCH-002..004, and AGENT-001..006"))
        listOf("BOOT_001=OPEN", "RECALL_001=OPEN", "ORCH_001=OPEN", "REST_001_002=OPEN", "F3.3 | Open").forEach {
            assertTrue("Missing open-state token $it", contract.contains(it))
        }
        assertTrue(contract.contains("MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED"))
        assertFalse(contract.contains("F3 complete", ignoreCase = true))
    }

    @Test
    fun agentResidualDebtRemainsVisible() {
        listOf(
            "AGENT-specific mutation testing is not established",
            "zero direct instrumented line coverage",
            "single-process Android architecture",
            "physical ARM64 inference"
        ).forEach { finding -> assertTrue("Missing residual $finding", contract.contains(finding, true)) }
    }

    private fun roomVersion(file: File): Int {
        val annotation = requireNotNull(Regex("""@Database\(([\s\S]*?)\)\s*abstract class""").find(file.readText())).groupValues[1]
        return requireNotNull(Regex("""version\s*=\s*(\d+)""").find(annotation)).groupValues[1].toInt()
    }

    private fun productionFile(relativePath: String): File = repositoryFile("app/src/main/java/$relativePath")

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
