package com.morimil.app.data.repository

import android.database.Cursor
import androidx.room.withTransaction
import com.morimil.app.data.local.AgentProfileEntity
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.local.OrchestratorDeviceEntity
import com.morimil.app.data.local.ProjectStateEntity
import com.morimil.app.data.local.UserWorkspaceEntity
import org.json.JSONArray
import org.json.JSONObject

internal interface RuntimeBootstrapMemoryProjectionStore {
    suspend fun ensureProjection(
        workspace: UserWorkspaceEntity,
        project: ProjectStateEntity
    )
}

internal interface RuntimeBootstrapOrganProjectionStore {
    suspend fun seedAgentProfilesIfEmpty(agents: List<AgentProfileEntity>): Int
    suspend fun seedOrchestratorDevicesIfEmpty(devices: List<OrchestratorDeviceEntity>): Int
}

private class RoomRuntimeBootstrapMemoryProjectionStore(
    private val database: MorimilDatabase
) : RuntimeBootstrapMemoryProjectionStore {
    private val dao = database.memoryDao()

    override suspend fun ensureProjection(
        workspace: UserWorkspaceEntity,
        project: ProjectStateEntity
    ) {
        database.withTransaction {
            val existingWorkspace = loadWorkspace(workspace.workspaceId)
            if (existingWorkspace == null) {
                dao.upsertWorkspace(workspace)
            } else {
                permanentCheck(
                    existingWorkspace.workspaceId == workspace.workspaceId &&
                        existingWorkspace.displayName == workspace.displayName &&
                        existingWorkspace.genesisSource == workspace.genesisSource &&
                        existingWorkspace.localPrimary == workspace.localPrimary
                )
            }

            val existingProject = loadProject(project.projectId)
            if (existingProject == null) {
                dao.upsertProject(project)
            } else {
                permanentCheck(
                    existingProject.projectId == project.projectId &&
                        existingProject.title == project.title
                )
                if (existingProject.status != project.status) {
                    dao.upsertProject(project)
                }
            }

            val durableWorkspace = loadWorkspace(workspace.workspaceId)
            permanentCheck(
                durableWorkspace != null &&
                    durableWorkspace.workspaceId == workspace.workspaceId &&
                    durableWorkspace.displayName == workspace.displayName &&
                    durableWorkspace.genesisSource == workspace.genesisSource &&
                    durableWorkspace.localPrimary == workspace.localPrimary
            )
            val durableProject = loadProject(project.projectId)
            permanentCheck(
                durableProject != null &&
                    durableProject.projectId == project.projectId &&
                    durableProject.title == project.title &&
                    durableProject.status == project.status
            )
        }
    }

    private fun loadWorkspace(workspaceId: String): WorkspaceSemantics? {
        return database.openHelper.writableDatabase.query(
            "SELECT workspaceId, displayName, genesisSource, localPrimary " +
                "FROM user_workspace WHERE workspaceId = ? LIMIT 1",
            arrayOf(workspaceId)
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            WorkspaceSemantics(
                workspaceId = cursor.string("workspaceId"),
                displayName = cursor.string("displayName"),
                genesisSource = cursor.string("genesisSource"),
                localPrimary = cursor.boolean("localPrimary")
            )
        }
    }

    private fun loadProject(projectId: String): ProjectSemantics? {
        return database.openHelper.writableDatabase.query(
            "SELECT projectId, title, status FROM project_state WHERE projectId = ? LIMIT 1",
            arrayOf(projectId)
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            ProjectSemantics(
                projectId = cursor.string("projectId"),
                title = cursor.string("title"),
                status = cursor.string("status")
            )
        }
    }
}

private class RoomRuntimeBootstrapOrganProjectionStore(
    private val database: MemoryOrganDatabase
) : RuntimeBootstrapOrganProjectionStore {
    private val dao = database.memoryOrganDao()

    override suspend fun seedAgentProfilesIfEmpty(agents: List<AgentProfileEntity>): Int {
        val before = dao.countAgentProfiles()
        if (before > 0) return before
        dao.insertAgentProfiles(agents)
        val after = dao.countAgentProfiles()
        permanentCheck(after == agents.size)
        return after
    }

    override suspend fun seedOrchestratorDevicesIfEmpty(
        devices: List<OrchestratorDeviceEntity>
    ): Int {
        val before = dao.countOrchestratorDevices()
        if (before > 0) return before
        dao.insertOrchestratorDevices(devices)
        val after = dao.countOrchestratorDevices()
        permanentCheck(after == devices.size)
        return after
    }
}

internal class RuntimeBootstrapProtocolFinalizer private constructor(
    private val memoryStore: RuntimeBootstrapMemoryProjectionStore,
    private val organStore: RuntimeBootstrapOrganProjectionStore
) : CrossDatabaseTypedFinalizer {
    constructor(
        memoryDatabase: MorimilDatabase,
        organDatabase: MemoryOrganDatabase
    ) : this(
        memoryStore = RoomRuntimeBootstrapMemoryProjectionStore(memoryDatabase),
        organStore = RoomRuntimeBootstrapOrganProjectionStore(organDatabase)
    )

    override val supportedOperationTypes: Set<String> =
        RuntimeBootstrapProtocolTypes.CLOSED_REGISTRY.keys

    override suspend fun prepareOutsideTransaction(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseFinalizationPreparation {
        requireOperation(operation, receipt)
        val plan = decodePlan(operation)
        memoryStore.ensureProjection(plan.workspace, plan.project)
        return preparation(operation, receipt, plan)
    }

    override suspend fun finalizeInsideTransaction(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        throw CrossDatabaseProtocolErrors.permanent(
            CrossDatabaseProtocolErrors.FINALIZATION_PREPARATION_CONFLICT
        )
    }

    override suspend fun finalizePreparedInsideTransaction(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt,
        preparation: CrossDatabaseFinalizationPreparation?
    ): CrossDatabaseLocalResult {
        requireOperation(operation, receipt)
        val plan = decodePlan(operation)
        val expectedPreparation = preparation(operation, receipt, plan)
        permanentCheck(
            preparation != null,
            CrossDatabaseProtocolErrors.FINALIZATION_PREPARATION_CONFLICT
        )
        permanentCheck(
            preparation == expectedPreparation,
            CrossDatabaseProtocolErrors.FINALIZATION_PREPARATION_CONFLICT
        )

        // Preserve pre-existing orchestration state. ORCH-001 owns convergence of
        // legacy/noncanonical seed rows; BOOT only reproduces the old "seed if empty"
        // behavior behind a durable receipt and must not silently overwrite that state.
        val agentProfileCount = organStore.seedAgentProfilesIfEmpty(plan.agentProfiles)
        val orchestratorDeviceCount =
            organStore.seedOrchestratorDevicesIfEmpty(plan.orchestratorDevices)

        val json = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "agent_profile_count" to agentProfileCount,
                "canonical_event_hash" to receipt.eventHash,
                "canonical_event_id" to receipt.eventId,
                "canonical_provenance_digest" to receipt.provenanceDigest,
                "canonical_sequence" to receipt.sequence,
                "orchestrator_device_count" to orchestratorDeviceCount,
                "owner_status" to OWNER_STATUS,
                "project_id" to plan.project.projectId,
                "schema" to RuntimeBootstrapProtocolSchemas.BOOT_001_LOCAL_RESULT,
                "workspace_id" to plan.workspace.workspaceId
            )
        )
        return CrossDatabaseLocalResult(
            schema = RuntimeBootstrapProtocolSchemas.BOOT_001_LOCAL_RESULT,
            json = json,
            digest = CrossDatabaseOperationIdentity.digestCanonicalJson(json),
            ownerStatus = OWNER_STATUS
        )
    }

    private fun requireOperation(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ) {
        permanentCheck(
            operation.status == CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT,
            CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT
        )
        permanentCheck(
            operation.ownerType == RuntimeBootstrapProtocolTypes.OWNER_TYPE &&
                operation.operationType == RuntimeBootstrapProtocolTypes.INITIALIZE &&
                operation.operationVersion == RuntimeBootstrapProtocolTypes.VERSION,
            CrossDatabaseProtocolErrors.UNSUPPORTED_OPERATION_VERSION
        )
        permanentCheck(
            operation.payloadSchema == RuntimeBootstrapProtocolSchemas.BOOT_001_PAYLOAD,
            CrossDatabaseProtocolErrors.UNSUPPORTED_PAYLOAD_SCHEMA
        )
        permanentCheck(
            receipt.eventId == operation.eventId,
            CrossDatabaseProtocolErrors.CANONICAL_RECEIPT_CONFLICT
        )
    }

    private fun decodePlan(operation: CrossDatabaseOperationRecord): RuntimeBootstrapPlan {
        val payload = try {
            JSONObject(operation.payloadJson)
        } catch (failure: Throwable) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.UNSUPPORTED_PAYLOAD_SCHEMA,
                failure
            )
        }
        permanentCheck(
            payload.getString("schema") == RuntimeBootstrapProtocolSchemas.BOOT_001_PAYLOAD,
            CrossDatabaseProtocolErrors.UNSUPPORTED_PAYLOAD_SCHEMA
        )
        permanentCheck(payload.getString("instance_id") == operation.instanceId)
        permanentCheck(payload.getString("active_body_id") == operation.writerBodyId)
        permanentCheck(payload.getString("writer_epoch") == operation.writerEpoch)
        permanentCheck(!payload.getBoolean("ownership_conferred"))
        permanentCheck(
            payload.getString("guardian_role") == RuntimeBootstrapOperationFactory.GUARDIAN_ROLE
        )

        val workspaceObject = payload.getJSONObject("workspace")
        val projectObject = payload.getJSONObject("project")
        val workspace = UserWorkspaceEntity(
            workspaceId = workspaceObject.getString("workspace_id"),
            displayName = workspaceObject.getString("display_name"),
            genesisSource = workspaceObject.getString("genesis_source"),
            localPrimary = workspaceObject.getBoolean("local_primary"),
            optionalRepoOwner = workspaceObject.nullableString("optional_repo_owner"),
            optionalRepoName = workspaceObject.nullableString("optional_repo_name"),
            optionalRepoPrivate = workspaceObject.getBoolean("optional_repo_private"),
            repoProposalApproved = workspaceObject.getBoolean("repo_proposal_approved"),
            updatedAtMillis = operation.occurredAtMillis
        )
        val project = ProjectStateEntity(
            projectId = projectObject.getString("project_id"),
            title = projectObject.getString("title"),
            status = projectObject.getString("status"),
            updatedAtMillis = operation.occurredAtMillis
        )
        permanentCheck(workspace.workspaceId == operation.instanceId)
        permanentCheck(project.projectId == "morimil_app:${operation.instanceId}")

        return RuntimeBootstrapPlan(
            workspace = workspace,
            project = project,
            agentProfiles = payload.getJSONArray("agent_profiles").decodeAgents(operation),
            orchestratorDevices = payload.getJSONArray("orchestrator_devices")
                .decodeDevices(operation)
        )
    }

    private fun JSONArray.decodeAgents(
        operation: CrossDatabaseOperationRecord
    ): List<AgentProfileEntity> {
        val result = (0 until length()).map { index ->
            val item = getJSONObject(index)
            AgentProfileEntity(
                agentId = item.getString("agent_id"),
                displayName = item.getString("display_name"),
                role = item.getString("role"),
                description = item.getString("description"),
                capabilitySetJson = item.getString("capability_set_json"),
                allowedToolsetJson = item.getString("allowed_toolset_json"),
                allowedTransportsJson = item.getString("allowed_transports_json"),
                riskLevel = item.getString("risk_level"),
                requiresHumanApproval = item.getBoolean("requires_human_approval"),
                status = item.getString("status"),
                createdAtMillis = operation.occurredAtMillis,
                updatedAtMillis = operation.occurredAtMillis
            )
        }
        permanentCheck(result.size == EXPECTED_AGENT_PROFILE_COUNT)
        permanentCheck(result.map { item -> item.agentId }.toSet().size == result.size)
        return result
    }

    private fun JSONArray.decodeDevices(
        operation: CrossDatabaseOperationRecord
    ): List<OrchestratorDeviceEntity> {
        val result = (0 until length()).map { index ->
            val item = getJSONObject(index)
            val authorizationStatus = item.getString("authorization_status")
            OrchestratorDeviceEntity(
                deviceId = item.getString("device_id"),
                displayName = item.getString("display_name"),
                deviceType = item.getString("device_type"),
                ownershipScope = item.getString("ownership_scope"),
                trustedOwner = item.getString("trusted_owner"),
                allowedTransportsJson = item.getString("allowed_transports_json"),
                authorizationStatus = authorizationStatus,
                authorizationRequired = item.getBoolean("authorization_required"),
                riskLevel = item.getString("risk_level"),
                pairingState = item.getString("pairing_state"),
                lastSeenAtMillis = if (authorizationStatus == "authorized") {
                    operation.occurredAtMillis
                } else {
                    null
                },
                createdAtMillis = operation.occurredAtMillis,
                updatedAtMillis = operation.occurredAtMillis
            )
        }
        permanentCheck(result.size == EXPECTED_DEVICE_COUNT)
        permanentCheck(result.map { item -> item.deviceId }.toSet().size == result.size)
        permanentCheck(result.any { item ->
            item.deviceId == operation.writerBodyId &&
                item.authorizationStatus == "authorized" &&
                item.pairingState == "genesis_ultra_bound"
        })
        return result
    }

    private fun preparation(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt,
        plan: RuntimeBootstrapPlan
    ): CrossDatabaseFinalizationPreparation {
        val json = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "canonical_event_hash" to receipt.eventHash,
                "operation_id" to operation.operationId,
                "payload_digest" to operation.payloadDigest,
                "project_id" to plan.project.projectId,
                "schema" to RuntimeBootstrapProtocolSchemas.BOOT_001_PREPARATION,
                "workspace_id" to plan.workspace.workspaceId
            )
        )
        return CrossDatabaseFinalizationPreparation(
            operationId = operation.operationId,
            receiptEventHash = receipt.eventHash,
            payloadDigest = operation.payloadDigest,
            schema = RuntimeBootstrapProtocolSchemas.BOOT_001_PREPARATION,
            json = json,
            digest = CrossDatabaseOperationIdentity.digestCanonicalJson(json)
        )
    }

    internal companion object {
        const val OWNER_STATUS = "runtime_bootstrap_ready"
        const val EXPECTED_AGENT_PROFILE_COUNT = 7
        const val EXPECTED_DEVICE_COUNT = 4

        fun testing(
            memoryStore: RuntimeBootstrapMemoryProjectionStore,
            organStore: RuntimeBootstrapOrganProjectionStore
        ): RuntimeBootstrapProtocolFinalizer {
            return RuntimeBootstrapProtocolFinalizer(memoryStore, organStore)
        }
    }
}

private data class RuntimeBootstrapPlan(
    val workspace: UserWorkspaceEntity,
    val project: ProjectStateEntity,
    val agentProfiles: List<AgentProfileEntity>,
    val orchestratorDevices: List<OrchestratorDeviceEntity>
)

private data class WorkspaceSemantics(
    val workspaceId: String,
    val displayName: String,
    val genesisSource: String,
    val localPrimary: Boolean
)

private data class ProjectSemantics(
    val projectId: String,
    val title: String,
    val status: String
)

private fun JSONObject.nullableString(key: String): String? {
    return if (isNull(key)) null else getString(key)
}

private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))
private fun Cursor.boolean(column: String): Boolean = getInt(getColumnIndexOrThrow(column)) != 0

private fun permanentCheck(
    condition: Boolean,
    code: String = CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
) {
    if (!condition) throw CrossDatabaseProtocolErrors.permanent(code)
}
