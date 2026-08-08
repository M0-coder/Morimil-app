package com.morimil.app.data.repository

import com.morimil.app.core.identity.StableIdDigest
import com.morimil.app.core.orchestration.AgentCapabilityPolicy
import com.morimil.app.core.orchestration.DelegationPlan
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.local.AgentInstanceEntity
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.DelegatedTaskEntity
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.ProjectVaultEntity
import java.text.Normalizer
import org.json.JSONObject

internal data class AgentLifecycleProtocolIdentity(
    val instanceId: String,
    val writerBodyId: String,
    val writerEpoch: String
) {
    init {
        require(instanceId.isNotBlank() && instanceId != writerBodyId) {
            "agent_lifecycle_identity_invalid"
        }
        require(writerBodyId.isNotBlank() && writerEpoch.isNotBlank()) {
            "agent_lifecycle_writer_invalid"
        }
    }
}

internal object AgentLifecycleProtocolSchemas {
    const val AGENT_001_PAYLOAD = "morimil.agent_lifecycle.agent_001.payload.v1"
    const val AGENT_001_EVIDENCE = "morimil.agent_lifecycle.agent_001.evidence.v1"
    const val AGENT_001_LOCAL_RESULT = "morimil.agent_lifecycle.agent_001.local_result.v1"

    const val AGENT_002_PAYLOAD = "morimil.agent_lifecycle.agent_002.payload.v1"
    const val AGENT_002_EVIDENCE = "morimil.agent_lifecycle.agent_002.evidence.v1"
    const val AGENT_002_LOCAL_RESULT = "morimil.agent_lifecycle.agent_002.local_result.v1"

    const val AGENT_003_PAYLOAD = "morimil.agent_lifecycle.agent_003.payload.v1"
    const val AGENT_003_EVIDENCE = "morimil.agent_lifecycle.agent_003.evidence.v1"
    const val AGENT_003_LOCAL_RESULT = "morimil.agent_lifecycle.agent_003.local_result.v1"

    const val AGENT_004_PAYLOAD = "morimil.agent_lifecycle.agent_004.payload.v1"
    const val AGENT_004_EVIDENCE = "morimil.agent_lifecycle.agent_004.evidence.v1"
    const val AGENT_004_LOCAL_RESULT = "morimil.agent_lifecycle.agent_004.local_result.v1"

    const val AGENT_005_PAYLOAD = "morimil.agent_lifecycle.agent_005.payload.v1"
    const val AGENT_005_EVIDENCE = "morimil.agent_lifecycle.agent_005.evidence.v1"
    const val AGENT_005_LOCAL_RESULT = "morimil.agent_lifecycle.agent_005.local_result.v1"

    const val AGENT_006_PAYLOAD = "morimil.agent_lifecycle.agent_006.payload.v1"
    const val AGENT_006_EVIDENCE = "morimil.agent_lifecycle.agent_006.evidence.v1"
    const val AGENT_006_LOCAL_RESULT = "morimil.agent_lifecycle.agent_006.local_result.v1"

    const val PLANNED_AGENT = "morimil.agent_lifecycle.agent.plan.v1"
    const val PLANNED_TASK = "morimil.agent_lifecycle.task.plan.v1"
    const val EVENT_BODY = "morimil.agent_lifecycle.event_body.v1"
}

internal object AgentLifecycleOperationFactory {
    private const val CREATED_BY = "morimil_project_vault"
    const val IMMUNE_BLOCK_PREFIX = "immune_policy_blocked"

    fun identityOf(identity: GenesisUltraRuntimeIdentity): AgentLifecycleProtocolIdentity {
        return AgentLifecycleProtocolIdentity(
            instanceId = identity.instanceId,
            writerBodyId = identity.activeBody.bodyId,
            writerEpoch = identity.activeBody.keyEpochId
        )
    }

    fun create(
        identity: AgentLifecycleProtocolIdentity,
        vault: ProjectVaultEntity,
        templateAgentId: String,
        briefing: String,
        ordinal: Int
    ): CrossDatabaseStageCommand {
        require(ordinal >= 1) { "agent_lifecycle_ordinal_invalid" }
        val cleanTemplate = normalizeToken(templateAgentId).ifBlank {
            AgentCapabilityPolicy.AGENT_FILE_AUDIT
        }
        val cleanBriefing = normalizeBriefing(briefing)
        val agentInstanceId = deterministicAgentId(
            identity = identity,
            vaultId = vault.vaultId,
            templateAgentId = cleanTemplate,
            briefing = cleanBriefing,
            ordinal = ordinal
        )
        val plannedAgent = plannedAgentJson(
            agentInstanceId = agentInstanceId,
            vault = vault,
            templateAgentId = cleanTemplate,
            briefing = cleanBriefing
        )
        val plannedDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(plannedAgent)
        val vaultDigest = vaultSemanticDigest(vault)
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "agent_instance_id" to agentInstanceId,
                "ordinal" to ordinal,
                "planned_agent" to JSONObject(plannedAgent),
                "planned_agent_digest" to plannedDigest,
                "schema" to AgentLifecycleProtocolSchemas.AGENT_001_PAYLOAD,
                "vault_digest" to vaultDigest,
                "vault_id" to vault.vaultId
            )
        )
        return command(
            identity = identity,
            operationType = AgentLifecycleProtocolTypes.CREATE,
            eventType = AgentLifecycleProtocolTypes.CREATED_EVENT,
            subjectId = agentInstanceId,
            payloadSchema = AgentLifecycleProtocolSchemas.AGENT_001_PAYLOAD,
            payloadJson = payload,
            transition = AgentInstanceLifecycleRepository.STATUS_THINKING,
            evidenceSchema = AgentLifecycleProtocolSchemas.AGENT_001_EVIDENCE,
            evidenceValues = mapOf(
                "agent_instance_id" to agentInstanceId,
                "briefing_digest" to CrossDatabaseOperationIdentity.digestUtf8(cleanBriefing),
                "ownership_conferred" to false,
                "planned_agent_digest" to plannedDigest,
                "vault_digest" to vaultDigest,
                "vault_id" to vault.vaultId
            )
        )
    }

    fun assign(
        identity: AgentLifecycleProtocolIdentity,
        vault: ProjectVaultEntity,
        agent: AgentInstanceEntity,
        goal: String,
        plan: DelegationPlan
    ): CrossDatabaseStageCommand {
        val cleanGoal = normalizeGoal(goal)
        val immuneBlocked = AgentCapabilityPolicy.isImmuneBlocked(plan.immuneDecision)
        val taskId = deterministicTaskId(identity, agent, cleanGoal, plan)
        val initialStatus = if (immuneBlocked) {
            AgentCapabilityPolicy.STATUS_REJECTED
        } else {
            AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL
        }
        val initialError = if (immuneBlocked) immuneErrorSummary(plan) else null
        val plannedTask = plannedTaskJson(
            taskId = taskId,
            vault = vault,
            agent = agent,
            goal = cleanGoal,
            plan = plan,
            initialStatus = initialStatus,
            initialErrorSummary = initialError
        )
        val plannedDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(plannedTask)
        val expectedAgentDigest = agentSemanticDigest(agent)
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "agent_instance_id" to agent.agentInstanceId,
                "expected_agent_digest" to expectedAgentDigest,
                "immune_blocked" to immuneBlocked,
                "immune_decision" to plan.immuneDecision,
                "immune_matched_signals" to plan.immuneMatchedSignals,
                "immune_reasons" to plan.immuneReasons,
                "planned_task" to JSONObject(plannedTask),
                "planned_task_digest" to plannedDigest,
                "schema" to AgentLifecycleProtocolSchemas.AGENT_002_PAYLOAD,
                "task_id" to taskId,
                "vault_digest" to vaultSemanticDigest(vault),
                "vault_id" to vault.vaultId
            )
        )
        return command(
            identity = identity,
            operationType = AgentLifecycleProtocolTypes.ASSIGN,
            eventType = AgentLifecycleProtocolTypes.ASSIGNED_EVENT,
            subjectId = taskId,
            payloadSchema = AgentLifecycleProtocolSchemas.AGENT_002_PAYLOAD,
            payloadJson = payload,
            transition = initialStatus,
            evidenceSchema = AgentLifecycleProtocolSchemas.AGENT_002_EVIDENCE,
            evidenceValues = mapOf(
                "agent_instance_id" to agent.agentInstanceId,
                "expected_agent_digest" to expectedAgentDigest,
                "immune_blocked" to immuneBlocked,
                "ownership_conferred" to false,
                "planned_task_digest" to plannedDigest,
                "task_id" to taskId,
                "vault_id" to vault.vaultId
            )
        )
    }

    fun submitResult(
        identity: AgentLifecycleProtocolIdentity,
        agent: AgentInstanceEntity,
        task: DelegatedTaskEntity,
        summary: String
    ): CrossDatabaseStageCommand {
        val cleanSummary = normalizeSummary(summary)
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "agent_instance_id" to agent.agentInstanceId,
                "expected_agent_digest" to agentSemanticDigest(agent),
                "expected_task_digest" to taskStateDigest(task),
                "result_summary" to cleanSummary,
                "result_summary_digest" to CrossDatabaseOperationIdentity.digestUtf8(cleanSummary),
                "schema" to AgentLifecycleProtocolSchemas.AGENT_003_PAYLOAD,
                "task_id" to task.taskId
            )
        )
        return command(
            identity = identity,
            operationType = AgentLifecycleProtocolTypes.SUBMIT_RESULT,
            eventType = AgentLifecycleProtocolTypes.RESULT_EVENT,
            subjectId = agent.agentInstanceId,
            payloadSchema = AgentLifecycleProtocolSchemas.AGENT_003_PAYLOAD,
            payloadJson = payload,
            transition = AgentInstanceLifecycleRepository.STATUS_AWAITING_REVIEW,
            evidenceSchema = AgentLifecycleProtocolSchemas.AGENT_003_EVIDENCE,
            evidenceValues = mapOf(
                "agent_instance_id" to agent.agentInstanceId,
                "ownership_conferred" to false,
                "result_summary_digest" to CrossDatabaseOperationIdentity.digestUtf8(cleanSummary),
                "task_id" to task.taskId
            )
        )
    }

    fun evaluate(
        identity: AgentLifecycleProtocolIdentity,
        agent: AgentInstanceEntity,
        status: String,
        qualityScore: Int,
        note: String
    ): CrossDatabaseStageCommand {
        val cleanStatus = normalizeReviewStatus(status)
        val cleanScore = qualityScore.coerceIn(0, 100)
        val cleanNote = normalizeNote(note)
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "agent_instance_id" to agent.agentInstanceId,
                "expected_agent_digest" to agentSemanticDigest(agent),
                "note" to cleanNote,
                "note_digest" to CrossDatabaseOperationIdentity.digestUtf8(cleanNote),
                "quality_score" to cleanScore,
                "schema" to AgentLifecycleProtocolSchemas.AGENT_004_PAYLOAD,
                "status" to cleanStatus
            )
        )
        return command(
            identity = identity,
            operationType = AgentLifecycleProtocolTypes.EVALUATE,
            eventType = AgentLifecycleProtocolTypes.EVALUATED_EVENT,
            subjectId = agent.agentInstanceId,
            payloadSchema = AgentLifecycleProtocolSchemas.AGENT_004_PAYLOAD,
            payloadJson = payload,
            transition = cleanStatus,
            evidenceSchema = AgentLifecycleProtocolSchemas.AGENT_004_EVIDENCE,
            evidenceValues = mapOf(
                "agent_instance_id" to agent.agentInstanceId,
                "note_digest" to CrossDatabaseOperationIdentity.digestUtf8(cleanNote),
                "ownership_conferred" to false,
                "quality_score" to cleanScore,
                "status" to cleanStatus
            )
        )
    }

    fun retire(
        identity: AgentLifecycleProtocolIdentity,
        agent: AgentInstanceEntity,
        reason: String
    ): CrossDatabaseStageCommand = terminalDecision(
        identity = identity,
        agent = agent,
        reason = reason,
        operationType = AgentLifecycleProtocolTypes.RETIRE,
        eventType = AgentLifecycleProtocolTypes.RETIRED_EVENT,
        decision = "retire",
        transition = AgentInstanceLifecycleRepository.STATUS_RETIRED
    )

    fun promote(
        identity: AgentLifecycleProtocolIdentity,
        agent: AgentInstanceEntity,
        reason: String
    ): CrossDatabaseStageCommand = terminalDecision(
        identity = identity,
        agent = agent,
        reason = reason,
        operationType = AgentLifecycleProtocolTypes.PROMOTE,
        eventType = AgentLifecycleProtocolTypes.PROMOTED_EVENT,
        decision = "promote",
        transition = AgentInstanceLifecycleRepository.STATUS_PROMOTED
    )

    fun quarantine(
        identity: AgentLifecycleProtocolIdentity,
        vault: ProjectVaultEntity,
        agent: AgentInstanceEntity,
        reason: String
    ): CrossDatabaseStageCommand {
        val cleanReason = normalizeReason(reason)
        val reasonDigest = CrossDatabaseOperationIdentity.digestUtf8(cleanReason)
        val replacementBriefing = normalizeBriefing(
            "Reemplazo especializado tras cuarentena de ${agent.displayName}. " +
                "No repetir este fallo: ${cleanReason.take(180)}. " +
                "Mantenerse en memoria de trabajo y reportar solo resultados utiles a Morimil."
        )
        val replacementId = deterministicReplacementId(
            identity = identity,
            failedAgent = agent,
            reasonDigest = reasonDigest
        )
        val replacementPlan = plannedAgentJson(
            agentInstanceId = replacementId,
            vault = vault,
            templateAgentId = agent.templateAgentId,
            briefing = replacementBriefing
        )
        val replacementDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(replacementPlan)
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "agent_instance_id" to agent.agentInstanceId,
                "decision" to "quarantine",
                "expected_agent_digest" to agentSemanticDigest(agent),
                "reason" to cleanReason,
                "reason_digest" to reasonDigest,
                "replacement_agent" to JSONObject(replacementPlan),
                "replacement_agent_digest" to replacementDigest,
                "replacement_agent_id" to replacementId,
                "schema" to AgentLifecycleProtocolSchemas.AGENT_006_PAYLOAD,
                "vault_digest" to vaultSemanticDigest(vault),
                "vault_id" to vault.vaultId
            )
        )
        return command(
            identity = identity,
            operationType = AgentLifecycleProtocolTypes.QUARANTINE,
            eventType = AgentLifecycleProtocolTypes.QUARANTINED_EVENT,
            subjectId = agent.agentInstanceId,
            payloadSchema = AgentLifecycleProtocolSchemas.AGENT_006_PAYLOAD,
            payloadJson = payload,
            transition = AgentInstanceLifecycleRepository.STATUS_QUARANTINED,
            evidenceSchema = AgentLifecycleProtocolSchemas.AGENT_006_EVIDENCE,
            evidenceValues = mapOf(
                "agent_instance_id" to agent.agentInstanceId,
                "ownership_conferred" to false,
                "reason_digest" to reasonDigest,
                "replacement_agent_digest" to replacementDigest,
                "replacement_agent_id" to replacementId,
                "vault_id" to vault.vaultId
            )
        )
    }

    fun agentSemanticDigest(agent: AgentInstanceEntity): String {
        return CrossDatabaseOperationIdentity.digestCanonicalJson(
            CrossDatabaseOperationIdentity.canonicalJson(agentSemanticValues(agent))
        )
    }

    fun taskStateDigest(task: DelegatedTaskEntity): String {
        return CrossDatabaseOperationIdentity.digestCanonicalJson(
            CrossDatabaseOperationIdentity.canonicalJson(
                mapOf(
                    "allowed_actions_json" to task.allowedActionsJson,
                    "allowed_transports_json" to task.allowedTransportsJson,
                    "approval_id" to task.approvalId,
                    "approval_required" to task.approvalRequired,
                    "assigned_agent_id" to task.assignedAgentId,
                    "context_summary" to task.contextSummary,
                    "created_by" to task.createdBy,
                    "error_summary" to task.errorSummary,
                    "goal" to task.goal,
                    "input_refs_json" to task.inputRefsJson,
                    "result_summary" to task.resultSummary,
                    "risk_level" to task.riskLevel,
                    "status" to task.status,
                    "target_device_id" to task.targetDeviceId,
                    "task_id" to task.taskId
                )
            )
        )
    }

    fun vaultSemanticDigest(vault: ProjectVaultEntity): String {
        return CrossDatabaseOperationIdentity.digestCanonicalJson(
            CrossDatabaseOperationIdentity.canonicalJson(
                mapOf(
                    "company_name" to vault.companyName,
                    "display_name" to vault.displayName,
                    "health_status" to vault.healthStatus,
                    "mission" to vault.mission,
                    "progress_percent" to vault.progressPercent,
                    "project_type" to vault.projectType,
                    "roadmap_summary" to vault.roadmapSummary,
                    "source_context" to vault.sourceContext,
                    "status" to vault.status,
                    "vault_id" to vault.vaultId
                )
            )
        )
    }

    fun normalizeGoal(value: String): String = normalizeText(value)
        .ifBlank { "Preparar avance verificable" }

    fun normalizeSummary(value: String): String = normalizeText(value)
        .ifBlank { "Resultado pendiente de revision humana." }

    fun normalizeNote(value: String): String = normalizeText(value).take(480)

    fun normalizeReason(value: String): String = normalizeText(value).take(240)

    fun normalizeBriefing(value: String): String = normalizeText(value).take(1200)

    fun normalizeReviewStatus(status: String): String {
        return when (normalizeToken(status)) {
            AgentInstanceLifecycleRepository.STATUS_WORKING ->
                AgentInstanceLifecycleRepository.STATUS_WORKING
            AgentInstanceLifecycleRepository.STATUS_THINKING ->
                AgentInstanceLifecycleRepository.STATUS_THINKING
            AgentInstanceLifecycleRepository.STATUS_AWAITING_REVIEW ->
                AgentInstanceLifecycleRepository.STATUS_AWAITING_REVIEW
            else -> AgentInstanceLifecycleRepository.STATUS_AWAITING_REVIEW
        }
    }

    private fun terminalDecision(
        identity: AgentLifecycleProtocolIdentity,
        agent: AgentInstanceEntity,
        reason: String,
        operationType: String,
        eventType: String,
        decision: String,
        transition: String
    ): CrossDatabaseStageCommand {
        val cleanReason = normalizeReason(reason)
        val reasonDigest = CrossDatabaseOperationIdentity.digestUtf8(cleanReason)
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "agent_instance_id" to agent.agentInstanceId,
                "decision" to decision,
                "expected_agent_digest" to agentSemanticDigest(agent),
                "reason" to cleanReason,
                "reason_digest" to reasonDigest,
                "schema" to AgentLifecycleProtocolSchemas.AGENT_005_PAYLOAD,
                "target_status" to transition
            )
        )
        return command(
            identity = identity,
            operationType = operationType,
            eventType = eventType,
            subjectId = agent.agentInstanceId,
            payloadSchema = AgentLifecycleProtocolSchemas.AGENT_005_PAYLOAD,
            payloadJson = payload,
            transition = transition,
            evidenceSchema = AgentLifecycleProtocolSchemas.AGENT_005_EVIDENCE,
            evidenceValues = mapOf(
                "agent_instance_id" to agent.agentInstanceId,
                "decision" to decision,
                "ownership_conferred" to false,
                "reason_digest" to reasonDigest,
                "target_status" to transition
            )
        )
    }

    private fun deterministicAgentId(
        identity: AgentLifecycleProtocolIdentity,
        vaultId: String,
        templateAgentId: String,
        briefing: String,
        ordinal: Int
    ): String {
        val suffix = StableIdDigest.shortSha256Hex(
            namespace = "morimil.agent_lifecycle.agent_instance.v1",
            parts = listOf(
                identity.instanceId,
                identity.writerEpoch,
                vaultId,
                templateAgentId,
                ordinal.toString(),
                briefing
            ),
            hexLength = 64
        )
        return "agent_instance_$suffix"
    }

    private fun deterministicReplacementId(
        identity: AgentLifecycleProtocolIdentity,
        failedAgent: AgentInstanceEntity,
        reasonDigest: String
    ): String {
        val suffix = StableIdDigest.shortSha256Hex(
            namespace = "morimil.agent_lifecycle.quarantine_replacement.v1",
            parts = listOf(
                identity.instanceId,
                identity.writerEpoch,
                failedAgent.agentInstanceId,
                failedAgent.projectVaultId,
                failedAgent.templateAgentId,
                reasonDigest
            ),
            hexLength = 64
        )
        return "agent_instance_$suffix"
    }

    private fun deterministicTaskId(
        identity: AgentLifecycleProtocolIdentity,
        agent: AgentInstanceEntity,
        goal: String,
        plan: DelegationPlan
    ): String {
        val suffix = StableIdDigest.shortSha256Hex(
            namespace = "morimil.agent_lifecycle.project_task.v1",
            parts = listOf(
                identity.instanceId,
                identity.writerEpoch,
                agent.agentInstanceId,
                agent.currentTaskId.orEmpty(),
                goal,
                plan.assignedAgentId,
                plan.targetDeviceId.orEmpty(),
                AgentCapabilityPolicy.encodeJson(plan.allowedActions),
                AgentCapabilityPolicy.encodeJson(plan.allowedTransports),
                plan.approvalRequired.toString(),
                plan.riskLevel,
                plan.contextSummary,
                plan.immuneDecision,
                plan.immuneReasons.joinToString("\u001f"),
                plan.immuneMatchedSignals.joinToString("\u001f")
            ),
            hexLength = 64
        )
        return "ptask_$suffix"
    }

    private fun plannedAgentJson(
        agentInstanceId: String,
        vault: ProjectVaultEntity,
        templateAgentId: String,
        briefing: String
    ): String {
        return CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "agent_instance_id" to agentInstanceId,
                "briefing" to briefing,
                "constraints_json" to buildConstraintsJson(vault),
                "current_task_id" to null,
                "display_name" to buildAgentDisplayName(vault.displayName, templateAgentId),
                "error_count" to 0,
                "project_vault_id" to vault.vaultId,
                "quality_score" to 50,
                "retire_reason" to null,
                "schema" to AgentLifecycleProtocolSchemas.PLANNED_AGENT,
                "status" to AgentInstanceLifecycleRepository.STATUS_THINKING,
                "template_agent_id" to templateAgentId
            )
        )
    }

    private fun plannedTaskJson(
        taskId: String,
        vault: ProjectVaultEntity,
        agent: AgentInstanceEntity,
        goal: String,
        plan: DelegationPlan,
        initialStatus: String,
        initialErrorSummary: String?
    ): String {
        return CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "allowed_actions_json" to AgentCapabilityPolicy.encodeJson(plan.allowedActions),
                "allowed_transports_json" to AgentCapabilityPolicy.encodeJson(plan.allowedTransports),
                "approval_required" to true,
                "assigned_agent_id" to agent.agentInstanceId,
                "context_summary" to
                    "vault=${vault.displayName}; vault_id=${vault.vaultId}; " +
                        "template_agent=${agent.templateAgentId}; ${plan.contextSummary}",
                "created_by" to CREATED_BY,
                "goal" to goal,
                "initial_error_summary" to initialErrorSummary,
                "initial_status" to initialStatus,
                "input_refs_json" to "[]",
                "risk_level" to plan.riskLevel,
                "schema" to AgentLifecycleProtocolSchemas.PLANNED_TASK,
                "target_device_id" to plan.targetDeviceId,
                "task_id" to taskId
            )
        )
    }

    private fun agentSemanticValues(agent: AgentInstanceEntity): Map<String, Any?> {
        return mapOf(
            "agent_instance_id" to agent.agentInstanceId,
            "briefing" to agent.briefing,
            "constraints_json" to agent.constraintsJson,
            "current_task_id" to agent.currentTaskId,
            "display_name" to agent.displayName,
            "error_count" to agent.errorCount,
            "project_vault_id" to agent.projectVaultId,
            "quality_score" to agent.qualityScore,
            "retire_reason" to agent.retireReason,
            "status" to agent.status,
            "template_agent_id" to agent.templateAgentId
        )
    }

    private fun buildConstraintsJson(vault: ProjectVaultEntity): String {
        return CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "human_approval_required" to true,
                "main_memory_owner" to "morimil",
                "no_autonomous_execution" to true,
                "scope" to "project_vault_only",
                "vault_id" to vault.vaultId,
                "vault_name" to vault.displayName
            )
        )
    }

    private fun buildAgentDisplayName(vaultName: String, templateAgentId: String): String {
        val role = templateAgentId.removeSuffix("_agent").replace('_', ' ')
        return "$vaultName $role worker"
    }

    private fun immuneErrorSummary(plan: DelegationPlan): String {
        val reasons = plan.immuneReasons.joinToString(separator = ",")
            .ifBlank { plan.immuneDecision }
        return "$IMMUNE_BLOCK_PREFIX:$reasons".take(240)
    }

    private fun normalizeToken(value: String): String = normalizeText(value).lowercase()

    private fun normalizeText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFC).trim()

    private fun command(
        identity: AgentLifecycleProtocolIdentity,
        operationType: String,
        eventType: String,
        subjectId: String,
        payloadSchema: String,
        payloadJson: String,
        transition: String,
        evidenceSchema: String,
        evidenceValues: Map<String, Any?>
    ): CrossDatabaseStageCommand {
        val payloadDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(payloadJson)
        val operationId = CrossDatabaseOperationIdentity.operationId(
            operationType = operationType,
            operationVersion = AgentLifecycleProtocolTypes.VERSION,
            instanceId = identity.instanceId,
            writerBodyId = identity.writerBodyId,
            writerEpoch = identity.writerEpoch,
            subjectId = subjectId,
            parentOperationId = null,
            childPhase = null,
            payloadDigest = payloadDigest
        )
        val eventId = CrossDatabaseOperationIdentity.eventId(operationId, eventType)
        val evidenceJson = CrossDatabaseOperationIdentity.canonicalJson(
            evidenceValues + mapOf(
                "event_id" to eventId,
                "operation_id" to operationId,
                "operation_type" to operationType,
                "owner_type" to AgentLifecycleProtocolTypes.OWNER_TYPE,
                "schema" to evidenceSchema,
                "subject_id" to subjectId
            )
        )
        val evidenceDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(evidenceJson)
        val eventBody = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "event_id" to eventId,
                "operation_id" to operationId,
                "operation_type" to operationType,
                "payload_digest" to payloadDigest,
                "schema" to AgentLifecycleProtocolSchemas.EVENT_BODY,
                "subject_id" to subjectId,
                "transition" to transition
            )
        )
        return CrossDatabaseStageCommand(
            operationId = operationId,
            ownerType = AgentLifecycleProtocolTypes.OWNER_TYPE,
            operationType = operationType,
            operationVersion = AgentLifecycleProtocolTypes.VERSION,
            instanceId = identity.instanceId,
            writerBodyId = identity.writerBodyId,
            writerEpoch = identity.writerEpoch,
            subjectId = subjectId,
            parentOperationId = null,
            childPhase = null,
            payloadSchema = payloadSchema,
            payloadJson = payloadJson,
            payloadDigest = payloadDigest,
            eventId = eventId,
            eventType = eventType,
            eventBody = eventBody,
            evidenceSchema = evidenceSchema,
            evidenceJson = evidenceJson,
            evidenceDigest = evidenceDigest
        )
    }
}

internal interface AgentLifecycleFinalizerStore {
    suspend fun loadProjectVault(vaultId: String): ProjectVaultEntity?
    suspend fun loadAgentInstance(agentInstanceId: String): AgentInstanceEntity?
    suspend fun loadDelegatedTask(taskId: String): DelegatedTaskEntity?
    suspend fun insertAgentInstance(agent: AgentInstanceEntity)
    suspend fun insertDelegatedTask(task: DelegatedTaskEntity)
    suspend fun updateAgentInstanceLifecycle(
        agentInstanceId: String,
        status: String,
        qualityScore: Int,
        errorCount: Int,
        currentTaskId: String?,
        lastHeartbeatAtMillis: Long?,
        updatedAtMillis: Long,
        retiredAtMillis: Long?,
        retireReason: String?
    ): Int
    suspend fun updateDelegatedTaskResult(
        taskId: String,
        status: String,
        resultSummary: String,
        updatedAtMillis: Long,
        completedAtMillis: Long?
    ): Int
    suspend fun refreshProjectVaultActiveAgentCount(vaultId: String, updatedAtMillis: Long): Int
}

private class RoomAgentLifecycleFinalizerStore(
    database: MemoryOrganDatabase
) : AgentLifecycleFinalizerStore {
    private val dao = database.memoryOrganDao()

    override suspend fun loadProjectVault(vaultId: String): ProjectVaultEntity? = dao.loadProjectVault(vaultId)
    override suspend fun loadAgentInstance(agentInstanceId: String): AgentInstanceEntity? =
        dao.loadAgentInstance(agentInstanceId)
    override suspend fun loadDelegatedTask(taskId: String): DelegatedTaskEntity? = dao.loadDelegatedTask(taskId)
    override suspend fun insertAgentInstance(agent: AgentInstanceEntity) = dao.insertAgentInstance(agent)
    override suspend fun insertDelegatedTask(task: DelegatedTaskEntity) = dao.insertDelegatedTask(task)
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
    ): Int = dao.updateAgentInstanceLifecycle(
        agentInstanceId,
        status,
        qualityScore,
        errorCount,
        currentTaskId,
        lastHeartbeatAtMillis,
        updatedAtMillis,
        retiredAtMillis,
        retireReason
    )
    override suspend fun updateDelegatedTaskResult(
        taskId: String,
        status: String,
        resultSummary: String,
        updatedAtMillis: Long,
        completedAtMillis: Long?
    ): Int = dao.updateDelegatedTaskResult(
        taskId,
        status,
        resultSummary,
        updatedAtMillis,
        completedAtMillis
    )
    override suspend fun refreshProjectVaultActiveAgentCount(vaultId: String, updatedAtMillis: Long): Int =
        dao.refreshProjectVaultActiveAgentCount(vaultId, updatedAtMillis)
}

internal class AgentLifecycleProtocolFinalizer private constructor(
    private val store: AgentLifecycleFinalizerStore
) : CrossDatabaseTypedFinalizer {
    constructor(database: MemoryOrganDatabase) : this(RoomAgentLifecycleFinalizerStore(database))

    override val supportedOperationTypes: Set<String> = AgentLifecycleProtocolTypes.CLOSED_REGISTRY.keys

    override suspend fun finalizeInsideTransaction(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        permanentCheck(
            operation.status == CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT,
            CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT
        )
        permanentCheck(
            operation.operationVersion == AgentLifecycleProtocolTypes.VERSION,
            CrossDatabaseProtocolErrors.UNSUPPORTED_OPERATION_VERSION
        )
        permanentCheck(
            operation.ownerType == AgentLifecycleProtocolTypes.OWNER_TYPE,
            CrossDatabaseProtocolErrors.UNSUPPORTED_OPERATION_VERSION
        )
        return when (operation.operationType) {
            AgentLifecycleProtocolTypes.CREATE -> finalizeCreate(operation, receipt)
            AgentLifecycleProtocolTypes.ASSIGN -> finalizeAssign(operation, receipt)
            AgentLifecycleProtocolTypes.SUBMIT_RESULT -> finalizeSubmitResult(operation, receipt)
            AgentLifecycleProtocolTypes.EVALUATE -> finalizeEvaluate(operation, receipt)
            AgentLifecycleProtocolTypes.RETIRE -> finalizeRetire(operation, receipt)
            AgentLifecycleProtocolTypes.PROMOTE -> finalizePromote(operation, receipt)
            AgentLifecycleProtocolTypes.QUARANTINE -> finalizeQuarantine(operation, receipt)
            else -> throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.UNSUPPORTED_OPERATION_VERSION
            )
        }
    }

    private suspend fun finalizeCreate(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        val payload = requirePayload(operation, AgentLifecycleProtocolSchemas.AGENT_001_PAYLOAD)
        permanentCheck(payload.getString("agent_instance_id") == operation.subjectId)
        val vault = requireVault(payload.getString("vault_id"))
        permanentCheck(
            AgentLifecycleOperationFactory.vaultSemanticDigest(vault) == payload.getString("vault_digest")
        )
        val plan = requirePlannedAgent(payload, "planned_agent", "planned_agent_digest")
        val candidate = plan.toAgentEntity(operation)
        permanentCheck(candidate.agentInstanceId == operation.subjectId)
        val existing = store.loadAgentInstance(candidate.agentInstanceId)
        val inserted = if (existing == null) {
            store.insertAgentInstance(candidate)
            true
        } else {
            permanentCheck(existing == candidate, CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT)
            false
        }
        store.refreshProjectVaultActiveAgentCount(vault.vaultId, operation.occurredAtMillis)
        return result(
            schema = AgentLifecycleProtocolSchemas.AGENT_001_LOCAL_RESULT,
            ownerStatus = candidate.status,
            receipt = receipt,
            values = mapOf(
                "agent_instance_id" to candidate.agentInstanceId,
                "record_inserted" to inserted,
                "vault_id" to vault.vaultId
            )
        )
    }

    private suspend fun finalizeAssign(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        val payload = requirePayload(operation, AgentLifecycleProtocolSchemas.AGENT_002_PAYLOAD)
        val agentId = payload.getString("agent_instance_id")
        val expectedAgentDigest = payload.getString("expected_agent_digest")
        val agent = requireAgent(agentId)
        requireAgentDigest(agent, expectedAgentDigest)
        permanentCheck(
            agent.status != AgentInstanceLifecycleRepository.STATUS_RETIRED &&
                agent.status != AgentInstanceLifecycleRepository.STATUS_QUARANTINED
        )
        val vault = requireVault(payload.getString("vault_id"))
        permanentCheck(vault.vaultId == agent.projectVaultId)
        permanentCheck(
            AgentLifecycleOperationFactory.vaultSemanticDigest(vault) == payload.getString("vault_digest")
        )
        val taskPlan = requirePlannedTask(payload)
        val task = taskPlan.toTaskEntity(operation)
        permanentCheck(task.taskId == operation.subjectId)
        permanentCheck(task.assignedAgentId == agent.agentInstanceId)
        val existingTask = store.loadDelegatedTask(task.taskId)
        val inserted = if (existingTask == null) {
            store.insertDelegatedTask(task)
            true
        } else {
            permanentCheck(existingTask == task, CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT)
            false
        }
        val immuneBlocked = payload.getBoolean("immune_blocked")
        var agentUpdated = false
        if (!immuneBlocked) {
            agentUpdated = updateAgent(
                expected = agent,
                status = AgentInstanceLifecycleRepository.STATUS_AWAITING_REVIEW,
                qualityScore = agent.qualityScore,
                errorCount = agent.errorCount,
                currentTaskId = task.taskId,
                retiredAtMillis = null,
                retireReason = null,
                operation = operation
            )
        }
        store.refreshProjectVaultActiveAgentCount(vault.vaultId, operation.occurredAtMillis)
        return result(
            schema = AgentLifecycleProtocolSchemas.AGENT_002_LOCAL_RESULT,
            ownerStatus = if (immuneBlocked) task.status else AgentInstanceLifecycleRepository.STATUS_AWAITING_REVIEW,
            receipt = receipt,
            values = mapOf(
                "agent_instance_id" to agent.agentInstanceId,
                "agent_updated" to agentUpdated,
                "immune_blocked" to immuneBlocked,
                "task_id" to task.taskId,
                "task_inserted" to inserted,
                "vault_id" to vault.vaultId
            )
        )
    }

    private suspend fun finalizeSubmitResult(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        val payload = requirePayload(operation, AgentLifecycleProtocolSchemas.AGENT_003_PAYLOAD)
        val agent = requireAgent(payload.getString("agent_instance_id"))
        permanentCheck(agent.agentInstanceId == operation.subjectId)
        requireAgentDigest(agent, payload.getString("expected_agent_digest"))
        val task = requireTask(payload.getString("task_id"))
        permanentCheck(task.assignedAgentId == agent.agentInstanceId)
        permanentCheck(agent.currentTaskId == task.taskId)
        permanentCheck(
            AgentLifecycleOperationFactory.taskStateDigest(task) == payload.getString("expected_task_digest")
        )
        permanentCheck(task.status == AgentCapabilityPolicy.STATUS_APPROVED && task.approvalId != null)
        val summary = payload.getString("result_summary")
        permanentCheck(
            CrossDatabaseOperationIdentity.digestUtf8(summary) == payload.getString("result_summary_digest")
        )
        permanentCheck(
            store.updateDelegatedTaskResult(
                taskId = task.taskId,
                status = AgentInstanceLifecycleRepository.STATUS_AWAITING_REVIEW,
                resultSummary = summary,
                updatedAtMillis = operation.occurredAtMillis,
                completedAtMillis = null
            ) == 1
        )
        updateAgent(
            expected = agent,
            status = AgentInstanceLifecycleRepository.STATUS_AWAITING_REVIEW,
            qualityScore = agent.qualityScore,
            errorCount = agent.errorCount,
            currentTaskId = task.taskId,
            retiredAtMillis = null,
            retireReason = null,
            operation = operation
        )
        store.refreshProjectVaultActiveAgentCount(agent.projectVaultId, operation.occurredAtMillis)
        return result(
            schema = AgentLifecycleProtocolSchemas.AGENT_003_LOCAL_RESULT,
            ownerStatus = AgentInstanceLifecycleRepository.STATUS_AWAITING_REVIEW,
            receipt = receipt,
            values = mapOf(
                "agent_instance_id" to agent.agentInstanceId,
                "result_summary_digest" to payload.getString("result_summary_digest"),
                "task_id" to task.taskId
            )
        )
    }

    private suspend fun finalizeEvaluate(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        val payload = requirePayload(operation, AgentLifecycleProtocolSchemas.AGENT_004_PAYLOAD)
        val agent = requireAgent(operation.subjectId)
        permanentCheck(payload.getString("agent_instance_id") == agent.agentInstanceId)
        requireAgentDigest(agent, payload.getString("expected_agent_digest"))
        permanentCheck(
            agent.status != AgentInstanceLifecycleRepository.STATUS_RETIRED &&
                agent.status != AgentInstanceLifecycleRepository.STATUS_QUARANTINED
        )
        val status = AgentLifecycleOperationFactory.normalizeReviewStatus(payload.getString("status"))
        val score = payload.getInt("quality_score").coerceIn(0, 100)
        val note = payload.getString("note")
        permanentCheck(
            CrossDatabaseOperationIdentity.digestUtf8(note) == payload.getString("note_digest")
        )
        updateAgent(
            expected = agent,
            status = status,
            qualityScore = score,
            errorCount = agent.errorCount,
            currentTaskId = agent.currentTaskId,
            retiredAtMillis = null,
            retireReason = null,
            operation = operation
        )
        store.refreshProjectVaultActiveAgentCount(agent.projectVaultId, operation.occurredAtMillis)
        return result(
            schema = AgentLifecycleProtocolSchemas.AGENT_004_LOCAL_RESULT,
            ownerStatus = status,
            receipt = receipt,
            values = mapOf(
                "agent_instance_id" to agent.agentInstanceId,
                "note_digest" to payload.getString("note_digest"),
                "quality_score" to score,
                "status" to status
            )
        )
    }

    private suspend fun finalizeRetire(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult = finalizeAgentDecision(
        operation = operation,
        receipt = receipt,
        expectedDecision = "retire",
        targetStatus = AgentInstanceLifecycleRepository.STATUS_RETIRED,
        minimumQuality = null
    )

    private suspend fun finalizePromote(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult = finalizeAgentDecision(
        operation = operation,
        receipt = receipt,
        expectedDecision = "promote",
        targetStatus = AgentInstanceLifecycleRepository.STATUS_PROMOTED,
        minimumQuality = 90
    )

    private suspend fun finalizeAgentDecision(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt,
        expectedDecision: String,
        targetStatus: String,
        minimumQuality: Int?
    ): CrossDatabaseLocalResult {
        val payload = requirePayload(operation, AgentLifecycleProtocolSchemas.AGENT_005_PAYLOAD)
        val agent = requireAgent(operation.subjectId)
        permanentCheck(payload.getString("agent_instance_id") == agent.agentInstanceId)
        requireAgentDigest(agent, payload.getString("expected_agent_digest"))
        permanentCheck(payload.getString("decision") == expectedDecision)
        permanentCheck(payload.getString("target_status") == targetStatus)
        val reason = payload.getString("reason")
        permanentCheck(
            CrossDatabaseOperationIdentity.digestUtf8(reason) == payload.getString("reason_digest")
        )
        permanentCheck(
            agent.status != AgentInstanceLifecycleRepository.STATUS_RETIRED &&
                agent.status != AgentInstanceLifecycleRepository.STATUS_QUARANTINED
        )
        val quality = minimumQuality?.let { maxOf(agent.qualityScore, it) } ?: agent.qualityScore
        updateAgent(
            expected = agent,
            status = targetStatus,
            qualityScore = quality,
            errorCount = agent.errorCount,
            currentTaskId = agent.currentTaskId,
            retiredAtMillis = if (targetStatus == AgentInstanceLifecycleRepository.STATUS_RETIRED) {
                operation.occurredAtMillis
            } else {
                null
            },
            retireReason = reason,
            operation = operation
        )
        store.refreshProjectVaultActiveAgentCount(agent.projectVaultId, operation.occurredAtMillis)
        return result(
            schema = AgentLifecycleProtocolSchemas.AGENT_005_LOCAL_RESULT,
            ownerStatus = targetStatus,
            receipt = receipt,
            values = mapOf(
                "agent_instance_id" to agent.agentInstanceId,
                "decision" to expectedDecision,
                "quality_score" to quality,
                "reason_digest" to payload.getString("reason_digest"),
                "status" to targetStatus
            )
        )
    }

    private suspend fun finalizeQuarantine(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        val payload = requirePayload(operation, AgentLifecycleProtocolSchemas.AGENT_006_PAYLOAD)
        val agent = requireAgent(operation.subjectId)
        permanentCheck(payload.getString("agent_instance_id") == agent.agentInstanceId)
        requireAgentDigest(agent, payload.getString("expected_agent_digest"))
        permanentCheck(payload.getString("decision") == "quarantine")
        permanentCheck(
            agent.status != AgentInstanceLifecycleRepository.STATUS_RETIRED &&
                agent.status != AgentInstanceLifecycleRepository.STATUS_QUARANTINED
        )
        val reason = payload.getString("reason")
        permanentCheck(
            CrossDatabaseOperationIdentity.digestUtf8(reason) == payload.getString("reason_digest")
        )
        val vault = requireVault(payload.getString("vault_id"))
        permanentCheck(vault.vaultId == agent.projectVaultId)
        permanentCheck(
            AgentLifecycleOperationFactory.vaultSemanticDigest(vault) == payload.getString("vault_digest")
        )
        val replacementPlan = requirePlannedAgent(
            payload,
            "replacement_agent",
            "replacement_agent_digest"
        )
        val replacement = replacementPlan.toAgentEntity(operation)
        permanentCheck(replacement.agentInstanceId == payload.getString("replacement_agent_id"))
        permanentCheck(replacement.projectVaultId == agent.projectVaultId)
        permanentCheck(replacement.templateAgentId == agent.templateAgentId)

        updateAgent(
            expected = agent,
            status = AgentInstanceLifecycleRepository.STATUS_QUARANTINED,
            qualityScore = agent.qualityScore,
            errorCount = agent.errorCount + 1,
            currentTaskId = agent.currentTaskId,
            retiredAtMillis = operation.occurredAtMillis,
            retireReason = reason,
            operation = operation
        )
        val existingReplacement = store.loadAgentInstance(replacement.agentInstanceId)
        val replacementInserted = if (existingReplacement == null) {
            store.insertAgentInstance(replacement)
            true
        } else {
            permanentCheck(
                existingReplacement == replacement,
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
            false
        }
        store.refreshProjectVaultActiveAgentCount(vault.vaultId, operation.occurredAtMillis)
        return result(
            schema = AgentLifecycleProtocolSchemas.AGENT_006_LOCAL_RESULT,
            ownerStatus = AgentInstanceLifecycleRepository.STATUS_QUARANTINED,
            receipt = receipt,
            values = mapOf(
                "agent_instance_id" to agent.agentInstanceId,
                "reason_digest" to payload.getString("reason_digest"),
                "replacement_agent_id" to replacement.agentInstanceId,
                "replacement_inserted" to replacementInserted,
                "vault_id" to vault.vaultId
            )
        )
    }

    private suspend fun updateAgent(
        expected: AgentInstanceEntity,
        status: String,
        qualityScore: Int,
        errorCount: Int,
        currentTaskId: String?,
        retiredAtMillis: Long?,
        retireReason: String?,
        operation: CrossDatabaseOperationRecord
    ): Boolean {
        val durable = requireAgent(expected.agentInstanceId)
        permanentCheck(
            AgentLifecycleOperationFactory.agentSemanticDigest(durable) ==
                AgentLifecycleOperationFactory.agentSemanticDigest(expected),
            CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT
        )
        val changed = store.updateAgentInstanceLifecycle(
            agentInstanceId = expected.agentInstanceId,
            status = status,
            qualityScore = qualityScore,
            errorCount = errorCount,
            currentTaskId = currentTaskId,
            lastHeartbeatAtMillis = operation.occurredAtMillis,
            updatedAtMillis = operation.occurredAtMillis,
            retiredAtMillis = retiredAtMillis,
            retireReason = retireReason
        )
        permanentCheck(changed == 1, CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT)
        return true
    }

    private fun requirePayload(
        operation: CrossDatabaseOperationRecord,
        expectedSchema: String
    ): JSONObject {
        if (operation.payloadSchema != expectedSchema) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.UNSUPPORTED_PAYLOAD_SCHEMA
            )
        }
        val payload = try {
            JSONObject(operation.payloadJson)
        } catch (failure: Throwable) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.UNSUPPORTED_PAYLOAD_SCHEMA,
                failure
            )
        }
        if (payload.getString("schema") != expectedSchema) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.UNSUPPORTED_PAYLOAD_SCHEMA
            )
        }
        return payload
    }

    private fun requirePlannedAgent(
        payload: JSONObject,
        key: String,
        digestKey: String
    ): JSONObject {
        val planned = payload.getJSONObject(key)
        permanentCheck(
            planned.getString("schema") == AgentLifecycleProtocolSchemas.PLANNED_AGENT,
            CrossDatabaseProtocolErrors.UNSUPPORTED_PAYLOAD_SCHEMA
        )
        val canonical = CrossDatabaseOperationIdentity.canonicalJson(planned)
        permanentCheck(
            CrossDatabaseOperationIdentity.digestCanonicalJson(canonical) == payload.getString(digestKey)
        )
        return planned
    }

    private fun requirePlannedTask(payload: JSONObject): JSONObject {
        val planned = payload.getJSONObject("planned_task")
        permanentCheck(
            planned.getString("schema") == AgentLifecycleProtocolSchemas.PLANNED_TASK,
            CrossDatabaseProtocolErrors.UNSUPPORTED_PAYLOAD_SCHEMA
        )
        val canonical = CrossDatabaseOperationIdentity.canonicalJson(planned)
        permanentCheck(
            CrossDatabaseOperationIdentity.digestCanonicalJson(canonical) ==
                payload.getString("planned_task_digest")
        )
        return planned
    }

    private suspend fun requireVault(vaultId: String): ProjectVaultEntity =
        store.loadProjectVault(vaultId)
            ?: throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )

    private suspend fun requireAgent(agentId: String): AgentInstanceEntity =
        store.loadAgentInstance(agentId)
            ?: throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )

    private suspend fun requireTask(taskId: String): DelegatedTaskEntity =
        store.loadDelegatedTask(taskId)
            ?: throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )

    private fun requireAgentDigest(agent: AgentInstanceEntity, expectedDigest: String) {
        permanentCheck(
            AgentLifecycleOperationFactory.agentSemanticDigest(agent) == expectedDigest,
            CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
        )
    }

    private fun JSONObject.toAgentEntity(operation: CrossDatabaseOperationRecord): AgentInstanceEntity {
        return AgentInstanceEntity(
            agentInstanceId = getString("agent_instance_id"),
            projectVaultId = getString("project_vault_id"),
            templateAgentId = getString("template_agent_id"),
            displayName = getString("display_name"),
            briefing = getString("briefing"),
            constraintsJson = getString("constraints_json"),
            status = getString("status"),
            qualityScore = getInt("quality_score"),
            errorCount = getInt("error_count"),
            currentTaskId = nullableString("current_task_id"),
            lastHeartbeatAtMillis = operation.occurredAtMillis,
            createdAtMillis = operation.occurredAtMillis,
            updatedAtMillis = operation.occurredAtMillis,
            retiredAtMillis = null,
            retireReason = nullableString("retire_reason")
        )
    }

    private fun JSONObject.toTaskEntity(operation: CrossDatabaseOperationRecord): DelegatedTaskEntity {
        val initialStatus = getString("initial_status")
        return DelegatedTaskEntity(
            taskId = getString("task_id"),
            createdBy = getString("created_by"),
            assignedAgentId = getString("assigned_agent_id"),
            targetDeviceId = nullableString("target_device_id"),
            goal = getString("goal"),
            contextSummary = getString("context_summary"),
            inputRefsJson = getString("input_refs_json"),
            allowedActionsJson = getString("allowed_actions_json"),
            allowedTransportsJson = getString("allowed_transports_json"),
            approvalRequired = getBoolean("approval_required"),
            approvalId = null,
            status = initialStatus,
            riskLevel = getString("risk_level"),
            resultSummary = null,
            errorSummary = nullableString("initial_error_summary"),
            createdAtMillis = operation.occurredAtMillis,
            updatedAtMillis = operation.occurredAtMillis,
            completedAtMillis = if (initialStatus == AgentCapabilityPolicy.STATUS_REJECTED) {
                operation.occurredAtMillis
            } else {
                null
            }
        )
    }

    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)

    private fun result(
        schema: String,
        ownerStatus: String,
        receipt: CrossDatabaseCanonicalReceipt,
        values: Map<String, Any?>
    ): CrossDatabaseLocalResult {
        val json = CrossDatabaseOperationIdentity.canonicalJson(
            values + mapOf(
                "canonical_event_hash" to receipt.eventHash,
                "canonical_event_id" to receipt.eventId,
                "canonical_provenance_digest" to receipt.provenanceDigest,
                "canonical_sequence" to receipt.sequence,
                "owner_status" to ownerStatus,
                "schema" to schema
            )
        )
        return CrossDatabaseLocalResult(
            schema = schema,
            json = json,
            digest = CrossDatabaseOperationIdentity.digestCanonicalJson(json),
            ownerStatus = ownerStatus
        )
    }

    private fun permanentCheck(
        condition: Boolean,
        code: String = CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
    ) {
        if (!condition) throw CrossDatabaseProtocolErrors.permanent(code)
    }

    internal companion object {
        fun testing(store: AgentLifecycleFinalizerStore): AgentLifecycleProtocolFinalizer =
            AgentLifecycleProtocolFinalizer(store)
    }
}
