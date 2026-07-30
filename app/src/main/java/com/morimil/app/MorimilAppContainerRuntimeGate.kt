package com.morimil.app

import com.morimil.app.runtime.GenesisUltraRuntimeBootstrapCoordinator
import com.morimil.app.runtime.GenesisUltraRuntimeStartupGate

/** Stateless canonical bootstrap backed only by verified Genesis Ultra identity. */
internal val MorimilAppContainer.genesisUltraRuntimeBootstrapCoordinator:
    GenesisUltraRuntimeBootstrapCoordinator
    get() = GenesisUltraRuntimeBootstrapCoordinator.production(
        memoryDatabase = memoryDatabase,
        organDatabase = organDatabase
    )

/** Startup gate backed by canonical identity, memory convergence and Ultra bootstrap. */
internal val MorimilAppContainer.genesisUltraRuntimeStartupGate:
    GenesisUltraRuntimeStartupGate
    get() {
        val convergence = legacyMemoryConvergenceCoordinator
        val cognitiveRead = canonicalCognitiveMigrationReadPort
        val cognitiveRecovery = cognitiveMigrationProtocolCoordinator
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
                convergence.converge(identity)
                val vaultRecovery = projectVaultRecovery.recoverPendingOperations()
                check(vaultRecovery.blockedCount == 0) { "project_vault_outbox_blocked" }
                bootstrap.bootstrap(identity)
                Unit
            }
        )
    }
