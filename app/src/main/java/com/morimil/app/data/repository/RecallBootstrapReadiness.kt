package com.morimil.app.data.repository

import com.morimil.app.data.genesis.ultra.CanonicalReadDisposition
import com.morimil.app.data.genesis.ultra.CanonicalReadResult
import com.morimil.app.data.genesis.ultra.CanonicalRecallCandidateBatch

/** Pure disposition resolver for the read-only RECALL startup readiness probe. */
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
}
