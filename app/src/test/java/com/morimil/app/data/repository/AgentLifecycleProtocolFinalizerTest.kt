package com.morimil.app.data.repository

import com.morimil.app.core.orchestration.AgentCapabilityPolicy
import com.morimil.app.core.orchestration.DelegationPlan
import com.morimil.app.data.local.AgentInstanceEntity
import com.morimil.app.data.local.CrossDatabaseOperationEntity
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.DelegatedTaskEntity
import com.morimil.app.data.local.ProjectVaultEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLifecycleProtocolFinalizerTest {
    @Test
    fun createFinalizesOnlyAfterReceiptAndExactReplayIsIdempotent() = runBlocking {
        val store = FakeStore(vault())
        val finalizer = AgentLifecycleProtocolFinalizer.testing(store)
        val command = AgentLifecycleOperationFactory.create(
            identity(), store.vault, AgentCapabilityPolicy.AGENT_FILE_AUDIT, "Briefing", 1
        )
        val operation = operation(command)

        assertEquals(null, store.agent)
        val first = finalizer.finalizeInsideTransaction(operation, receipt(command, 11))
        val created = requireNotNull(store.agent)
        assertEquals(command.subjectId, created.agentInstanceId)
        assertEquals(1, store.agentInsertCalls)
        assertEquals(AgentLifecycleProtocolSchemas.AGENT_001_LOCAL_RESULT, first.schema)
        assertTrue(first.json.contains("\"record_inserted\":true"))

        val replay = finalizer.finalizeInsideTransaction(operation, receipt(command, 11))
        assertEquals(1, store.agentInsertCalls)
        assertTrue(replay.json.contains("\"record_inserted\":false"))
    }

    @Test
    fun assignmentAtomicallyCreatesTaskAndMovesAgentAfterReceipt() = runBlocking {
        val store = FakeStore(vault(), agent = agent())
        val finalizer = AgentLifecycleProtocolFinalizer.testing(store)
        val command = AgentLifecycleOperationFactory.assign(
            identity(), store.vault, requireNotNull(store.agent), "Revisar repo", plan()
        )

        val result = finalizer.finalizeInsideTransaction(operation(command), receipt(command, 21))
        val task = requireNotNull(store.task)
        val updatedAgent = requireNotNull(store.agent)

        assertEquals(command.subjectId, task.taskId)
        assertEquals(AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL, task.status)
        assertEquals(task.taskId, updatedAgent.currentTaskId)
        assertEquals(AgentInstanceLifecycleRepository.STATUS_AWAITING_REVIEW, updatedAgent.status)
        assertEquals(1, store.taskInsertCalls)
        assertEquals(1, store.agentUpdateCalls)
        assertEquals(1, store.refreshCalls)
        assertEquals(AgentLifecycleProtocolSchemas.AGENT_002_LOCAL_RESULT, result.schema)
    }

    @Test
    fun resultRequiresApprovedTaskAndUpdatesTaskAndAgentTogether() = runBlocking {
        val approvedTask = task(
            status = AgentCapabilityPolicy.STATUS_APPROVED,
            approvalId = "xop_" + "a".repeat(64)
        )
        val store = FakeStore(
            vault = vault(),
            agent = agent(currentTaskId = approvedTask.taskId),
            task = approvedTask
        )
        val finalizer = AgentLifecycleProtocolFinalizer.testing(store)
        val command = AgentLifecycleOperationFactory.submitResult(
            identity(), requireNotNull(store.agent), approvedTask, "Resultado verificable"
        )

        finalizer.finalizeInsideTransaction(operation(command), receipt(command, 31))
        assertEquals("Resultado verificable", store.task?.resultSummary)
        assertEquals(AgentInstanceLifecycleRepository.STATUS_AWAITING_REVIEW, store.task?.status)
        assertEquals(1, store.taskUpdateCalls)
        assertEquals(1, store.agentUpdateCalls)

        val rejectedStore = FakeStore(
            vault = vault(),
            agent = agent(currentTaskId = approvedTask.taskId),
            task = approvedTask.copy(status = AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL, approvalId = null)
        )
        val staleCommand = AgentLifecycleOperationFactory.submitResult(
            identity(), requireNotNull(rejectedStore.agent), requireNotNull(rejectedStore.task), "No permitido"
        )
        assertFailure(
            CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT,
            runCatching {
                AgentLifecycleProtocolFinalizer.testing(rejectedStore).finalizeInsideTransaction(
                    operation(staleCommand), receipt(staleCommand, 32)
                )
            }.exceptionOrNull()
        )
    }

    @Test
    fun evaluationPromotionAndRetirementBindExactSemanticState() = runBlocking {
        val store = FakeStore(vault(), agent = agent())
        val finalizer = AgentLifecycleProtocolFinalizer.testing(store)

        val evaluation = AgentLifecycleOperationFactory.evaluate(
            identity(), requireNotNull(store.agent), "working", 87, "buena evidencia"
        )
        finalizer.finalizeInsideTransaction(operation(evaluation), receipt(evaluation, 41))
        assertEquals(87, store.agent?.qualityScore)
        assertEquals(AgentInstanceLifecycleRepository.STATUS_WORKING, store.agent?.status)

        val promotion = AgentLifecycleOperationFactory.promote(
            identity(), requireNotNull(store.agent), "calidad sostenida"
        )
        finalizer.finalizeInsideTransaction(operation(promotion), receipt(promotion, 42))
        assertEquals(AgentInstanceLifecycleRepository.STATUS_PROMOTED, store.agent?.status)
        assertEquals(90, store.agent?.qualityScore)

        val retirement = AgentLifecycleOperationFactory.retire(
            identity(), requireNotNull(store.agent), "fin de trabajo"
        )
        finalizer.finalizeInsideTransaction(operation(retirement), receipt(retirement, 43))
        assertEquals(AgentInstanceLifecycleRepository.STATUS_RETIRED, store.agent?.status)
        assertEquals("fin de trabajo", store.agent?.retireReason)
        assertNotNull(store.agent?.retiredAtMillis)
    }

    @Test
    fun quarantineClosesFailedAgentAndCreatesDeterministicReplacement() = runBlocking {
        val store = FakeStore(vault(), agent = agent())
        val finalizer = AgentLifecycleProtocolFinalizer.testing(store)
        val command = AgentLifecycleOperationFactory.quarantine(
            identity(), store.vault, requireNotNull(store.agent), "fallo de herramienta"
        )

        val result = finalizer.finalizeInsideTransaction(operation(command), receipt(command, 51))
        val failed = requireNotNull(store.agent)
        val replacement = requireNotNull(store.replacement)

        assertEquals(AgentInstanceLifecycleRepository.STATUS_QUARANTINED, failed.status)
        assertEquals(1, failed.errorCount)
        assertEquals("fallo de herramienta", failed.retireReason)
        assertEquals(failed.projectVaultId, replacement.projectVaultId)
        assertEquals(failed.templateAgentId, replacement.templateAgentId)
        assertEquals(AgentInstanceLifecycleRepository.STATUS_THINKING, replacement.status)
        assertTrue(result.json.contains(replacement.agentInstanceId))
    }

    @Test
    fun malformedOwnerOrPayloadFailsClosed() = runBlocking {
        val store = FakeStore(vault())
        val finalizer = AgentLifecycleProtocolFinalizer.testing(store)
        val command = AgentLifecycleOperationFactory.create(
            identity(), store.vault, AgentCapabilityPolicy.AGENT_FILE_AUDIT, "Briefing", 1
        )

        assertFailure(
            CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT,
            runCatching {
                finalizer.finalizeInsideTransaction(
                    operation(command).copy(status = CrossDatabaseOperationStatus.STAGED),
                    receipt(command, 61)
                )
            }.exceptionOrNull()
        )
        assertFailure(
            CrossDatabaseProtocolErrors.UNSUPPORTED_OPERATION_VERSION,
            runCatching {
                finalizer.finalizeInsideTransaction(
                    operation(command).copy(ownerType = "foreign"),
                    receipt(command, 62)
                )
            }.exceptionOrNull()
        )
        assertFailure(
            CrossDatabaseProtocolErrors.UNSUPPORTED_PAYLOAD_SCHEMA,
            runCatching {
                finalizer.finalizeInsideTransaction(
                    operation(command).copy(payloadSchema = "wrong.schema"),
                    receipt(command, 63)
                )
            }.exceptionOrNull()
        )
    }

    private fun identity() = AgentLifecycleProtocolIdentity(
        instanceId = "instance_test",
        writerBodyId = "body_test",
        writerEpoch = "epoch_test"
    )

    private fun vault() = ProjectVaultEntity(
        vaultId = "vault_test",
        displayName = "Vault Test",
        companyName = "Morimil",
        projectType = "software",
        mission = "Construir con evidencia",
        status = "active",
        roadmapSummary = "roadmap",
        progressPercent = 25,
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

    private fun operation(command: CrossDatabaseStageCommand): CrossDatabaseOperationEntity {
        val receipt = receipt(command, 1)
        return CrossDatabaseOperationEntity(
            operationId = command.operationId,
            ownerType = command.ownerType,
            operationType = command.operationType,
            operationVersion = command.operationVersion,
            instanceId = command.instanceId,
            writerBodyId = command.writerBodyId,
            writerEpoch = command.writerEpoch,
            subjectId = command.subjectId,
            parentOperationId = command.parentOperationId,
            childPhase = command.childPhase,
            payloadSchema = command.payloadSchema,
            payloadJson = command.payloadJson,
            payloadDigest = command.payloadDigest,
            eventId = command.eventId,
            eventType = command.eventType,
            eventBody = command.eventBody,
            evidenceSchema = command.evidenceSchema,
            evidenceJson = command.evidenceJson,
            evidenceDigest = command.evidenceDigest,
            status = CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT,
            attemptCount = 0,
            lastErrorCode = null,
            canonicalEventHash = receipt.eventHash,
            canonicalSequence = receipt.sequence,
            canonicalProvenanceDigest = receipt.provenanceDigest,
            localResultSchema = null,
            localResultJson = null,
            localResultDigest = null,
            occurredAtMillis = 1_000,
            createdAtMillis = 1_000,
            updatedAtMillis = 1_000,
            committedAtMillis = null
        )
    }

    private fun receipt(command: CrossDatabaseStageCommand, sequence: Long) =
        CrossDatabaseCanonicalReceipt(
            eventId = command.eventId,
            eventHash = "evsha256:" + "1".repeat(64),
            sequence = sequence,
            provenanceDigest = "sha256:" + "2".repeat(64),
            reusedExistingEvent = true
        )

    private fun assertFailure(expectedCode: String, error: Throwable?) {
        val failure = error as CrossDatabaseProtocolFailure
        assertEquals(expectedCode, failure.stableCode)
        assertTrue(failure.permanent)
    }

    private class FakeStore(
        val vault: ProjectVaultEntity,
        var agent: AgentInstanceEntity? = null,
        var task: DelegatedTaskEntity? = null
    ) : AgentLifecycleFinalizerStore {
        var replacement: AgentInstanceEntity? = null
        var agentInsertCalls = 0
        var taskInsertCalls = 0
        var agentUpdateCalls = 0
        var taskUpdateCalls = 0
        var refreshCalls = 0

        override suspend fun loadProjectVault(vaultId: String): ProjectVaultEntity? =
            vault.takeIf { it.vaultId == vaultId }

        override suspend fun loadAgentInstance(agentInstanceId: String): AgentInstanceEntity? {
            return when (agentInstanceId) {
                agent?.agentInstanceId -> agent
                replacement?.agentInstanceId -> replacement
                else -> null
            }
        }

        override suspend fun loadDelegatedTask(taskId: String): DelegatedTaskEntity? =
            task?.takeIf { it.taskId == taskId }

        override suspend fun insertAgentInstance(agent: AgentInstanceEntity) {
            agentInsertCalls += 1
            if (this.agent == null) this.agent = agent else replacement = agent
        }

        override suspend fun insertDelegatedTask(task: DelegatedTaskEntity) {
            taskInsertCalls += 1
            this.task = task
        }

        override suspend fun updateAgentInstanceLifecycle(
            agentInstanceId: String,
            status: String,
            qualityScore: Int,
            errorCount: Int,
            currentTaskId: String?,
            lastHeartbeatAtMillis: Long?,
            updatedAtMillis: Long,
            retiredAtMillis: Long?,
            retireReason: String?
        ): Int {
            val current = agent ?: return 0
            if (current.agentInstanceId != agentInstanceId) return 0
            agentUpdateCalls += 1
            agent = current.copy(
                status = status,
                qualityScore = qualityScore,
                errorCount = errorCount,
                currentTaskId = currentTaskId,
                lastHeartbeatAtMillis = lastHeartbeatAtMillis,
                updatedAtMillis = updatedAtMillis,
                retiredAtMillis = retiredAtMillis,
                retireReason = retireReason
            )
            return 1
        }

        override suspend fun updateDelegatedTaskResult(
            taskId: String,
            status: String,
            resultSummary: String,
            updatedAtMillis: Long,
            completedAtMillis: Long?
        ): Int {
            val current = task ?: return 0
            if (current.taskId != taskId) return 0
            taskUpdateCalls += 1
            task = current.copy(
                status = status,
                resultSummary = resultSummary,
                updatedAtMillis = updatedAtMillis,
                completedAtMillis = completedAtMillis
            )
            return 1
        }

        override suspend fun refreshProjectVaultActiveAgentCount(
            vaultId: String,
            updatedAtMillis: Long
        ): Int {
            refreshCalls += 1
            return if (vaultId == vault.vaultId) 1 else 0
        }
    }
}
