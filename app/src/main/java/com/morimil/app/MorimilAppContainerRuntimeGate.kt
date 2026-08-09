package com.morimil.app

import com.morimil.app.runtime.GenesisUltraRuntimeBootstrapCoordinator
import com.morimil.app.runtime.GenesisUltraRuntimeStartupGate

/** Durable canonical bootstrap backed only by verified Genesis Ultra identity. */
internal val MorimilAppContainer.genesisUltraRuntimeBootstrapCoordinator:
    GenesisUltraRuntimeBootstrapCoordinator
    get() = GenesisUltraRuntimeBootstrapCoordinator.production(
        memoryDatabase = memoryDatabase,
        organDatabase = organDatabase,
        protocol = runtimeBootstrapProtocolCoordinator,
        probeRestCycleReady = { identity ->
            restCycleRepository.isBootstrapReady(identity)
        }
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
        val restCycleRecovery = restCycleProtocolCoordinator
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
                val restCycleReport = restCycleRecovery.recoverAtStartup(
                    identity = identity,
                    limit = 200
                )
                check(restCycleReport.blockedCount == 0) {
                    "rest_cycle_protocol_blocked"
                }
                check(restCycleReport.retryableFailureCount == 0) {
                    "rest_cycle_protocol_recovery_incomplete"
                }
                convergence.converge(identity)
                val vaultRecovery = projectVaultRecovery.recoverPendingOperations()
                check(vaultRecovery.blockedCount == 0) { "project_vault_outbox_blocked" }
                // BOOT-001 recovery is owner-scoped inside bootstrap and intentionally
                // runs only after legacy memory convergence is known durable. REST readiness
                // is then probed read-only through the same verified canonical planning boundary
                // used by RestCycleRepository; RECALL readiness remains a separate front.
                bootstrap.bootstrap(identity)
                Unit
            }
        )
    }
