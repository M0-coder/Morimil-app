package com.morimil.app.data.repository

import com.morimil.app.core.orchestration.AgentCapabilityPolicy
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.DelegatedTaskEntity
import com.morimil.app.data.local.MemoryOrganDatabase
import org.json.JSONObject

internal class OrchestrationProtocolFinalizer(
    database: MemoryOrganDatabase
) : CrossDatabaseTypedFinalizer {
    private val organDao = database.memoryOrganDao()

    override val supportedOperationTypes: Set<String> =
        OrchestrationProtocolTypes.CLOSED_REGISTRY.keys

    override suspend fun finalizeInsideTransaction(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        permanentCheck(
            operation.status == CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT,
            CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT
        )
        permanentCheck(
            operation.operationVersion == OrchestrationProtocolTypes.VERSION,
            CrossDatabaseProtocolErrors.UNSUPPORTED_OPERATION_VERSION
        )
        permanentCheck(
            operation.ownerType == OrchestrationProtocolTypes.OWNER_TYPE,
            CrossDatabaseProtocolErrors.UNSUPPORTED_OPERATION_VERSION
        )

        return when (operation.operationType) {
            OrchestrationProtocolTypes.PROPOSE -> finalizeProposal(operation, receipt)
            OrchestrationProtocolTypes.APPROVE -> finalizeApproval(operation, receipt)
            OrchestrationProtocolTypes.REJECT -> finalizeRejection(operation, receipt)
            else -> throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.UNSUPPORTED_OPERATION_VERSION
            )
        }
    }

    private suspend fun finalizeProposal(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        val payload = requirePayload(operation, OrchestrationProtocolSchemas.ORCH_002_PAYLOAD)
        permanentCheck(payload.getString("task_id") == operation.subjectId)
        val plannedTaskObject = payload.getJSONObject("planned_task")
        permanentCheck(
            plannedTaskObject.getString("schema") == OrchestrationProtocolSchemas.PLANNED_TASK,
            CrossDatabaseProtocolErrors.UNSUPPORTED_PAYLOAD_SCHEMA
        )
        val plannedTaskJson = CrossDatabaseOperationIdentity.canonicalJson(plannedTaskObject)
        val plannedTaskDigest = payload.getString("planned_task_digest")
        permanentCheck(
            CrossDatabaseOperationIdentity.digestCanonicalJson(plannedTaskJson) == plannedTaskDigest,
            CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
        )

        val candidate = plannedTaskObject.toEntity(operation)
        val existing = organDao.loadDelegatedTask(candidate.taskId)
        val inserted = if (existing == null) {
            organDao.insertDelegatedTask(candidate)
            true
        } else {
            permanentCheck(existing == candidate, CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT)
            false
        }

        val json = localResultJson(
            mapOf(
                "canonical_event_hash" to receipt.eventHash,
                "canonical_event_id" to receipt.eventId,
                "canonical_provenance_digest" to receipt.provenanceDigest,
                "canonical_sequence" to receipt.sequence,
                "owner_status" to candidate.status,
                "planned_task_digest" to plannedTaskDigest,
                "record_inserted" to inserted,
                "schema" to OrchestrationProtocolSchemas.ORCH_002_LOCAL_RESULT,
                "task_id" to candidate.taskId
            )
        )
        return localResult(
            schema = OrchestrationProtocolSchemas.ORCH_002_LOCAL_RESULT,
            json = json,
            ownerStatus = candidate.status
        )
    }

    private suspend fun finalizeApproval(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        val payload = requirePayload(operation, OrchestrationProtocolSchemas.ORCH_003_PAYLOAD)
        permanentCheck(payload.getString("task_id") == operation.subjectId)
        permanentCheck(payload.getString("decision") == "approve")
        permanentCheck(
            payload.getString("expected_owner_status") ==
                AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL
        )
        val task = requireTask(operation.subjectId)
        requireTaskIdentity(task, payload.getString("task_identity_digest"))

        val updated = when {
            task.status == AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL &&
                task.approvalId == null -> {
                val changed = organDao.approveDelegatedTaskIfAwaitingApproval(
                    taskId = task.taskId,
                    approvalId = operation.operationId,
                    updatedAtMillis = operation.occurredAtMillis
                )
                if (changed == 1) {
                    true
                } else {
                    val durable = requireTask(task.taskId)
                    permanentCheck(
                        durable.status == AgentCapabilityPolicy.STATUS_APPROVED &&
                            durable.approvalId == operation.operationId,
                        CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT
                    )
                    false
                }
            }

            task.status == AgentCapabilityPolicy.STATUS_APPROVED &&
                task.approvalId == operation.operationId -> false

            else -> throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
        }

        val json = localResultJson(
            mapOf(
                "approval_id" to operation.operationId,
                "canonical_event_hash" to receipt.eventHash,
                "canonical_event_id" to receipt.eventId,
                "canonical_provenance_digest" to receipt.provenanceDigest,
                "canonical_sequence" to receipt.sequence,
                "owner_status" to AgentCapabilityPolicy.STATUS_APPROVED,
                "record_updated" to updated,
                "schema" to OrchestrationProtocolSchemas.ORCH_003_LOCAL_RESULT,
                "task_id" to task.taskId,
                "task_identity_digest" to payload.getString("task_identity_digest")
            )
        )
        return localResult(
            schema = OrchestrationProtocolSchemas.ORCH_003_LOCAL_RESULT,
            json = json,
            ownerStatus = AgentCapabilityPolicy.STATUS_APPROVED
        )
    }

    private suspend fun finalizeRejection(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        val payload = requirePayload(operation, OrchestrationProtocolSchemas.ORCH_004_PAYLOAD)
        permanentCheck(payload.getString("task_id") == operation.subjectId)
        permanentCheck(payload.getString("decision") == "reject")
        permanentCheck(
            payload.getString("expected_owner_status") ==
                AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL
        )
        val reason = payload.getString("reason")
        permanentCheck(
            payload.getString("reason_digest") ==
                CrossDatabaseOperationIdentity.digestUtf8(reason),
            CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
        )
        val task = requireTask(operation.subjectId)
        requireTaskIdentity(task, payload.getString("task_identity_digest"))

        val updated = when {
            task.status == AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL &&
                task.approvalId == null -> {
                val changed = organDao.rejectDelegatedTaskIfAwaitingApproval(
                    taskId = task.taskId,
                    errorSummary = reason,
                    updatedAtMillis = operation.occurredAtMillis,
                    completedAtMillis = operation.occurredAtMillis
                )
                if (changed == 1) {
                    true
                } else {
                    val durable = requireTask(task.taskId)
                    permanentCheck(
                        durable.status == AgentCapabilityPolicy.STATUS_REJECTED &&
                            durable.approvalId == null &&
                            durable.errorSummary == reason,
                        CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT
                    )
                    false
                }
            }

            task.status == AgentCapabilityPolicy.STATUS_REJECTED &&
                task.approvalId == null &&
                task.errorSummary == reason -> false

            else -> throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
        }

        val json = localResultJson(
            mapOf(
                "canonical_event_hash" to receipt.eventHash,
                "canonical_event_id" to receipt.eventId,
                "canonical_provenance_digest" to receipt.provenanceDigest,
                "canonical_sequence" to receipt.sequence,
                "owner_status" to AgentCapabilityPolicy.STATUS_REJECTED,
                "reason_digest" to payload.getString("reason_digest"),
                "record_updated" to updated,
                "schema" to OrchestrationProtocolSchemas.ORCH_004_LOCAL_RESULT,
                "task_id" to task.taskId,
                "task_identity_digest" to payload.getString("task_identity_digest")
            )
        )
        return localResult(
            schema = OrchestrationProtocolSchemas.ORCH_004_LOCAL_RESULT,
            json = json,
            ownerStatus = AgentCapabilityPolicy.STATUS_REJECTED
        )
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

    private suspend fun requireTask(taskId: String): DelegatedTaskEntity {
        return organDao.loadDelegatedTask(taskId)
            ?: throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
    }

    private fun requireTaskIdentity(task: DelegatedTaskEntity, expectedDigest: String) {
        permanentCheck(
            OrchestrationOperationFactory.taskIdentityDigest(task) == expectedDigest,
            CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
        )
    }

    private fun JSONObject.toEntity(operation: CrossDatabaseOperationRecord): DelegatedTaskEntity {
        val targetDeviceId = nullableString("target_device_id")
        val initialErrorSummary = nullableString("initial_error_summary")
        val initialStatus = getString("initial_status")
        return DelegatedTaskEntity(
            taskId = getString("task_id"),
            createdBy = getString("created_by"),
            assignedAgentId = getString("assigned_agent_id"),
            targetDeviceId = targetDeviceId,
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
            errorSummary = initialErrorSummary,
            createdAtMillis = operation.occurredAtMillis,
            updatedAtMillis = operation.occurredAtMillis,
            completedAtMillis = if (initialStatus == AgentCapabilityPolicy.STATUS_REJECTED) {
                operation.occurredAtMillis
            } else {
                null
            }
        )
    }

    private fun JSONObject.nullableString(key: String): String? {
        return if (isNull(key)) null else getString(key)
    }

    private fun localResultJson(values: Map<String, Any?>): String {
        return CrossDatabaseOperationIdentity.canonicalJson(values)
    }

    private fun localResult(
        schema: String,
        json: String,
        ownerStatus: String
    ): CrossDatabaseLocalResult {
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
}
