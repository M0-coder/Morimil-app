package com.morimil.app.runtime

import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.genesis.ultra.LegacyBirthConflictProbe
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.LegacyMemoryConvergenceEntity
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.repository.CrossDatabaseOperationCoordinator
import com.morimil.app.data.repository.RuntimeBootstrapOperationFactory
import com.morimil.app.data.repository.RuntimeBootstrapProtocolSchemas
import com.morimil.app.data.repository.RuntimeBootstrapProtocolTypes
import org.json.JSONObject

internal enum class GenesisUltraRuntimeSubsystemState {
    READY,
    WAITING_FOR_CANONICAL_MEMORY_ADAPTER
}

internal enum class GenesisUltraRuntimeHealthState {
    READY,
    WAITING_FOR_DEPENDENCIES
}

internal object GenesisUltraRuntimeHealthConvergence {
    fun evaluate(
        legacyMemoryConverged: Boolean,
        restCycleState: GenesisUltraRuntimeSubsystemState,
        recallState: GenesisUltraRuntimeSubsystemState
    ): GenesisUltraRuntimeHealthState {
        return if (
            legacyMemoryConverged &&
            restCycleState == GenesisUltraRuntimeSubsystemState.READY &&
            recallState == GenesisUltraRuntimeSubsystemState.READY
        ) {
            GenesisUltraRuntimeHealthState.READY
        } else {
            GenesisUltraRuntimeHealthState.WAITING_FOR_DEPENDENCIES
        }
    }
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
    val legacyMemoryConverged: Boolean,
    val healthState: GenesisUltraRuntimeHealthState,
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
        require(legacyCounts.isEmpty || legacyMemoryConverged) {
            "runtime_bootstrap_legacy_rows_not_converged"
        }
        require(
            healthState == GenesisUltraRuntimeHealthConvergence.evaluate(
                legacyMemoryConverged = legacyMemoryConverged,
                restCycleState = restCycleState,
                recallState = recallState
            )
        ) { "runtime_bootstrap_health_state_inconsistent" }
    }
}

/**
 * Durable post-birth initializer for Genesis Ultra runtime projections.
 *
 * BOOT-001 now stages one deterministic XOP operation scoped to the current
 * writer Body/epoch. The canonical receipt is committed before either database
 * receives new bootstrap projection state. Memory-database projection is the
 * recoverable saga preparation; MemoryOrgan projection and XOP COMMITTED state
 * finalize atomically in the owner database.
 *
 * The subject is writer-epoch scoped, so a future F5 successor Body can rebuild
 * projections without changing instanceId or colliding with a previous Body's
 * completed BOOT operation.
 */
internal class GenesisUltraRuntimeBootstrapCoordinator private constructor(
    private val inspectLegacyCounts: suspend () -> GenesisUltraRuntimeLegacyCounts,
    private val executeDurableBootstrap: suspend (
        GenesisUltraRuntimeIdentity,
        Long
    ) -> GenesisUltraRuntimeProjection,
    private val countAgentProfiles: suspend () -> Int,
    private val countOrchestratorDevices: suspend () -> Int,
    private val countCanonicalMemoryEvents: suspend () -> Int,
    private val isLegacyMemoryConverged: suspend (String) -> Boolean = { false },
    private val probeRestCycleReady: suspend (GenesisUltraRuntimeIdentity) -> Boolean = { false },
    private val probeRecallReady: suspend (GenesisUltraRuntimeIdentity) -> Boolean = { false }
) {
    suspend fun bootstrap(
        identity: GenesisUltraRuntimeIdentity,
        nowMillis: Long = System.currentTimeMillis()
    ): GenesisUltraRuntimeBootstrapReport {
        val before = inspectLegacyCounts()
        val convergedBefore = before.isEmpty || isLegacyMemoryConverged(identity.instanceId)
        require(convergedBefore) { "runtime_bootstrap_legacy_identity_conflict" }

        val projection = executeDurableBootstrap(identity, nowMillis)
        val orchestration = GenesisUltraRuntimeOrchestrationSeed(
            agentProfileCount = countAgentProfiles(),
            orchestratorDeviceCount = countOrchestratorDevices()
        )
        val canonicalMemoryEventCount = countCanonicalMemoryEvents()

        val after = inspectLegacyCounts()
        val convergedAfter = after.isEmpty || isLegacyMemoryConverged(identity.instanceId)
        require(convergedAfter) { "runtime_bootstrap_created_unconverged_legacy_identity" }

        val restCycleState = if (probeRestCycleReady(identity)) {
            GenesisUltraRuntimeSubsystemState.READY
        } else {
            GenesisUltraRuntimeSubsystemState.WAITING_FOR_CANONICAL_MEMORY_ADAPTER
        }
        val recallState = if (probeRecallReady(identity)) {
            GenesisUltraRuntimeSubsystemState.READY
        } else {
            GenesisUltraRuntimeSubsystemState.WAITING_FOR_CANONICAL_MEMORY_ADAPTER
        }
        val healthState = GenesisUltraRuntimeHealthConvergence.evaluate(
            legacyMemoryConverged = convergedAfter,
            restCycleState = restCycleState,
            recallState = recallState
        )

        return GenesisUltraRuntimeBootstrapReport(
            instanceId = identity.instanceId,
            companionName = identity.companionName,
            workspaceId = projection.workspaceId,
            projectId = projection.projectId,
            agentProfileCount = orchestration.agentProfileCount,
            orchestratorDeviceCount = orchestration.orchestratorDeviceCount,
            canonicalMemoryEventCount = canonicalMemoryEventCount,
            legacyMemoryConverged = convergedAfter,
            healthState = healthState,
            restCycleState = restCycleState,
            recallState = recallState,
            legacyCounts = after
        )
    }

    internal companion object {
        private const val RECOVERY_LIMIT = 64

        fun production(
            memoryDatabase: MorimilDatabase,
            organDatabase: MemoryOrganDatabase,
            protocol: CrossDatabaseOperationCoordinator,
            probeRestCycleReady: suspend (GenesisUltraRuntimeIdentity) -> Boolean = { false },
            probeRecallReady: suspend (GenesisUltraRuntimeIdentity) -> Boolean = { false }
        ): GenesisUltraRuntimeBootstrapCoordinator {
            val legacyConflictProbe = LegacyBirthConflictProbe.production(memoryDatabase)
            val organDao = organDatabase.memoryOrganDao()
            return GenesisUltraRuntimeBootstrapCoordinator(
                inspectLegacyCounts = {
                    val counts = legacyConflictProbe.inspect()
                    GenesisUltraRuntimeLegacyCounts(
                        localIdentityCount = counts.localIdentityCount,
                        genesisCoreCount = counts.genesisCoreCount
                    )
                },
                executeDurableBootstrap = { identity, _ ->
                    val recovery = protocol.recoverBeforeMutation(
                        identity = identity,
                        ownerType = RuntimeBootstrapProtocolTypes.OWNER_TYPE,
                        limit = RECOVERY_LIMIT
                    )
                    check(recovery.blockedCount == 0) {
                        "runtime_bootstrap_protocol_blocked"
                    }
                    check(recovery.retryableFailureCount == 0) {
                        "runtime_bootstrap_protocol_recovery_incomplete"
                    }

                    val operation = protocol.execute(
                        identity = identity,
                        command = RuntimeBootstrapOperationFactory.initialize(identity)
                    )
                    check(operation.status == CrossDatabaseOperationStatus.COMMITTED) {
                        "runtime_bootstrap_protocol_not_committed"
                    }
                    check(
                        operation.localResultSchema ==
                            RuntimeBootstrapProtocolSchemas.BOOT_001_LOCAL_RESULT
                    ) { "runtime_bootstrap_protocol_result_schema_invalid" }
                    val result = JSONObject(
                        requireNotNull(operation.localResultJson) {
                            "runtime_bootstrap_protocol_result_missing"
                        }
                    )
                    check(
                        result.getString("schema") ==
                            RuntimeBootstrapProtocolSchemas.BOOT_001_LOCAL_RESULT
                    ) { "runtime_bootstrap_protocol_result_invalid" }
                    val workspaceId = result.getString("workspace_id")
                    val projectId = result.getString("project_id")
                    check(workspaceId == identity.instanceId) {
                        "runtime_bootstrap_protocol_workspace_mismatch"
                    }
                    check(projectId == "morimil_app:${identity.instanceId}") {
                        "runtime_bootstrap_protocol_project_mismatch"
                    }
                    GenesisUltraRuntimeProjection(
                        workspaceId = workspaceId,
                        projectId = projectId
                    )
                },
                countAgentProfiles = organDao::countAgentProfiles,
                countOrchestratorDevices = organDao::countOrchestratorDevices,
                countCanonicalMemoryEvents = {
                    memoryDatabase.genesisUltraMemoryDao().countAll()
                },
                isLegacyMemoryConverged = { instanceId ->
                    val state = memoryDatabase.legacyMemoryConvergenceDao().loadState()
                    state?.instanceId == instanceId &&
                        state.status == LegacyMemoryConvergenceEntity.STATUS_COMPLETE &&
                        state.activeWriter == LegacyMemoryConvergenceEntity.WRITER_GENESIS_ULTRA &&
                        state.legacyReadOnly &&
                        state.failureCode == null
                },
                probeRestCycleReady = probeRestCycleReady,
                probeRecallReady = probeRecallReady
            )
        }

        fun forTest(
            inspectLegacyCounts: suspend () -> GenesisUltraRuntimeLegacyCounts,
            executeDurableBootstrap: suspend (
                GenesisUltraRuntimeIdentity,
                Long
            ) -> GenesisUltraRuntimeProjection,
            countAgentProfiles: suspend () -> Int,
            countOrchestratorDevices: suspend () -> Int,
            countCanonicalMemoryEvents: suspend () -> Int,
            isLegacyMemoryConverged: suspend (String) -> Boolean = { false },
            probeRestCycleReady: suspend (GenesisUltraRuntimeIdentity) -> Boolean = { false },
            probeRecallReady: suspend (GenesisUltraRuntimeIdentity) -> Boolean = { false }
        ): GenesisUltraRuntimeBootstrapCoordinator {
            return GenesisUltraRuntimeBootstrapCoordinator(
                inspectLegacyCounts = inspectLegacyCounts,
                executeDurableBootstrap = executeDurableBootstrap,
                countAgentProfiles = countAgentProfiles,
                countOrchestratorDevices = countOrchestratorDevices,
                countCanonicalMemoryEvents = countCanonicalMemoryEvents,
                isLegacyMemoryConverged = isLegacyMemoryConverged,
                probeRestCycleReady = probeRestCycleReady,
                probeRecallReady = probeRecallReady
            )
        }
    }
}
