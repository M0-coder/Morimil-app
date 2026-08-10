package com.morimil.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.morimil.app.core.orchestration.AgentCapabilityPolicy
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
import com.morimil.app.data.local.DelegatedTaskEntity
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.ProjectVaultEntity
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentLifecycleRemainingOperationKillTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun submitResultReceiptRecoversTaskAndAgentAfterDatabaseReopen() = runBlocking {
        val name = testDatabaseName("submit-result")
        context.deleteDatabase(name)
        var db = openDatabase(name)
        try {
            val identity = identity()
            val approvedTask = task(
                status = AgentCapabilityPolicy.STATUS_APPROVED,
                approvalId = "xop_" + "a".repeat(64)
            )
            val agent = agent(currentTaskId = approvedTask.taskId)
            seed(db, agent, approvedTask)
            val command = AgentLifecycleOperationFactory.submitResult(
                AgentLifecycleOperationFactory.identityOf(identity),
                agent,
                approvedTask,
                "Resultado verificable"
            )
            stagePendingLocalCommit(db, command, 601L)
            db.close()
            db = openDatabase(name)

            val report = coordinator(db).recoverAtStartup(identity, 20)
            val recoveredTask = requireNotNull(db.memoryOrganDao().loadDelegatedTask(approvedTask.taskId))
            val recoveredAgent = requireNotNull(db.memoryOrganDao().loadAgentInstance(agent.agentInstanceId))

            assertEquals(1, report.recoveredCount)
            assertEquals("Resultado verificable", recoveredTask.resultSummary)
            assertEquals(AgentInstanceLifecycleRepository.STATUS_AWAITING_REVIEW, recoveredTask.status)
            assertEquals(AgentInstanceLifecycleRepository.STATUS_AWAITING_REVIEW, recoveredAgent.status)
            assertCommitted(db, command)
        } finally {
            if (db.isOpen) db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun evaluationReceiptRecoversExactSemanticStateAfterDatabaseReopen() = runBlocking {
        val name = testDatabaseName("evaluate")
        context.deleteDatabase(name)
        var db = openDatabase(name)
        try {
            val identity = identity()
            val agent = agent()
            seed(db, agent)
            val command = AgentLifecycleOperationFactory.evaluate(
                AgentLifecycleOperationFactory.identityOf(identity),
                agent,
                "working",
                87,
                "buena evidencia"
            )
            stagePendingLocalCommit(db, command, 602L)
            db.close()
            db = openDatabase(name)

            val report = coordinator(db).recoverAtStartup(identity, 20)
            val recovered = requireNotNull(db.memoryOrganDao().loadAgentInstance(agent.agentInstanceId))

            assertEquals(1, report.recoveredCount)
            assertEquals(AgentInstanceLifecycleRepository.STATUS_WORKING, recovered.status)
            assertEquals(87, recovered.qualityScore)
            assertCommitted(db, command)
        } finally {
            if (db.isOpen) db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun promotionReceiptRecoversPromotedStateAfterDatabaseReopen() = runBlocking {
        val name = testDatabaseName("promote")
        context.deleteDatabase(name)
        var db = openDatabase(name)
        try {
            val identity = identity()
            val agent = agent()
            seed(db, agent)
            val command = AgentLifecycleOperationFactory.promote(
                AgentLifecycleOperationFactory.identityOf(identity),
                agent,
                "calidad sostenida"
            )
            stagePendingLocalCommit(db, command, 603L)
            db.close()
            db = openDatabase(name)

            val report = coordinator(db).recoverAtStartup(identity, 20)
            val recovered = requireNotNull(db.memoryOrganDao().loadAgentInstance(agent.agentInstanceId))

            assertEquals(1, report.recoveredCount)
            assertEquals(AgentInstanceLifecycleRepository.STATUS_PROMOTED, recovered.status)
            assertEquals(90, recovered.qualityScore)
            assertCommitted(db, command)
        } finally {
            if (db.isOpen) db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun retirementReceiptRecoversRetiredStateAfterDatabaseReopen() = runBlocking {
        val name = testDatabaseName("retire")
        context.deleteDatabase(name)
        var db = openDatabase(name)
        try {
            val identity = identity()
            val agent = agent()
            seed(db, agent)
            val command = AgentLifecycleOperationFactory.retire(
                AgentLifecycleOperationFactory.identityOf(identity),
                agent,
                "fin de trabajo"
            )
            stagePendingLocalCommit(db, command, 604L)
            db.close()
            db = openDatabase(name)

            val report = coordinator(db).recoverAtStartup(identity, 20)
            val recovered = requireNotNull(db.memoryOrganDao().loadAgentInstance(agent.agentInstanceId))

            assertEquals(1, report.recoveredCount)
            assertEquals(AgentInstanceLifecycleRepository.STATUS_RETIRED, recovered.status)
            assertEquals("fin de trabajo", recovered.retireReason)
            assertNotNull(recovered.retiredAtMillis)
            assertCommitted(db, command)
        } finally {
            if (db.isOpen) db.close()
            context.deleteDatabase(name)
        }
    }

    private fun openDatabase(name: String): MemoryOrganDatabase =
        Room.databaseBuilder(context, MemoryOrganDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()

    private suspend fun seed(
        db: MemoryOrganDatabase,
        agent: AgentInstanceEntity,
        task: DelegatedTaskEntity? = null
    ) {
        db.memoryOrganDao().insertProjectVault(vault())
        db.memoryOrganDao().insertAgentInstance(agent)
        if (task != null) db.memoryOrganDao().insertDelegatedTask(task)
    }

    private suspend fun stagePendingLocalCommit(
        db: MemoryOrganDatabase,
        command: CrossDatabaseStageCommand,
        sequence: Long
    ) {
        coordinator(db).stageExact(command)
        val dao = db.crossDatabaseOperationDao()
        assertEquals(1, dao.transitionStagedToPendingCanonical(command.operationId, 5_001L))
        val receipt = receipt(command, sequence)
        assertEquals(
            1,
            dao.persistCanonicalReceipt(
                operationId = command.operationId,
                canonicalEventHash = receipt.eventHash,
                canonicalSequence = receipt.sequence,
                canonicalProvenanceDigest = receipt.provenanceDigest,
                updatedAtMillis = 5_002L
            )
        )
        assertEquals(
            1,
            dao.transitionCanonicalCommittedToPendingLocalCommit(command.operationId, 5_003L)
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
            finalizers = listOf(AgentLifecycleProtocolFinalizer(db)),
            protocolRegistry = AgentLifecycleProtocolTypes.REGISTRY,
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

    private fun agent(currentTaskId: String? = null) = AgentInstanceEntity(
        agentInstanceId = "agent_instance_test",
        projectVaultId = "vault_test",
        templateAgentId = AgentCapabilityPolicy.AGENT_FILE_AUDIT,
        displayName = "Vault Test file audit worker",
        briefing = "Briefing",
        constraintsJson = "{}",
        status = AgentInstanceLifecycleRepository.STATUS_THINKING,
        qualityScore = 50,
        errorCount = 0,
        currentTaskId = currentTaskId,
        lastHeartbeatAtMillis = 10,
        createdAtMillis = 10,
        updatedAtMillis = 10,
        retiredAtMillis = null,
        retireReason = null
    )

    private fun task(status: String, approvalId: String?) = DelegatedTaskEntity(
        taskId = "ptask_test",
        createdBy = "morimil_project_vault",
        assignedAgentId = "agent_instance_test",
        targetDeviceId = null,
        goal = "Revisar repo",
        contextSummary = "contexto",
        inputRefsJson = "[]",
        allowedActionsJson = "[]",
        allowedTransportsJson = "[]",
        approvalRequired = true,
        approvalId = approvalId,
        status = status,
        riskLevel = "medium",
        resultSummary = null,
        errorSummary = null,
        createdAtMillis = 20,
        updatedAtMillis = 20,
        completedAtMillis = null
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
        private var value = 5_000L
        override fun invoke(): Long = value++
    }

    private fun testDatabaseName(suffix: String): String =
        "agent-lifecycle-remaining-kill-$suffix.db"
}
