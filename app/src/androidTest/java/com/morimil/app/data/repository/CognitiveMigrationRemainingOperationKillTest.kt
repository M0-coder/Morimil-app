package com.morimil.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.morimil.app.core.memory.CognitiveMigrationPlanner
import com.morimil.app.data.genesis.ultra.CanonicalCognitiveMigrationAudit
import com.morimil.app.data.genesis.ultra.CognitiveMigrationCanonicalAuditPort
import com.morimil.app.data.genesis.ultra.GenesisUltraHashProfile
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeActiveBody
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeAuthorization
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeAuthorizationState
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeDocument
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeGuardian
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimePolicy
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeVerifiedSeed
import com.morimil.app.data.local.CrossDatabaseOperationEntity
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MigrationRecordEntity
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CognitiveMigrationRemainingOperationKillTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun approvalReceiptRecoversApprovedRecordAfterDatabaseReopen() = runBlocking {
        val name = testDatabaseName("approve")
        context.deleteDatabase(name)
        var db = openDatabase(name)
        try {
            val identity = identity()
            val record = plannedRecord()
            db.memoryOrganDao().insertMigrationRecord(record)
            val command = CognitiveMigrationOperationFactory.approve(
                identity,
                record,
                MigrationRecordRepository.plannedRecordDigestOf(record)
            )
            stagePendingLocalCommit(db, command, 701L)
            db.close()
            db = openDatabase(name)

            val report = coordinator(db).recoverAtStartup(identity, 20)
            val recovered = requireNotNull(db.memoryOrganDao().loadMigrationRecord(record.migrationId))

            assertEquals(1, report.recoveredCount)
            assertEquals("approved", recovered.status)
            assertEquals(command.operationId, recovered.approvalId)
            assertEquals(true, recovered.approvedByUser)
            assertCommitted(db, command)
        } finally {
            if (db.isOpen) db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun executionReceiptRecoversCompletedRecordAfterDatabaseReopen() = runBlocking {
        val name = testDatabaseName("execute")
        context.deleteDatabase(name)
        var db = openDatabase(name)
        try {
            val identity = identity()
            val planned = plannedRecord()
            db.memoryOrganDao().insertMigrationRecord(planned)
            val approvalCommand = CognitiveMigrationOperationFactory.approve(
                identity,
                planned,
                MigrationRecordRepository.plannedRecordDigestOf(planned)
            )
            stagePendingLocalCommit(db, approvalCommand, 711L)
            assertEquals(1, coordinator(db).recoverAtStartup(identity, 20).recoveredCount)
            val approved = requireNotNull(db.memoryOrganDao().loadMigrationRecord(planned.migrationId))
            val approval = requireNotNull(
                db.crossDatabaseOperationDao().loadOperation(approvalCommand.operationId)
            )
            val execute = CognitiveMigrationOperationFactory.execute(
                identity,
                approved,
                MigrationRecordRepository.plannedRecordDigestOf(approved),
                approval
            )
            stagePendingLocalCommit(db, execute, 712L)
            db.close()
            db = openDatabase(name)

            val report = coordinator(db).recoverAtStartup(identity, 20)
            val recovered = requireNotNull(db.memoryOrganDao().loadMigrationRecord(planned.migrationId))

            assertEquals(1, report.recoveredCount)
            assertEquals("completed", recovered.status)
            assertNotNull(recovered.postSnapshotId)
            assertCommitted(db, execute)
        } finally {
            if (db.isOpen) db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun rollbackReceiptRecoversRolledBackRecordAfterDatabaseReopen() = runBlocking {
        val name = testDatabaseName("rollback")
        context.deleteDatabase(name)
        var db = openDatabase(name)
        try {
            val identity = identity()
            val planned = plannedRecord()
            db.memoryOrganDao().insertMigrationRecord(planned)
            val approvalCommand = CognitiveMigrationOperationFactory.approve(
                identity,
                planned,
                MigrationRecordRepository.plannedRecordDigestOf(planned)
            )
            stagePendingLocalCommit(db, approvalCommand, 721L)
            assertEquals(1, coordinator(db).recoverAtStartup(identity, 20).recoveredCount)
            val approved = requireNotNull(db.memoryOrganDao().loadMigrationRecord(planned.migrationId))
            val approval = requireNotNull(
                db.crossDatabaseOperationDao().loadOperation(approvalCommand.operationId)
            )
            val rollback = CognitiveMigrationOperationFactory.rollback(
                identity,
                approved,
                MigrationRecordRepository.plannedRecordDigestOf(approved),
                approval
            )
            stagePendingLocalCommit(db, rollback, 722L)
            db.close()
            db = openDatabase(name)

            val report = coordinator(db).recoverAtStartup(identity, 20)
            val recovered = requireNotNull(db.memoryOrganDao().loadMigrationRecord(planned.migrationId))

            assertEquals(1, report.recoveredCount)
            assertEquals("rolled_back", recovered.status)
            assertCommitted(db, rollback)
        } finally {
            if (db.isOpen) db.close()
            context.deleteDatabase(name)
        }
    }

    private fun openDatabase(name: String): MemoryOrganDatabase =
        Room.databaseBuilder(context, MemoryOrganDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()

    private suspend fun stagePendingLocalCommit(
        db: MemoryOrganDatabase,
        command: CrossDatabaseStageCommand,
        sequence: Long
    ) {
        coordinator(db).stageExact(command)
        val dao = db.crossDatabaseOperationDao()
        assertEquals(1, dao.transitionStagedToPendingCanonical(command.operationId, 6_001L))
        val receipt = receipt(command, sequence)
        assertEquals(
            1,
            dao.persistCanonicalReceipt(
                operationId = command.operationId,
                canonicalEventHash = receipt.eventHash,
                canonicalSequence = receipt.sequence,
                canonicalProvenanceDigest = receipt.provenanceDigest,
                updatedAtMillis = 6_002L
            )
        )
        assertEquals(
            1,
            dao.transitionCanonicalCommittedToPendingLocalCommit(command.operationId, 6_003L)
        )
    }

    private fun coordinator(db: MemoryOrganDatabase): CrossDatabaseOperationCoordinator =
        CrossDatabaseOperationCoordinator.production(
            database = db,
            canonicalEnsurePort = object : CrossDatabaseCanonicalEnsurePort {
                override suspend fun ensureCommitted(
                    command: CrossDatabaseCanonicalCommand
                ): CrossDatabaseCanonicalReceipt = error("canonical_must_not_replay")
            },
            finalizers = listOf(
                CognitiveMigrationProtocolFinalizer(
                    database = db,
                    canonicalAuditPort = object : CognitiveMigrationCanonicalAuditPort {
                        override suspend fun auditVerifiedCanonicalChain(): CanonicalCognitiveMigrationAudit {
                            return CanonicalCognitiveMigrationAudit(
                                verified = true,
                                snapshotDigest = digest("verified-post-append-snapshot"),
                                notes = listOf("canonical_chain_verified")
                            )
                        }
                    }
                )
            ),
            protocolRegistry = COGNITIVE_MIGRATION_PROTOCOL_REGISTRY,
            clockMillis = IncrementingClock()
        )

    private suspend fun assertCommitted(
        db: MemoryOrganDatabase,
        command: CrossDatabaseStageCommand
    ) {
        assertEquals(
            CrossDatabaseOperationStatus.COMMITTED,
            db.crossDatabaseOperationDao().loadOperation(command.operationId)?.status
        )
    }

    private fun receipt(command: CrossDatabaseStageCommand, sequence: Long) =
        CrossDatabaseCanonicalReceipt(
            eventId = command.eventId,
            eventHash = "evsha256:" + digest("event-${command.eventId}").removePrefix("sha256:"),
            sequence = sequence,
            provenanceDigest = digest("provenance-${command.eventId}"),
            reusedExistingEvent = true
        )

    private fun plannedRecord(): MigrationRecordEntity = MigrationRecordEntity(
        migrationId = "migration_test",
        instanceId = "instance_test",
        genesisCoreHash = "evsha256:" + "3".repeat(64),
        proposalId = "proposal_test",
        migrationType = CognitiveMigrationPlanner.MIGRATION_TYPE,
        fromVersion = CognitiveMigrationPlanner.FROM_VERSION,
        toVersion = CognitiveMigrationPlanner.TO_VERSION,
        affectedArtifactsJson = "[\"reasoning_policy\"]",
        preSnapshotId = "evsha256:" + "4".repeat(64),
        chainVerified = true,
        backupRequired = true,
        stepsJson = "[\"refine\"]",
        expectedEffect = "refinement",
        riskLevel = "medium",
        approvalRequired = true,
        approvedByUser = false,
        approvalId = null,
        status = "planned",
        postSnapshotId = null,
        errorsJson = "[]",
        rollbackAvailable = true,
        rollbackStrategy = "append_only_compensation",
        createdBy = "canonical_migration_protocol",
        createdAtMillis = 1_000L,
        updatedAtMillis = 1_000L
    )

    private fun identity(): GenesisUltraRuntimeIdentity {
        val doctrine = document("doctrine/test.md", "doctrine", "doctrine")
        val charter = document("policy/charter.json", "freedom_charter", "{}")
        val recovery = document("policy/recovery.json", "recovery_policy", "{}")
        return GenesisUltraRuntimeIdentity(
            instanceId = "instance_test",
            companionName = "Morimil",
            bornAt = "2026-08-09T00:00:00Z",
            identityDigest = digest("identity"),
            activeBody = GenesisUltraRuntimeActiveBody(
                bodyId = "body_test",
                status = "active_writer",
                platformProfile = "android",
                publicKeyFingerprint = digest("body_key"),
                keyEpochId = "epoch_test",
                keyEpochDigest = digest("epoch"),
                registryEpoch = 1,
                registryDigest = digest("registry")
            ),
            guardian = GenesisUltraRuntimeGuardian(
                guardianId = "guardian_test",
                keyEpochId = "guardian_epoch",
                publicKeyRef = digest("guardian_key"),
                status = "active",
                role = "custodian_without_ownership",
                anchorDigest = digest("guardian_anchor")
            ),
            seed = GenesisUltraRuntimeVerifiedSeed(
                seedId = "seed_test",
                rootHash = digest("seed"),
                protocolVersion = "genesis-ultra-v1",
                hashProfile = GenesisUltraHashProfile.FIELD_PROFILE,
                identityDigest = digest("identity"),
                doctrineDigest = doctrine.digest
            ),
            doctrine = doctrine,
            policy = GenesisUltraRuntimePolicy(
                freedomCharter = charter,
                recoveryPolicy = recovery,
                freedomCharterDigest = charter.digest,
                recoveryPolicyDigest = recovery.digest
            ),
            authorization = GenesisUltraRuntimeAuthorization(
                state = GenesisUltraRuntimeAuthorizationState.COMMITTED,
                authorizationDigest = digest("authorization"),
                candidateDigest = digest("candidate"),
                consentDigest = digest("consent"),
                authorizedAt = "2026-08-09T00:00:00Z",
                expiresAt = "2026-08-09T01:00:00Z",
                receiptDigest = digest("receipt"),
                birthStatus = "born",
                ownershipConferred = false
            )
        )
    }

    private fun document(path: String, kind: String, text: String): GenesisUltraRuntimeDocument {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        return GenesisUltraRuntimeDocument(
            relativePath = path,
            documentKind = kind,
            digest = GenesisUltraHashProfile.sha256(bytes),
            sourceBytes = bytes
        )
    }

    private fun digest(value: String): String =
        GenesisUltraHashProfile.sha256(value.toByteArray(StandardCharsets.UTF_8))

    private class IncrementingClock : () -> Long {
        private var value = 6_000L
        override fun invoke(): Long = value++
    }

    private fun testDatabaseName(suffix: String): String =
        "cognitive-migration-remaining-kill-$suffix.db"
}
