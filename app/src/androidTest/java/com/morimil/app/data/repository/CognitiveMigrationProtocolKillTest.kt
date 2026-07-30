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
import com.morimil.app.data.genesis.ultra.VerifiedCognitiveMigrationPlanningInput
import com.morimil.app.data.genesis.ultra.VerifiedCognitiveMigrationSource
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MigrationRecordEntity
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CognitiveMigrationProtocolKillTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun stagedBeforeDispatchRecoversAcrossDatabaseReopen() = runBlocking {
        val databaseName = testDatabaseName("staged")
        context.deleteDatabase(databaseName)
        var database = openDatabase(databaseName)
        try {
            val identity = identity()
            val command = command(identity)
            coordinator(
                database = database,
                canonical = object : CrossDatabaseCanonicalEnsurePort {
                    override suspend fun ensureCommitted(
                        command: CrossDatabaseCanonicalCommand
                    ): CrossDatabaseCanonicalReceipt = error("canonical_must_not_run_before_reopen")
                }
            ).stageExact(command)
            assertEquals(
                CrossDatabaseOperationStatus.STAGED,
                database.crossDatabaseOperationDao().loadOperation(command.operationId)?.status
            )

            database.close()
            database = openDatabase(databaseName)
            var canonicalCalls = 0
            var finalizerCalls = 0
            var reused = true
            val recovered = coordinator(
                database = database,
                canonical = object : CrossDatabaseCanonicalEnsurePort {
                    override suspend fun ensureCommitted(
                        command: CrossDatabaseCanonicalCommand
                    ): CrossDatabaseCanonicalReceipt {
                        canonicalCalls += 1
                        return receipt(command.eventId, reusedExistingEvent = false)
                    }
                },
                onFinalize = { operation, observedReceipt ->
                    finalizerCalls += 1
                    reused = observedReceipt.reusedExistingEvent
                    database.memoryOrganDao().insertMigrationRecord(
                        migrationRecord(operation.subjectId)
                    )
                }
            )
            val report = recovered.recoverAtStartup(identity, 20)
            val committed = database.crossDatabaseOperationDao()
                .loadOperation(command.operationId)

            assertEquals(1, report.recoveredCount)
            assertEquals(1, canonicalCalls)
            assertEquals(1, finalizerCalls)
            assertFalse(reused)
            assertEquals(CrossDatabaseOperationStatus.COMMITTED, committed?.status)
            assertEquals(expectedLocalResultJson(), committed?.localResultJson)
            assertNotNull(database.memoryOrganDao().loadMigrationRecord(command.subjectId))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun appendCommittedBeforeReceiptRecoversOnceAcrossDatabaseReopen() = runBlocking {
        val databaseName = testDatabaseName("append-before-receipt")
        context.deleteDatabase(databaseName)
        var database = openDatabase(databaseName)
        try {
            val identity = identity()
            val command = command(identity)
            var interruptedCalls = 0
            var canonicalEventExists = false
            var canonicalEventCount = 0
            val interruptedCoordinator = coordinator(
                database = database,
                canonical = object : CrossDatabaseCanonicalEnsurePort {
                    override suspend fun ensureCommitted(
                        command: CrossDatabaseCanonicalCommand
                    ): CrossDatabaseCanonicalReceipt {
                        interruptedCalls += 1
                        canonicalEventExists = true
                        canonicalEventCount += 1
                        throw IOException("simulated_death_after_append_before_receipt")
                    }
                }
            )
            interruptedCoordinator.stageExact(command)

            val interruptedReport = interruptedCoordinator.recoverAtStartup(identity, 20)
            val pending = database.crossDatabaseOperationDao()
                .loadOperation(command.operationId)
            assertEquals(1, interruptedCalls)
            assertEquals(1, interruptedReport.retryableFailureCount)
            assertEquals(CrossDatabaseOperationStatus.PENDING_CANONICAL, pending?.status)
            assertEquals(1, pending?.attemptCount)

            database.close()
            database = openDatabase(databaseName)
            var recoveredCanonicalCalls = 0
            var finalizerCalls = 0
            var replayReceiptReused = false
            val recoveredCoordinator = coordinator(
                database = database,
                canonical = object : CrossDatabaseCanonicalEnsurePort {
                    override suspend fun ensureCommitted(
                        command: CrossDatabaseCanonicalCommand
                    ): CrossDatabaseCanonicalReceipt {
                        recoveredCanonicalCalls += 1
                        check(canonicalEventExists)
                        return receipt(command.eventId)
                    }
                },
                onFinalize = { _, receipt ->
                    finalizerCalls += 1
                    replayReceiptReused = receipt.reusedExistingEvent
                    database.memoryOrganDao().insertMigrationRecord(
                        migrationRecord(command.subjectId)
                    )
                }
            )
            val recoveredReport = recoveredCoordinator.recoverAtStartup(identity, 20)
            val committed = database.crossDatabaseOperationDao()
                .loadOperation(command.operationId)

            assertEquals(1, recoveredReport.recoveredCount)
            assertEquals(1, recoveredCanonicalCalls)
            assertEquals(1, finalizerCalls)
            assertEquals(1, canonicalEventCount)
            assertTrue(replayReceiptReused)
            assertEquals(CrossDatabaseOperationStatus.COMMITTED, committed?.status)
            assertNotNull(committed?.canonicalEventHash)
            assertNotNull(committed?.localResultDigest)
            assertEquals(expectedLocalResultJson(), committed?.localResultJson)
            assertNotNull(database.memoryOrganDao().loadMigrationRecord(command.subjectId))

            val replayReport = recoveredCoordinator.recoverAtStartup(identity, 20)
            assertEquals(0, replayReport.recoveredCount)
            assertEquals(1, recoveredCanonicalCalls)
            assertEquals(1, finalizerCalls)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun pendingCanonicalBeforeAppendRecoversAcrossDatabaseReopen() = runBlocking {
        val databaseName = testDatabaseName("pending-before-append")
        context.deleteDatabase(databaseName)
        var database = openDatabase(databaseName)
        try {
            val identity = identity()
            val command = command(identity)
            coordinator(
                database = database,
                canonical = object : CrossDatabaseCanonicalEnsurePort {
                    override suspend fun ensureCommitted(
                        command: CrossDatabaseCanonicalCommand
                    ): CrossDatabaseCanonicalReceipt = error("canonical_must_not_run")
                }
            ).stageExact(command)
            assertEquals(
                1,
                database.crossDatabaseOperationDao()
                    .transitionStagedToPendingCanonical(command.operationId, 1001)
            )

            database.close()
            database = openDatabase(databaseName)
            var canonicalCalls = 0
            var finalizerCalls = 0
            val recovered = coordinator(
                database = database,
                canonical = object : CrossDatabaseCanonicalEnsurePort {
                    override suspend fun ensureCommitted(
                        command: CrossDatabaseCanonicalCommand
                    ): CrossDatabaseCanonicalReceipt {
                        canonicalCalls += 1
                        return receipt(command.eventId, reusedExistingEvent = false)
                    }
                },
                onFinalize = { operation, observedReceipt ->
                    finalizerCalls += 1
                    assertFalse(observedReceipt.reusedExistingEvent)
                    database.memoryOrganDao().insertMigrationRecord(
                        migrationRecord(operation.subjectId)
                    )
                }
            )
            val report = recovered.recoverAtStartup(identity, 20)
            val committed = database.crossDatabaseOperationDao()
                .loadOperation(command.operationId)

            assertEquals(1, report.recoveredCount)
            assertEquals(1, canonicalCalls)
            assertEquals(1, finalizerCalls)
            assertEquals(CrossDatabaseOperationStatus.COMMITTED, committed?.status)
            assertEquals(expectedLocalResultJson(), committed?.localResultJson)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun sameOperationPayloadAndEvidenceConflictsFailClosedBeforeAppend() = runBlocking {
        val databaseName = testDatabaseName("same-id-conflicts")
        context.deleteDatabase(databaseName)
        val database = openDatabase(databaseName)
        try {
            val identity = identity()
            val command = command(identity)
            var canonicalCalls = 0
            val coordinator = coordinator(
                database = database,
                canonical = object : CrossDatabaseCanonicalEnsurePort {
                    override suspend fun ensureCommitted(
                        command: CrossDatabaseCanonicalCommand
                    ): CrossDatabaseCanonicalReceipt {
                        canonicalCalls += 1
                        return receipt(command.eventId)
                    }
                }
            )
            coordinator.stageExact(command)

            val payloadFailure = runCatching {
                coordinator.stageExact(withConflictingPayload(command))
            }.exceptionOrNull()
            val conflictingEvidence = CrossDatabaseOperationIdentity.canonicalJson(
                mapOf(
                    "event_id" to command.eventId,
                    "operation_id" to command.operationId,
                    "schema" to "test.kill.evidence.conflict.v1"
                )
            )
            val evidenceFailure = runCatching {
                coordinator.stageExact(
                    command.copy(
                        evidenceJson = conflictingEvidence,
                        evidenceDigest =
                            CrossDatabaseOperationIdentity.digestCanonicalJson(
                                conflictingEvidence
                            )
                    )
                )
            }.exceptionOrNull()

            assertEquals(
                CrossDatabaseProtocolErrors.OPERATION_ID_PAYLOAD_CONFLICT,
                (payloadFailure as CrossDatabaseProtocolFailure).stableCode
            )
            assertEquals(
                CrossDatabaseProtocolErrors.OPERATION_ID_EVIDENCE_CONFLICT,
                (evidenceFailure as CrossDatabaseProtocolFailure).stableCode
            )
            assertEquals(0, canonicalCalls)
            assertEquals(
                CrossDatabaseOperationStatus.STAGED,
                database.crossDatabaseOperationDao()
                    .loadOperation(command.operationId)?.status
            )
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun writerEpochSuccessionBlocksStaleOperationBeforeAppend() = runBlocking {
        val databaseName = testDatabaseName("writer-succession")
        context.deleteDatabase(databaseName)
        var database = openDatabase(databaseName)
        try {
            val originalIdentity = identity()
            val command = command(originalIdentity)
            coordinator(
                database = database,
                canonical = object : CrossDatabaseCanonicalEnsurePort {
                    override suspend fun ensureCommitted(
                        command: CrossDatabaseCanonicalCommand
                    ): CrossDatabaseCanonicalReceipt = error("canonical_must_not_run")
                }
            ).stageExact(command)

            database.close()
            database = openDatabase(databaseName)
            var canonicalCalls = 0
            val recovered = coordinator(
                database = database,
                canonical = object : CrossDatabaseCanonicalEnsurePort {
                    override suspend fun ensureCommitted(
                        command: CrossDatabaseCanonicalCommand
                    ): CrossDatabaseCanonicalReceipt {
                        canonicalCalls += 1
                        return receipt(command.eventId)
                    }
                }
            )
            val successorIdentity = originalIdentity.copy(
                activeBody = originalIdentity.activeBody.copy(
                    keyEpochId = "epoch_successor"
                )
            )
            val report = recovered.recoverAtStartup(successorIdentity, 20)
            val blocked = database.crossDatabaseOperationDao()
                .loadOperation(command.operationId)

            assertEquals(1, report.blockedCount)
            assertEquals(0, canonicalCalls)
            assertEquals(CrossDatabaseOperationStatus.BLOCKED, blocked?.status)
            assertEquals(CrossDatabaseProtocolErrors.STALE_WRITER_EPOCH, blocked?.lastErrorCode)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun persistedReceiptBeforeFinalizationRecoversWithoutCanonicalReplay() = runBlocking {
        val databaseName = testDatabaseName("receipt-before-finalization")
        context.deleteDatabase(databaseName)
        var database = openDatabase(databaseName)
        try {
            val identity = identity()
            val command = command(identity)
            val staging = coordinator(
                database = database,
                canonical = object : CrossDatabaseCanonicalEnsurePort {
                    override suspend fun ensureCommitted(
                        command: CrossDatabaseCanonicalCommand
                    ): CrossDatabaseCanonicalReceipt = error("canonical_must_not_run")
                }
            )
            staging.stageExact(command)
            val dao = database.crossDatabaseOperationDao()
            assertEquals(1, dao.transitionStagedToPendingCanonical(command.operationId, 1001))
            assertEquals(
                1,
                dao.persistCanonicalReceipt(
                    operationId = command.operationId,
                    canonicalEventHash = receipt(command.eventId).eventHash,
                    canonicalSequence = receipt(command.eventId).sequence,
                    canonicalProvenanceDigest = receipt(command.eventId).provenanceDigest,
                    updatedAtMillis = 1002
                )
            )

            database.close()
            database = openDatabase(databaseName)
            var canonicalCalls = 0
            var finalizerCalls = 0
            var reused = false
            val recovered = coordinator(
                database = database,
                canonical = object : CrossDatabaseCanonicalEnsurePort {
                    override suspend fun ensureCommitted(
                        command: CrossDatabaseCanonicalCommand
                    ): CrossDatabaseCanonicalReceipt {
                        canonicalCalls += 1
                        error("canonical_must_not_replay")
                    }
                },
                onFinalize = { operation, observedReceipt ->
                    finalizerCalls += 1
                    reused = observedReceipt.reusedExistingEvent
                    database.memoryOrganDao().insertMigrationRecord(
                        migrationRecord(operation.subjectId)
                    )
                }
            )
            val report = recovered.recoverAtStartup(identity, 20)
            val committed = database.crossDatabaseOperationDao()
                .loadOperation(command.operationId)
            assertEquals(1, report.recoveredCount)
            assertEquals(0, canonicalCalls)
            assertEquals(1, finalizerCalls)
            assertTrue(reused)
            assertEquals(CrossDatabaseOperationStatus.COMMITTED, committed?.status)
            assertEquals(expectedLocalResultJson(), committed?.localResultJson)
            assertNotNull(database.memoryOrganDao().loadMigrationRecord(command.subjectId))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun interruptedLocalTransactionRollsBackAndRecoversOnce() = runBlocking {
        val databaseName = testDatabaseName("local-transaction")
        context.deleteDatabase(databaseName)
        var database = openDatabase(databaseName)
        try {
            val identity = identity()
            val command = command(identity)
            stagePendingLocalCommit(database, command)
            database.close()
            database = openDatabase(databaseName)
            var canonicalCalls = 0
            val interruptedFinalization = coordinator(
                database = database,
                canonical = object : CrossDatabaseCanonicalEnsurePort {
                    override suspend fun ensureCommitted(
                        command: CrossDatabaseCanonicalCommand
                    ): CrossDatabaseCanonicalReceipt {
                        canonicalCalls += 1
                        return receipt(command.eventId)
                    }
                },
                onFinalize = { operation, persistedReceipt ->
                    assertTrue(persistedReceipt.reusedExistingEvent)
                    database.memoryOrganDao().insertMigrationRecord(
                        migrationRecord(operation.subjectId)
                    )
                    throw IOException("simulated_death_during_local_finalization")
                }
            )
            val interrupted = interruptedFinalization.recoverAtStartup(identity, 20)
            assertEquals(1, interrupted.retryableFailureCount)
            assertEquals(0, canonicalCalls)
            assertEquals(
                CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT,
                database.crossDatabaseOperationDao()
                    .loadOperation(command.operationId)?.status
            )
            assertEquals(null, database.memoryOrganDao().loadMigrationRecord(command.subjectId))

            database.close()
            database = openDatabase(databaseName)
            var finalizerCalls = 0
            val recovered = coordinator(
                database = database,
                canonical = object : CrossDatabaseCanonicalEnsurePort {
                    override suspend fun ensureCommitted(
                        command: CrossDatabaseCanonicalCommand
                    ): CrossDatabaseCanonicalReceipt = error("canonical_must_not_replay")
                },
                onFinalize = { operation, persistedReceipt ->
                    finalizerCalls += 1
                    assertTrue(persistedReceipt.reusedExistingEvent)
                    database.memoryOrganDao().insertMigrationRecord(
                        migrationRecord(operation.subjectId)
                    )
                }
            )
            val report = recovered.recoverAtStartup(identity, 20)
            assertEquals(1, report.recoveredCount)
            assertEquals(1, finalizerCalls)
            assertEquals(
                CrossDatabaseOperationStatus.COMMITTED,
                database.crossDatabaseOperationDao()
                    .loadOperation(command.operationId)?.status
            )
            assertNotNull(database.memoryOrganDao().loadMigrationRecord(command.subjectId))

            val replay = recovered.recoverAtStartup(identity, 20)
            assertEquals(0, replay.recoveredCount)
            assertEquals(1, finalizerCalls)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun committedReplayPreservesOriginalResultWithoutRefinalizing() = runBlocking {
        val databaseName = testDatabaseName("committed-replay")
        context.deleteDatabase(databaseName)
        var database = openDatabase(databaseName)
        try {
            val identity = identity()
            val command = command(identity)
            var initialFinalizerCalls = 0
            val initial = coordinator(
                database = database,
                canonical = object : CrossDatabaseCanonicalEnsurePort {
                    override suspend fun ensureCommitted(
                        command: CrossDatabaseCanonicalCommand
                    ): CrossDatabaseCanonicalReceipt {
                        return receipt(command.eventId, reusedExistingEvent = false)
                    }
                },
                onFinalize = { operation, observedReceipt ->
                    initialFinalizerCalls += 1
                    assertFalse(observedReceipt.reusedExistingEvent)
                    database.memoryOrganDao().insertMigrationRecord(
                        migrationRecord(operation.subjectId)
                    )
                }
            )
            val committed = initial.execute(identity, command)
            val originalResult = requireNotNull(committed.localResultJson)
            val originalDigest = requireNotNull(committed.localResultDigest)
            assertEquals(1, initialFinalizerCalls)
            assertEquals(expectedLocalResultJson(), originalResult)

            database.close()
            database = openDatabase(databaseName)
            val replay = coordinator(
                database = database,
                canonical = object : CrossDatabaseCanonicalEnsurePort {
                    override suspend fun ensureCommitted(
                        command: CrossDatabaseCanonicalCommand
                    ): CrossDatabaseCanonicalReceipt = error("canonical_must_not_replay")
                },
                onFinalize = { _, _ -> error("finalizer_must_not_replay") }
            )
            val report = replay.recoverAtStartup(identity, 20)
            val preserved = database.crossDatabaseOperationDao()
                .loadOperation(command.operationId)
            assertEquals(0, report.recoveredCount)
            assertEquals(originalResult, preserved?.localResultJson)
            assertEquals(originalDigest, preserved?.localResultDigest)
            assertNotNull(database.memoryOrganDao().loadMigrationRecord(command.subjectId))

            val repeated = replay.execute(identity, command)
            assertEquals(preserved, repeated)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun emptyRecoveryDoesNotStageOrAppend() = runBlocking {
        val databaseName = testDatabaseName("empty")
        context.deleteDatabase(databaseName)
        val database = openDatabase(databaseName)
        try {
            var canonicalCalls = 0
            var finalizerCalls = 0
            val coordinator = coordinator(
                database = database,
                canonical = object : CrossDatabaseCanonicalEnsurePort {
                    override suspend fun ensureCommitted(
                        command: CrossDatabaseCanonicalCommand
                    ): CrossDatabaseCanonicalReceipt {
                        canonicalCalls += 1
                        return receipt(command.eventId)
                    }
                },
                onFinalize = { _, _ -> finalizerCalls += 1 }
            )
            val report = coordinator.recoverAtStartup(identity(), 20)

            assertEquals(0, report.recoveredCount)
            assertEquals(0, canonicalCalls)
            assertEquals(0, finalizerCalls)
            assertEquals(
                0,
                database.crossDatabaseOperationDao()
                    .countByInstanceAndStatus("instance_test", CrossDatabaseOperationStatus.STAGED)
            )
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun realFinalizerRecoversCog001ThroughCog004AcrossDatabaseReopen() = runBlocking {
        val databaseName = testDatabaseName("real-finalizer-all-phases")
        context.deleteDatabase(databaseName)
        var database = openDatabase(databaseName)
        try {
            val identity = identity()
            val input = verifiedInput(identity)
            val plan = CognitiveMigrationPlanner.buildVerifiedPlan(input)
            val propose = CognitiveMigrationOperationFactory.propose(input, plan)

            stagePendingRealFinalization(
                database = database,
                command = propose,
                canonicalReceipt = protocolReceipt(propose, 101)
            )
            database.close()
            database = openDatabase(databaseName)
            assertEquals(
                1,
                realCoordinator(database).recoverAtStartup(identity, 20).recoveredCount
            )
            var record = requireNotNull(
                database.memoryOrganDao().loadMigrationRecord(plan.migrationId)
            )
            assertEquals("planned", record.status)
            assertEquals(
                "morimil.cognitive_migration.cog_001.local_result.v2",
                database.crossDatabaseOperationDao()
                    .loadOperation(propose.operationId)?.localResultSchema
            )

            val approve = CognitiveMigrationOperationFactory.approve(
                identity = identity,
                record = record,
                plannedRecordDigest = MigrationRecordRepository.plannedRecordDigestOf(record)
            )
            stagePendingRealFinalization(
                database = database,
                command = approve,
                canonicalReceipt = protocolReceipt(approve, 102)
            )
            database.close()
            database = openDatabase(databaseName)
            assertEquals(
                1,
                realCoordinator(database).recoverAtStartup(identity, 20).recoveredCount
            )
            record = requireNotNull(
                database.memoryOrganDao().loadMigrationRecord(plan.migrationId)
            )
            assertEquals("approved", record.status)
            assertTrue(record.approvedByUser)
            assertEquals(approve.operationId, record.approvalId)

            val approvalOperation = requireNotNull(
                database.crossDatabaseOperationDao().loadOperation(approve.operationId)
            )
            val execute = CognitiveMigrationOperationFactory.execute(
                identity = identity,
                record = record,
                plannedRecordDigest = MigrationRecordRepository.plannedRecordDigestOf(record),
                approval = approvalOperation
            )
            stagePendingRealFinalization(
                database = database,
                command = execute,
                canonicalReceipt = protocolReceipt(execute, 103)
            )
            database.close()
            database = openDatabase(databaseName)
            assertEquals(
                1,
                realCoordinator(database).recoverAtStartup(identity, 20).recoveredCount
            )
            record = requireNotNull(
                database.memoryOrganDao().loadMigrationRecord(plan.migrationId)
            )
            assertEquals("completed", record.status)
            assertNotNull(record.postSnapshotId)

            val executeOperation = requireNotNull(
                database.crossDatabaseOperationDao().loadOperation(execute.operationId)
            )
            val rollback = CognitiveMigrationOperationFactory.rollback(
                identity = identity,
                record = record,
                plannedRecordDigest = MigrationRecordRepository.plannedRecordDigestOf(record),
                predecessor = executeOperation
            )
            stagePendingRealFinalization(
                database = database,
                command = rollback,
                canonicalReceipt = protocolReceipt(rollback, 104)
            )
            database.close()
            database = openDatabase(databaseName)
            assertEquals(
                1,
                realCoordinator(database).recoverAtStartup(identity, 20).recoveredCount
            )
            record = requireNotNull(
                database.memoryOrganDao().loadMigrationRecord(plan.migrationId)
            )
            assertEquals("rolled_back", record.status)

            listOf(propose, approve, execute, rollback).forEach { command ->
                val committed = requireNotNull(
                    database.crossDatabaseOperationDao().loadOperation(command.operationId)
                )
                assertEquals(CrossDatabaseOperationStatus.COMMITTED, committed.status)
                assertNotNull(committed.localResultDigest)
                assertFalse(
                    requireNotNull(committed.localResultJson)
                        .contains("reused_existing_event")
                )
            }
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun pendingCog001V1BlocksBeforeCanonicalAppendInRoom() = runBlocking {
        val databaseName = testDatabaseName("pending-cog001-v1")
        context.deleteDatabase(databaseName)
        val database = openDatabase(databaseName)
        try {
            val identity = identity()
            val legacy = command(
                identity = identity,
                payloadSchema = LEGACY_COG_001_PAYLOAD_SCHEMA
            )
            var canonicalCalls = 0
            val coordinator = coordinator(
                database = database,
                canonical = object : CrossDatabaseCanonicalEnsurePort {
                    override suspend fun ensureCommitted(
                        command: CrossDatabaseCanonicalCommand
                    ): CrossDatabaseCanonicalReceipt {
                        canonicalCalls += 1
                        return receipt(command.eventId)
                    }
                }
            )
            coordinator.stageExact(legacy)

            val failure = runCatching {
                coordinator.recoverAtStartup(identity, 20)
            }.exceptionOrNull() as CrossDatabaseProtocolFailure

            assertEquals(
                CrossDatabaseProtocolErrors.UNSUPPORTED_PAYLOAD_SCHEMA,
                failure.stableCode
            )
            assertEquals(0, canonicalCalls)
            assertEquals(
                CrossDatabaseOperationStatus.STAGED,
                database.crossDatabaseOperationDao()
                    .loadOperation(legacy.operationId)?.status
            )
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun openDatabase(databaseName: String): MemoryOrganDatabase {
        return Room.databaseBuilder(
            context,
            MemoryOrganDatabase::class.java,
            databaseName
        ).allowMainThreadQueries().build()
    }

    private suspend fun stagePendingLocalCommit(
        database: MemoryOrganDatabase,
        command: CrossDatabaseStageCommand
    ) {
        coordinator(
            database = database,
            canonical = object : CrossDatabaseCanonicalEnsurePort {
                override suspend fun ensureCommitted(
                    command: CrossDatabaseCanonicalCommand
                ): CrossDatabaseCanonicalReceipt = error("canonical_must_not_run")
            }
        ).stageExact(command)
        val dao = database.crossDatabaseOperationDao()
        assertEquals(1, dao.transitionStagedToPendingCanonical(command.operationId, 1001))
        val canonicalReceipt = receipt(command.eventId)
        assertEquals(
            1,
            dao.persistCanonicalReceipt(
                operationId = command.operationId,
                canonicalEventHash = canonicalReceipt.eventHash,
                canonicalSequence = canonicalReceipt.sequence,
                canonicalProvenanceDigest = canonicalReceipt.provenanceDigest,
                updatedAtMillis = 1002
            )
        )
        assertEquals(
            1,
            dao.transitionCanonicalCommittedToPendingLocalCommit(command.operationId, 1003)
        )
    }

    private suspend fun stagePendingRealFinalization(
        database: MemoryOrganDatabase,
        command: CrossDatabaseStageCommand,
        canonicalReceipt: CrossDatabaseCanonicalReceipt
    ) {
        realCoordinator(database).stageExact(command)
        val dao = database.crossDatabaseOperationDao()
        assertEquals(
            1,
            dao.transitionStagedToPendingCanonical(command.operationId, 2001)
        )
        assertEquals(
            1,
            dao.persistCanonicalReceipt(
                operationId = command.operationId,
                canonicalEventHash = canonicalReceipt.eventHash,
                canonicalSequence = canonicalReceipt.sequence,
                canonicalProvenanceDigest = canonicalReceipt.provenanceDigest,
                updatedAtMillis = 2002
            )
        )
        assertEquals(
            1,
            dao.transitionCanonicalCommittedToPendingLocalCommit(
                command.operationId,
                2003
            )
        )
    }

    private fun realCoordinator(
        database: MemoryOrganDatabase
    ): CrossDatabaseOperationCoordinator {
        val canonicalMustNotReplay = object : CrossDatabaseCanonicalEnsurePort {
            override suspend fun ensureCommitted(
                command: CrossDatabaseCanonicalCommand
            ): CrossDatabaseCanonicalReceipt = error("canonical_must_not_replay")
        }
        val auditVerified = object : CognitiveMigrationCanonicalAuditPort {
            override suspend fun auditVerifiedCanonicalChain():
                CanonicalCognitiveMigrationAudit {
                return CanonicalCognitiveMigrationAudit(
                    verified = true,
                    snapshotDigest = digest("verified_snapshot"),
                    notes = listOf("canonical_chain_verified")
                )
            }
        }
        return CrossDatabaseOperationCoordinator.production(
            database = database,
            canonicalEnsurePort = canonicalMustNotReplay,
            finalizers = listOf(
                CognitiveMigrationProtocolFinalizer(database, auditVerified)
            ),
            clockMillis = IncrementingClock()
        )
    }

    private fun coordinator(
        database: MemoryOrganDatabase,
        canonical: CrossDatabaseCanonicalEnsurePort,
        onFinalize: suspend (
            CrossDatabaseOperationRecord,
            CrossDatabaseCanonicalReceipt
        ) -> Unit = { _, _ -> }
    ): CrossDatabaseOperationCoordinator {
        return CrossDatabaseOperationCoordinator.production(
            database = database,
            canonicalEnsurePort = canonical,
            finalizers = listOf(
                object : CrossDatabaseTypedFinalizer {
                    override val supportedOperationTypes =
                        setOf(CognitiveMigrationProtocolTypes.PROPOSE)

                    override suspend fun finalizeInsideTransaction(
                        operation: CrossDatabaseOperationRecord,
                        receipt: CrossDatabaseCanonicalReceipt
                    ): CrossDatabaseLocalResult {
                        onFinalize(operation, receipt)
                        val json = expectedLocalResultJson()
                        return CrossDatabaseLocalResult(
                            schema = TEST_LOCAL_RESULT_SCHEMA,
                            json = json,
                            digest =
                                CrossDatabaseOperationIdentity.digestCanonicalJson(json),
                            ownerStatus = "planned"
                        )
                    }
                }
            ),
            clockMillis = IncrementingClock()
        )
    }

    private fun command(
        identity: GenesisUltraRuntimeIdentity,
        payloadSchema: String = "test.kill.payload.v1"
    ): CrossDatabaseStageCommand {
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf("schema" to payloadSchema, "subject" to MIGRATION_ID)
        )
        val payloadDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(payload)
        val operationId = CrossDatabaseOperationIdentity.operationId(
            operationType = CognitiveMigrationProtocolTypes.PROPOSE,
            operationVersion = 1,
            instanceId = identity.instanceId,
            writerBodyId = identity.activeBody.bodyId,
            writerEpoch = identity.activeBody.keyEpochId,
            subjectId = MIGRATION_ID,
            parentOperationId = null,
            childPhase = null,
            payloadDigest = payloadDigest
        )
        val eventId = CrossDatabaseOperationIdentity.eventId(
            operationId,
            CognitiveMigrationProtocolTypes.PROPOSED_EVENT
        )
        val evidence = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "event_id" to eventId,
                "operation_id" to operationId,
                "schema" to "test.kill.evidence.v1"
            )
        )
        return CrossDatabaseStageCommand(
            operationId = operationId,
            ownerType = CognitiveMigrationProtocolTypes.OWNER_TYPE,
            operationType = CognitiveMigrationProtocolTypes.PROPOSE,
            operationVersion = 1,
            instanceId = identity.instanceId,
            writerBodyId = identity.activeBody.bodyId,
            writerEpoch = identity.activeBody.keyEpochId,
            subjectId = MIGRATION_ID,
            parentOperationId = null,
            childPhase = null,
            payloadSchema = payloadSchema,
            payloadJson = payload,
            payloadDigest = payloadDigest,
            eventId = eventId,
            eventType = CognitiveMigrationProtocolTypes.PROPOSED_EVENT,
            eventBody = "deterministic kill test body",
            evidenceSchema = "test.kill.evidence.v1",
            evidenceJson = evidence,
            evidenceDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(evidence)
        )
    }

    private fun withConflictingPayload(
        command: CrossDatabaseStageCommand
    ): CrossDatabaseStageCommand {
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "schema" to "test.kill.payload.v1",
                "subject" to command.subjectId,
                "variant" to "conflict"
            )
        )
        val payloadDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(payload)
        val operationId = CrossDatabaseOperationIdentity.operationId(
            operationType = command.operationType,
            operationVersion = command.operationVersion,
            instanceId = command.instanceId,
            writerBodyId = command.writerBodyId,
            writerEpoch = command.writerEpoch,
            subjectId = command.subjectId,
            parentOperationId = command.parentOperationId,
            childPhase = command.childPhase,
            payloadDigest = payloadDigest
        )
        val eventId = CrossDatabaseOperationIdentity.eventId(
            operationId,
            command.eventType
        )
        val evidence = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "event_id" to eventId,
                "operation_id" to operationId,
                "schema" to command.evidenceSchema
            )
        )
        return command.copy(
            operationId = operationId,
            payloadJson = payload,
            payloadDigest = payloadDigest,
            eventId = eventId,
            eventBody = "deterministic conflicting payload body",
            evidenceJson = evidence,
            evidenceDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(evidence)
        )
    }

    private fun receipt(
        eventId: String,
        reusedExistingEvent: Boolean = true
    ): CrossDatabaseCanonicalReceipt {
        return CrossDatabaseCanonicalReceipt(
            eventId = eventId,
            eventHash = "evsha256:" + "1".repeat(64),
            sequence = 9,
            provenanceDigest = "sha256:" + "2".repeat(64),
            reusedExistingEvent = reusedExistingEvent
        )
    }

    private fun protocolReceipt(
        command: CrossDatabaseStageCommand,
        sequence: Long
    ): CrossDatabaseCanonicalReceipt {
        return CrossDatabaseCanonicalReceipt(
            eventId = command.eventId,
            eventHash = "evsha256:" +
                digest("event-${command.eventId}").removePrefix("sha256:"),
            sequence = sequence,
            provenanceDigest = digest("provenance-${command.eventId}"),
            reusedExistingEvent = true
        )
    }

    private fun expectedLocalResultJson(): String {
        return CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "owner_status" to "planned",
                "schema" to TEST_LOCAL_RESULT_SCHEMA
            )
        )
    }

    private fun migrationRecord(migrationId: String): MigrationRecordEntity {
        return MigrationRecordEntity(
            migrationId = migrationId,
            instanceId = "instance_test",
            genesisCoreHash = "evsha256:" + "3".repeat(64),
            proposalId = "proposal_test",
            migrationType = "test",
            fromVersion = "v1",
            toVersion = "v2",
            affectedArtifactsJson = "[]",
            preSnapshotId = "sha256:" + "4".repeat(64),
            chainVerified = true,
            backupRequired = true,
            stepsJson = "[]",
            expectedEffect = "test",
            riskLevel = "low",
            approvalRequired = true,
            approvedByUser = false,
            approvalId = null,
            status = "planned",
            postSnapshotId = null,
            errorsJson = "[]",
            rollbackAvailable = true,
            rollbackStrategy = "append_only",
            createdBy = "test",
            createdAtMillis = 1000,
            updatedAtMillis = 1000
        )
    }

    private fun identity(): GenesisUltraRuntimeIdentity {
        val doctrine = document("doctrine/test.md", "doctrine", "doctrine")
        val charter = document("policy/charter.json", "freedom_charter", "{}")
        val recovery = document("policy/recovery.json", "recovery_policy", "{}")
        return GenesisUltraRuntimeIdentity(
            instanceId = "instance_test",
            companionName = "Morimil",
            bornAt = "2026-07-29T00:00:00Z",
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
                authorizedAt = "2026-07-29T00:00:00Z",
                expiresAt = "2026-07-29T01:00:00Z",
                receiptDigest = digest("receipt"),
                birthStatus = "born",
                ownershipConferred = false
            )
        )
    }

    private fun verifiedInput(
        identity: GenesisUltraRuntimeIdentity
    ): VerifiedCognitiveMigrationPlanningInput {
        return VerifiedCognitiveMigrationPlanningInput(
            instanceId = identity.instanceId,
            writerBodyId = identity.activeBody.bodyId,
            writerEpoch = identity.activeBody.keyEpochId,
            canonicalBirthRootHash = "evsha256:" + "1".repeat(64),
            canonicalLastSequence = 12,
            canonicalLastEventHash = "evsha256:" + "2".repeat(64),
            canonicalRecordSetDigest = "sha256:" + "3".repeat(64),
            canonicalPreSnapshotHash = "sha256:" + "4".repeat(64),
            sourceSetDigest = "sha256:" + "5".repeat(64),
            sources = listOf(
                VerifiedCognitiveMigrationSource(
                    eventId = "memory_test",
                    eventHash = "evsha256:" + "6".repeat(64),
                    sequence = 12,
                    eventType = "memory.user_confirmed",
                    actor = "user",
                    content = "Verified canonical content",
                    observedAt = "2026-07-29T00:00:00Z",
                    provenanceDigest = "sha256:" + "7".repeat(64)
                )
            )
        )
    }

    private fun document(
        path: String,
        kind: String,
        text: String
    ): GenesisUltraRuntimeDocument {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        return GenesisUltraRuntimeDocument(
            relativePath = path,
            documentKind = kind,
            digest = GenesisUltraHashProfile.sha256(bytes),
            sourceBytes = bytes
        )
    }

    private fun digest(value: String): String {
        return GenesisUltraHashProfile.sha256(value.toByteArray(StandardCharsets.UTF_8))
    }

    private class IncrementingClock : () -> Long {
        private var value = 1000L
        override fun invoke(): Long = value++
    }

    private companion object {
        const val TEST_DATABASE_PREFIX = "cognitive-migration-protocol-kill"
        const val TEST_LOCAL_RESULT_SCHEMA = "test.kill.local_result.v2"
        const val LEGACY_COG_001_PAYLOAD_SCHEMA =
            "morimil.cognitive_migration.cog_001.payload.v1"
        const val MIGRATION_ID =
            "cog_migration_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }

    private fun testDatabaseName(suffix: String): String {
        return "$TEST_DATABASE_PREFIX-$suffix.db"
    }
}
