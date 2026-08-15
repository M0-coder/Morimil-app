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

/** Stable description of the observed problem before any patch exists. */
internal data class SelfChangeObservation(
    val changeId: String,
    val problem: String,
    val proposal: String,
    val surfaces: Set<SelfChangeSurface>,
    val observationDigest: String
) {
    init {
        require(changeId.isNotBlank()) { "self_change_id_blank" }
        require(problem.isNotBlank()) { "self_change_problem_blank" }
        require(proposal.isNotBlank()) { "self_change_proposal_blank" }
        require(surfaces.isNotEmpty()) { "self_change_surface_empty" }
        require(SHA256_REF.matches(observationDigest)) { "self_change_observation_digest_invalid" }
    }

    private companion object {
        val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
    }
}

/** Evidence attached to one exact patch candidate and exact repository base. */
internal data class SelfChangeEvidence(
    val candidateDigest: String,
    val baseCommitSha: String,
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
    val exactBaseVerified: Boolean = false
) {
    init {
        require(SHA256_REF.matches(candidateDigest)) { "self_change_evidence_digest_invalid" }
        require(COMMIT_SHA.matches(baseCommitSha)) { "self_change_evidence_base_sha_invalid" }
    }

    private companion object {
        val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
        val COMMIT_SHA = Regex("^[a-f0-9]{40}$")
    }
}

internal data class SelfChangeCandidate(
    val changeId: String,
    val problem: String,
    val proposal: String,
    val surfaces: Set<SelfChangeSurface>,
    val observationDigest: String,
    val stage: SelfChangeStage,
    val candidateDigest: String? = null,
    val baseCommitSha: String? = null,
    val evidence: SelfChangeEvidence? = null,
    val authorizedBy: SelfChangeActor? = null
) {
    val risk: SelfChangeRisk
        get() = SelfImprovementPolicy.classify(surfaces)

    init {
        require(changeId.isNotBlank()) { "self_change_id_blank" }
        require(problem.isNotBlank()) { "self_change_problem_blank" }
        require(proposal.isNotBlank()) { "self_change_proposal_blank" }
        require(surfaces.isNotEmpty()) { "self_change_surface_empty" }
        require(SHA256_REF.matches(observationDigest)) { "self_change_observation_digest_invalid" }
        require(authorizedBy != SelfChangeActor.MORIMIL) { "self_authorization_forbidden" }

        when (stage) {
            SelfChangeStage.DETECTED,
            SelfChangeStage.DIAGNOSED,
            SelfChangeStage.PROPOSED -> {
                require(candidateDigest == null) { "self_change_patch_digest_before_patch" }
                require(baseCommitSha == null) { "self_change_base_sha_before_patch" }
                require(evidence == null) { "self_change_evidence_before_verification" }
                require(authorizedBy == null) { "self_change_authorization_before_verification" }
            }
            SelfChangeStage.PATCH_CANDIDATE -> {
                requirePatchBinding(candidateDigest, baseCommitSha)
                require(evidence == null) { "self_change_evidence_before_verification" }
                require(authorizedBy == null) { "self_change_authorization_before_verification" }
            }
            SelfChangeStage.VERIFIED -> {
                requirePatchBinding(candidateDigest, baseCommitSha)
                require(evidence != null) { "self_change_verified_evidence_missing" }
                require(authorizedBy == null) { "self_change_authorization_before_authorized_stage" }
            }
            SelfChangeStage.AUTHORIZED,
            SelfChangeStage.MERGE_READY -> {
                requirePatchBinding(candidateDigest, baseCommitSha)
                require(evidence != null) { "self_change_verified_evidence_missing" }
                require(authorizedBy != null) { "self_change_authorization_missing" }
            }
            SelfChangeStage.REJECTED -> Unit
        }
    }

    private fun requirePatchBinding(candidateDigest: String?, baseCommitSha: String?) {
        require(candidateDigest != null && SHA256_REF.matches(candidateDigest)) {
            "self_change_patch_digest_missing_or_invalid"
        }
        require(baseCommitSha != null && COMMIT_SHA.matches(baseCommitSha)) {
            "self_change_base_sha_missing_or_invalid"
        }
    }

    private companion object {
        val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
        val COMMIT_SHA = Regex("^[a-f0-9]{40}$")
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
        require(evidence.baseCommitSha == candidate.baseCommitSha) {
            "self_change_evidence_base_mismatch"
        }
        require(evidence.exactBaseVerified) { "self_change_exact_base_not_verified" }
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
    fun detect(observation: SelfChangeObservation): SelfChangeCandidate {
        return SelfChangeCandidate(
            changeId = observation.changeId,
            problem = observation.problem,
            proposal = observation.proposal,
            surfaces = observation.surfaces,
            observationDigest = observation.observationDigest,
            stage = SelfChangeStage.DETECTED
        )
    }

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
        candidateDigest: String,
        baseCommitSha: String,
        actor: SelfChangeActor
    ): SelfChangeCandidate {
        require(actor == SelfChangeActor.MORIMIL || actor == SelfChangeActor.EXTERNAL_EXECUTOR) {
            "self_change_patch_actor_invalid"
        }
        require(candidate.stage == SelfChangeStage.PROPOSED) { "self_change_stage_invalid" }
        return candidate.copy(
            stage = SelfChangeStage.PATCH_CANDIDATE,
            candidateDigest = candidateDigest,
            baseCommitSha = baseCommitSha
        )
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
