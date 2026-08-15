package com.morimil.app.improvements

/** Areas that a self-generated change may affect. */
internal enum class SelfChangeSurface {
    PRESENTATION,
    LOCAL_ADAPTER,
    PERFORMANCE,
    CORE_IMPLEMENTATION,
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

/** Actor labels are audit metadata only; they are not trusted authorization tokens. */
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
        require(
            observationDigest == SelfImprovementHashProfile.observationDigest(
                changeId = changeId,
                problem = problem,
                proposal = proposal,
                surfaces = surfaces
            )
        ) { "self_change_observation_digest_mismatch" }
    }

    internal companion object {
        fun create(
            changeId: String,
            problem: String,
            proposal: String,
            surfaces: Set<SelfChangeSurface>
        ): SelfChangeObservation {
            return SelfChangeObservation(
                changeId = changeId,
                problem = problem,
                proposal = proposal,
                surfaces = surfaces,
                observationDigest = SelfImprovementHashProfile.observationDigest(
                    changeId = changeId,
                    problem = problem,
                    proposal = proposal,
                    surfaces = surfaces
                )
            )
        }

        private val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
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
    val evidence: SelfVerifiedEvidence? = null,
    val authorization: SelfAuthorizationEvidence? = null
) {
    val risk: SelfChangeRisk
        get() = SelfImprovementPolicy.classify(surfaces)

    init {
        require(changeId.isNotBlank()) { "self_change_id_blank" }
        require(problem.isNotBlank()) { "self_change_problem_blank" }
        require(proposal.isNotBlank()) { "self_change_proposal_blank" }
        require(surfaces.isNotEmpty()) { "self_change_surface_empty" }
        require(SHA256_REF.matches(observationDigest)) { "self_change_observation_digest_invalid" }
        require(
            observationDigest == SelfImprovementHashProfile.observationDigest(
                changeId = changeId,
                problem = problem,
                proposal = proposal,
                surfaces = surfaces
            )
        ) { "self_change_observation_digest_mismatch" }

        when (stage) {
            SelfChangeStage.DETECTED,
            SelfChangeStage.DIAGNOSED,
            SelfChangeStage.PROPOSED -> {
                require(candidateDigest == null) { "self_change_patch_digest_before_patch" }
                require(baseCommitSha == null) { "self_change_base_sha_before_patch" }
                require(evidence == null) { "self_change_evidence_before_verification" }
                require(authorization == null) { "self_change_authorization_before_verification" }
            }
            SelfChangeStage.PATCH_CANDIDATE -> {
                requirePatchBinding(candidateDigest, baseCommitSha)
                require(evidence == null) { "self_change_evidence_before_verification" }
                require(authorization == null) { "self_change_authorization_before_verification" }
            }
            SelfChangeStage.VERIFIED -> {
                requirePatchBinding(candidateDigest, baseCommitSha)
                require(evidence != null) { "self_change_verified_evidence_missing" }
                require(authorization == null) { "self_change_authorization_before_authorized_stage" }
            }
            SelfChangeStage.AUTHORIZED,
            SelfChangeStage.MERGE_READY -> {
                requirePatchBinding(candidateDigest, baseCommitSha)
                require(evidence != null) { "self_change_verified_evidence_missing" }
                require(authorization != null) { "self_change_authorization_missing" }
                require(authorization.verificationAttestationDigest == evidence.attestationDigest) {
                    "self_change_authorization_verification_mismatch"
                }
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
 * Verification and authorization are based on cryptographically verified
 * attestations. Actor enums and boolean claims cannot advance trusted stages.
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
        SelfChangeSurface.CORE_IMPLEMENTATION,
        SelfChangeSurface.SECURITY_BOUNDARY,
        SelfChangeSurface.BUILD_AND_SUPPLY_CHAIN,
        SelfChangeSurface.REASONING_RUNTIME
    )

    private val baseClaims = setOf(
        SelfVerificationClaim.PATCH_CONTENT_RECOMPUTED,
        SelfVerificationClaim.EXACT_BASE,
        SelfVerificationClaim.ARCHITECTURE_REVIEW,
        SelfVerificationClaim.COMPILATION,
        SelfVerificationClaim.UNIT_TESTS,
        SelfVerificationClaim.STATIC_ANALYSIS
    )

    private val highClaims = setOf(
        SelfVerificationClaim.SECURITY_CHECKS,
        SelfVerificationClaim.REPRODUCIBILITY,
        SelfVerificationClaim.COVERAGE_REVIEW,
        SelfVerificationClaim.MUTATION_REVIEW,
        SelfVerificationClaim.SANDBOX_ISOLATION,
        SelfVerificationClaim.SECRET_ISOLATION,
        SelfVerificationClaim.BLAST_RADIUS_REVIEW,
        SelfVerificationClaim.ROLLBACK_PLAN_REVIEW,
        SelfVerificationClaim.AUDIT_TRAIL
    )

    private val criticalClaims = setOf(
        SelfVerificationClaim.INSTRUMENTED_TESTS,
        SelfVerificationClaim.CROSS_LANGUAGE_VECTORS
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

    fun requireVerificationEvidence(candidate: SelfChangeCandidate, evidence: SelfVerifiedEvidence) {
        require(evidence.observationDigest == candidate.observationDigest) {
            "self_change_evidence_observation_mismatch"
        }
        require(evidence.candidateDigest == candidate.candidateDigest) {
            "self_change_evidence_candidate_mismatch"
        }
        require(evidence.baseCommitSha == candidate.baseCommitSha) {
            "self_change_evidence_base_mismatch"
        }
        val required = linkedSetOf<SelfVerificationClaim>().apply {
            addAll(baseClaims)
            if (candidate.risk in setOf(SelfChangeRisk.HIGH, SelfChangeRisk.CRITICAL)) {
                addAll(highClaims)
            }
            if (candidate.risk == SelfChangeRisk.CRITICAL) {
                addAll(criticalClaims)
            }
        }
        val missing = required - evidence.claims
        require(missing.isEmpty()) { "self_change_required_evidence_missing:$missing" }
        require(SelfVerificationClaim.HUMAN_AUTHORIZATION !in evidence.claims) {
            "self_change_verifier_human_claim_forbidden"
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
        attestation: SelfSignedAuthorityAttestation,
        authorityVerifier: SelfImprovementAuthorityVerifier
    ): SelfChangeCandidate {
        require(candidate.stage == SelfChangeStage.PATCH_CANDIDATE) { "self_change_stage_invalid" }
        val digest = requireNotNull(candidate.candidateDigest)
        val base = requireNotNull(candidate.baseCommitSha)
        val evidence = authorityVerifier.verifyIndependent(
            attestation = attestation,
            expectedObservationDigest = candidate.observationDigest,
            expectedCandidateDigest = digest,
            expectedBaseCommitSha = base
        )
        SelfImprovementPolicy.requireVerificationEvidence(candidate, evidence)
        return candidate.copy(stage = SelfChangeStage.VERIFIED, evidence = evidence)
    }

    /** LOW/MEDIUM changes may rely on the already trusted independent verifier. */
    fun authorizeLowRiskFromIndependentVerification(candidate: SelfChangeCandidate): SelfChangeCandidate {
        require(candidate.stage == SelfChangeStage.VERIFIED) { "self_change_stage_invalid" }
        require(!SelfImprovementPolicy.requiresHumanAuthorization(candidate)) {
            "self_change_human_authorization_required"
        }
        val evidence = requireNotNull(candidate.evidence)
        val authorization = SelfAuthorizationEvidence(
            role = SelfAuthorityRole.INDEPENDENT_VERIFIER,
            signerId = evidence.verifierId,
            signerPublicKeyRef = evidence.verifierPublicKeyRef,
            authorizationAttestationDigest = evidence.attestationDigest,
            verificationAttestationDigest = evidence.attestationDigest,
            issuedAtMillis = evidence.issuedAtMillis
        )
        return candidate.copy(
            stage = SelfChangeStage.AUTHORIZED,
            authorization = authorization
        )
    }

    /** HIGH/CRITICAL changes require a separate signed human authorization. */
    fun authorizeHighRisk(
        candidate: SelfChangeCandidate,
        attestation: SelfSignedAuthorityAttestation,
        authorityVerifier: SelfImprovementAuthorityVerifier
    ): SelfChangeCandidate {
        require(candidate.stage == SelfChangeStage.VERIFIED) { "self_change_stage_invalid" }
        require(SelfImprovementPolicy.requiresHumanAuthorization(candidate)) {
            "self_change_human_authorization_not_required_for_low_risk"
        }
        val evidence = requireNotNull(candidate.evidence)
        val authorization = authorityVerifier.verifyHumanAuthorization(
            attestation = attestation,
            expectedObservationDigest = candidate.observationDigest,
            expectedCandidateDigest = requireNotNull(candidate.candidateDigest),
            expectedBaseCommitSha = requireNotNull(candidate.baseCommitSha),
            expectedVerificationAttestationDigest = evidence.attestationDigest,
            expectedVerifierId = evidence.verifierId,
            expectedVerifierPublicKeyRef = evidence.verifierPublicKeyRef
        )
        return candidate.copy(
            stage = SelfChangeStage.AUTHORIZED,
            authorization = authorization
        )
    }

    fun markMergeReady(candidate: SelfChangeCandidate): SelfChangeCandidate {
        require(candidate.stage == SelfChangeStage.AUTHORIZED) { "self_change_stage_invalid" }
        require(candidate.evidence != null) { "self_change_verified_evidence_missing" }
        require(candidate.authorization != null) { "self_change_authorization_missing" }
        return candidate.copy(stage = SelfChangeStage.MERGE_READY)
    }

    fun reject(candidate: SelfChangeCandidate): SelfChangeCandidate {
        require(candidate.stage != SelfChangeStage.MERGE_READY) {
            "self_change_merge_ready_rejection_requires_new_candidate"
        }
        return candidate.copy(stage = SelfChangeStage.REJECTED)
    }
}
