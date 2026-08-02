package com.morimil.app

import com.morimil.app.data.genesis.ultra.CanonicalConsumerReadPort
import com.morimil.app.data.genesis.ultra.GenesisUltraCanonicalConsumerReadAdapter

/** Shared read-only canonical consumer boundary for bounded product owners. */
internal val MorimilAppContainer.canonicalConsumerReadPort: CanonicalConsumerReadPort
    get() = GenesisUltraCanonicalConsumerReadAdapter.production(
        identityRepository = genesisUltraRuntimeIdentityRepository,
        memoryRepository = canonicalMemoryRepository
    )
