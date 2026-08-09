package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestCycleCanonicalProtocolContractTest {
    @Test
    fun rest001ConsumesCanonicalPlanningAndDurableProtocolOnly() {
        val repository = production("data/repository/RestCycleRepository.kt")
        listOf(
            "GenesisUltraRuntimeIdentityRepository",
            "CanonicalConsumerReadPort",
            "readRestCyclePlanningInput",
            "RestCycleProtocolTypes.OWNER_TYPE",
            "protocol.recoverBeforeMutation",
            "protocol.execute",
            "CanonicalReadDisposition.NOT_READY"
        ).forEach { token -> assertTrue("Missing REST-001 token $token", repository.contains(token)) }

        listOf(
            "MorimilDatabase",
            "MemoryDao",
            "MemoryRepository",
            "MemoryIntegrityCore",
            "loadGenesisCore(",
            "loadLocalIdentity(",
            "loadMemoryContext(",
            "loadMemoryEventAuditChain(",
            "recordSystemMemoryEvent(",
            "local_instance_pending",
            "legacy_instance_read_only",
            "RestRepairProposalPlanner",
            "planRestRepairProposalIfNeeded"
        ).forEach { token -> assertFalse("Legacy/REST-002 dependency returned: $token", repository.contains(token)) }
    }

    @Test
    fun rest001ProtocolHasClosedOwnerExactCanonicalEnsureAndAtomicLocalProjection() {
        val registry = production("data/repository/CrossDatabaseProtocolRegistry.kt")
        val operations = production("data/repository/RestCycleProtocolOperations.kt")
        val canonical = production("data/genesis/ultra/CanonicalRestCycleCommitPort.kt")
        val finalizer = production("data/repository/RestCycleProtocolFinalizer.kt")

        assertTrue(registry.contains("const val OWNER_TYPE = \"rest_cycle\""))
        assertTrue(registry.contains("const val EXECUTE = \"rest_cycle.execute\""))
        assertTrue(registry.contains("const val EXECUTED_EVENT = \"rest_cycle.local_consolidation\""))
        assertTrue(operations.contains("CrossDatabaseOperationIdentity.operationId"))
        assertFalse(operations.contains("System.currentTimeMillis"))
        assertFalse(operations.contains("nowMillis"))
        assertTrue(canonical.contains("findVerified(command)"))
        assertTrue(canonical.contains("CANONICAL_EVENT_MISMATCH"))
        assertTrue(canonical.contains("CANONICAL_PROVENANCE_MISMATCH"))
        assertTrue(finalizer.contains("canonical_memory_event"))
        assertTrue(finalizer.contains("upsertSelfSnapshot"))
        assertTrue(finalizer.contains("updateMigrationRecordResult"))
    }

    @Test
    fun startupRecoveryIncludesRestOwnerBeforeLegacyConvergence() {
        val runtimeGate = productionRoot("MorimilAppContainerRuntimeGate.kt")
        val rest = runtimeGate.indexOf("restCycleRecovery.recoverAtStartup")
        val legacy = runtimeGate.indexOf("convergence.converge(identity)")
        assertTrue(rest >= 0 && legacy > rest)
        assertTrue(runtimeGate.contains("rest_cycle_protocol_blocked"))
        assertTrue(runtimeGate.contains("rest_cycle_protocol_recovery_incomplete"))
    }

    @Test
    fun currentDocsStillKeepRestUnmergedUntilCandidateIntegration() {
        val f1 = repositoryFile("docs/F1_CANONICAL_CONSUMER_CONVERGENCE.md").readText()
        val inventory = repositoryFile("docs/F3_CROSS_DATABASE_OPERATION_INVENTORY.md").readText()
        assertTrue(f1.contains("REST_PLANNING_CONVERGED=false"))
        assertTrue(f1.contains("REST_EXECUTION_CONVERGED=false"))
        assertTrue(inventory.contains("`REST-001`"))
        assertTrue(inventory.contains("`REST-002`"))
    }

    private fun production(relative: String): String =
        repositoryFile("app/src/main/java/com/morimil/app/$relative").readText()

    private fun productionRoot(fileName: String): String =
        repositoryFile("app/src/main/java/com/morimil/app/$fileName").readText()

    private fun repositoryFile(relativePath: String): File =
        sequenceOf(File(relativePath), File("../$relativePath")).firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")
}
