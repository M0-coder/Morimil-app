package com.morimil.app

import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentityRepository

/**
 * Stateless canonical identity boundary backed by the container's durable Ultra stores.
 * Consumers must use this source instead of legacy identity tables or bundled Genesis assets.
 */
internal val MorimilAppContainer.genesisUltraRuntimeIdentityRepository:
    GenesisUltraRuntimeIdentityRepository
    get() = GenesisUltraRuntimeIdentityRepository.production(
        database = memoryDatabase,
        bodyIdentityRootStore = genesisUltraBodyIdentityRootStore,
        guardianTrustAnchorStore = genesisUltraGuardianTrustAnchorStore
    )
