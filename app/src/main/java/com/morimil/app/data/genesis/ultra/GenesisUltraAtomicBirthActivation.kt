package com.morimil.app.data.genesis.ultra

import androidx.room.withTransaction
import com.morimil.app.data.local.GenesisUltraBirthCommitEntity
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.repository.MemoryAppendGate

/** Result of one indivisible authorization, birth, recovery audit and canonical append. */
internal class GenesisUltraAtomicBirthActivationResult(
    val commit: GenesisUltraBirthCommitEntity,
    val authorization: GenesisUltraDurableBirthAuthorization,
    val recoveredBirth: GenesisUltraRecoveredAtomicBirth,
    val firstPostBirthMemory: GenesisUltraCanonicalMemoryAppendResult
)

/**
 * Isolated activation boundary. It is deliberately not connected to onboarding.
 * The database can never expose a committed birth without also containing a
 * verified, Body-signed sequence-1 memory event from that same birth.
 *
 * A cryptographically verified birth alone is insufficient. The caller must
 * present the short-lived type-state produced after exact candidate binding,
 * explicit host consent and locally anchored Body/Guardian verification.
 */
internal class GenesisUltraAtomicBirthActivationCoordinator(
    private val database: MorimilDatabase,
    private val checkpoint: suspend (GenesisUltraBirthPersistenceCheckpoint) -> Unit = {},
    private val recoverCommitted: suspend (
        GenesisUltraAtomicBirthStore,
        GenesisUltraAtomicBirthRecoveryRequest
    ) -> GenesisUltraRecoveredAtomicBirth? = { store, request ->
        store.recoverCommittedInsideTransaction(request)
    }
) {
    suspend fun activate(
        authorization: GenesisUltraAuthorizedAtomicBirth,
        activatedAt: String,
        persistedAtMillis: Long,
        recoveryRequest: GenesisUltraAtomicBirthRecoveryRequest,
        signer: GenesisUltraBodyMemorySigner,
        firstPostBirthRequest: GenesisUltraCanonicalMemoryAppendRequest
    ): GenesisUltraAtomicBirthActivationResult {
        require(authorization.birthCommitAuthorized) {
            "activation_atomic_birth_not_authorized"
        }
        authorization.requireUsableAt(activatedAt)
        val verifiedBirth = authorization.copyVerifiedBirth()
        val durableAuthorization = GenesisUltraDurableBirthAuthorization.from(authorization)

        require(recoveryRequest.bodyRawPublicKey.contentEquals(signer.key.copyRawPublicKey())) {
            "activation_recovery_body_key_mismatch"
        }
        require(durableAuthorization.bodyId == signer.key.bodyId) {
            "activation_authorization_body_key_mismatch"
        }

        return MemoryAppendGate.withAppendLock {
            database.withTransaction {
                val birthDao = database.genesisUltraBirthDao()
                require(birthDao.countBirthAuthorizations() == 0) {
                    "activation_birth_authorization_not_absent"
                }
                val birthStore = GenesisUltraAtomicBirthStore(database, checkpoint)
                val commit = birthStore.writeVerifiedInsideTransaction(
                    verifiedBirth = verifiedBirth,
                    persistedAtMillis = persistedAtMillis
                )
                birthDao.insertBirthAuthorization(
                    durableAuthorization.toEntity(GenesisUltraBirthCommitEntity.PRIMARY_SLOT)
                )
                val persistedAuthorization = GenesisUltraDurableBirthAuthorization.fromEntity(
                    requireNotNull(
                        birthDao.loadBirthAuthorization(GenesisUltraBirthCommitEntity.PRIMARY_SLOT)
                    ) { "activation_birth_authorization_missing" }
                )
                persistedAuthorization.requireMatchesCommit(
                    commit = commit,
                    artifacts = birthDao.loadBirthArtifacts(commit.slotId)
                )
                require(persistedAuthorization == durableAuthorization) {
                    "activation_birth_authorization_round_trip_mismatch"
                }

                val recovered = requireNotNull(recoverCommitted(birthStore, recoveryRequest)) {
                    "activation_recovery_missing_committed_birth"
                }
                val memory = GenesisUltraCanonicalMemoryStore(database).appendInsideTransaction(
                    recoveredBirth = recovered,
                    signer = signer,
                    request = firstPostBirthRequest
                )
                require(memory.event.sequence == 1L) {
                    "activation_first_post_birth_sequence_invalid"
                }
                require(
                    GenesisUltraAuthorizedBirthStateAudit(database).readState() ==
                        GenesisUltraPersistedBirthState.COMMITTED
                ) { "activation_authorized_birth_audit_failed" }
                GenesisUltraAtomicBirthActivationResult(
                    commit = commit,
                    authorization = persistedAuthorization,
                    recoveredBirth = recovered,
                    firstPostBirthMemory = memory
                )
            }
        }
    }
}
