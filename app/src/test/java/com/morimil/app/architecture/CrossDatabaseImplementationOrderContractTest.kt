package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossDatabaseImplementationOrderContractTest {
    @Test
    fun implementationOrderRecordsHealthLegacyConvergenceBeforeRemainingRecallWork() {
        val inventory = repositoryFile("docs/F3_CROSS_DATABASE_OPERATION_INVENTORY.md").readText()
        val section = inventory.substringAfter("## Implementation order after STOP S5")

        val cognitive = section.indexOf("`COG-001` through `COG-004`")
        val orchestration = section.indexOf("`ORCH-002` through `ORCH-004`")
        val agents = section.indexOf("`AGENT-001` through `AGENT-006`")
        val bootstrap = section.indexOf("`BOOT-001`")
        val recall = section.indexOf("`RECALL-001` — integrated canonical derived rebuild")
        val orch001 = section.indexOf("`ORCH-001` — integrated canonical identity-gated seed convergence")
        val rest001 = section.indexOf("`REST-001` — integrated canonical planning and owner-scoped durable XOP")
        val rest002 = section.indexOf("`REST-002` — integrated canonical proposal-only XOP")
        val bootstrapHealth = section.indexOf("Bootstrap dependency-derived Health — integrated by PR #187")
        val restReadiness = section.indexOf("`REST-BOOT-001` — integrated canonical read-only startup readiness")
        val healthLegacy = section.indexOf("F1 Health legacy-consumer convergence — integrated as canonical read-only observation with no memory writer")
        val recallReadiness = section.indexOf("RECALL startup-readiness convergence")
        val reaudit = section.indexOf("Full F1/F3.2 reaudit")
        val f33 = section.indexOf("F3.3 only after every F3.2/readiness dependency")

        listOf(
            cognitive,
            orchestration,
            agents,
            bootstrap,
            recall,
            orch001,
            rest001,
            rest002,
            bootstrapHealth,
            restReadiness,
            healthLegacy,
            recallReadiness,
            reaudit,
            f33
        ).forEach { position -> assertTrue("F3.2 implementation order entry is missing", position >= 0) }
        assertTrue(cognitive < orchestration)
        assertTrue(orchestration < agents)
        assertTrue(agents < bootstrap)
        assertTrue(bootstrap < recall)
        assertTrue(recall < orch001)
        assertTrue(orch001 < rest001)
        assertTrue(rest001 < rest002)
        assertTrue(rest002 < bootstrapHealth)
        assertTrue(bootstrapHealth < restReadiness)
        assertTrue(restReadiness < healthLegacy)
        assertTrue(healthLegacy < recallReadiness)
        assertTrue(recallReadiness < reaudit)
        assertTrue(reaudit < f33)

        assertFalse(section.contains("`REST-001` and `REST-002`"))
        assertFalse(section.contains("`REST-001` — next bounded convergence work"))
        assertFalse(section.contains("F1 health legacy-consumer convergence for `LocalNervousSystemRepository`"))
        assertFalse(section.contains("Health convergence and REST/RECALL startup-readiness"))
    }

    @Test
    fun inventoryKeepsMovingMainExternalAndF33Separate() {
        val inventory = repositoryFile("docs/F3_CROSS_DATABASE_OPERATION_INVENTORY.md").readText()
        assertTrue(inventory.contains("CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF"))
        assertTrue(inventory.contains("MERGE_SHA_EVIDENCE=EXTERNAL"))
        assertTrue(inventory.contains("STOP_S5=CLOSED"))
        assertTrue(inventory.contains("F3.3 only after every F3.2/readiness dependency has a recorded disposition and separate authorization"))
        assertTrue(inventory.contains("REST_002=INTEGRATED"))
        assertTrue(inventory.contains("REST_REPAIR_PROPOSAL_CONVERGED=true"))
        assertTrue(inventory.contains("REST_REPAIR_EXECUTION_IMPLEMENTED=false"))
        assertTrue(inventory.contains("REST_BOOT_READINESS=INTEGRATED"))
        assertTrue(inventory.contains("RECALL_BOOT_READINESS=OPEN"))
        assertTrue(inventory.contains("BOOTSTRAP_HEALTH_DERIVATION=INTEGRATED"))
        assertTrue(inventory.contains("HEALTH_LEGACY_CONSUMER_CONVERGENCE=INTEGRATED"))
        assertTrue(inventory.contains("HEALTH_CAN_READ_CANONICAL_MEMORY=true"))
        assertTrue(inventory.contains("HEALTH_CAN_WRITE_CANONICAL_MEMORY=false"))
        assertTrue(inventory.contains("HEALTH_CAN_WRITE_LEGACY_MEMORY_EVENTS=false"))
        assertTrue(inventory.contains("HEALTH_CONVERGENCE=OPEN"))
        assertTrue(inventory.contains("HEALTH_CONVERGED=false"))
        assertTrue(inventory.contains("HEALTH_STATE=WAITING_FOR_DEPENDENCIES"))
        assertFalse(inventory.contains("REST_BOOT_READINESS=OPEN"))
        assertFalse(inventory.contains("HEALTH_CONVERGENCE=INTEGRATED"))
        assertFalse(inventory.contains("REST_002=OPEN"))
        assertFalse(inventory.contains("MORIMIL_OPERATIONAL_BIRTH=OCCURRED"))
    }

    private fun repositoryFile(relativePath: String): File =
        sequenceOf(File(relativePath), File("../$relativePath")).firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")
}
