package com.morimil.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.morimil.app.core.orchestration.AgentCapabilityPolicy
import com.morimil.app.core.orchestration.DelegationPlan
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrchestrationProtocolKillTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun proposalReceiptBeforeLocalCommitRecoversAfterDatabaseReopen() = runBlocking {
        val databaseName = testDatabaseName("proposal-recovery")
        context.deleteDatabase(databaseName)
        var database = openDatabase(databaseName)
        try {
            val identity = identity()
            val command = OrchestrationOperationFactory.propose(
                identity = OrchestrationOperationFactory.identityOf(identity),
                goal = "Revisar repositorio",
                plan = plan()
            )
            stagePendingLocalCommit(database, command, sequence = 301)

            assertNull(database.memoryOrganDao().loadDelegatedTask(command.subjectId))
            assertEquals(
                CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT,
                database.crossDatabaseOperationDao().loadOperation(command.operationId)?.status
            )

            database.close()
            database = openDatabase(databaseName)
            val report = realCoordinator(database).recoverAtStartup(identity, 20)
            val task = requireNotNull(database.memoryOrganDao().loadDelegatedTask(command.subjectId))
            val operation = requireNotNull(
                database.crossDatabaseOperationDao().loadOperation(command.operationId)
            )

            assertEquals(1, report.recoveredCount)
            assertEquals(AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL, task.status)
            assertEquals(command.subjectId, task.taskId)
            assertEquals(CrossDatabaseOperationStatus.COMMITTED, operation.status)
            assertEquals(
                OrchestrationProtocolSchemas.ORCH_002_LOCAL_RESULT,
                operation.localResultSchema
            )
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun approvalReceiptBeforeLocalCommitRecoversExactlyOnceAfterReopen() = runBlocking {
        val databaseName = testDatabaseName("approval-recovery")
        context.deleteDatabase(databaseName)
        var database = openDatabase(databaseName)
        try {
            val identity = identity()
            val proposal = OrchestrationOperationFactory.propose(
                identity = OrchestrationOperationFactory.identityOf(identity),
                goal = "Auditar seguridad",
                plan = plan()
            )
            stagePendingLocalCommit(database, proposal, sequence = 311)
            assertEquals(1, realCoordinator(database).recoverAtStartup(identity, 20).recoveredCount)
            val pendingTask = requireNotNull(
                database.memoryOrganDao().loadDelegatedTask(proposal.subjectId)
            )

            val approval = OrchestrationOperationFactory.approve(
                identity = OrchestrationOperationFactory.identityOf(identity),
                task = pendingTask
            )
            stagePendingLocalCommit(database, approval, sequence = 312)
            assertEquals(
                AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL,
                database.memoryOrganDao().loadDelegatedTask(proposal.subjectId)?.status
            )

            database.close()
            database = openDatabase(databaseName)
            val report = realCoordinator(database).recoverAtStartup(identity, 20)
            val approved = requireNotNull(
                database.memoryOrganDao().loadDelegatedTask(proposal.subjectId)
            )

            assertEquals(1, report.recoveredCount)
            assertEquals(AgentCapabilityPolicy.STATUS_APPROVED, approved.status)
            assertEquals(approval.operationId, approved.approvalId)
            assertEquals(
                CrossDatabaseOperationStatus.COMMITTED,
                database.crossDatabaseOperationDao().loadOperation(approval.operationId)?.status
            )

            val replay = realCoordinator(database).recoverAtStartup(identity, 20)
            assertEquals(0, replay.recoveredCount)
            assertEquals(approval.operationId, approved.approvalId)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun rejectionReceiptBeforeLocalCommitRecoversWithExactReasonAfterReopen() = runBlocking {
        val databaseName = testDatabaseName("rejection-recovery")
        context.deleteDatabase(databaseName)
        var database = openDatabase(databaseName)
        try {
            val identity = identity()
            val proposal = OrchestrationOperationFactory.propose(
                identity = OrchestrationOperationFactory.identityOf(identity),
                goal = "Preparar diff",
                plan = plan()
            )
            stagePendingLocalCommit(database, proposal, sequence = 321)
            assertEquals(1, realCoordinator(database).recoverAtStartup(identity, 20).recoveredCount)
            val pendingTask = requireNotNull(
                database.memoryOrganDao().loadDelegatedTask(proposal.subjectId)
            )

            val reason = "Requiere revisión manual"
            val rejection = OrchestrationOperationFactory.reject(
                identity = OrchestrationOperationFactory.identityOf(identity),
                task = pendingTask,
                reason = reason
            )
            stagePendingLocalCommit(database, rejection, sequence = 322)

            database.close()
            database = openDatabase(databaseName)
            val report = realCoordinator(database).recoverAtStartup(identity, 20)
            val rejected = requireNotNull(
                database.memoryOrganDao().loadDelegatedTask(proposal.subjectId)
            )

            assertEquals(1, report.recoveredCount)
            assertEquals(AgentCapabilityPolicy.STATUS_REJECTED, rejected.status)
            assertEquals(reason, rejected.errorSummary)
            assertTrue(rejected.completedAtMillis != null)
            assertEquals(
                CrossDatabaseOperationStatus.COMMITTED,
                database.crossDatabaseOperationDao().loadOperation(rejection.operationId)?.status
            )
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun cognitiveRecoveryDoesNotConsumeOrchestrationRows() = runBlocking {
        val databaseName = testDatabaseName("owner-isolation")
        context.deleteDatabase(databaseName)
        val database = openDatabase(databaseName)
        try {
            val identity = identity()
            val command = OrchestrationOperationFactory.propose(
                identity = OrchestrationOperationFactory.identityOf(identity),
                goal = "Revisar aislamiento",
                plan = plan()
            )
            orchestrationCoordinator(database).stageExact(command)

            val cognitiveOnly = CrossDatabaseOperationCoordinator.production(
                database = database,
                canonicalEnsurePort = canonicalMustNotRun(),
                finalizers = listOf(
                    object : CrossDatabaseTypedFinalizer {
                        override val supportedOperationTypes =
                            CognitiveMigrationProtocolTypes.CLOSED_REGISTRY.keys

                        override suspend fun finalizeInsideTransaction(
                            operation: CrossDatabaseOperationRecord,
                            receipt: CrossDatabaseCanonicalReceipt
                        ): CrossDatabaseLocalResult = error("cognitive_finalizer_must_not_run")
                    }
                ),
                protocolRegistry = COGNITIVE_MIGRATION_PROTOCOL_REGISTRY,
                clockMillis = IncrementingClock()
            )

            val report = cognitiveOnly.recoverAtStartup(identity, 20)
            assertEquals(0, report.recoveredCount)
            assertEquals(
                CrossDatabaseOperationStatus.STAGED,
                database.crossDatabaseOperationDao().loadOperation(command.operationId)?.status
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
        command: CrossDatabaseStageCommand,
        sequence: Long
    ) {
        orchestrationCoordinator(database).stageExact(command)
        val dao = database.crossDatabaseOperationDao()
        assertEquals(1, dao.transitionStagedToPendingCanonical(command.operationId, 2001))
        val receipt = receipt(command, sequence)
        assertEquals(
            1,
            dao.persistCanonicalReceipt(
                operationId = command.operationId,
                canonicalEventHash = receipt.eventHash,
                canonicalSequence = receipt.sequence,
                canonicalProvenanceDigest = receipt.provenanceDigest,
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

    private fun realCoordinator(database: MemoryOrganDatabase): CrossDatabaseOperationCoordinator {
        return orchestrationCoordinator(database)
    }

    private fun orchestrationCoordinator(
        database: MemoryOrganDatabase
    ): CrossDatabaseOperationCoordinator {
        return CrossDatabaseOperationCoordinator.production(
            database = database,
            canonicalEnsurePort = canonicalMustNotRun(),
            finalizers = listOf(OrchestrationProtocolFinalizer(database)),
            protocolRegistry = OrchestrationProtocolTypes.REGISTRY,
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

    private fun receipt(
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

    private fun plan() = DelegationPlan(
        assignedAgentId = AgentCapabilityPolicy.AGENT_GITHUB,
        targetDeviceId = "android_body",
        allowedActions = listOf("read_repository"),
        allowedTransports = listOf(AgentCapabilityPolicy.TRANSPORT_WIFI),
        approvalRequired = true,
        riskLevel = "medium",
        contextSummary = "contexto estable",
        immuneDecision = "allow",
        immuneReasons = emptyList(),
        immuneMatchedSignals = emptyList()
    )

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

    private fun testDatabaseName(suffix: String): String {
        return "$TEST_DATABASE_PREFIX-$suffix.db"
    }

    private companion object {
        const val TEST_DATABASE_PREFIX = "orchestration-protocol-kill"
    }
}
