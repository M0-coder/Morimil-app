package com.morimil.app.data.repository

import com.morimil.app.data.genesis.ultra.CanonicalReadDisposition
import com.morimil.app.data.genesis.ultra.CanonicalReadResult
import com.morimil.app.data.genesis.ultra.CanonicalRecallCandidateBatch
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity

/** Pure resolver and identity-binding validator for the read-only RECALL startup probe. */
internal object RecallBootstrapReadiness {
    fun resolve(
        result: CanonicalReadResult<CanonicalRecallCandidateBatch>
    ): CanonicalRecallCandidateBatch? {
        return when (result) {
            is CanonicalReadResult.Ready -> result.value
            is CanonicalReadResult.Blocked -> {
                if (result.failure.disposition == CanonicalReadDisposition.NOT_READY) {
                    null
                } else {
                    throw CanonicalRecallReadException(result.failure)
                }
            }
        }
    }

    fun requireIdentityBinding(
        identity: GenesisUltraRuntimeIdentity,
        batch: CanonicalRecallCandidateBatch
    ) {
        require(batch.instanceId == identity.instanceId) {
            "canonical_recall_bootstrap_foreign_instance"
        }
        require(batch.snapshot.instanceId == identity.instanceId) {
            "canonical_recall_bootstrap_snapshot_instance_mismatch"
        }
        require(batch.snapshot.snapshotDigest.isNotBlank()) {
            "canonical_recall_bootstrap_snapshot_digest_missing"
        }
        require(batch.snapshot.birthRootEventHash.isNotBlank()) {
            "canonical_recall_bootstrap_birth_root_missing"
        }
        require(batch.writerBodyId == identity.activeBody.bodyId) {
            "canonical_recall_bootstrap_wrong_body"
        }
        require(batch.writerEpochId == identity.activeBody.keyEpochId) {
            "canonical_recall_bootstrap_stale_epoch"
        }
        batch.candidates.forEach { candidate ->
            require(candidate.event.instanceId == identity.instanceId) {
                "canonical_recall_bootstrap_candidate_foreign_instance"
            }
            require(candidate.event.bodyId == identity.activeBody.bodyId) {
                "canonical_recall_bootstrap_candidate_wrong_body"
            }
            require(candidate.event.signerId == identity.activeBody.bodyId) {
                "canonical_recall_bootstrap_candidate_wrong_signer"
            }
            require(candidate.event.signerEpochId == identity.activeBody.keyEpochId) {
                "canonical_recall_bootstrap_candidate_stale_epoch"
            }
            require(candidate.event.eventHash.isNotBlank()) {
                "canonical_recall_bootstrap_candidate_hash_missing"
            }
        }
    }
}
