package com.morimil.app.data.repository

internal data class CrossDatabaseProtocolRegistry(
    val ownerType: String,
    val version: Int,
    val closedOperations: Map<String, String>,
    val preRecoveryBlockedPayloadSchemas: Set<String> = emptySet()
) {
    init {
        require(ownerType.isNotBlank()) { "xop_registry_owner_empty" }
        require(version >= 1) { "xop_registry_version_invalid" }
        require(closedOperations.isNotEmpty()) { "xop_registry_empty" }
        require(closedOperations.keys.none(String::isBlank)) { "xop_registry_operation_empty" }
        require(closedOperations.values.none(String::isBlank)) { "xop_registry_event_empty" }
        require(closedOperations.values.toSet().size == closedOperations.size) {
            "xop_registry_event_collision"
        }
        require(preRecoveryBlockedPayloadSchemas.none(String::isBlank)) {
            "xop_registry_blocked_schema_empty"
        }
    }
}

internal val COGNITIVE_MIGRATION_PROTOCOL_REGISTRY = CrossDatabaseProtocolRegistry(
    ownerType = CognitiveMigrationProtocolTypes.OWNER_TYPE,
    version = CognitiveMigrationProtocolTypes.VERSION,
    closedOperations = CognitiveMigrationProtocolTypes.CLOSED_REGISTRY,
    preRecoveryBlockedPayloadSchemas = setOf(
        "morimil.cognitive_migration.cog_001.payload.v1"
    )
)

internal object OrchestrationProtocolTypes {
    const val OWNER_TYPE = "agent_orchestration"

    const val PROPOSE = "agent_orchestration.propose_delegated_task"
    const val APPROVE = "agent_orchestration.approve_delegated_task"
    const val REJECT = "agent_orchestration.reject_delegated_task"

    const val PROPOSED_EVENT = "orchestration.task_proposed"
    const val APPROVED_EVENT = "orchestration.task_approved"
    const val REJECTED_EVENT = "orchestration.task_rejected"

    const val VERSION = 1

    val CLOSED_REGISTRY = mapOf(
        PROPOSE to PROPOSED_EVENT,
        APPROVE to APPROVED_EVENT,
        REJECT to REJECTED_EVENT
    )

    val REGISTRY = CrossDatabaseProtocolRegistry(
        ownerType = OWNER_TYPE,
        version = VERSION,
        closedOperations = CLOSED_REGISTRY
    )
}

internal object AgentLifecycleProtocolTypes {
    const val OWNER_TYPE = "agent_instance_lifecycle"

    const val CREATE = "agent_lifecycle.create_agent"
    const val ASSIGN = "agent_lifecycle.assign_task"
    const val SUBMIT_RESULT = "agent_lifecycle.submit_result"
    const val EVALUATE = "agent_lifecycle.evaluate_agent"
    const val RETIRE = "agent_lifecycle.retire_agent"
    const val PROMOTE = "agent_lifecycle.promote_agent"
    const val QUARANTINE = "agent_lifecycle.quarantine_agent"

    const val CREATED_EVENT = "agent_lifecycle.agent_created"
    const val ASSIGNED_EVENT = "agent_lifecycle.task_assigned"
    const val RESULT_EVENT = "agent_lifecycle.result_submitted"
    const val EVALUATED_EVENT = "agent_lifecycle.agent_evaluated"
    const val RETIRED_EVENT = "agent_lifecycle.agent_retired"
    const val PROMOTED_EVENT = "agent_lifecycle.agent_promoted"
    const val QUARANTINED_EVENT = "agent_lifecycle.agent_quarantined"

    const val VERSION = 1

    val CLOSED_REGISTRY = mapOf(
        CREATE to CREATED_EVENT,
        ASSIGN to ASSIGNED_EVENT,
        SUBMIT_RESULT to RESULT_EVENT,
        EVALUATE to EVALUATED_EVENT,
        RETIRE to RETIRED_EVENT,
        PROMOTE to PROMOTED_EVENT,
        QUARANTINE to QUARANTINED_EVENT
    )

    val REGISTRY = CrossDatabaseProtocolRegistry(
        ownerType = OWNER_TYPE,
        version = VERSION,
        closedOperations = CLOSED_REGISTRY
    )
}

internal object RuntimeBootstrapProtocolTypes {
    const val OWNER_TYPE = "runtime_bootstrap"
    const val INITIALIZE = "runtime_bootstrap.initialize"
    const val INITIALIZED_EVENT = "runtime.bootstrap_initialized"
    const val VERSION = 1

    val CLOSED_REGISTRY = mapOf(
        INITIALIZE to INITIALIZED_EVENT
    )

    val REGISTRY = CrossDatabaseProtocolRegistry(
        ownerType = OWNER_TYPE,
        version = VERSION,
        closedOperations = CLOSED_REGISTRY
    )
}

internal object RestCycleProtocolTypes {
    const val OWNER_TYPE = "rest_cycle"
    const val EXECUTE = "rest_cycle.execute"
    const val PROPOSE_REPAIR = "rest_cycle.propose_repair"
    const val EXECUTED_EVENT = "rest_cycle.local_consolidation"
    const val REPAIR_PROPOSED_EVENT = "memory.repair_proposed"
    const val VERSION = 1

    val CLOSED_REGISTRY = mapOf(
        EXECUTE to EXECUTED_EVENT,
        PROPOSE_REPAIR to REPAIR_PROPOSED_EVENT
    )

    val REGISTRY = CrossDatabaseProtocolRegistry(
        ownerType = OWNER_TYPE,
        version = VERSION,
        closedOperations = CLOSED_REGISTRY
    )
}
