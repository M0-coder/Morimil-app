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
    fun specializedReadPortConsumesOnlyTheF1ACanonicalBoundary() {
        val readPort = source(
            "data/genesis/ultra/CanonicalCognitiveMigrationReadPort.kt"
        ).readText()
        val composition = sourceAtRoot(
            "MorimilAppContainerCognitiveMigrationProtocol.kt"
        ).readText()

        assertTrue(readPort.contains("private val consumerReadPort: CanonicalConsumerReadPort"))
        assertFalse(readPort.contains("GenesisUltraRuntimeIdentityRepository"))
        assertFalse(readPort.contains("readCommittedIdentity"))
        assertTrue(composition.contains("CanonicalCognitiveMigrationReadPort.production("))
        assertTrue(composition.contains("consumerReadPort = GenesisUltraCanonicalConsumerReadAdapter.production("))
        assertFalse(
            composition.contains(
                "CanonicalCognitiveMigrationReadPort.production(\n" +
                    "        identityRepository"
            )
        )
    }

    @Test
    fun cognitivePlanningHasAClosedMemorySourcePolicyAndFullDescriptors() {
        val source = source(
            "data/genesis/ultra/CanonicalCognitiveMigrationReadPort.kt"
        ).readText()

        assertTrue(source.contains("ALLOWED_PLANNING_NOTE_SCHEMAS"))
        assertTrue(source.contains("isAllowedPlanningSource"))
        assertTrue(source.contains("cognitive_migration."))
        assertTrue(source.contains("cognitive_migration_protocol"))
        assertTrue(source.contains("cross_database_operations"))
        assertTrue(source.contains("canonicalRecordSetDigest"))
        assertTrue(source.contains("canonicalPreSnapshotHash"))
        assertTrue(source.contains("content_digest"))
        assertTrue(source.contains("provenance_digest"))
        assertTrue(source.contains("signer_epoch_id"))
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
    fun canonicalAuditPreparationRunsBeforeRoomFinalizationTransaction() {
        val coordinator = source(
            "data/repository/CrossDatabaseOperationCoordinator.kt"
        ).readText()
        val finalizer = source(
            "data/repository/CognitiveMigrationProtocolFinalizer.kt"
        ).readText()

        val preparation = coordinator.indexOf("finalizer.prepareOutsideTransaction")
        val dispatch = coordinator.indexOf("store.finalizeCommitted", preparation)
        val transaction = coordinator.indexOf("return database.withTransaction", dispatch)

        assertTrue(preparation >= 0)
        assertTrue(dispatch > preparation)
        assertTrue(transaction > dispatch)
        assertTrue(finalizer.contains("override suspend fun prepareOutsideTransaction"))
        assertTrue(finalizer.contains("finalizePreparedInsideTransaction"))
        assertTrue(finalizer.contains("audit_preparation.v1"))
    }

    @Test
    fun coordinatorUsesTypedErrorsAndDurableRemainderCounts() {
        val coordinator = source(
            "data/repository/CrossDatabaseOperationCoordinator.kt"
        ).readText()
        val dao = source("data/local/CrossDatabaseOperationDao.kt").readText()

        assertFalse(coordinator.contains("failure.message"))
        assertFalse(coordinator.contains("Regex(\"mismatch|invalid|conflict"))
        assertTrue(coordinator.contains("countRecoverableForInstance"))
        assertTrue(coordinator.contains("countRecoverableForOwner"))
        assertTrue(dao.contains("status NOT IN ('COMMITTED', 'BLOCKED')"))
    }

    @Test
    fun localResultsRemainDeterministicAcrossReceiptRecovery() {
        val coordinator =
            source("data/repository/CrossDatabaseOperationCoordinator.kt").readText()
        val finalizer =
            source("data/repository/CognitiveMigrationProtocolFinalizer.kt").readText()

        assertTrue(coordinator.contains("resolveReceipt"))
        assertTrue(coordinator.contains("reusedExistingEvent = true"))
        assertFalse(finalizer.contains("\"reused_existing_event\""))
        assertFalse(finalizer.contains("receipt.reusedExistingEvent"))
        assertTrue(finalizer.contains("cog_001.local_result.v2"))
        assertTrue(finalizer.contains("cog_004.local_result.v2"))
    }

    @Test
    fun predecessorReceiptsAreClosedByOwnerTypeVersionAndOperationType() {
        val finalizer =
            source("data/repository/CognitiveMigrationProtocolFinalizer.kt").readText()

        assertTrue(finalizer.contains("expectedOperationType"))
        assertTrue(finalizer.contains("predecessor.ownerType == CognitiveMigrationProtocolTypes.OWNER_TYPE"))
        assertTrue(finalizer.contains("predecessor.operationType == expectedOperationType"))
        assertTrue(finalizer.contains("predecessor.operationVersion == CognitiveMigrationProtocolTypes.VERSION"))
        assertTrue(finalizer.contains("CognitiveMigrationProtocolTypes.APPROVE"))
        assertTrue(finalizer.contains("CognitiveMigrationProtocolTypes.EXECUTE"))
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
    fun freshAndMigratedV9StoresInstallTheSameJournalGuards() {
        val migration = source("data/local/MemoryOrganDatabaseMigrationV9.kt").readText()
        val encryption = source("data/local/MemoryOrganDatabaseEncryption.kt").readText()

        assertTrue(migration.contains("override fun onCreate"))
        assertTrue(migration.contains("override fun onOpen"))
        assertTrue(migration.contains("installGuards(db)"))
        assertTrue(migration.contains("cross_database_operations_validate_insert"))
        assertTrue(migration.contains("cross_database_operations_validate_update"))
        assertTrue(encryption.contains(".addCallback(MemoryOrganDatabaseMigrationV9.CALLBACK)"))
    }

    @Test
    fun cp5ActivationFailsClosedForPendingCog001V1Operations() {
        val runtimeContract =
            File(repositoryRoot(), "docs/CURRENT_RUNTIME_CONTRACT.md").readText()
        val coordinator =
            source("data/repository/CrossDatabaseOperationCoordinator.kt").readText()
        val dao = source("data/local/CrossDatabaseOperationDao.kt").readText()

        assertTrue(runtimeContract.contains("zero non-committed"))
        assertTrue(runtimeContract.contains("cog_001.payload.v1"))
        assertTrue(runtimeContract.contains("blocks"))
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
