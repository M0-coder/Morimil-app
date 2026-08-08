package com.morimil.app.data.repository

import com.morimil.app.core.orchestration.AgentCapabilityPolicy
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentityRepository
import com.morimil.app.data.local.AgentInstanceEntity
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
        val existingAgents = dao.loadAgentInstancesForVault(vaultId)
        existingAgents.firstOrNull { existing ->
            existing.status != STATUS_RETIRED &&
                existing.status != STATUS_QUARANTINED &&
                existing.templateAgentId == cleanTemplate &&
                existing.briefing == cleanBriefing &&
                hasCommittedOperation(existing.agentInstanceId, AgentLifecycleProtocolTypes.CREATE) { payload ->
                    payload.optString("agent_instance_id") == existing.agentInstanceId &&
                        payload.optString("vault_id") == vaultId
                }
        }?.let { existing -> return@withVaultLock existing.agentInstanceId }

        val ordinal = existingAgents.count { it.templateAgentId == cleanTemplate } + 1
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
        agent.currentTaskId?.let { currentTaskId ->
            val currentTask = dao.loadDelegatedTask(currentTaskId)
            if (
                currentTask != null &&
                taskMatchesPlan(currentTask, agent, vault, cleanGoal, plan) &&
                hasCommittedOperation(currentTaskId, AgentLifecycleProtocolTypes.ASSIGN)
            ) {
                return@withAgentLock currentTaskId
            }
        }
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
        if (task.assignedAgentId != agent.agentInstanceId) return@withAgentLock false
        val cleanSummary = AgentLifecycleOperationFactory.normalizeSummary(summary)
        if (
            task.status == STATUS_AWAITING_REVIEW &&
            task.resultSummary == cleanSummary &&
            hasCommittedOperation(agentInstanceId, AgentLifecycleProtocolTypes.SUBMIT_RESULT) { payload ->
                payload.optString("task_id") == task.taskId &&
                    payload.optString("result_summary") == cleanSummary
            }
        ) {
            return@withAgentLock true
        }
        if (
            task.status != AgentCapabilityPolicy.STATUS_APPROVED ||
            task.approvalId == null
        ) {
            return@withAgentLock false
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
        val cleanStatus = AgentLifecycleOperationFactory.normalizeReviewStatus(status)
        val cleanScore = qualityScore.coerceIn(0, 100)
        val cleanNote = AgentLifecycleOperationFactory.normalizeNote(note)
        if (
            agent.status == cleanStatus &&
            agent.qualityScore == cleanScore &&
            hasCommittedOperation(agentInstanceId, AgentLifecycleProtocolTypes.EVALUATE) { payload ->
                payload.optString("status") == cleanStatus &&
                    payload.optInt("quality_score", -1) == cleanScore &&
                    payload.optString("note") == cleanNote
            }
        ) {
            return@withAgentLock true
        }
        val command = AgentLifecycleOperationFactory.evaluate(
            identity = AgentLifecycleOperationFactory.identityOf(identity),
            agent = agent,
            status = cleanStatus,
            qualityScore = cleanScore,
            note = cleanNote
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
            return@withAgentLock agent.retireReason == cleanReason &&
                hasCommittedOperation(agentInstanceId, AgentLifecycleProtocolTypes.RETIRE) { payload ->
                    payload.optString("reason") == cleanReason
                }
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
            return@withAgentLock agent.retireReason == cleanReason &&
                hasCommittedOperation(agentInstanceId, AgentLifecycleProtocolTypes.QUARANTINE) { payload ->
                    payload.optString("reason") == cleanReason
                }
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
        if (agent.status == STATUS_PROMOTED) {
            return@withAgentLock agent.retireReason == cleanReason &&
                hasCommittedOperation(agentInstanceId, AgentLifecycleProtocolTypes.PROMOTE) { payload ->
                    payload.optString("reason") == cleanReason
                }
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

    private suspend fun taskMatchesPlan(
        task: DelegatedTaskEntity,
        agent: AgentInstanceEntity,
        vault: ProjectVaultEntity,
        cleanGoal: String,
        plan: com.morimil.app.core.orchestration.DelegationPlan
    ): Boolean {
        val expectedContext =
            "vault=${vault.displayName}; vault_id=${vault.vaultId}; " +
                "template_agent=${agent.templateAgentId}; ${plan.contextSummary}"
        val expectedBlocked = AgentCapabilityPolicy.isImmuneBlocked(plan.immuneDecision)
        val expectedStatus = if (expectedBlocked) {
            AgentCapabilityPolicy.STATUS_REJECTED
        } else {
            AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL
        }
        return task.assignedAgentId == agent.agentInstanceId &&
            task.goal == cleanGoal &&
            task.targetDeviceId == plan.targetDeviceId &&
            task.contextSummary == expectedContext &&
            task.allowedActionsJson == AgentCapabilityPolicy.encodeJson(plan.allowedActions) &&
            task.allowedTransportsJson == AgentCapabilityPolicy.encodeJson(plan.allowedTransports) &&
            task.approvalRequired &&
            task.status == expectedStatus &&
            task.riskLevel == plan.riskLevel
    }

    private suspend fun hasCommittedOperation(
        subjectId: String,
        operationType: String,
        payloadMatches: (JSONObject) -> Boolean = { true }
    ): Boolean {
        return operationDao.loadAnyForOwnerSubjectAndOperationType(
            ownerType = AgentLifecycleProtocolTypes.OWNER_TYPE,
            subjectId = subjectId,
            operationType = operationType
        ).any { operation ->
            operation.status == CrossDatabaseOperationStatus.COMMITTED &&
                runCatching { JSONObject(operation.payloadJson) }
                    .getOrNull()
                    ?.let(payloadMatches) == true
        }
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
