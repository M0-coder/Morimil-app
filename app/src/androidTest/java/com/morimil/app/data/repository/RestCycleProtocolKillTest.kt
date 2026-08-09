package com.morimil.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.morimil.app.core.memory.RestCycleMode
import com.morimil.app.data.genesis.ultra.GenesisUltraHashProfile
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeActiveBody
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeAuthorization
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeAuthorizationState
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeDocument
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeGuardian
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimePolicy
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeVerifiedSeed
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.MemoryOrganDatabase
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestCycleProtocolKillTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun canonicalReceiptBeforeLocalCommitRecoversExactlyOnceAfterDatabaseReopen() = runBlocking {
        val databaseName = testDatabaseName("receipt-before-local")
        context.deleteDatabase(databaseName)
        var database = openDatabase(databaseName)
        try {
            val identity = identity()
            val protocolIdentity = RestCycleOperationFactory.identityOf(identity)
            val sourceSetDigest = digest("rest-source-set")
            val snapshotDigest = digest("rest-snapshot")
            val birthRootHash = "evsha256:${digest("birth-root").removePrefix("sha256:")}" 
            val sources = listOf(
                source("a", "decision", 91, 96, true, 1_000L),
                source("b", "learning", 75, 88, false, 2_000L)
            )
            val migrationId = RestCycleOperationFactory.deterministicMigrationId(
                identity = protocolIdentity,
                sourceSetDigest = sourceSetDigest,
                mode = RestCycleMode.Normal
            )
            val autobiography = AutobiographicalMemoryConsolidator.build(
                alias = identity.companionName,
                sourceRestCycleRef = migrationId,
                events = sources,
                generatedAtMillis = 2_000L
            )
            val summary = "REST_CYCLE_CANONICAL_V1\nsource_set_digest=$sourceSetDigest"

            RestCycleMigrationStore(database) { 1_500L }.ensurePlanned(
                migrationId = migrationId,
                instanceId = identity.instanceId,
                birthRootEventHash = birthRootHash,
                sourceEventHashes = sources.map { it.eventHash },
                preSnapshotId = birthRootHash,
                snapshotDigest = snapshotDigest,
                sourceSetDigest = sourceSetDigest,
                mode = RestCycleMode.Normal,
                approvalRequired = false,
                riskLevel = "low",
                summary = summary
            )
            val command = RestCycleOperationFactory.execute(
                identity = protocolIdentity,
                companionName = identity.companionName,
                migrationId = migrationId,
                mode = RestCycleMode.Normal,
                sourceSetDigest = sourceSetDigest,
                snapshotDigest = snapshotDigest,
                birthRootEventHash = birthRootHash,
                summary = summary,
                sourceEvents = sources,
                autobiography = autobiography,
                approvalRequired = false,
                approvalId = null
            )
            val receipt = stagePendingLocalCommit(database, command, sequence = 401)

            assertEquals(
                RestCycleMigrationStore.STATUS_PLANNED,
                database.memoryOrganDao().loadMigrationRecord(migrationId)?.status
            )
            assertEquals(
                CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT,
                database.crossDatabaseOperationDao().loadOperation(command.operationId)?.status
            )

            database.close()
            database = openDatabase(databaseName)
            val firstRecovery = realCoordinator(database).recoverAtStartup(identity, 20)
            val migration = requireNotNull(database.memoryOrganDao().loadMigrationRecord(migrationId))
            val operation = requireNotNull(database.crossDatabaseOperationDao().loadOperation(command.operationId))
            val snapshot = database.memoryOrganDao().getCurrentSelfSnapshot()
            val links = database.memoryOrganDao().loadMemoryLinksForReconciliation()
                .filter { link -> link.sourceId == receipt.eventHash && link.relation == "derived_from" }

            assertEquals(1, firstRecovery.recoveredCount)
            assertEquals(RestCycleMigrationStore.STATUS_COMPLETED, migration.status)
            assertEquals(receipt.eventHash, migration.postSnapshotId)
            assertEquals(CrossDatabaseOperationStatus.COMMITTED, operation.status)
            assertEquals(RestCycleProtocolSchemas.REST_001_LOCAL_RESULT, operation.localResultSchema)
            assertNotNull(snapshot)
            assertEquals(receipt.eventHash, snapshot?.sourceEventHash)
            assertEquals(identity.companionName, snapshot?.alias)
            assertEquals(sources.size, links.size)
            assertTrue(links.all { link -> link.sourceType == "canonical_memory_event" })
            assertTrue(links.all { link -> link.targetType == "canonical_memory_event" })

            val secondRecovery = realCoordinator(database).recoverAtStartup(identity, 20)
            val replayLinks = database.memoryOrganDao().loadMemoryLinksForReconciliation()
                .filter { link -> link.sourceId == receipt.eventHash && link.relation == "derived_from" }
            assertEquals(0, secondRecovery.recoveredCount)
            assertEquals(sources.size, replayLinks.size)
            assertEquals(
                receipt.eventHash,
                database.memoryOrganDao().getCurrentSelfSnapshot()?.sourceEventHash
            )
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun repairProposalReceiptRecoversExactlyOnceAndNeverExecutesRepair() = runBlocking {
        val databaseName = testDatabaseName("repair-proposal-receipt-before-local")
        context.deleteDatabase(databaseName)
        var database = openDatabase(databaseName)
        try {
            val identity = identity()
            val protocolIdentity = RestCycleOperationFactory.identityOf(identity)
            val sourceSetDigest = digest("repair-source-set")
            val snapshotDigest = digest("repair-snapshot")
            val birthRootHash = "evsha256:${digest("repair-birth-root").removePrefix("sha256:")}" 
            val repairSources = listOf(
                source("repair", "decision", 95, 90, false, 3_000L)
            )
            val report = RestRepairProposalPlanner.build(repairSources)
            assertTrue(report.hasCandidates)
            val migrationId = RestCycleOperationFactory.deterministicRepairMigrationId(
                identity = protocolIdentity,
                report = report
            )
            val proposalDigest = RestCycleOperationFactory.repairProposalDigest(report)
            RestRepairProposalStore(database) { 3_100L }.ensurePlanned(
                migrationId = migrationId,
                instanceId = identity.instanceId,
                birthRootEventHash = birthRootHash,
                preSnapshotId = birthRootHash,
                snapshotDigest = snapshotDigest,
                sourceSetDigest = sourceSetDigest,
                proposalDigest = proposalDigest,
                report = report
            )
            val command = RestCycleOperationFactory.proposeRepair(
                identity = protocolIdentity,
                migrationId = migrationId,
                sourceSetDigest = sourceSetDigest,
                snapshotDigest = snapshotDigest,
                birthRootEventHash = birthRootHash,
                report = report
            )
            val receipt = stagePendingLocalCommit(database, command, sequence = 402)

            val before = requireNotNull(database.memoryOrganDao().loadMigrationRecord(migrationId))
            assertEquals(RestRepairProposalStore.STATUS_PLANNED, before.status)
            assertTrue(before.approvalRequired)
            assertTrue(!before.approvedByUser)
            assertEquals(null, before.approvalId)
            assertEquals(null, before.postSnapshotId)
            assertEquals(
                CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT,
                database.crossDatabaseOperationDao().loadOperation(command.operationId)?.status
            )

            database.close()
            database = openDatabase(databaseName)
            val firstRecovery = realCoordinator(database).recoverAtStartup(identity, 20)
            val migration = requireNotNull(database.memoryOrganDao().loadMigrationRecord(migrationId))
            val operation = requireNotNull(database.crossDatabaseOperationDao().loadOperation(command.operationId))

            assertEquals(1, firstRecovery.recoveredCount)
            assertEquals(RestRepairProposalStore.STATUS_PLANNED, migration.status)
            assertTrue(migration.approvalRequired)
            assertTrue(!migration.approvedByUser)
            assertEquals(null, migration.approvalId)
            assertEquals(null, migration.postSnapshotId)
            assertEquals(CrossDatabaseOperationStatus.COMMITTED, operation.status)
            assertEquals(RestCycleProtocolSchemas.REST_002_LOCAL_RESULT, operation.localResultSchema)
            assertTrue(operation.localResultJson?.contains("\"repair_execution\":\"not_implemented\"") == true)
            assertTrue(operation.localResultJson?.contains(receipt.eventHash) == true)
            assertEquals(null, database.memoryOrganDao().getCurrentSelfSnapshot())
            assertTrue(database.memoryOrganDao().loadMemoryLinksForReconciliation().isEmpty())

            val secondRecovery = realCoordinator(database).recoverAtStartup(identity, 20)
            assertEquals(0, secondRecovery.recoveredCount)
            val durable = requireNotNull(database.memoryOrganDao().loadMigrationRecord(migrationId))
            assertEquals(RestRepairProposalStore.STATUS_PLANNED, durable.status)
            assertEquals(null, durable.postSnapshotId)
            assertTrue(database.memoryOrganDao().loadMemoryLinksForReconciliation().isEmpty())
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun openDatabase(databaseName: String): MemoryOrganDatabase {
        return Room.databaseBuilder(context, MemoryOrganDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
    }

    private suspend fun stagePendingLocalCommit(
        database: MemoryOrganDatabase,
        command: CrossDatabaseStageCommand,
        sequence: Long
    ): CrossDatabaseCanonicalReceipt {
        restCoordinator(database).stageExact(command)
        val dao = database.crossDatabaseOperationDao()
        assertEquals(1, dao.transitionStagedToPendingCanonical(command.operationId, 2_001L))
        val receipt = receipt(command, sequence)
        assertEquals(
            1,
            dao.persistCanonicalReceipt(
                operationId = command.operationId,
                canonicalEventHash = receipt.eventHash,
                canonicalSequence = receipt.sequence,
                canonicalProvenanceDigest = receipt.provenanceDigest,
                updatedAtMillis = 2_002L
            )
        )
        assertEquals(
            1,
            dao.transitionCanonicalCommittedToPendingLocalCommit(command.operationId, 2_003L)
        )
        return receipt
    }

    private fun realCoordinator(database: MemoryOrganDatabase): CrossDatabaseOperationCoordinator {
        return restCoordinator(database)
    }

    private fun restCoordinator(database: MemoryOrganDatabase): CrossDatabaseOperationCoordinator {
        return CrossDatabaseOperationCoordinator.production(
            database = database,
            canonicalEnsurePort = canonicalMustNotRun(),
            finalizers = listOf(RestCycleProtocolFinalizer(database)),
            protocolRegistry = RestCycleProtocolTypes.REGISTRY,
            clockMillis = IncrementingClock()
        )
    }

    private fun canonicalMustNotRun(): CrossDatabaseCanonicalEnsurePort {
        return object : CrossDatabaseCanonicalEnsurePort {
            override suspend fun ensureCommitted(
                command: CrossDatabaseCanonicalCommand
            ): CrossDatabaseCanonicalReceipt = error("canonical_must_not_replay")
        }
    }

    private fun receipt(command: CrossDatabaseStageCommand, sequence: Long): CrossDatabaseCanonicalReceipt {
        return CrossDatabaseCanonicalReceipt(
            eventId = command.eventId,
            eventHash = "evsha256:${digest("event-${command.eventId}").removePrefix("sha256:")}",
            sequence = sequence,
            provenanceDigest = digest("provenance-${command.eventId}"),
            reusedExistingEvent = true
        )
    }

    private fun source(
        suffix: String,
        memoryKind: String,
        importance: Int,
        confidence: Int,
        userConfirmed: Boolean,
        observedAtMillis: Long
    ): RestCycleSourceEvent {
        return RestCycleSourceEvent(
            eventHash = "evsha256:${digest("source-$suffix").removePrefix("sha256:")}",
            eventType = "conversation.user_message",
            actor = "user",
            source = "test",
            memoryKind = memoryKind,
            tagsJson = "[]",
            body = "source-$suffix",
            importance = importance,
            confidence = confidence,
            userConfirmed = userConfirmed,
            observedAtMillis = observedAtMillis
        )
    }

    private fun identity(): GenesisUltraRuntimeIdentity {
        val doctrine = document("doctrine/test.md", "doctrine", "doctrine")
        val charter = document("policy/charter.json", "freedom_charter", "{}")
        val recovery = document("policy/recovery.json", "recovery_policy", "{}")
        return GenesisUltraRuntimeIdentity(
            instanceId = "instance_test",
            companionName = "Morimil",
            bornAt = "2026-08-07T00:00:00Z",
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
                authorizedAt = "2026-08-07T00:00:00Z",
                expiresAt = "2026-08-07T01:00:00Z",
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

    private fun digest(value: String): String {
        return GenesisUltraHashProfile.sha256(value.toByteArray(StandardCharsets.UTF_8))
    }

    private class IncrementingClock : () -> Long {
        private var value = 1_000L
        override fun invoke(): Long = value++
    }

    private fun testDatabaseName(suffix: String): String = "$TEST_DATABASE_PREFIX-$suffix.db"

    private companion object {
        const val TEST_DATABASE_PREFIX = "rest-cycle-protocol-kill"
    }
}
