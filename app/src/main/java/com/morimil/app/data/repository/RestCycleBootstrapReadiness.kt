package com.morimil.app.data.repository

import com.morimil.app.data.genesis.ultra.CanonicalReadDisposition
import com.morimil.app.data.genesis.ultra.CanonicalReadResult
import com.morimil.app.data.genesis.ultra.CanonicalRestCyclePlanningInput

/** Pure disposition resolver for the read-only REST startup readiness probe. */
internal object RestCycleBootstrapReadiness {
    fun resolve(
        result: CanonicalReadResult<CanonicalRestCyclePlanningInput>
    ): CanonicalRestCyclePlanningInput? {
        return when (result) {
            is CanonicalReadResult.Ready -> result.value
            is CanonicalReadResult.Blocked -> {
                if (result.failure.disposition == CanonicalReadDisposition.NOT_READY) {
                    null
                } else {
                    throw CanonicalRestCycleReadException(result.failure)
                }
            }
        }
    }
}
