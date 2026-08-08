package com.morimil.app.data.repository

import com.morimil.app.core.orchestration.AgentCapabilityPolicy
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentityRepository
import com.morimil.app.data.local.AgentProfileEntity
import com.morimil.app.data.local.CrossDatabaseOperationEntity
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.DelegatedTaskEntity
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.OrchestratorDeviceEntity
import java.text.Normalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray

class AgentOrchestrationRepository internal constructor(
    organDatabase: MemoryOrganDatabase,
    private val identityRepository: GenesisUltraRuntimeIdentityRepository,
    private val protocol: CrossDatabaseOperationCoordinator
) {
    private val dao = organDatabase.memoryOrganDao()
    private val operationDao = organDatabase.crossDatabaseOperationDao()

    val orchestratorDevices: Flow<List<OrchestratorDeviceEntity>> = dao.observeOrchestratorDevices()
    val agentProfiles: Flow<List<AgentProfileEntity>> = dao.observeAgentProfiles()
    val delegatedTasks: Flow<List<DelegatedTaskEntity>> = dao.observeDelegatedTasks()

    suspend fun seedDefaultOrchestrationIfNeeded(nowMillis: Long = System.currentTimeMillis()) {
        identityRepository.readCommittedIdentity() ?: return
        if (dao.countAgentProfiles() == 0) {
            dao.insertAgentProfiles(defaultAgents(nowMillis))
        }
        if (dao.countOrchestratorDevices() == 0) {
            dao.insertOrchestratorDevices(defaultDevices(nowMillis))
        }
    }

    suspend fun proposeDelegatedTask(
        goal: String,
        preferredAgentId: String? = null,
        targetDeviceId: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val identity = requireNotNull(identityRepository.readCommittedIdentity()) {
            "Genesis Ultra committed identity is required before orchestration can create durable tasks."
        }
        recoverBeforeMutation(identity)
        seedDefaultOrchestrationIfNeeded(nowMillis)

        val cleanGoal = OrchestrationOperationFactory.normalizeGoal(goal)
        val plan = AgentCapabilityPolicy.planDelegation(
            goal = cleanGoal,
            preferredAgentId = normalizeOptional(preferredAgentId),
            targetDeviceId = normalizeOptional(targetDeviceId)
        )
        val command = OrchestrationOperationFactory.propose(
            identity = OrchestrationOperationFactory.identityOf(identity),
            goal = cleanGoal,
            plan = plan
        )
        val committed = protocol.execute(identity, command)
        require(committed.status == CrossDatabaseOperationStatus.COMMITTED) {
            "orchestration_proposal_not_committed"
        }
        return committed.subjectId
    }

    suspend fun approveDelegatedTask(
        taskId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = withTaskDecisionLock(taskId) {
        require(nowMillis >= 0L) { "orchestration_decision_clock_invalid" }
        val identity = identityRepository.readCommittedIdentity() ?: return@withTaskDecisionLock false
        recoverBeforeMutation(identity)
        val task = dao.loadDelegatedTask(taskId) ?: return@withTaskDecisionLock false
        if (task.errorSummary?.startsWith(OrchestrationOperationFactory.IMMUNE_BLOCK_PREFIX) == true) {
            return@withTaskDecisionLock false
        }
        if (task.status == AgentCapabilityPolicy.STATUS_APPROVED && task.approvalId != null) {
            return@withTaskDecisionLock protocol.load(task.approvalId)?.status ==
                CrossDatabaseOperationStatus.COMMITTED
        }
        if (
            task.status != AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL ||
            task.approvalId != null
        ) {
            return@withTaskDecisionLock false
        }

        val command = OrchestrationOperationFactory.approve(
            identity = OrchestrationOperationFactory.identityOf(identity),
            task = task
        )
        protocol.execute(identity, command).status == CrossDatabaseOperationStatus.COMMITTED
    }

    suspend fun rejectDelegatedTask(
        taskId: String,
        reason: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = withTaskDecisionLock(taskId) {
        require(nowMillis >= 0L) { "orchestration_decision_clock_invalid" }
        val identity = identityRepository.readCommittedIdentity() ?: return@withTaskDecisionLock false
        recoverBeforeMutation(identity)
        val task = dao.loadDelegatedTask(taskId) ?: return@withTaskDecisionLock false
        val cleanReason = OrchestrationOperationFactory.normalizeReason(reason)

        if (task.status == AgentCapabilityPolicy.STATUS_REJECTED) {
            if (task.errorSummary?.startsWith(OrchestrationOperationFactory.IMMUNE_BLOCK_PREFIX) == true) {
                return@withTaskDecisionLock false
            }
            if (task.errorSummary != cleanReason) return@withTaskDecisionLock false
            return@withTaskDecisionLock loadSingleOperation(
                taskId,
                OrchestrationProtocolTypes.REJECT
            )?.status == CrossDatabaseOperationStatus.COMMITTED
        }
        if (
            task.status != AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL ||
            task.approvalId != null
        ) {
            return@withTaskDecisionLock false
        }

        val command = OrchestrationOperationFactory.reject(
            identity = OrchestrationOperationFactory.identityOf(identity),
            task = task,
            reason = cleanReason
        )
        protocol.execute(identity, command).status == CrossDatabaseOperationStatus.COMMITTED
    }

    private suspend fun recoverBeforeMutation(identity: GenesisUltraRuntimeIdentity) {
        val recovery = protocol.recoverBeforeMutation(
            identity = identity,
            ownerType = OrchestrationProtocolTypes.OWNER_TYPE,
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

    private suspend fun loadSingleOperation(
        taskId: String,
        operationType: String
    ): CrossDatabaseOperationEntity? {
        val operations = operationDao.loadAnyForOwnerSubjectAndOperationType(
            ownerType = OrchestrationProtocolTypes.OWNER_TYPE,
            subjectId = taskId,
            operationType = operationType
        )
        if (operations.size > 1) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
        }
        return operations.singleOrNull()
    }

    private fun normalizeOptional(value: String?): String? {
        return value?.let { raw -> Normalizer.normalize(raw, Normalizer.Form.NFC) }
    }

    companion object {
        private const val RECOVERY_LIMIT = 64
        private const val DECISION_MUTEX_STRIPES = 64
        private val DECISION_MUTEXES = Array(DECISION_MUTEX_STRIPES) { Mutex() }

        private suspend fun <T> withTaskDecisionLock(
            taskId: String,
            block: suspend () -> T
        ): T {
            val index = (taskId.hashCode() and Int.MAX_VALUE) % DECISION_MUTEX_STRIPES
            return DECISION_MUTEXES[index].withLock { block() }
        }

        private fun defaultAgents(nowMillis: Long): List<AgentProfileEntity> {
            return listOf(
                agent(AgentCapabilityPolicy.AGENT_GITHUB, "GitHub Agent", "github", "Lee repositorios, revisa ramas y propone diffs.", listOf("read_repository", "inspect_branch", "propose_diff"), "medium", nowMillis),
                agent(AgentCapabilityPolicy.AGENT_ANDROID_BUILD, "Android Build Agent", "android_build", "Corre tests/builds aprobados y resume fallos.", listOf("run_gradle_tests", "run_assemble_debug"), "medium", nowMillis),
                agent(AgentCapabilityPolicy.AGENT_FILE_AUDIT, "File Audit Agent", "file_audit", "Lee archivos permitidos y propone parches.", listOf("read_allowed_files", "propose_patch"), "medium", nowMillis),
                agent(AgentCapabilityPolicy.AGENT_RESEARCH, "Research Agent", "research", "Investiga fuentes externas y produce informes.", listOf("research_web", "summarize_sources"), "low", nowMillis),
                agent(AgentCapabilityPolicy.AGENT_DESIGN, "Design Agent", "design", "Propone mejoras visuales y revisa UI.", listOf("inspect_ui", "produce_design_notes"), "low", nowMillis),
                agent(AgentCapabilityPolicy.AGENT_SECURITY, "Security Agent", "security", "Audita permisos, riesgos y politica.", listOf("audit_permissions", "audit_risk"), "low", nowMillis),
                agent(AgentCapabilityPolicy.AGENT_PC_EXECUTOR, "PC Executor Agent", "pc_executor", "Prepara ejecucion en equipos autorizados; no ejecuta sin aprobacion.", listOf("prepare_command", "await_human_approval", "report_result"), "high", nowMillis)
            )
        }

        private fun agent(
            agentId: String,
            displayName: String,
            role: String,
            description: String,
            capabilities: List<String>,
            riskLevel: String,
            nowMillis: Long
        ): AgentProfileEntity {
            return AgentProfileEntity(
                agentId = agentId,
                displayName = displayName,
                role = role,
                description = description,
                capabilitySetJson = JSONArray(capabilities).toString(),
                allowedToolsetJson = JSONArray(capabilities).toString(),
                allowedTransportsJson = AgentCapabilityPolicy.encodeJson(
                    listOf(
                        AgentCapabilityPolicy.TRANSPORT_WIFI,
                        AgentCapabilityPolicy.TRANSPORT_BLUETOOTH,
                        AgentCapabilityPolicy.TRANSPORT_USB,
                        AgentCapabilityPolicy.TRANSPORT_INTERNET,
                        AgentCapabilityPolicy.TRANSPORT_MANUAL
                    )
                ),
                riskLevel = riskLevel,
                requiresHumanApproval = true,
                status = AgentCapabilityPolicy.STATUS_ACTIVE,
                createdAtMillis = nowMillis,
                updatedAtMillis = nowMillis
            )
        }

        private fun defaultDevices(nowMillis: Long): List<OrchestratorDeviceEntity> {
            return listOf(
                device("android_body", "Telefono Morimil", "android_phone", listOf(AgentCapabilityPolicy.TRANSPORT_WIFI, AgentCapabilityPolicy.TRANSPORT_BLUETOOTH, AgentCapabilityPolicy.TRANSPORT_MANUAL), "authorized", "paired_local", "low", nowMillis),
                device("personal_pc", "PC principal", "windows_pc", listOf(AgentCapabilityPolicy.TRANSPORT_WIFI, AgentCapabilityPolicy.TRANSPORT_USB, AgentCapabilityPolicy.TRANSPORT_INTERNET), "pending_authorization", "not_paired", "high", nowMillis),
                device("personal_laptop", "Laptop personal", "laptop", listOf(AgentCapabilityPolicy.TRANSPORT_WIFI, AgentCapabilityPolicy.TRANSPORT_BLUETOOTH, AgentCapabilityPolicy.TRANSPORT_INTERNET), "pending_authorization", "not_paired", "medium", nowMillis),
                device("personal_tablet", "Tablet personal", "tablet", listOf(AgentCapabilityPolicy.TRANSPORT_WIFI, AgentCapabilityPolicy.TRANSPORT_BLUETOOTH, AgentCapabilityPolicy.TRANSPORT_MANUAL), "pending_authorization", "not_paired", "medium", nowMillis)
            )
        }

        private fun device(
            deviceId: String,
            displayName: String,
            deviceType: String,
            transports: List<String>,
            authorizationStatus: String,
            pairingState: String,
            riskLevel: String,
            nowMillis: Long
        ): OrchestratorDeviceEntity {
            return OrchestratorDeviceEntity(
                deviceId = deviceId,
                displayName = displayName,
                deviceType = deviceType,
                ownershipScope = "user_owned",
                trustedOwner = "founder",
                allowedTransportsJson = AgentCapabilityPolicy.encodeJson(transports),
                authorizationStatus = authorizationStatus,
                authorizationRequired = authorizationStatus != "authorized",
                riskLevel = riskLevel,
                pairingState = pairingState,
                lastSeenAtMillis = if (authorizationStatus == "authorized") nowMillis else null,
                createdAtMillis = nowMillis,
                updatedAtMillis = nowMillis
            )
        }
    }
}
