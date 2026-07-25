package com.morimil.app.data.genesis.ultra

import com.morimil.app.data.local.GenesisUltraBirthCommitEntity
import com.morimil.app.data.local.MorimilDatabase

/**
 * Strengthens the low-level birth-store state with the consent-bound receipt.
 * A birth row without exactly one valid authorization receipt is inconsistent.
 */
internal class GenesisUltraAuthorizedBirthStateAudit(
    private val database: MorimilDatabase
) {
    private val dao = database.genesisUltraBirthDao()

    suspend fun readState(): GenesisUltraPersistedBirthState {
        val baseState = GenesisUltraAtomicBirthStore(database).readState()
        val authorizationCount = dao.countBirthAuthorizations()
        return when {
            baseState == GenesisUltraPersistedBirthState.ABSENT && authorizationCount == 0 -> {
                GenesisUltraPersistedBirthState.ABSENT
            }
            baseState == GenesisUltraPersistedBirthState.COMMITTED && authorizationCount == 1 -> {
                if (runCatching { loadCommittedAuthorization() }.isSuccess) {
                    GenesisUltraPersistedBirthState.COMMITTED
                } else {
                    GenesisUltraPersistedBirthState.INCONSISTENT
                }
            }
            else -> GenesisUltraPersistedBirthState.INCONSISTENT
        }
    }

    suspend fun loadCommittedAuthorization(): GenesisUltraDurableBirthAuthorization {
        require(GenesisUltraAtomicBirthStore(database).readState() == GenesisUltraPersistedBirthState.COMMITTED) {
            "authorized_birth_audit_requires_committed_birth"
        }
        require(dao.countBirthAuthorizations() == 1) {
            "authorized_birth_audit_authorization_count_invalid"
        }
        val commit = requireNotNull(
            dao.loadBirthCommit(GenesisUltraBirthCommitEntity.PRIMARY_SLOT)
        ) { "authorized_birth_audit_commit_missing" }
        val authorization = GenesisUltraDurableBirthAuthorization.fromEntity(
            requireNotNull(
                dao.loadBirthAuthorization(GenesisUltraBirthCommitEntity.PRIMARY_SLOT)
            ) { "authorized_birth_audit_authorization_missing" }
        )
        authorization.requireMatchesCommit(
            commit = commit,
            artifacts = dao.loadBirthArtifacts(commit.slotId)
        )
        return authorization
    }
}
