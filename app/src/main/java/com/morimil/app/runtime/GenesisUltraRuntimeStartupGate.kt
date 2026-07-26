package com.morimil.app.runtime

import com.morimil.app.data.genesis.ultra.GenesisUltraPersistedBirthState
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeAuthorizationState
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentityRepository

/**
 * Final startup authority for the living runtime.
 *
 * A durable COMMITTED marker is necessary but not sufficient. The full identity must also be
 * reconstructed and cryptographically verified against the local Body and Guardian anchors.
 * The post-verification bootstrap receives only that verified projection.
 */
internal class GenesisUltraRuntimeStartupGate private constructor(
    private val readState: suspend () -> GenesisUltraPersistedBirthState,
    private val readCommittedIdentity: suspend () -> GenesisUltraRuntimeIdentity?,
    private val bootstrapVerifiedIdentity: suspend (GenesisUltraRuntimeIdentity) -> Unit
) {
    suspend fun requireReady(): GenesisUltraRuntimeIdentity {
        when (readState()) {
            GenesisUltraPersistedBirthState.ABSENT -> {
                throw IllegalStateException("genesis_ultra_runtime_birth_not_committed")
            }
            GenesisUltraPersistedBirthState.INCONSISTENT -> {
                throw IllegalStateException("genesis_ultra_runtime_birth_inconsistent")
            }
            GenesisUltraPersistedBirthState.COMMITTED -> Unit
        }

        val identity = requireNotNull(readCommittedIdentity()) {
            "genesis_ultra_runtime_identity_not_recoverable"
        }
        require(identity.authorization.state == GenesisUltraRuntimeAuthorizationState.COMMITTED) {
            "genesis_ultra_runtime_authorization_not_committed"
        }
        require(identity.authorization.birthStatus == "born") {
            "genesis_ultra_runtime_birth_status_invalid"
        }
        require(!identity.authorization.ownershipConferred) {
            "genesis_ultra_runtime_ownership_conferred_invalid"
        }
        require(identity.instanceId.isNotBlank() && identity.identityDigest.isNotBlank()) {
            "genesis_ultra_runtime_identity_projection_invalid"
        }

        bootstrapVerifiedIdentity(identity)
        return identity
    }

    internal companion object {
        fun production(
            identityRepository: GenesisUltraRuntimeIdentityRepository,
            bootstrapVerifiedIdentity: suspend (GenesisUltraRuntimeIdentity) -> Unit = {}
        ): GenesisUltraRuntimeStartupGate {
            return GenesisUltraRuntimeStartupGate(
                readState = identityRepository::readState,
                readCommittedIdentity = identityRepository::readCommittedIdentity,
                bootstrapVerifiedIdentity = bootstrapVerifiedIdentity
            )
        }

        fun forTest(
            readState: suspend () -> GenesisUltraPersistedBirthState,
            readCommittedIdentity: suspend () -> GenesisUltraRuntimeIdentity?,
            bootstrapVerifiedIdentity: suspend (GenesisUltraRuntimeIdentity) -> Unit = {}
        ): GenesisUltraRuntimeStartupGate {
            return GenesisUltraRuntimeStartupGate(
                readState = readState,
                readCommittedIdentity = readCommittedIdentity,
                bootstrapVerifiedIdentity = bootstrapVerifiedIdentity
            )
        }
    }
}
