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

/** Startup gate backed by the canonical identity source and idempotent Ultra bootstrap. */
internal val MorimilAppContainer.genesisUltraRuntimeStartupGate:
    GenesisUltraRuntimeStartupGate
    get() {
        val bootstrap = genesisUltraRuntimeBootstrapCoordinator
        return GenesisUltraRuntimeStartupGate.production(
            identityRepository = genesisUltraRuntimeIdentityRepository,
            bootstrapVerifiedIdentity = { identity ->
                bootstrap.bootstrap(identity)
                Unit
            }
        )
    }
