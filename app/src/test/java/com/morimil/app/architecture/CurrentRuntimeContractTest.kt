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
    fun contractUsesPostRest002TruthAndStableMainResolution() {
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
            "PR_181=MERGED_BY_SQUASH_HISTORICAL",
            "PR_182=MERGED_BY_SQUASH_HISTORICAL",
            "PR_183=MERGED_BY_SQUASH_HISTORICAL",
            "PR_184=MERGED_BY_SQUASH_HISTORICAL"
        ).forEach { token -> assertTrue("Missing runtime token $token", contract.contains(token)) }

        assertTrue(contract.contains("| `MorimilDatabase` | `15` |"))
        assertTrue(contract.contains("| `MemoryOrganDatabase` | `9` |"))
        assertEquals(15, roomVersion(productionFile("com/morimil/app/data/local/MorimilDatabase.kt")))
        assertEquals(9, roomVersion(productionFile("com/morimil/app/data/local/MemoryOrganDatabase.kt")))
    }

    @Test
    fun canonicalAuthorityIncludesRestWithoutExpandingIdentityAuthority() {
        val rest = productionFile("com/morimil/app/data/repository/RestCycleRepository.kt").readText()
        assertTrue(contract.contains("GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository"))
        assertTrue(contract.contains("CanonicalRuntimeBootstrapCommitPort"))
        assertTrue(contract.contains("CanonicalRestCycleCommitPort"))
        assertTrue(contract.contains("No specialized port or derived projection becomes an identity source"))
        assertTrue(contract.contains("writer authorization is not ownership", true))
        assertTrue(rest.contains("CanonicalConsumerReadPort"))
        assertTrue(rest.contains("readRestCyclePlanningInput"))
        assertTrue(rest.contains("RestCycleProtocolTypes.OWNER_TYPE"))
        assertTrue(rest.contains("planRestRepairProposalIfNeeded"))
        assertTrue(rest.contains("RestCycleOperationFactory.proposeRepair"))
        listOf(
            "loadGenesisCore(",
            "loadLocalIdentity(",
            "loadMemoryContext(",
            "loadMemoryEventAuditChain(",
            "MemoryRepository",
            "recordSystemMemoryEvent("
        ).forEach { assertFalse("Legacy REST dependency returned: $it", rest.contains(it)) }
    }

    @Test
    fun startupRecoveryOrderIncludesRestBeforeLegacyConvergence() {
        val section = contract.substringAfter("## Startup and recovery")
        val cog = section.indexOf("COG recovery")
        val orch = section.indexOf("ORCH recovery")
        val agent = section.indexOf("AGENT recovery")
        val rest = section.indexOf("REST recovery")
        val legacy = section.indexOf("remaining legacy convergence")
        val vault = section.indexOf("ProjectVault recovery")
        val boot = section.indexOf("BOOT-001 bootstrap/recovery")
        assertTrue(cog >= 0 && orch > cog && agent > orch && rest > agent && legacy > rest && vault > legacy && boot > vault)
        assertTrue(contract.contains("REST recovery is owner-scoped"))
        assertTrue(contract.contains("BOOT still reports `recallState=WAITING_FOR_CANONICAL_MEMORY_ADAPTER`"))
        assertTrue(contract.contains("BOOT still reports `restCycleState=WAITING_FOR_CANONICAL_MEMORY_ADAPTER`"))
        assertTrue(contract.contains("BOOT cannot consume COG, ORCH, AGENT or REST journal rows"))
    }

    @Test
    fun phaseTableIntegratesRest002ProposalOnlyButKeepsReadinessOpen() {
        listOf(
            "RECALL_001=INTEGRATED",
            "REST_BOOT_READINESS=OPEN",
            "RECALL_BOOT_READINESS=OPEN",
            "ORCH_001=INTEGRATED",
            "REST_001=INTEGRATED",
            "REST_002=INTEGRATED",
            "REST_REPAIR_PROPOSAL_CONVERGED=true",
            "REST_REPAIR_EXECUTION_IMPLEMENTED=false",
            "HEALTH_CONVERGENCE=OPEN",
            "F3_3=OPEN"
        ).forEach { assertTrue("Missing phase token $it", contract.contains(it)) }
        assertTrue(contract.contains("F3.2 | Integrated for ProjectVault, COG-001..004, ORCH-001..004, AGENT-001..006, BOOT-001, RECALL-001 derived rebuild, REST-001 and REST-002 proposal convergence"))
        assertFalse(contract.contains("REST_001_002=OPEN"))
        assertFalse(contract.contains("REST_001=OPEN"))
        assertFalse(contract.contains("REST_002=OPEN"))
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
            "REST-specific mutation testing is not established",
            "RECALL-specific mutation testing is not established",
            "REST repair execution remains intentionally not implemented",
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
        const val CONTENT_BASELINE_SHA = "CONTENT_BASELINE_SHA=e05ae7a08b1a88d2fbc0d4f2dff8ff06d282c908"
        const val CONTENT_BASELINE_PARENT_SHA = "CONTENT_BASELINE_PARENT_SHA=9585e94a690d4f00d591f81d14e56aedefda3341"
        const val COG_AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        const val ORCH_AUDITED_SOURCE_HEAD = "0348dccb561e576d17c45e7f8b1e38717332772b"
        const val ORCH_001_AUDITED_SOURCE_HEAD = "fe188fdee8eae901434a255051b6fa4f852b929b"
        const val AGENT_AUDITED_SOURCE_HEAD = "74e072b911db692041d3716af9d0511b83ad70b7"
        const val BOOT_AUDITED_SOURCE_HEAD = "c7710635fa172108cce87b3f7a76d6e037095864"
        const val RECALL_AUDITED_SOURCE_HEAD = "fae8a0df3c29775317986877bce2b8eda8593d27"
        const val REST_001_AUDITED_SOURCE_HEAD = "3661450325237fcadb86098ec16ee45cd039bc0b"
        const val REST_002_AUDITED_SOURCE_HEAD = "2ecca3f48d5e0ef27bd927da3986292daf7f7e2c"
    }
}
