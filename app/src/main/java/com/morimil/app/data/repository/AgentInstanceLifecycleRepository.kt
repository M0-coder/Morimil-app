package com.morimil.app.data.repository

import com.morimil.app.core.orchestration.AgentCapabilityPolicy
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentityRepository
import com.morimil.app.data.local.AgentInstanceEntity
import com.morimil.app.data.local.CrossDatabaseOperationEntity
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.DelegatedTaskEntity
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.ProjectVaultEntity
import java.text.Normalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class AgentInstanceLifecycleRepository internal constructor(
    organDatabase: MemoryOrganDatabase,
    private val identityRepository: GenesisUltraRuntimeIdentityRepository,
    private val protocol: CrossDatabaseOperationCoordinator
) {
    private val dao = organDatabase.memoryOrganDao()
    private val operationDao = organDatabase.crossDatabaseOperationDao()

    val agentInstances: Flow<List<AgentInstanceEntity>> = dao.observeAgentInstances()

    suspend fun createAgentForVault(
        vaultId: String,
        templateAgentId: String = AgentCapabilityPolicy.AGENT_FILE_AUDIT,
        briefing: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): String = withVaultLock(vaultId) {
        require(nowMillis >= 0L) { "agent_lifecycle_clock_invalid" }
        val identity = requireIdentity()
        recoverBeforeMutation(identity)
        val vault = requireVault(vaultId)
        val cleanTemplate = normalizeTemplate(templateAgentId)
        val cleanBriefing = briefing?.let(AgentLifecycleOperationFactory::normalizeBriefing)
            ?.takeUnless(String::isBlank)
            ?: AgentLifecycleOperationFactory.normalizeBriefing(
                "Trabajador temporal creado para ${vault.displayName}. " +
                    "Debe operar solo dentro de esta boveda, reportar resultados y esperar " +
                    "aprobacion humana antes de ejecutar cambios reales."
            )
        val ordinal = dao.loadAgentInstancesForVault(vaultId)
            .count { it.templateAgentId == cleanTemplate } + 1
        val command = AgentLifecycleOperationFactory.create(
            identity = AgentLifecycleOperationFactory.identityOf(identity),
            vault = vault,
            templateAgentId = cleanTemplate,
            briefing = cleanBriefing,
            ordinal = ordinal
        )
        val committed = protocol.execute(identity, command)
        require(committed.status == CrossDatabaseOperationStatus.COMMITTED) {
            "agent_lifecycle_create_not_committed"
        }
        committed.subjectId
    }

    suspend fun assignTaskToAgent(
        agentInstanceId: String,
        goal: String,
        nowMillis: Long = System.currentTimeMillis()
    ): String = withAgentLock(agentInstanceId) {
        require(nowMillis >= 0L) { "agent_lifecycle_clock_invalid" }
        val identity = requireIdentity()
        recoverBeforeMutation(identity)
        val agent = requireMutableAgent(agentInstanceId)
        val vault = requireVault(agent.projectVaultId)
        val cleanGoal = AgentLifecycleOperationFactory.normalizeGoal(goal)
        val plan = AgentCapabilityPolicy.planDelegation(
            cleanGoal,
            agent.templateAgentId,
            targetDeviceId = null
        )
        val command = AgentLifecycleOperationFactory.assign(
            identity = AgentLifecycleOperationFactory.identityOf(identity),
            vault = vault,
            agent = agent,
            goal = cleanGoal,
            plan = plan
        )
        val committed = protocol.execute(identity, command)
        require(committed.status == CrossDatabaseOperationStatus.COMMITTED) {
            "agent_lifecycle_assign_not_committed"
        }
        committed.subjectId
    }

    suspend fun submitAgentResult(
        agentInstanceId: String,
        summary: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = withAgentLock(agentInstanceId) {
        require(nowMillis >= 0L) { "agent_lifecycle_clock_invalid" }
        val identity = identityRepository.readCommittedIdentity() ?: return@withAgentLock false
        recoverBeforeMutation(identity)
        val agent = dao.loadAgentInstance(agentInstanceId) ?: return@withAgentLock false
        if (agent.status == STATUS_RETIRED || agent.status == STATUS_QUARANTINED) {
            return@withAgentLock false
        }
        val taskId = agent.currentTaskId ?: return@withAgentLock false
        val task = dao.loadDelegatedTask(taskId) ?: return@withAgentLock false
        if (
            task.assignedAgentId != agent.agentInstanceId ||
            task.status != AgentCapabilityPolicy.STATUS_APPROVED ||
            task.approvalId == null
        ) {
            return@withAgentLock false
        }
        val cleanSummary = AgentLifecycleOperationFactory.normalizeSummary(summary)
        if (
            task.resultSummary == cleanSummary &&
            task.status == STATUS_AWAITING_REVIEW
        ) {
            return@withAgentLock loadSingleOperation(
                agentInstanceId,
                AgentLifecycleProtocolTypes.SUBMIT_RESULT
            )?.status == CrossDatabaseOperationStatus.COMMITTED
        }
        val command = AgentLifecycleOperationFactory.submitResult(
            identity = AgentLifecycleOperationFactory.identityOf(identity),
            agent = agent,
            task = task,
            summary = cleanSummary
        )
        protocol.execute(identity, command).status == CrossDatabaseOperationStatus.COMMITTED
    }

    suspend fun evaluateAgent(
        agentInstanceId: String,
        status: String,
        qualityScore: Int,
        note: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = withAgentLock(agentInstanceId) {
        require(nowMillis >= 0L) { "agent_lifecycle_clock_invalid" }
        val identity = identityRepository.readCommittedIdentity() ?: return@withAgentLock false
        recoverBeforeMutation(identity)
        val agent = dao.loadAgentInstance(agentInstanceId) ?: return@withAgentLock false
        if (agent.status == STATUS_RETIRED || agent.status == STATUS_QUARANTINED) {
            return@withAgentLock false
        }
        val command = AgentLifecycleOperationFactory.evaluate(
            identity = AgentLifecycleOperationFactory.identityOf(identity),
            agent = agent,
            status = status,
            qualityScore = qualityScore,
            note = note
        )
        protocol.execute(identity, command).status == CrossDatabaseOperationStatus.COMMITTED
    }

    suspend fun retireAgent(
        agentInstanceId: String,
        reason: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = withAgentLock(agentInstanceId) {
        require(nowMillis >= 0L) { "agent_lifecycle_clock_invalid" }
        val identity = identityRepository.readCommittedIdentity() ?: return@withAgentLock false
        recoverBeforeMutation(identity)
        val agent = dao.loadAgentInstance(agentInstanceId) ?: return@withAgentLock false
        val cleanReason = AgentLifecycleOperationFactory.normalizeReason(reason)
        if (agent.status == STATUS_RETIRED) {
            if (agent.retireReason != cleanReason) return@withAgentLock false
            return@withAgentLock loadSingleOperation(
                agentInstanceId,
                AgentLifecycleProtocolTypes.RETIRE
            )?.status == CrossDatabaseOperationStatus.COMMITTED
        }
        if (agent.status == STATUS_QUARANTINED) return@withAgentLock false
        val command = AgentLifecycleOperationFactory.retire(
            AgentLifecycleOperationFactory.identityOf(identity),
            agent,
            cleanReason
        )
        protocol.execute(identity, command).status == CrossDatabaseOperationStatus.COMMITTED
    }

    suspend fun quarantineAgent(
        agentInstanceId: String,
        reason: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = withAgentLock(agentInstanceId) {
        require(nowMillis >= 0L) { "agent_lifecycle_clock_invalid" }
        val identity = identityRepository.readCommittedIdentity() ?: return@withAgentLock false
        recoverBeforeMutation(identity)
        val agent = dao.loadAgentInstance(agentInstanceId) ?: return@withAgentLock false
        val cleanReason = AgentLifecycleOperationFactory.normalizeReason(reason)
        if (agent.status == STATUS_QUARANTINED) {
            if (agent.retireReason != cleanReason) return@withAgentLock false
            return@withAgentLock loadSingleOperation(
                agentInstanceId,
                AgentLifecycleProtocolTypes.QUARANTINE
            )?.status == CrossDatabaseOperationStatus.COMMITTED
        }
        if (agent.status == STATUS_RETIRED) return@withAgentLock false
        val vault = requireVault(agent.projectVaultId)
        val command = AgentLifecycleOperationFactory.quarantine(
            identity = AgentLifecycleOperationFactory.identityOf(identity),
            vault = vault,
            agent = agent,
            reason = cleanReason
        )
        protocol.execute(identity, command).status == CrossDatabaseOperationStatus.COMMITTED
    }

    suspend fun promoteAgent(
        agentInstanceId: String,
        reason: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = withAgentLock(agentInstanceId) {
        require(nowMillis >= 0L) { "agent_lifecycle_clock_invalid" }
        val identity = identityRepository.readCommittedIdentity() ?: return@withAgentLock false
        recoverBeforeMutation(identity)
        val agent = dao.loadAgentInstance(agentInstanceId) ?: return@withAgentLock false
        val cleanReason = AgentLifecycleOperationFactory.normalizeReason(reason)
        if (agent.status == STATUS_PROMOTED && agent.retireReason == cleanReason) {
            return@withAgentLock loadSingleOperation(
                agentInstanceId,
                AgentLifecycleProtocolTypes.PROMOTE
            )?.status == CrossDatabaseOperationStatus.COMMITTED
        }
        if (agent.status == STATUS_RETIRED || agent.status == STATUS_QUARANTINED) {
            return@withAgentLock false
        }
        val command = AgentLifecycleOperationFactory.promote(
            AgentLifecycleOperationFactory.identityOf(identity),
            agent,
            cleanReason
        )
        protocol.execute(identity, command).status == CrossDatabaseOperationStatus.COMMITTED
    }

    private suspend fun requireIdentity(): GenesisUltraRuntimeIdentity {
        return requireNotNull(identityRepository.readCommittedIdentity()) {
            "Genesis Ultra committed identity is required before agent lifecycle mutation."
        }
    }

    private suspend fun recoverBeforeMutation(identity: GenesisUltraRuntimeIdentity) {
        val recovery = protocol.recoverBeforeMutation(
            identity = identity,
            ownerType = AgentLifecycleProtocolTypes.OWNER_TYPE,
            limit = RECOVERY_LIMIT
        )
        if (recovery.blockedCount > 0) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
        }
        if (recovery.retryableFailureCount > 0) {
            throw CrossDatabaseProtocolErrors.retryable(
                CrossDatabaseProtocolErrors.CANONICAL_APPEND_INTERRUPTED
            )
        }
    }

    private suspend fun requireVault(vaultId: String): ProjectVaultEntity {
        return dao.loadProjectVault(vaultId) ?: error("Project vault not found: $vaultId")
    }

    private suspend fun requireMutableAgent(agentInstanceId: String): AgentInstanceEntity {
        val agent = dao.loadAgentInstance(agentInstanceId)
            ?: error("Agent instance not found: $agentInstanceId")
        require(agent.status != STATUS_RETIRED && agent.status != STATUS_QUARANTINED) {
            "agent_lifecycle_terminal_agent"
        }
        return agent
    }

    private suspend fun loadSingleOperation(
        subjectId: String,
        operationType: String
    ): CrossDatabaseOperationEntity? {
        val operations = operationDao.loadAnyForOwnerSubjectAndOperationType(
            ownerType = AgentLifecycleProtocolTypes.OWNER_TYPE,
            subjectId = subjectId,
            operationType = operationType
        )
        if (operations.size > 1) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
        }
        return operations.singleOrNull()
    }

    private fun normalizeTemplate(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFC)
            .trim()
            .ifBlank { AgentCapabilityPolicy.AGENT_FILE_AUDIT }
    }

    companion object {
        const val STATUS_WORKING = "working"
        const val STATUS_THINKING = "thinking"
        const val STATUS_AWAITING_REVIEW = "awaiting_review"
        const val STATUS_QUARANTINED = "error_quarantined"
        const val STATUS_RETIRED = "retired"
        const val STATUS_PROMOTED = "promoted"

        private const val RECOVERY_LIMIT = 64
        private const val MUTEX_STRIPES = 64
        private val AGENT_MUTEXES = Array(MUTEX_STRIPES) { Mutex() }
        private val VAULT_MUTEXES = Array(MUTEX_STRIPES) { Mutex() }

        private suspend fun <T> withAgentLock(
            agentInstanceId: String,
            block: suspend () -> T
        ): T {
            val index = (agentInstanceId.hashCode() and Int.MAX_VALUE) % MUTEX_STRIPES
            return AGENT_MUTEXES[index].withLock { block() }
        }

        private suspend fun <T> withVaultLock(
            vaultId: String,
            block: suspend () -> T
        ): T {
            val index = (vaultId.hashCode() and Int.MAX_VALUE) % MUTEX_STRIPES
            return VAULT_MUTEXES[index].withLock { block() }
        }
    }
}
