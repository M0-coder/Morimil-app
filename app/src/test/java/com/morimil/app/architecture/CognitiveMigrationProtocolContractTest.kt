package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CognitiveMigrationProtocolContractTest {
    @Test
    fun cogRepositoryHasNoLegacyCanonicalInput() {
        val source = source("data/repository/CognitiveMigrationRepository.kt").readText()

        listOf(
            "MemoryEventEntity",
            "memoryDatabase",
            "memoryDao",
            "loadGenesisCore",
            "loadLocalIdentity",
            "recordSystemMemoryEvent",
            "auditLivingMemoryChain"
        ).forEach { forbidden ->
            assertFalse("Legacy cognitive input leaked: $forbidden", source.contains(forbidden))
        }
        assertTrue(source.contains("CognitiveMigrationCanonicalReadPort"))
        assertTrue(source.contains("GenesisUltraRuntimeIdentityRepository"))
        assertTrue(source.contains("CrossDatabaseOperationCoordinator"))
    }

    @Test
    fun cognitivePlanningHasAClosedMemorySourcePolicy() {
        val source = source(
            "data/genesis/ultra/CanonicalCognitiveMigrationReadPort.kt"
        ).readText()

        assertTrue(source.contains("ALLOWED_PLANNING_NOTE_SCHEMAS"))
        assertTrue(source.contains("isAllowedPlanningSource"))
        assertTrue(source.contains("cognitive_migration."))
        assertTrue(source.contains("cognitive_migration_protocol"))
        assertTrue(source.contains("cross_database_operations"))
        assertFalse(source.contains("event.semantics?.memoryKind != \"chat_noise\""))
    }

    @Test
    fun protocolTipMetadataCannotEnterProposalIdentityOrPayload() {
        val planner = sourceAtRoot("core/memory/CognitiveMigrationPlanner.kt").readText()
        val repository = source("data/repository/CognitiveMigrationRepository.kt").readText()

        assertTrue(planner.contains("planning_anchor_digest"))
        assertTrue(planner.contains("plan_core.v4"))
        assertTrue(planner.contains("planned_record.v2"))
        assertFalse(planner.contains("\"canonical_last_event_hash\" to input"))
        assertFalse(planner.contains("\"canonical_pre_snapshot_hash\" to input"))
        assertTrue(repository.contains("cog_001.payload.v2"))
        assertFalse(repository.contains("\"canonical_last_sequence\" to input"))
        assertFalse(repository.contains("\"canonical_pre_snapshot_hash\" to input"))
    }

    @Test
    fun coordinatorPersistsReceiptBeforeTypedOwnerFinalization() {
        val source = source("data/repository/CrossDatabaseOperationCoordinator.kt").readText()
        val receipt = source.indexOf("persistCanonicalReceipt")
        val pendingLocal = source.indexOf("transitionCanonicalCommitted")
        val finalizer = source.indexOf("finalizeCommitted")

        assertTrue(receipt >= 0)
        assertTrue(pendingLocal > receipt)
        assertTrue(finalizer > pendingLocal)
        assertTrue(source.contains("database.withTransaction"))
        assertTrue(source.contains("markCommittedWithLocalResult"))
    }

    @Test
    fun localResultsRemainDeterministicAcrossReceiptRecovery() {
        val coordinator =
            source("data/repository/CrossDatabaseOperationCoordinator.kt").readText()
        val finalizer =
            source("data/repository/CognitiveMigrationProtocolFinalizer.kt").readText()

        assertTrue(coordinator.contains("receiptObservedThisExecution"))
        assertTrue(coordinator.contains("reusedExistingEvent = true"))
        assertFalse(finalizer.contains("\"reused_existing_event\""))
        assertFalse(finalizer.contains("receipt.reusedExistingEvent"))
        assertTrue(finalizer.contains("cog_001.local_result.v2"))
        assertTrue(finalizer.contains("cog_004.local_result.v2"))
    }

    @Test
    fun runtimeGateRecoversCognitiveProtocolBeforeProjectVaultAndBootstrap() {
        val source = sourceAtRoot("MorimilAppContainerRuntimeGate.kt").readText()
        val canonicalRead = source.indexOf("readVerifiedPlanningInput")
        val cognitiveRecovery = source.indexOf("recoverAtStartup")
        val vaultRecovery = source.indexOf("recoverPendingOperations")
        val bootstrap = source.indexOf("bootstrap.bootstrap")

        assertTrue(canonicalRead >= 0)
        assertTrue(cognitiveRecovery > canonicalRead)
        assertTrue(vaultRecovery > cognitiveRecovery)
        assertTrue(bootstrap > vaultRecovery)
    }

    @Test
    fun operationRegistryIsClosedToCog001ThroughCog004() {
        val source = source("data/repository/CrossDatabaseOperationContracts.kt").readText()
        listOf(
            "cognitive_migration.propose",
            "cognitive_migration.approve",
            "cognitive_migration.execute",
            "cognitive_migration.rollback"
        ).forEach { operation -> assertTrue(source.contains(operation)) }
        assertTrue(source.contains("CLOSED_REGISTRY"))
        assertFalse(source.contains("ProjectVault"))
    }

    @Test
    fun cp5ActivationFailsClosedForPendingCog001V1Operations() {
        val runtimeContract =
            File(repositoryRoot(), "docs/CURRENT_RUNTIME_CONTRACT.md").readText()
        val blueprint =
            File(
                repositoryRoot(),
                "docs/F3_COGNITIVE_MIGRATION_IMPLEMENTATION_BLUEPRINT.md"
            ).readText()
        val coordinator =
            source("data/repository/CrossDatabaseOperationCoordinator.kt").readText()
        val dao =
            source("data/local/CrossDatabaseOperationDao.kt").readText()

        assertTrue(runtimeContract.contains("zero non-committed"))
        assertTrue(runtimeContract.contains("cog_001.payload.v1"))
        assertTrue(runtimeContract.contains("blocks"))
        assertTrue(blueprint.contains("must not be silently finalized under v2 rules"))
        assertTrue(dao.contains("countNonTerminalByInstanceOwnerAndPayloadSchema"))
        assertTrue(dao.contains("status != 'COMMITTED'"))
        val gate = coordinator.indexOf("requireNoPendingCog001V1(identity.instanceId)")
        val load = coordinator.indexOf("store.loadRecoverableForInstance")
        assertTrue(gate >= 0)
        assertTrue(load > gate)
    }

    private fun source(relative: String): File {
        return File(repositoryRoot(), "app/src/main/java/com/morimil/app/$relative")
    }

    private fun sourceAtRoot(filename: String): File {
        return File(repositoryRoot(), "app/src/main/java/com/morimil/app/$filename")
    }

    private fun repositoryRoot(): File {
        return sequenceOf(File("."), File(".."))
            .map(File::getCanonicalFile)
            .firstOrNull { candidate ->
                File(candidate, "README.md").isFile &&
                    File(candidate, "app/build.gradle.kts").isFile
            }
            ?: error("Repository root not found")
    }
}
