package com.morimil.app.data.genesis

/**
 * Capabilities of the current Android Body. This does not define Morimil's
 * identity or freedom; it only describes what this Body can currently do.
 */
data class MobileAppCapabilities(
    val localCanonicalMemory: Boolean,
    val voicePushToTalk: Boolean,
    val canonicalGenesisUltraIdentity: Boolean,
    val externalReadOnlySync: Boolean,
    val externalWriteExecution: Boolean,
    val pcExecution: Boolean,
    val productionRelease: Boolean,
    val currentAppPhase: String
)

object CurrentMobileAppCapabilities {
    val value = MobileAppCapabilities(
        localCanonicalMemory = true,
        voicePushToTalk = true,
        canonicalGenesisUltraIdentity = true,
        externalReadOnlySync = false,
        externalWriteExecution = false,
        pcExecution = false,
        productionRelease = false,
        currentAppPhase = "f3_3a_canonical_runtime_surface"
    )
}
