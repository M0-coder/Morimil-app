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
