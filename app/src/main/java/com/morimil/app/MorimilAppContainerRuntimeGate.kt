package com.morimil.app

import com.morimil.app.runtime.GenesisUltraRuntimeStartupGate

/** Stateless startup gate backed by the canonical verified Genesis Ultra identity source. */
internal val MorimilAppContainer.genesisUltraRuntimeStartupGate:
    GenesisUltraRuntimeStartupGate
    get() = GenesisUltraRuntimeStartupGate.production(
        identityRepository = genesisUltraRuntimeIdentityRepository
    )
