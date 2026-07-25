package com.morimil.app.data.genesis

import com.morimil.app.data.genesis.ultra.GenesisUltraBirthCandidate
import com.morimil.app.data.genesis.ultra.GenesisUltraBirthCandidateAssessment
import com.morimil.app.data.genesis.ultra.GenesisUltraBirthCandidateValidator

object GenesisUltraIntegrationGate {
    const val BLOCK_CODE = "genesis_ultra_birth_not_ready"

    fun assess(
        candidate: GenesisUltraBirthCandidate,
        evaluatedAt: String
    ): GenesisUltraBirthCandidateAssessment {
        return GenesisUltraBirthCandidateValidator.assess(candidate, evaluatedAt)
    }

    fun requireBirthReady(candidate: GenesisUltraBirthCandidate, evaluatedAt: String) {
        val assessment = assess(candidate, evaluatedAt)
        check(assessment.birthReady) {
            "$BLOCK_CODE: issues=${assessment.issues}; blockers=${assessment.remainingBlockers}"
        }
    }
}
