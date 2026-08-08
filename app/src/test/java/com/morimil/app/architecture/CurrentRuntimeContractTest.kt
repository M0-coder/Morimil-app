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
    fun contractUsesPostOrchTruthAndStableMainResolution() {
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
            "PR_178=MERGED_BY_SQUASH_HISTORICAL",
            "PR_179=MERGED_BY_SQUASH_HISTORICAL",
            "PR_180=MERGED_BY_SQUASH_HISTORICAL"
        ).forEach { token -> assertTrue("Missing runtime token $token", contract.contains(token)) }

        assertTrue(contract.contains("| `MorimilDatabase` | `15` |"))
        assertTrue(contract.contains("| `MemoryOrganDatabase` | `9` |"))
        assertEquals(15, roomVersion(productionFile("com/morimil/app/data/local/MorimilDatabase.kt")))
        assertEquals(9, roomVersion(productionFile("com/morimil/app/data/local/MemoryOrganDatabase.kt")))
    }

    @Test
    fun canonicalAuthorityIncludesRecallAndOrchSeedWithoutExpandingIdentityAuthority() {
        val recall = productionFile("com/morimil/app/data/repository/RecallScheduleRepository.kt").readText()
        val orch = productionFile("com/morimil/app/data/repository/AgentOrchestrationRepository.kt").readText()
        assertTrue(contract.contains("GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository"))
        assertTrue(contract.contains("CanonicalRuntimeBootstrapCommitPort"))
        assertTrue(contract.contains("No specialized port or derived projection becomes an identity source"))
        assertTrue(contract.contains("writer authorization is not ownership", true))
        assertTrue(recall.contains("CanonicalConsumerReadPort"))
        assertTrue(recall.contains("readRecallCandidates"))
        assertTrue(orch.contains("identityRepository.readCommittedIdentity() ?: return"))
        assertFalse(orch.contains("memoryRepository.hasCompleteBirth()"))
        listOf("loadGenesisCore(", "loadLocalIdentity(", "loadMemoryContext(", "local_instance_pending").forEach {
            assertFalse("Legacy recall dependency returned: $it", recall.contains(it))
        }
    }

    @Test
    fun startupRecoveryOrderAndRecallReadinessAreCurrent() {
        val section = contract.substringAfter("## Startup and recovery")
        val cog = section.indexOf("COG recovery")
        val orch = section.indexOf("ORCH recovery")
        val agent = section.indexOf("AGENT recovery")
        val legacy = section.indexOf("remaining legacy convergence")
        val vault = section.indexOf("ProjectVault recovery")
        val boot = section.indexOf("BOOT-001 bootstrap/recovery")
        assertTrue(cog >= 0 && orch > cog && agent > orch && legacy > agent && vault > legacy && boot > vault)
        assertTrue(contract.contains("BOOT cannot consume COG, ORCH or AGENT journal rows"))
        assertTrue(contract.contains("BOOT still reports `recallState=WAITING_FOR_CANONICAL_MEMORY_ADAPTER`"))
    }

    @Test
    fun phaseTableIntegratesOrchButKeepsRemainingOwnersOpen() {
        listOf(
            "RECALL_001=INTEGRATED",
            "RECALL_BOOT_READINESS=OPEN",
            "ORCH_001=INTEGRATED",
            "REST_001_002=OPEN",
            "HEALTH_CONVERGENCE=OPEN",
            "F3_3=OPEN"
        ).forEach { assertTrue("Missing phase token $it", contract.contains(it)) }
        assertTrue(contract.contains("F3.2 | Integrated for ProjectVault, COG-001..004, ORCH-001..004, AGENT-001..006, BOOT-001 and RECALL-001 derived rebuild"))
        assertFalse(contract.contains("RECALL_001=OPEN"))
        assertFalse(contract.contains("ORCH_001=OPEN"))
        assertTrue(contract.contains("MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED"))
        assertFalse(contract.contains("F3 complete", ignoreCase = true))
    }

    @Test
    fun sovereigntyAndResidualDebtRemainVisible() {
        listOf(
            "instanceId != bodyId",
            "ownership_conferred=false",
            "guardian_role=custodian_witness",
            "future F5 successor Body",
            "RECALL-specific mutation testing is not established",
            "ORCH-specific mutation testing remains unestablished",
            "physical ARM64 inference",
            "F5 succession/revocation"
        ).forEach { finding -> assertTrue("Missing residual/invariant $finding", contract.contains(finding, true)) }
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
        const val CONTENT_BASELINE_SHA = "CONTENT_BASELINE_SHA=6e0444b698bdc5c557ec3ea83f48d7980da1a36b"
        const val CONTENT_BASELINE_PARENT_SHA = "CONTENT_BASELINE_PARENT_SHA=bdbb5b2a040b728508948cd3cfbd8807b40a12f6"
        const val COG_AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        const val ORCH_AUDITED_SOURCE_HEAD = "0348dccb561e576d17c45e7f8b1e38717332772b"
        const val ORCH_001_AUDITED_SOURCE_HEAD = "fe188fdee8eae901434a255051b6fa4f852b929b"
        const val AGENT_AUDITED_SOURCE_HEAD = "74e072b911db692041d3716af9d0511b83ad70b7"
        const val BOOT_AUDITED_SOURCE_HEAD = "c7710635fa172108cce87b3f7a76d6e037095864"
        const val RECALL_AUDITED_SOURCE_HEAD = "fae8a0df3c29775317986877bce2b8eda8593d27"
    }
}
