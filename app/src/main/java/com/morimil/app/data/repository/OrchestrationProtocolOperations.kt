package com.morimil.app.data.repository

import com.morimil.app.core.identity.StableIdDigest
import com.morimil.app.core.orchestration.AgentCapabilityPolicy
import com.morimil.app.core.orchestration.DelegationPlan
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.local.DelegatedTaskEntity
import java.text.Normalizer
import org.json.JSONObject

internal data class OrchestrationProtocolIdentity(
    val instanceId: String,
    val writerBodyId: String,
    val writerEpoch: String
) {
    init {
        require(instanceId.isNotBlank() && instanceId != writerBodyId) {
            "orchestration_identity_invalid"
        }
        require(writerBodyId.isNotBlank() && writerEpoch.isNotBlank()) {
            "orchestration_writer_invalid"
        }
    }
}

internal object OrchestrationProtocolSchemas {
    const val ORCH_002_PAYLOAD = "morimil.orchestration.orch_002.payload.v1"
    const val ORCH_002_EVIDENCE = "morimil.orchestration.orch_002.evidence.v1"
    const val ORCH_002_LOCAL_RESULT = "morimil.orchestration.orch_002.local_result.v1"

    const val ORCH_003_PAYLOAD = "morimil.orchestration.orch_003.payload.v1"
    const val ORCH_003_EVIDENCE = "morimil.orchestration.orch_003.evidence.v1"
    const val ORCH_003_LOCAL_RESULT = "morimil.orchestration.orch_003.local_result.v1"

    const val ORCH_004_PAYLOAD = "morimil.orchestration.orch_004.payload.v1"
    const val ORCH_004_EVIDENCE = "morimil.orchestration.orch_004.evidence.v1"
    const val ORCH_004_LOCAL_RESULT = "morimil.orchestration.orch_004.local_result.v1"

    const val PLANNED_TASK = "morimil.orchestration.delegated_task.plan.v1"
    const val EVENT_BODY = "morimil.orchestration.event_body.v1"
}

internal object OrchestrationOperationFactory {
    private const val CREATED_BY = "morimil_orchestrator"
    const val IMMUNE_BLOCK_PREFIX = "immune_policy_blocked"

    fun propose(
        identity: OrchestrationProtocolIdentity,
        goal: String,
        plan: DelegationPlan
    ): CrossDatabaseStageCommand {
        val cleanGoal = normalizeGoal(goal)
        val immuneBlocked = AgentCapabilityPolicy.isImmuneBlocked(plan.immuneDecision)
        val initialStatus = if (immuneBlocked) {
            AgentCapabilityPolicy.STATUS_REJECTED
        } else {
            AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL
        }
        val initialError = if (immuneBlocked) immuneErrorSummary(plan) else null
        val taskId = deterministicTaskId(identity, cleanGoal, plan)
        val plannedTaskJson = plannedTaskJson(
            taskId = taskId,
            goal = cleanGoal,
            plan = plan,
            initialStatus = initialStatus,
            initialErrorSummary = initialError
        )
        val plannedTaskDigest =
            CrossDatabaseOperationIdentity.digestCanonicalJson(plannedTaskJson)
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "immune_decision" to plan.immuneDecision,
                "immune_matched_signals" to plan.immuneMatchedSignals,
                "immune_reasons" to plan.immuneReasons,
                "initial_owner_status" to initialStatus,
                "planned_task" to JSONObject(plannedTaskJson),
                "planned_task_digest" to plannedTaskDigest,
                "schema" to OrchestrationProtocolSchemas.ORCH_002_PAYLOAD,
                "task_id" to taskId
            )
        )
        return command(
            identity = identity,
            operationType = OrchestrationProtocolTypes.PROPOSE,
            eventType = OrchestrationProtocolTypes.PROPOSED_EVENT,
            subjectId = taskId,
            payloadSchema = OrchestrationProtocolSchemas.ORCH_002_PAYLOAD,
            payloadJson = payload,
            transition = initialStatus,
            evidenceSchema = OrchestrationProtocolSchemas.ORCH_002_EVIDENCE,
            evidenceValues = mapOf(
                "approval_required" to plan.approvalRequired,
                "immune_blocked" to immuneBlocked,
                "immune_decision" to plan.immuneDecision,
                "ownership_conferred" to false,
                "planned_task_digest" to plannedTaskDigest,
                "task_id" to taskId
            )
        )
    }

    fun approve(
        identity: OrchestrationProtocolIdentity,
        task: DelegatedTaskEntity
    ): CrossDatabaseStageCommand {
        val taskIdentityDigest = taskIdentityDigest(task)
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "decision" to "approve",
                "expected_owner_status" to AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL,
                "schema" to OrchestrationProtocolSchemas.ORCH_003_PAYLOAD,
                "task_id" to task.taskId,
                "task_identity_digest" to taskIdentityDigest
            )
        )
        return command(
            identity = identity,
            operationType = OrchestrationProtocolTypes.APPROVE,
            eventType = OrchestrationProtocolTypes.APPROVED_EVENT,
            subjectId = task.taskId,
            payloadSchema = OrchestrationProtocolSchemas.ORCH_003_PAYLOAD,
            payloadJson = payload,
            transition = AgentCapabilityPolicy.STATUS_APPROVED,
            evidenceSchema = OrchestrationProtocolSchemas.ORCH_003_EVIDENCE,
            evidenceValues = mapOf(
                "decision" to "approve",
                "decision_source" to "interactive_local_user",
                "ownership_conferred" to false,
                "task_id" to task.taskId,
                "task_identity_digest" to taskIdentityDigest
            )
        )
    }

    fun reject(
        identity: OrchestrationProtocolIdentity,
        task: DelegatedTaskEntity,
        reason: String
    ): CrossDatabaseStageCommand {
        val cleanReason = normalizeReason(reason)
        val taskIdentityDigest = taskIdentityDigest(task)
        val reasonDigest = CrossDatabaseOperationIdentity.digestUtf8(cleanReason)
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "decision" to "reject",
                "expected_owner_status" to AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL,
                "reason" to cleanReason,
                "reason_digest" to reasonDigest,
                "schema" to OrchestrationProtocolSchemas.ORCH_004_PAYLOAD,
                "task_id" to task.taskId,
                "task_identity_digest" to taskIdentityDigest
            )
        )
        return command(
            identity = identity,
            operationType = OrchestrationProtocolTypes.REJECT,
            eventType = OrchestrationProtocolTypes.REJECTED_EVENT,
            subjectId = task.taskId,
            payloadSchema = OrchestrationProtocolSchemas.ORCH_004_PAYLOAD,
            payloadJson = payload,
            transition = AgentCapabilityPolicy.STATUS_REJECTED,
            evidenceSchema = OrchestrationProtocolSchemas.ORCH_004_EVIDENCE,
            evidenceValues = mapOf(
                "decision" to "reject",
                "decision_source" to "interactive_local_user",
                "ownership_conferred" to false,
                "reason_digest" to reasonDigest,
                "task_id" to task.taskId,
                "task_identity_digest" to taskIdentityDigest
            )
        )
    }

    fun taskIdentityDigest(task: DelegatedTaskEntity): String {
        return CrossDatabaseOperationIdentity.digestCanonicalJson(
            CrossDatabaseOperationIdentity.canonicalJson(taskIdentityValues(task))
        )
    }

    fun normalizeGoal(goal: String): String {
        return Normalizer.normalize(goal, Normalizer.Form.NFC)
            .trim()
            .ifBlank { "Preparar trabajo delegado" }
    }

    fun normalizeReason(reason: String): String {
        return Normalizer.normalize(reason, Normalizer.Form.NFC).take(240)
    }

    fun identityOf(identity: GenesisUltraRuntimeIdentity): OrchestrationProtocolIdentity {
        return OrchestrationProtocolIdentity(
            instanceId = identity.instanceId,
            writerBodyId = identity.activeBody.bodyId,
            writerEpoch = identity.activeBody.keyEpochId
        )
    }

    private fun deterministicTaskId(
        identity: OrchestrationProtocolIdentity,
        goal: String,
        plan: DelegationPlan
    ): String {
        val suffix = StableIdDigest.shortSha256Hex(
            namespace = "morimil.orchestration.delegated_task.v1",
            parts = listOf(
                identity.instanceId,
                identity.writerEpoch,
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
        return "dtask_$suffix"
    }

    private fun plannedTaskJson(
        taskId: String,
        goal: String,
        plan: DelegationPlan,
        initialStatus: String,
        initialErrorSummary: String?
    ): String {
        return CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "allowed_actions_json" to AgentCapabilityPolicy.encodeJson(plan.allowedActions),
                "allowed_transports_json" to AgentCapabilityPolicy.encodeJson(plan.allowedTransports),
                "approval_required" to plan.approvalRequired,
                "assigned_agent_id" to plan.assignedAgentId,
                "context_summary" to plan.contextSummary,
                "created_by" to CREATED_BY,
                "goal" to goal,
                "initial_error_summary" to initialErrorSummary,
                "initial_status" to initialStatus,
                "input_refs_json" to "[]",
                "risk_level" to plan.riskLevel,
                "schema" to OrchestrationProtocolSchemas.PLANNED_TASK,
                "target_device_id" to plan.targetDeviceId,
                "task_id" to taskId
            )
        )
    }

    private fun taskIdentityValues(task: DelegatedTaskEntity): Map<String, Any?> {
        return mapOf(
            "allowed_actions_json" to task.allowedActionsJson,
            "allowed_transports_json" to task.allowedTransportsJson,
            "approval_required" to task.approvalRequired,
            "assigned_agent_id" to task.assignedAgentId,
            "context_summary" to task.contextSummary,
            "created_by" to task.createdBy,
            "goal" to task.goal,
            "input_refs_json" to task.inputRefsJson,
            "risk_level" to task.riskLevel,
            "target_device_id" to task.targetDeviceId,
            "task_id" to task.taskId
        )
    }

    private fun immuneErrorSummary(plan: DelegationPlan): String {
        val reasons = plan.immuneReasons.joinToString(separator = ",")
            .ifBlank { plan.immuneDecision }
        return "$IMMUNE_BLOCK_PREFIX:$reasons".take(240)
    }

    private fun command(
        identity: OrchestrationProtocolIdentity,
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
            operationVersion = OrchestrationProtocolTypes.VERSION,
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
                "owner_type" to OrchestrationProtocolTypes.OWNER_TYPE,
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
                "schema" to OrchestrationProtocolSchemas.EVENT_BODY,
                "subject_id" to subjectId,
                "transition" to transition
            )
        )
        return CrossDatabaseStageCommand(
            operationId = operationId,
            ownerType = OrchestrationProtocolTypes.OWNER_TYPE,
            operationType = operationType,
            operationVersion = OrchestrationProtocolTypes.VERSION,
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
