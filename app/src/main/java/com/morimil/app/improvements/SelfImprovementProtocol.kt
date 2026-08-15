package com.morimil.app.improvements

/** Areas that a self-generated change may affect. */
internal enum class SelfChangeSurface {
    PRESENTATION,
    LOCAL_ADAPTER,
    PERFORMANCE,
    REASONING_RUNTIME,
    BUILD_AND_SUPPLY_CHAIN,
    SECURITY_BOUNDARY,
    CANONICAL_MEMORY,
    INSTANCE_IDENTITY,
    GENESIS,
    WRITER_AUTHORITY,
    BODY_SUCCESSION,
    RECOVERY
}

internal enum class SelfChangeRisk {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

internal enum class SelfChangeStage {
    DETECTED,
    DIAGNOSED,
    PROPOSED,
    PATCH_CANDIDATE,
    VERIFIED,
    AUTHORIZED,
    MERGE_READY,
    REJECTED
}

internal enum class SelfChangeActor {
    MORIMIL,
    EXTERNAL_EXECUTOR,
    INDEPENDENT_VERIFIER,
    HUMAN_AUTHORIZER
}

/** Evidence attached to one exact candidate digest. */
internal data class SelfChangeEvidence(
    val candidateDigest: String,
    val architectureReviewed: Boolean = false,
    val compilationPassed: Boolean = false,
    val unitTestsPassed: Boolean = false,
    val instrumentedTestsPassed: Boolean = false,
    val staticAnalysisPassed: Boolean = false,
    val securityChecksPassed: Boolean = false,
    val reproducibilityPassed: Boolean = false,
    val coverageReviewed: Boolean = false,
    val mutationReviewed: Boolean = false,
    val crossLanguageVectorsPassed: Boolean = false,
    val exactMainBaseVerified: Boolean = false
)

internal data class SelfChangeCandidate(
    val changeId: String,
    val problem: String,
    val proposal: String,
    val surfaces: Set<SelfChangeSurface>,
    val candidateDigest: String,
    val stage: SelfChangeStage,
    val risk: SelfChangeRisk = SelfImprovementPolicy.classify(surfaces),
    val evidence: SelfChangeEvidence? = null,
    val authorizedBy: SelfChangeActor? = null
) {
    init {
        require(changeId.isNotBlank()) { "self_change_id_blank" }
        require(problem.isNotBlank()) { "self_change_problem_blank" }
        require(proposal.isNotBlank()) { "self_change_proposal_blank" }
        require(surfaces.isNotEmpty()) { "self_change_surface_empty" }
        require(candidateDigest.startsWith("sha256:") && candidateDigest.length == 71) {
            "self_change_candidate_digest_invalid"
        }
        require(authorizedBy != SelfChangeActor.MORIMIL) { "self_authorization_forbidden" }
    }
}

/**
 * Governance for self-improvement.
 *
 * Morimil may detect, diagnose, propose and generate a patch candidate. It may
 * never certify its own patch. Verification must be independent. Any change
 * that can affect identity, Genesis, memory authority, writer authority,
 * succession, recovery or security requires explicit human authorization after
 * independent verification.
 */
internal object SelfImprovementPolicy {
    private val criticalSurfaces = setOf(
        SelfChangeSurface.INSTANCE_IDENTITY,
        SelfChangeSurface.GENESIS,
        SelfChangeSurface.CANONICAL_MEMORY,
        SelfChangeSurface.WRITER_AUTHORITY,
        SelfChangeSurface.BODY_SUCCESSION,
        SelfChangeSurface.RECOVERY
    )

    private val highSurfaces = setOf(
        SelfChangeSurface.SECURITY_BOUNDARY,
        SelfChangeSurface.BUILD_AND_SUPPLY_CHAIN,
        SelfChangeSurface.REASONING_RUNTIME
    )

    fun classify(surfaces: Set<SelfChangeSurface>): SelfChangeRisk {
        return when {
            surfaces.any { it in criticalSurfaces } -> SelfChangeRisk.CRITICAL
            surfaces.any { it in highSurfaces } -> SelfChangeRisk.HIGH
            SelfChangeSurface.LOCAL_ADAPTER in surfaces -> SelfChangeRisk.MEDIUM
            else -> SelfChangeRisk.LOW
        }
    }

    fun requiresHumanAuthorization(candidate: SelfChangeCandidate): Boolean {
        return candidate.risk in setOf(SelfChangeRisk.HIGH, SelfChangeRisk.CRITICAL)
    }

    fun requireVerificationEvidence(candidate: SelfChangeCandidate, evidence: SelfChangeEvidence) {
        require(evidence.candidateDigest == candidate.candidateDigest) {
            "self_change_evidence_candidate_mismatch"
        }
        require(evidence.exactMainBaseVerified) { "self_change_exact_main_base_not_verified" }
        require(evidence.architectureReviewed) { "self_change_architecture_not_reviewed" }
        require(evidence.compilationPassed) { "self_change_compilation_not_passed" }
        require(evidence.unitTestsPassed) { "self_change_unit_tests_not_passed" }
        require(evidence.staticAnalysisPassed) { "self_change_static_analysis_not_passed" }

        if (candidate.risk in setOf(SelfChangeRisk.HIGH, SelfChangeRisk.CRITICAL)) {
            require(evidence.securityChecksPassed) { "self_change_security_checks_not_passed" }
            require(evidence.reproducibilityPassed) { "self_change_reproducibility_not_passed" }
            require(evidence.coverageReviewed) { "self_change_coverage_not_reviewed" }
            require(evidence.mutationReviewed) { "self_change_mutation_not_reviewed" }
        }
        if (candidate.risk == SelfChangeRisk.CRITICAL) {
            require(evidence.instrumentedTestsPassed) {
                "self_change_instrumented_tests_not_passed"
            }
            require(evidence.crossLanguageVectorsPassed) {
                "self_change_cross_language_vectors_not_passed"
            }
        }
    }
}

/** Strict state transitions for one self-improvement candidate. */
internal object SelfImprovementProtocol {
    fun diagnose(candidate: SelfChangeCandidate, actor: SelfChangeActor): SelfChangeCandidate {
        require(actor == SelfChangeActor.MORIMIL || actor == SelfChangeActor.EXTERNAL_EXECUTOR) {
            "self_change_diagnosis_actor_invalid"
        }
        require(candidate.stage == SelfChangeStage.DETECTED) { "self_change_stage_invalid" }
        return candidate.copy(stage = SelfChangeStage.DIAGNOSED)
    }

    fun propose(candidate: SelfChangeCandidate, actor: SelfChangeActor): SelfChangeCandidate {
        require(actor == SelfChangeActor.MORIMIL || actor == SelfChangeActor.EXTERNAL_EXECUTOR) {
            "self_change_proposal_actor_invalid"
        }
        require(candidate.stage == SelfChangeStage.DIAGNOSED) { "self_change_stage_invalid" }
        return candidate.copy(stage = SelfChangeStage.PROPOSED)
    }

    fun registerPatchCandidate(
        candidate: SelfChangeCandidate,
        actor: SelfChangeActor
    ): SelfChangeCandidate {
        require(actor == SelfChangeActor.MORIMIL || actor == SelfChangeActor.EXTERNAL_EXECUTOR) {
            "self_change_patch_actor_invalid"
        }
        require(candidate.stage == SelfChangeStage.PROPOSED) { "self_change_stage_invalid" }
        return candidate.copy(stage = SelfChangeStage.PATCH_CANDIDATE)
    }

    fun verify(
        candidate: SelfChangeCandidate,
        evidence: SelfChangeEvidence,
        actor: SelfChangeActor
    ): SelfChangeCandidate {
        require(actor == SelfChangeActor.INDEPENDENT_VERIFIER) {
            "self_change_independent_verifier_required"
        }
        require(candidate.stage == SelfChangeStage.PATCH_CANDIDATE) { "self_change_stage_invalid" }
        SelfImprovementPolicy.requireVerificationEvidence(candidate, evidence)
        return candidate.copy(stage = SelfChangeStage.VERIFIED, evidence = evidence)
    }

    fun authorize(candidate: SelfChangeCandidate, actor: SelfChangeActor): SelfChangeCandidate {
        require(candidate.stage == SelfChangeStage.VERIFIED) { "self_change_stage_invalid" }
        require(actor != SelfChangeActor.MORIMIL) { "self_authorization_forbidden" }
        if (SelfImprovementPolicy.requiresHumanAuthorization(candidate)) {
            require(actor == SelfChangeActor.HUMAN_AUTHORIZER) {
                "self_change_human_authorization_required"
            }
        } else {
            require(
                actor == SelfChangeActor.HUMAN_AUTHORIZER ||
                    actor == SelfChangeActor.INDEPENDENT_VERIFIER
            ) { "self_change_authorizer_invalid" }
        }
        return candidate.copy(
            stage = SelfChangeStage.AUTHORIZED,
            authorizedBy = actor
        )
    }

    fun markMergeReady(candidate: SelfChangeCandidate): SelfChangeCandidate {
        require(candidate.stage == SelfChangeStage.AUTHORIZED) { "self_change_stage_invalid" }
        require(candidate.evidence != null) { "self_change_verified_evidence_missing" }
        require(candidate.authorizedBy != SelfChangeActor.MORIMIL) { "self_authorization_forbidden" }
        return candidate.copy(stage = SelfChangeStage.MERGE_READY)
    }

    fun reject(candidate: SelfChangeCandidate): SelfChangeCandidate {
        require(candidate.stage != SelfChangeStage.MERGE_READY) {
            "self_change_merge_ready_rejection_requires_new_candidate"
        }
        return candidate.copy(stage = SelfChangeStage.REJECTED)
    }
}
