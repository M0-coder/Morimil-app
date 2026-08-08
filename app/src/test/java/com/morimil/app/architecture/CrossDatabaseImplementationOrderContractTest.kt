package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossDatabaseImplementationOrderContractTest {
    @Test
    fun implementationOrderRecordsRecallIntegratedAndOrchNext() {
        val inventory = repositoryFile("docs/F3_CROSS_DATABASE_OPERATION_INVENTORY.md").readText()
        val section = inventory.substringAfter("## Implementation order after STOP S5")

        val cognitive = section.indexOf("`COG-001` through `COG-004`")
        val orchestration = section.indexOf("`ORCH-002` through `ORCH-004`")
        val agents = section.indexOf("`AGENT-001` through `AGENT-006`")
        val bootstrap = section.indexOf("`BOOT-001`")
        val recall = section.indexOf("`RECALL-001` — integrated canonical derived rebuild")
        val orch001 = section.indexOf("`ORCH-001` — next bounded convergence work")
        val rest = section.indexOf("`REST-001` and `REST-002`")

        listOf(cognitive, orchestration, agents, bootstrap, recall, orch001, rest).forEach { position ->
            assertTrue("F3.2 implementation order entry is missing", position >= 0)
        }
        assertTrue(cognitive < orchestration)
        assertTrue(orchestration < agents)
        assertTrue(agents < bootstrap)
        assertTrue(bootstrap < recall)
        assertTrue(recall < orch001)
        assertTrue(orch001 < rest)

        assertTrue(section.contains("`RECALL-001` — integrated canonical derived rebuild"))
        assertTrue(section.contains("`ORCH-001` — next bounded convergence work"))
        assertFalse(section.contains("`RECALL-001` and `ORCH-001` — next bounded convergence work"))
        assertFalse(section.contains("`BOOT-001` — next bounded owner"))
    }

    @Test
    fun inventoryKeepsMovingMainExternalAndF33Separate() {
        val inventory = repositoryFile("docs/F3_CROSS_DATABASE_OPERATION_INVENTORY.md").readText()
        assertTrue(inventory.contains("CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF"))
        assertTrue(inventory.contains("MERGE_SHA_EVIDENCE=EXTERNAL"))
        assertTrue(inventory.contains("STOP_S5=CLOSED"))
        assertTrue(inventory.contains("F3.3 only after every F3.2 owner has a recorded disposition and separate authorization"))
        assertFalse(inventory.contains("MORIMIL_OPERATIONAL_BIRTH=OCCURRED"))
    }

    private fun repositoryFile(relativePath: String): File =
        sequenceOf(File(relativePath), File("../$relativePath")).firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")
}
