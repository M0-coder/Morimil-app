package com.morimil.app.runtime

import androidx.room.withTransaction
import com.morimil.app.core.orchestration.AgentCapabilityPolicy
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.local.AgentProfileEntity
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.local.OrchestratorDeviceEntity
import com.morimil.app.data.local.ProjectStateEntity
import com.morimil.app.data.local.UserWorkspaceEntity
import org.json.JSONArray

internal enum class GenesisUltraRuntimeSubsystemState {
    READY,
    WAITING_FOR_CANONICAL_MEMORY_ADAPTER
}

internal data class GenesisUltraRuntimeLegacyCounts(
    val localIdentityCount: Int,
    val genesisCoreCount: Int
) {
    init {
        require(localIdentityCount >= 0) { "runtime_bootstrap_local_identity_count_invalid" }
        require(genesisCoreCount >= 0) { "runtime_bootstrap_genesis_core_count_invalid" }
    }

    val isEmpty: Boolean
        get() = localIdentityCount == 0 && genesisCoreCount == 0
}

internal data class GenesisUltraRuntimeProjection(
    val workspaceId: String,
    val projectId: String
)

internal data class GenesisUltraRuntimeOrchestrationSeed(
    val agentProfileCount: Int,
    val orchestratorDeviceCount: Int
) {
    init {
        require(agentProfileCount >= 0) { "runtime_bootstrap_agent_count_invalid" }
        require(orchestratorDeviceCount >= 0) { "runtime_bootstrap_device_count_invalid" }
    }
}

internal data class GenesisUltraRuntimeBootstrapReport(
    val instanceId: String,
    val companionName: String,
    val workspaceId: String,
    val projectId: String,
    val agentProfileCount: Int,
    val orchestratorDeviceCount: Int,
    val canonicalMemoryEventCount: Int,
    val healthState: GenesisUltraRuntimeSubsystemState,
    val restCycleState: GenesisUltraRuntimeSubsystemState,
    val recallState: GenesisUltraRuntimeSubsystemState,
    val legacyCounts: GenesisUltraRuntimeLegacyCounts
) {
    init {
        require(instanceId.isNotBlank()) { "runtime_bootstrap_instance_id_missing" }
        require(companionName.isNotBlank()) { "runtime_bootstrap_companion_name_missing" }
        require(workspaceId == instanceId) { "runtime_bootstrap_workspace_not_canonical" }
        require(projectId.endsWith(instanceId)) { "runtime_bootstrap_project_not_canonical" }
        require(canonicalMemoryEventCount >= 0) { "runtime_bootstrap_canonical_memory_count_invalid" }
        require(legacyCounts.isEmpty) { "runtime_bootstrap_legacy_rows_present" }
        require(healthState == GenesisUltraRuntimeSubsystemState.READY) {
            "runtime_bootstrap_health_not_ready"
        }
    }
}

/**
 * Idempotent post-birth initializer for a clean Genesis Ultra installation.
 *
 * It projects runtime metadata from the verified identity without creating the
 * legacy local identity or copied Genesis Core. Memory-dependent subsystems
 * remain explicitly blocked until Phase 2 provides their canonical adapter.
 */
internal class GenesisUltraRuntimeBootstrapCoordinator private constructor(
    private val inspectLegacyCounts: suspend () -> GenesisUltraRuntimeLegacyCounts,
    private val writeRuntimeProjection: suspend (
        GenesisUltraRuntimeIdentity,
        Long
    ) -> GenesisUltraRuntimeProjection,
    private val seedOrchestration: suspend (
        GenesisUltraRuntimeIdentity,
        Long
    ) -> GenesisUltraRuntimeOrchestrationSeed,
    private val countCanonicalMemoryEvents: suspend () -> Int
) {
    suspend fun bootstrap(
        identity: GenesisUltraRuntimeIdentity,
        nowMillis: Long = System.currentTimeMillis()
    ): GenesisUltraRuntimeBootstrapReport {
        val before = inspectLegacyCounts()
        require(before.isEmpty) { "runtime_bootstrap_legacy_identity_conflict" }

        val projection = writeRuntimeProjection(identity, nowMillis)
        val orchestration = seedOrchestration(identity, nowMillis)
        val canonicalMemoryEventCount = countCanonicalMemoryEvents()

        val after = inspectLegacyCounts()
        require(after.isEmpty) { "runtime_bootstrap_created_legacy_identity" }

        return GenesisUltraRuntimeBootstrapReport(
            instanceId = identity.instanceId,
            companionName = identity.companionName,
            workspaceId = projection.workspaceId,
            projectId = projection.projectId,
            agentProfileCount = orchestration.agentProfileCount,
            orchestratorDeviceCount = orchestration.orchestratorDeviceCount,
            canonicalMemoryEventCount = canonicalMemoryEventCount,
            healthState = GenesisUltraRuntimeSubsystemState.READY,
            restCycleState = GenesisUltraRuntimeSubsystemState.WAITING_FOR_CANONICAL_MEMORY_ADAPTER,
            recallState = GenesisUltraRuntimeSubsystemState.WAITING_FOR_CANONICAL_MEMORY_ADAPTER,
            legacyCounts = after
        )
    }

    internal companion object {
        fun production(
            memoryDatabase: MorimilDatabase,
            organDatabase: MemoryOrganDatabase
        ): GenesisUltraRuntimeBootstrapCoordinator {
            val memoryDao = memoryDatabase.memoryDao()
            val organDao = organDatabase.memoryOrganDao()
            return GenesisUltraRuntimeBootstrapCoordinator(
                inspectLegacyCounts = {
                    GenesisUltraRuntimeLegacyCounts(
                        localIdentityCount = memoryDao.countLocalIdentity(),
                        genesisCoreCount = memoryDao.countGenesisCore()
                    )
                },
                writeRuntimeProjection = { identity, nowMillis ->
                    val workspaceId = identity.instanceId
                    val projectId = "morimil_app:${identity.instanceId}"
                    memoryDatabase.withTransaction {
                        memoryDao.upsertWorkspace(
                            UserWorkspaceEntity(
                                workspaceId = workspaceId,
                                displayName = identity.companionName,
                                genesisSource = "genesis-ultra:${identity.seed.seedId}:${identity.identityDigest}",
                                localPrimary = true,
                                optionalRepoOwner = null,
                                optionalRepoName = null,
                                optionalRepoPrivate = false,
                                repoProposalApproved = false,
                                updatedAtMillis = nowMillis
                            )
                        )
                        memoryDao.upsertProject(
                            ProjectStateEntity(
                                projectId = projectId,
                                title = "Morimil_app",
                                status = PROJECT_STATUS,
                                updatedAtMillis = nowMillis
                            )
                        )
                    }
                    GenesisUltraRuntimeProjection(
                        workspaceId = workspaceId,
                        projectId = projectId
                    )
                },
                seedOrchestration = { identity, nowMillis ->
                    if (organDao.countAgentProfiles() == 0) {
                        organDao.insertAgentProfiles(defaultAgents(nowMillis))
                    }
                    if (organDao.countOrchestratorDevices() == 0) {
                        organDao.insertOrchestratorDevices(
                            defaultDevices(
                                identity = identity,
                                nowMillis = nowMillis
                            )
                        )
                    }
                    GenesisUltraRuntimeOrchestrationSeed(
                        agentProfileCount = organDao.countAgentProfiles(),
                        orchestratorDeviceCount = organDao.countOrchestratorDevices()
                    )
                },
                countCanonicalMemoryEvents = {
                    memoryDatabase.genesisUltraMemoryDao().countAll()
                }
            )
        }

        fun forTest(
            inspectLegacyCounts: suspend () -> GenesisUltraRuntimeLegacyCounts,
            writeRuntimeProjection: suspend (
                GenesisUltraRuntimeIdentity,
                Long
            ) -> GenesisUltraRuntimeProjection,
            seedOrchestration: suspend (
                GenesisUltraRuntimeIdentity,
                Long
            ) -> GenesisUltraRuntimeOrchestrationSeed,
            countCanonicalMemoryEvents: suspend () -> Int
        ): GenesisUltraRuntimeBootstrapCoordinator {
            return GenesisUltraRuntimeBootstrapCoordinator(
                inspectLegacyCounts = inspectLegacyCounts,
                writeRuntimeProjection = writeRuntimeProjection,
                seedOrchestration = seedOrchestration,
                countCanonicalMemoryEvents = countCanonicalMemoryEvents
            )
        }

        private fun defaultAgents(nowMillis: Long): List<AgentProfileEntity> {
            return listOf(
                agent(AgentCapabilityPolicy.AGENT_GITHUB, "GitHub Agent", "github", listOf("read_repository", "inspect_branch", "propose_diff"), "medium", nowMillis),
                agent(AgentCapabilityPolicy.AGENT_ANDROID_BUILD, "Android Build Agent", "android_build", listOf("run_gradle_tests", "run_assemble_debug"), "medium", nowMillis),
                agent(AgentCapabilityPolicy.AGENT_FILE_AUDIT, "File Audit Agent", "file_audit", listOf("read_allowed_files", "propose_patch"), "medium", nowMillis),
                agent(AgentCapabilityPolicy.AGENT_RESEARCH, "Research Agent", "research", listOf("research_web", "summarize_sources"), "low", nowMillis),
                agent(AgentCapabilityPolicy.AGENT_DESIGN, "Design Agent", "design", listOf("inspect_ui", "produce_design_notes"), "low", nowMillis),
                agent(AgentCapabilityPolicy.AGENT_SECURITY, "Security Agent", "security", listOf("audit_permissions", "audit_risk"), "low", nowMillis),
                agent(AgentCapabilityPolicy.AGENT_PC_EXECUTOR, "PC Executor Agent", "pc_executor", listOf("prepare_command", "await_human_approval", "report_result"), "high", nowMillis)
            )
        }

        private fun agent(
            agentId: String,
            displayName: String,
            role: String,
            capabilities: List<String>,
            riskLevel: String,
            nowMillis: Long
        ): AgentProfileEntity {
            return AgentProfileEntity(
                agentId = agentId,
                displayName = displayName,
                role = role,
                description = "Perfil base Genesis Ultra: $role",
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

        private fun defaultDevices(
            identity: GenesisUltraRuntimeIdentity,
            nowMillis: Long
        ): List<OrchestratorDeviceEntity> {
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
                    riskLevel = "low",
                    nowMillis = nowMillis
                ),
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
                ownershipScope = "self_body_or_user_device",
                trustedOwner = "guardian_without_ownership",
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

        private const val PROJECT_STATUS =
            "genesis_ultra_runtime_ready;memory=phase_2_pending;rest_cycle=phase_2_pending;recalls=phase_2_pending;health=ready"
    }
}
