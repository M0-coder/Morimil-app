package com.morimil.app

import com.morimil.app.runtime.GenesisUltraRuntimeBootstrapCoordinator
import com.morimil.app.runtime.GenesisUltraRuntimeStartupGate

/** Durable canonical bootstrap backed only by verified Genesis Ultra identity. */
internal val MorimilAppContainer.genesisUltraRuntimeBootstrapCoordinator:
    GenesisUltraRuntimeBootstrapCoordinator
    get() = GenesisUltraRuntimeBootstrapCoordinator.production(
        memoryDatabase = memoryDatabase,
        organDatabase = organDatabase,
        protocol = runtimeBootstrapProtocolCoordinator
    )

/** Startup gate backed by canonical identity, memory convergence and Ultra bootstrap. */
internal val MorimilAppContainer.genesisUltraRuntimeStartupGate:
    GenesisUltraRuntimeStartupGate
    get() {
        val convergence = legacyMemoryConvergenceCoordinator
        val cognitiveRead = canonicalCognitiveMigrationReadPort
        val cognitiveRecovery = cognitiveMigrationProtocolCoordinator
        val orchestrationRecovery = orchestrationProtocolCoordinator
        val agentLifecycleRecovery = agentLifecycleProtocolCoordinator
        val projectVaultRecovery = projectVaultRepository
        val bootstrap = genesisUltraRuntimeBootstrapCoordinator
        return GenesisUltraRuntimeStartupGate.production(
            identityRepository = genesisUltraRuntimeIdentityRepository,
            bootstrapVerifiedIdentity = { identity ->
                cognitiveRead.readVerifiedPlanningInput()
                val cognitiveReport = cognitiveRecovery.recoverAtStartup(
                    identity = identity,
                    limit = 200
                )
                check(cognitiveReport.blockedCount == 0) {
                    "cognitive_migration_protocol_blocked"
                }
                check(cognitiveReport.retryableFailureCount == 0) {
                    "cognitive_migration_protocol_recovery_incomplete"
                }
                val orchestrationReport = orchestrationRecovery.recoverAtStartup(
                    identity = identity,
                    limit = 200
                )
                check(orchestrationReport.blockedCount == 0) {
                    "orchestration_protocol_blocked"
                }
                check(orchestrationReport.retryableFailureCount == 0) {
                    "orchestration_protocol_recovery_incomplete"
                }
                val agentLifecycleReport = agentLifecycleRecovery.recoverAtStartup(
                    identity = identity,
                    limit = 200
                )
                check(agentLifecycleReport.blockedCount == 0) {
                    "agent_lifecycle_protocol_blocked"
                }
                check(agentLifecycleReport.retryableFailureCount == 0) {
                    "agent_lifecycle_protocol_recovery_incomplete"
                }
                convergence.converge(identity)
                val vaultRecovery = projectVaultRecovery.recoverPendingOperations()
                check(vaultRecovery.blockedCount == 0) { "project_vault_outbox_blocked" }
                // BOOT-001 recovery is owner-scoped inside bootstrap and intentionally
                // runs only after legacy memory convergence is known durable.
                bootstrap.bootstrap(identity)
                Unit
            }
        )
    }
