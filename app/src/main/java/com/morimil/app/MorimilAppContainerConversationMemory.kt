package com.morimil.app

import com.morimil.app.data.genesis.ultra.ConversationMemoryPromotionCoordinator

/** Explicit Guardian-approved bridge from transcript to signed canonical memory. */
internal val MorimilAppContainer.conversationMemoryPromotionCoordinator:
    ConversationMemoryPromotionCoordinator
    get() = ConversationMemoryPromotionCoordinator.production(
        canonicalRepository = canonicalMemoryRepository,
        identityRepository = genesisUltraRuntimeIdentityRepository
    )
