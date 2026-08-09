package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestCycleCanonicalProtocolContractTest {
    @Test
    fun restRepositoryConsumesCanonicalPlanningWithoutLegacyAuthority() {
        val repository = production("data/repository/RestCycleRepository.kt")
        listOf(
            "GenesisUltraRuntimeIdentityRepository",
            "CanonicalConsumerReadPort",
            "readRestCyclePlanningInput",
            "RestCycleProtocolTypes.OWNER_TYPE",
            "protocol.recoverBeforeMutation",
            "protocol.execute",
            "CanonicalReadDisposition.NOT_READY"
        ).forEach { token -> assertTrue("Missing REST token $token", repository.contains(token)) }

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
            "legacy_instance_read_only"
        ).forEach { token -> assertFalse("Legacy REST dependency returned: $token", repository.contains(token)) }
    }

    @Test
    fun rest002PlannerUsesCanonicalNeutralSourcesAndNeverExecutesRepair() {
        val planner = production("data/repository/RestRepairProposalPlanner.kt")
        val repository = production("data/repository/RestCycleRepository.kt")
        val worker = production("runtime/RestCycleWorker.kt")
        assertTrue(planner.contains("List<RestCycleSourceEvent>"))
        assertFalse(planner.contains("MemoryEventEntity"))
        assertTrue(repository.contains("planRestRepairProposalIfNeeded"))
        assertTrue(repository.contains("RestCycleOperationFactory.proposeRepair"))
        assertTrue(worker.contains("planRepairProposalIfNeeded()"))
        assertFalse(repository.contains("approveRestRepair"))
        assertFalse(repository.contains("executeRestRepair"))
        assertTrue(planner.contains("proposal_only_no_automatic_memory_mutation"))
    }

    @Test
    fun restOwnerHasClosedRest001AndRest002OperationsWithExactCanonicalEnsure() {
        val registry = production("data/repository/CrossDatabaseProtocolRegistry.kt")
        val operations = production("data/repository/RestCycleProtocolOperations.kt")
        val canonical = production("data/genesis/ultra/CanonicalRestCycleCommitPort.kt")
        val finalizer = production("data/repository/RestCycleProtocolFinalizer.kt")

        assertTrue(registry.contains("const val OWNER_TYPE = \"rest_cycle\""))
        assertTrue(registry.contains("const val EXECUTE = \"rest_cycle.execute\""))
        assertTrue(registry.contains("const val PROPOSE_REPAIR = \"rest_cycle.propose_repair\""))
        assertTrue(registry.contains("const val REPAIR_PROPOSED_EVENT = \"memory.repair_proposed\""))
        assertTrue(operations.contains("REST_002_PAYLOAD"))
        assertTrue(operations.contains("deterministicRepairMigrationId"))
        assertTrue(operations.contains("automatic_changes\" to false"))
        assertTrue(canonical.contains("command.operationType in RestCycleProtocolTypes.CLOSED_REGISTRY"))
        assertTrue(canonical.contains("findVerified(command)"))
        assertTrue(canonical.contains("CANONICAL_EVENT_MISMATCH"))
        assertTrue(canonical.contains("CANONICAL_PROVENANCE_MISMATCH"))
        assertTrue(finalizer.contains("finalizeRest002"))
        assertTrue(finalizer.contains("repair_execution\" to \"not_implemented\""))
        assertTrue(finalizer.contains("RestRepairProposalStore.STATUS_PLANNED"))
    }

    @Test
    fun rest001LocalProjectionRemainsAtomicAndUnchangedInAuthority() {
        val finalizer = production("data/repository/RestCycleProtocolFinalizer.kt")
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
    fun currentDocsRecordRest002IntegratedWithoutPretendingRepairExecutionOrReadiness() {
        val f1 = repositoryFile("docs/F1_CANONICAL_CONSUMER_CONVERGENCE.md").readText()
        val inventory = repositoryFile("docs/F3_CROSS_DATABASE_OPERATION_INVENTORY.md").readText()
        val runtime = repositoryFile("docs/CURRENT_RUNTIME_CONTRACT.md").readText()

        assertTrue(f1.contains("F1_REST_001=INTEGRATED_IN_MAIN"))
        assertTrue(f1.contains("F1_REST_002=INTEGRATED_IN_MAIN"))
        assertTrue(f1.contains("REST_REPAIR_PROPOSAL_CONVERGED=true"))
        assertTrue(f1.contains("REST_REPAIR_EXECUTION_IMPLEMENTED=false"))
        assertTrue(f1.contains("REST_BOOT_READINESS=OPEN"))
        assertTrue(runtime.contains("repair_execution=not_implemented"))
        assertTrue(runtime.contains("HEALTH_CONVERGENCE=OPEN"))
        assertFalse(f1.contains("REST_002=OPEN"))

        val integrated = inventory.substringAfter("### REST cycle").substringBefore("## Remaining operations")
        val remaining = inventory.substringAfter("## Remaining operations").substringBefore("## Integrated guarantees")
        assertTrue(integrated.contains("REST-001"))
        assertTrue(integrated.contains("REST-002"))
        assertFalse(remaining.contains("REST-001"))
        assertFalse(remaining.contains("REST-002"))
    }

    private fun production(relative: String): String =
        repositoryFile("app/src/main/java/com/morimil/app/$relative").readText()

    private fun productionRoot(fileName: String): String =
        repositoryFile("app/src/main/java/com/morimil/app/$fileName").readText()

    private fun repositoryFile(relativePath: String): File =
        sequenceOf(File(relativePath), File("../$relativePath")).firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")
}
