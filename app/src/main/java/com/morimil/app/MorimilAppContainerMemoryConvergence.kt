package com.morimil.app

import com.morimil.app.data.genesis.ultra.CanonicalLivingMemoryPort
import com.morimil.app.data.genesis.ultra.LegacyMemoryConvergenceCoordinator
import com.morimil.app.data.repository.LivingMemoryPort

internal val MorimilAppContainer.canonicalLivingMemoryPort: LivingMemoryPort
    get() = CanonicalLivingMemoryPort(canonicalMemoryRepository)

internal val MorimilAppContainer.legacyMemoryConvergenceCoordinator:
    LegacyMemoryConvergenceCoordinator
    get() = LegacyMemoryConvergenceCoordinator.production(
        database = memoryDatabase,
        memoryIntegrityCore = memoryIntegrityCore,
        canonicalRepository = canonicalMemoryRepository
    )
