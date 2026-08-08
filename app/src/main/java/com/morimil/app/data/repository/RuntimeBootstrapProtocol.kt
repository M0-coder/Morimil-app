package com.morimil.app.data.repository

import com.morimil.app.core.orchestration.AgentCapabilityPolicy
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity

internal object RuntimeBootstrapProtocolSchemas {
    const val BOOT_001_PAYLOAD = "morimil.runtime_bootstrap.boot_001.payload.v1"
    const val BOOT_001_EVIDENCE = "morimil.runtime_bootstrap.boot_001.evidence.v1"
    const val BOOT_001_PREPARATION = "morimil.runtime_bootstrap.boot_001.preparation.v1"
    const val BOOT_001_LOCAL_RESULT = "morimil.runtime_bootstrap.boot_001.local_result.v1"
}

internal object RuntimeBootstrapOperationFactory {
    const val PROJECT_STATUS =
        "genesis_ultra_runtime_ready;memory=canonical;boot=durable;" +
            "rest_cycle=canonical_adapter_pending;recalls=canonical_adapter_pending;health=ready"

    fun initialize(identity: GenesisUltraRuntimeIdentity): CrossDatabaseStageCommand {
        require(identity.instanceId != identity.activeBody.bodyId) {
            "runtime_bootstrap_instance_body_collision"
        }
        require(identity.authorization.birthStatus == "born") {
            "runtime_bootstrap_birth_not_committed"
        }
        require(!identity.authorization.ownershipConferred) {
            "runtime_bootstrap_ownership_conferred"
        }
        require(identity.activeBody.status == "active_writer") {
            "runtime_bootstrap_writer_not_active"
        }

        val workspaceId = identity.instanceId
        val projectId = "morimil_app:${identity.instanceId}"
        val subjectId = buildString {
            append("bootstrap:")
            append(identity.instanceId)
            append(':')
            append(identity.activeBody.bodyId)
            append(':')
            append(identity.activeBody.keyEpochId)
        }
        val payloadJson = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "active_body_id" to identity.activeBody.bodyId,
                "agent_profiles" to defaultAgentProfiles(),
                "companion_name" to identity.companionName,
                "guardian_anchor_digest" to identity.guardian.anchorDigest,
                "guardian_id" to identity.guardian.guardianId,
                "guardian_role" to identity.guardian.role,
                "identity_digest" to identity.identityDigest,
                "instance_id" to identity.instanceId,
                "orchestrator_devices" to defaultDevices(identity),
                "ownership_conferred" to false,
                "project" to mapOf(
                    "project_id" to projectId,
                    "status" to PROJECT_STATUS,
                    "title" to "Morimil_app"
                ),
                "schema" to RuntimeBootstrapProtocolSchemas.BOOT_001_PAYLOAD,
                "seed_id" to identity.seed.seedId,
                "seed_root_hash" to identity.seed.rootHash,
                "workspace" to mapOf(
                    "display_name" to identity.companionName,
                    "genesis_source" to
                        "genesis-ultra:${identity.seed.seedId}:${identity.identityDigest}",
                    "local_primary" to true,
                    "optional_repo_name" to null,
                    "optional_repo_owner" to null,
                    "optional_repo_private" to false,
                    "repo_proposal_approved" to false,
                    "workspace_id" to workspaceId
                ),
                "writer_epoch" to identity.activeBody.keyEpochId
            )
        )
        val payloadDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(payloadJson)
        val operationId = CrossDatabaseOperationIdentity.operationId(
            operationType = RuntimeBootstrapProtocolTypes.INITIALIZE,
            operationVersion = RuntimeBootstrapProtocolTypes.VERSION,
            instanceId = identity.instanceId,
            writerBodyId = identity.activeBody.bodyId,
            writerEpoch = identity.activeBody.keyEpochId,
            subjectId = subjectId,
            parentOperationId = null,
            childPhase = null,
            payloadDigest = payloadDigest
        )
        val evidenceJson = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "active_body_id" to identity.activeBody.bodyId,
                "identity_digest" to identity.identityDigest,
                "instance_id" to identity.instanceId,
                "operation_id" to operationId,
                "owner_type" to RuntimeBootstrapProtocolTypes.OWNER_TYPE,
                "ownership_conferred" to false,
                "payload_digest" to payloadDigest,
                "projection_model" to "rebuildable_runtime_projection",
                "schema" to RuntimeBootstrapProtocolSchemas.BOOT_001_EVIDENCE,
                "seed_root_hash" to identity.seed.rootHash,
                "successor_body_rebootstrap_allowed" to true,
                "writer_epoch" to identity.activeBody.keyEpochId
            )
        )
        val evidenceDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(evidenceJson)
        val eventId = CrossDatabaseOperationIdentity.eventId(
            operationId = operationId,
            eventType = RuntimeBootstrapProtocolTypes.INITIALIZED_EVENT
        )
        val eventBody = buildString {
            append("Genesis Ultra runtime bootstrap projection committed; instance=")
            append(identity.instanceId)
            append("; writer_body=")
            append(identity.activeBody.bodyId)
            append("; writer_epoch=")
            append(identity.activeBody.keyEpochId)
            append("; workspace=")
            append(workspaceId)
            append("; project=")
            append(projectId)
        }

        return CrossDatabaseStageCommand(
            operationId = operationId,
            ownerType = RuntimeBootstrapProtocolTypes.OWNER_TYPE,
            operationType = RuntimeBootstrapProtocolTypes.INITIALIZE,
            operationVersion = RuntimeBootstrapProtocolTypes.VERSION,
            instanceId = identity.instanceId,
            writerBodyId = identity.activeBody.bodyId,
            writerEpoch = identity.activeBody.keyEpochId,
            subjectId = subjectId,
            parentOperationId = null,
            childPhase = null,
            payloadSchema = RuntimeBootstrapProtocolSchemas.BOOT_001_PAYLOAD,
            payloadJson = payloadJson,
            payloadDigest = payloadDigest,
            eventId = eventId,
            eventType = RuntimeBootstrapProtocolTypes.INITIALIZED_EVENT,
            eventBody = eventBody,
            evidenceSchema = RuntimeBootstrapProtocolSchemas.BOOT_001_EVIDENCE,
            evidenceJson = evidenceJson,
            evidenceDigest = evidenceDigest
        )
    }

    private fun defaultAgentProfiles(): List<Map<String, Any?>> {
        val allTransports = encodedList(
            listOf(
                AgentCapabilityPolicy.TRANSPORT_WIFI,
                AgentCapabilityPolicy.TRANSPORT_BLUETOOTH,
                AgentCapabilityPolicy.TRANSPORT_USB,
                AgentCapabilityPolicy.TRANSPORT_INTERNET,
                AgentCapabilityPolicy.TRANSPORT_MANUAL
            )
        )
        return listOf(
            agent(
                AgentCapabilityPolicy.AGENT_GITHUB,
                "GitHub Agent",
                "github",
                listOf("read_repository", "inspect_branch", "propose_diff"),
                "medium",
                allTransports
            ),
            agent(
                AgentCapabilityPolicy.AGENT_ANDROID_BUILD,
                "Android Build Agent",
                "android_build",
                listOf("run_gradle_tests", "run_assemble_debug"),
                "medium",
                allTransports
            ),
            agent(
                AgentCapabilityPolicy.AGENT_FILE_AUDIT,
                "File Audit Agent",
                "file_audit",
                listOf("read_allowed_files", "propose_patch"),
                "medium",
                allTransports
            ),
            agent(
                AgentCapabilityPolicy.AGENT_RESEARCH,
                "Research Agent",
                "research",
                listOf("research_web", "summarize_sources"),
                "low",
                allTransports
            ),
            agent(
                AgentCapabilityPolicy.AGENT_DESIGN,
                "Design Agent",
                "design",
                listOf("inspect_ui", "produce_design_notes"),
                "low",
                allTransports
            ),
            agent(
                AgentCapabilityPolicy.AGENT_SECURITY,
                "Security Agent",
                "security",
                listOf("audit_permissions", "audit_risk"),
                "low",
                allTransports
            ),
            agent(
                AgentCapabilityPolicy.AGENT_PC_EXECUTOR,
                "PC Executor Agent",
                "pc_executor",
                listOf("prepare_command", "await_human_approval", "report_result"),
                "high",
                allTransports
            )
        )
    }

    private fun agent(
        agentId: String,
        displayName: String,
        role: String,
        capabilities: List<String>,
        riskLevel: String,
        allTransports: String
    ): Map<String, Any?> {
        val capabilityJson = encodedList(capabilities)
        return mapOf(
            "agent_id" to agentId,
            "allowed_toolset_json" to capabilityJson,
            "allowed_transports_json" to allTransports,
            "capability_set_json" to capabilityJson,
            "description" to "Perfil base Genesis Ultra: $role",
            "display_name" to displayName,
            "requires_human_approval" to true,
            "risk_level" to riskLevel,
            "role" to role,
            "status" to AgentCapabilityPolicy.STATUS_ACTIVE
        )
    }

    private fun defaultDevices(identity: GenesisUltraRuntimeIdentity): List<Map<String, Any?>> {
        return listOf(
            device(
                deviceId = identity.activeBody.bodyId,
                displayName = "${identity.companionName} Body",
                deviceType = identity.activeBody.platformProfile,
                transports = listOf(
                    AgentCapabilityPolicy.TRANSPORT_WIFI,
                    AgentCapabilityPolicy.TRANSPORT_BLUETOOTH,
                    AgentCapabilityPolicy.TRANSPORT_MANUAL
                ),
                authorizationStatus = "authorized",
                pairingState = "genesis_ultra_bound",
                riskLevel = "low"
            ),
            device(
                "personal_pc",
                "PC principal",
                "windows_pc",
                listOf(
                    AgentCapabilityPolicy.TRANSPORT_WIFI,
                    AgentCapabilityPolicy.TRANSPORT_USB,
                    AgentCapabilityPolicy.TRANSPORT_INTERNET
                ),
                "pending_authorization",
                "not_paired",
                "high"
            ),
            device(
                "personal_laptop",
                "Laptop personal",
                "laptop",
                listOf(
                    AgentCapabilityPolicy.TRANSPORT_WIFI,
                    AgentCapabilityPolicy.TRANSPORT_BLUETOOTH,
                    AgentCapabilityPolicy.TRANSPORT_INTERNET
                ),
                "pending_authorization",
                "not_paired",
                "medium"
            ),
            device(
                "personal_tablet",
                "Tablet personal",
                "tablet",
                listOf(
                    AgentCapabilityPolicy.TRANSPORT_WIFI,
                    AgentCapabilityPolicy.TRANSPORT_BLUETOOTH,
                    AgentCapabilityPolicy.TRANSPORT_MANUAL
                ),
                "pending_authorization",
                "not_paired",
                "medium"
            )
        )
    }

    private fun device(
        deviceId: String,
        displayName: String,
        deviceType: String,
        transports: List<String>,
        authorizationStatus: String,
        pairingState: String,
        riskLevel: String
    ): Map<String, Any?> {
        return mapOf(
            "allowed_transports_json" to encodedList(transports),
            "authorization_required" to (authorizationStatus != "authorized"),
            "authorization_status" to authorizationStatus,
            "device_id" to deviceId,
            "device_type" to deviceType,
            "display_name" to displayName,
            "ownership_scope" to "self_body_or_user_device",
            "pairing_state" to pairingState,
            "risk_level" to riskLevel,
            "trusted_owner" to "guardian_without_ownership"
        )
    }

    private fun encodedList(values: List<String>): String =
        CrossDatabaseOperationIdentity.canonicalJson(values)
}
