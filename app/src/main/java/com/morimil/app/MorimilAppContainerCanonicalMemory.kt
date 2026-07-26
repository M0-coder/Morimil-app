package com.morimil.app

import com.morimil.app.data.genesis.ultra.CanonicalMemoryRepository

/** Single product memory boundary backed by Genesis Ultra event and payload stores. */
internal val MorimilAppContainer.canonicalMemoryRepository: CanonicalMemoryRepository
    get() = CanonicalMemoryRepository.production(
        database = memoryDatabase,
        bodyIdentityRootStore = genesisUltraBodyIdentityRootStore,
        guardianTrustAnchorStore = genesisUltraGuardianTrustAnchorStore,
        identityRepository = genesisUltraRuntimeIdentityRepository
    )
