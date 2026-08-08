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
import com.morimil.app.data.local.AgentInstanceEntity
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.ProjectVaultEntity
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentLifecycleProtocolKillTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun createReceiptBeforeLocalCommitRecoversAfterDatabaseReopen() = runBlocking {
        val databaseName = testDatabaseName("create-recovery")
        context.deleteDatabase(databaseName)
        var database = openDatabase(databaseName)
        try {
            val identity = identity()
            val vault = vault()
            database.memoryOrganDao().insertProjectVault(vault)
            val command = AgentLifecycleOperationFactory.create(
                identity = AgentLifecycleOperationFactory.identityOf(identity),
                vault = vault,
                templateAgentId = AgentCapabilityPolicy.AGENT_FILE_AUDIT,
                briefing = "Briefing durable",
                ordinal = 1
            )
            stagePendingLocalCommit(database, command, sequence = 401)

            assertNull(database.memoryOrganDao().loadAgentInstance(command.subjectId))
            database.close()
            database = openDatabase(databaseName)

            val report = agentCoordinator(database).recoverAtStartup(identity, 20)
            val agent = requireNotNull(database.memoryOrganDao().loadAgentInstance(command.subjectId))
            val operation = requireNotNull(database.crossDatabaseOperationDao().loadOperation(command.operationId))

            assertEquals(1, report.recoveredCount)
            assertEquals(AgentInstanceLifecycleRepository.STATUS_THINKING, agent.status)
            assertEquals(CrossDatabaseOperationStatus.COMMITTED, operation.status)
            assertEquals(AgentLifecycleProtocolSchemas.AGENT_001_LOCAL_RESULT, operation.localResultSchema)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun assignmentReceiptBeforeLocalCommitRecoversTaskAndAgentTogether() = runBlocking {
        val databaseName = testDatabaseName("assign-recovery")
        context.deleteDatabase(databaseName)
        var database = openDatabase(databaseName)
        try {
            val identity = identity()
            val vault = vault()
            val agent = agent()
            database.memoryOrganDao().insertProjectVault(vault)
            database.memoryOrganDao().insertAgentInstance(agent)
            val command = AgentLifecycleOperationFactory.assign(
                identity = AgentLifecycleOperationFactory.identityOf(identity),
                vault = vault,
                agent = agent,
                goal = "Revisar repositorio",
                plan = plan()
            )
            stagePendingLocalCommit(database, command, sequence = 411)

            assertNull(database.memoryOrganDao().loadDelegatedTask(command.subjectId))
            assertNull(database.memoryOrganDao().loadAgentInstance(agent.agentInstanceId)?.currentTaskId)
            database.close()
            database = openDatabase(databaseName)

            val report = agentCoordinator(database).recoverAtStartup(identity, 20)
            val task = requireNotNull(database.memoryOrganDao().loadDelegatedTask(command.subjectId))
            val updatedAgent = requireNotNull(database.memoryOrganDao().loadAgentInstance(agent.agentInstanceId))

            assertEquals(1, report.recoveredCount)
            assertEquals(AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL, task.status)
            assertEquals(task.taskId, updatedAgent.currentTaskId)
            assertEquals(AgentInstanceLifecycleRepository.STATUS_AWAITING_REVIEW, updatedAgent.status)
            assertEquals(
                CrossDatabaseOperationStatus.COMMITTED,
                database.crossDatabaseOperationDao().loadOperation(command.operationId)?.status
            )
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun quarantineReceiptRecoversFailedAgentAndReplacementAtomically() = runBlocking {
        val databaseName = testDatabaseName("quarantine-recovery")
        context.deleteDatabase(databaseName)
        var database = openDatabase(databaseName)
        try {
            val identity = identity()
            val vault = vault()
            val agent = agent()
            database.memoryOrganDao().insertProjectVault(vault)
            database.memoryOrganDao().insertAgentInstance(agent)
            val command = AgentLifecycleOperationFactory.quarantine(
                identity = AgentLifecycleOperationFactory.identityOf(identity),
                vault = vault,
                agent = agent,
                reason = "fallo repetible"
            )
            val replacementId = JSONObject(command.payloadJson).getString("replacement_agent_id")
            stagePendingLocalCommit(database, command, sequence = 421)

            assertEquals(
                AgentInstanceLifecycleRepository.STATUS_THINKING,
                database.memoryOrganDao().loadAgentInstance(agent.agentInstanceId)?.status
            )
            assertNull(database.memoryOrganDao().loadAgentInstance(replacementId))
            database.close()
            database = openDatabase(databaseName)

            val report = agentCoordinator(database).recoverAtStartup(identity, 20)
            val failed = requireNotNull(database.memoryOrganDao().loadAgentInstance(agent.agentInstanceId))
            val replacement = requireNotNull(database.memoryOrganDao().loadAgentInstance(replacementId))

            assertEquals(1, report.recoveredCount)
            assertEquals(AgentInstanceLifecycleRepository.STATUS_QUARANTINED, failed.status)
            assertEquals(1, failed.errorCount)
            assertEquals("fallo repetible", failed.retireReason)
            assertEquals(AgentInstanceLifecycleRepository.STATUS_THINKING, replacement.status)
            assertEquals(agent.projectVaultId, replacement.projectVaultId)
            assertEquals(agent.templateAgentId, replacement.templateAgentId)
            assertEquals(
                CrossDatabaseOperationStatus.COMMITTED,
                database.crossDatabaseOperationDao().loadOperation(command.operationId)?.status
            )
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun orchestrationRecoveryDoesNotConsumeAgentLifecycleRows() = runBlocking {
        val databaseName = testDatabaseName("owner-isolation")
        context.deleteDatabase(databaseName)
        val database = openDatabase(databaseName)
        try {
            val identity = identity()
            val vault = vault()
            database.memoryOrganDao().insertProjectVault(vault)
            val command = AgentLifecycleOperationFactory.create(
                identity = AgentLifecycleOperationFactory.identityOf(identity),
                vault = vault,
                templateAgentId = AgentCapabilityPolicy.AGENT_FILE_AUDIT,
                briefing = "Aislamiento",
                ordinal = 1
            )
            agentCoordinator(database).stageExact(command)

            val orchestrationOnly = CrossDatabaseOperationCoordinator.production(
                database = database,
                canonicalEnsurePort = canonicalMustNotRun(),
                finalizers = listOf(OrchestrationProtocolFinalizer(database)),
                protocolRegistry = OrchestrationProtocolTypes.REGISTRY,
                clockMillis = IncrementingClock()
            )
            val report = orchestrationOnly.recoverAtStartup(identity, 20)

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

    private fun openDatabase(databaseName: String): MemoryOrganDatabase =
        Room.databaseBuilder(context, MemoryOrganDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()

    private suspend fun stagePendingLocalCommit(
        database: MemoryOrganDatabase,
        command: CrossDatabaseStageCommand,
        sequence: Long
    ) {
        agentCoordinator(database).stageExact(command)
        val dao = database.crossDatabaseOperationDao()
        assertEquals(1, dao.transitionStagedToPendingCanonical(command.operationId, 3001))
        val receipt = receipt(command, sequence)
        assertEquals(
            1,
            dao.persistCanonicalReceipt(
                operationId = command.operationId,
                canonicalEventHash = receipt.eventHash,
                canonicalSequence = receipt.sequence,
                canonicalProvenanceDigest = receipt.provenanceDigest,
                updatedAtMillis = 3002
            )
        )
        assertEquals(
            1,
            dao.transitionCanonicalCommittedToPendingLocalCommit(command.operationId, 3003)
        )
    }

    private fun agentCoordinator(database: MemoryOrganDatabase): CrossDatabaseOperationCoordinator =
        CrossDatabaseOperationCoordinator.production(
            database = database,
            canonicalEnsurePort = canonicalMustNotRun(),
            finalizers = listOf(AgentLifecycleProtocolFinalizer(database)),
            protocolRegistry = AgentLifecycleProtocolTypes.REGISTRY,
            clockMillis = IncrementingClock()
        )

    private fun canonicalMustNotRun(): CrossDatabaseCanonicalEnsurePort =
        object : CrossDatabaseCanonicalEnsurePort {
            override suspend fun ensureCommitted(
                command: CrossDatabaseCanonicalCommand
            ): CrossDatabaseCanonicalReceipt = error("canonical_must_not_replay")
        }

    private fun receipt(command: CrossDatabaseStageCommand, sequence: Long) =
        CrossDatabaseCanonicalReceipt(
            eventId = command.eventId,
            eventHash = "evsha256:" + digest("event-${command.eventId}").removePrefix("sha256:"),
            sequence = sequence,
            provenanceDigest = digest("provenance-${command.eventId}"),
            reusedExistingEvent = true
        )

    private fun vault() = ProjectVaultEntity(
        vaultId = "vault_test",
        displayName = "Vault Test",
        companyName = "Morimil",
        projectType = "software",
        mission = "Construir con evidencia",
        status = "active",
        roadmapSummary = "roadmap",
        progressPercent = 20,
        activeAgentCount = 0,
        healthStatus = "healthy",
        sourceContext = "test",
        createdAtMillis = 1,
        updatedAtMillis = 2,
        completedAtMillis = null
    )

    private fun agent() = AgentInstanceEntity(
        agentInstanceId = "agent_instance_test",
        projectVaultId = "vault_test",
        templateAgentId = AgentCapabilityPolicy.AGENT_FILE_AUDIT,
        displayName = "Vault Test file audit worker",
        briefing = "Briefing",
        constraintsJson = "{}",
        status = AgentInstanceLifecycleRepository.STATUS_THINKING,
        qualityScore = 50,
        errorCount = 0,
        currentTaskId = null,
        lastHeartbeatAtMillis = 10,
        createdAtMillis = 10,
        updatedAtMillis = 10,
        retiredAtMillis = null,
        retireReason = null
    )

    private fun plan() = DelegationPlan(
        assignedAgentId = AgentCapabilityPolicy.AGENT_FILE_AUDIT,
        targetDeviceId = null,
        allowedActions = listOf("read_allowed_files"),
        allowedTransports = listOf(AgentCapabilityPolicy.TRANSPORT_MANUAL),
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
        private var value = 2000L
        override fun invoke(): Long = value++
    }

    private fun testDatabaseName(suffix: String): String =
        "$TEST_DATABASE_PREFIX-$suffix.db"

    private companion object {
        const val TEST_DATABASE_PREFIX = "agent-lifecycle-protocol-kill"
    }
}
