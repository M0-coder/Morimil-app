package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossDatabaseImplementationOrderContractTest {
    @Test
    fun implementationOrderStartsBoundedAndLeavesRestCycleLast() {
        val inventory = repositoryFile(
            "docs/F3_CROSS_DATABASE_OPERATION_INVENTORY.md"
        ).readText()

        val section = inventory.substringAfter("## Implementation order after STOP S5")
        val cognitive = section.indexOf("`COG-001` through `COG-004`")
        val orchestration = section.indexOf("`ORCH-002` through `ORCH-004`")
        val agents = section.indexOf("`AGENT-001` through `AGENT-006`")
        val bootstrap = section.indexOf("`BOOT-001`")
        val recall = section.indexOf("`RECALL-001` and `ORCH-001`")
        val rest = section.indexOf("`REST-001` and `REST-002`")

        listOf(cognitive, orchestration, agents, bootstrap, recall, rest).forEach { position ->
            assertTrue("F3.2 implementation order entry is missing", position >= 0)
        }
        assertTrue("Cognitive migration must be the first bounded protocol owner", cognitive < orchestration)
        assertTrue("Orchestration must precede agent lifecycle migration", orchestration < agents)
        assertTrue("Agent lifecycle must precede bootstrap migration", agents < bootstrap)
        assertTrue("Bootstrap must precede canonical rebuild projections", bootstrap < recall)
        assertTrue("RestCycle must remain the final and widest workflow migration", recall < rest)
    }

    @Test
    fun inventorySeparatesRuntimeAuditFromRepositoryReconciliation() {
        val inventory = repositoryFile(
            "docs/F3_CROSS_DATABASE_OPERATION_INVENTORY.md"
        ).readText()

        assertTrue(
            inventory.contains(
                "Audited baseline: `main@612d91aef131f367140ffb87a60a19ef49adcbc8`"
            )
        )
        assertTrue(
            inventory.contains(
                "Baseline scope: production runtime and cross-database owner inventory."
            )
        )
        assertTrue(
            inventory.contains(
                "Repository state reconciled: `main@29b24d4167bea613a01059da02aa8f9040d0ec2a`"
            )
        )
        assertTrue(inventory.contains("STOP S5 remains open through #123 and #124"))
        assertTrue(inventory.contains("This inventory does not authorize runtime changes."))
    }

    private fun repositoryFile(relativePath: String): File {
        return sequenceOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")
    }
}
