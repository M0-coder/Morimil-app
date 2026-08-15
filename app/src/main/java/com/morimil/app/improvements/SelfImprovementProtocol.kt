package com.morimil.app.improvements

import java.util.Collections
import java.util.LinkedHashSet

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

/** Stable immutable description of the observed problem before any patch exists. */
internal class SelfChangeObservation private constructor(
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
            val surfaceSnapshot: Set<SelfChangeSurface> = Collections.unmodifiableSet(
                LinkedHashSet(surfaces)
            )
            return SelfChangeObservation(
                changeId = changeId,
                problem = problem,
                proposal = proposal,
                surfaces = surfaceSnapshot,
                observationDigest = SelfImprovementHashProfile.observationDigest(
                    changeId = changeId,
                    problem = problem,
                    proposal = proposal,
                    surfaces = surfaceSnapshot
                )
            )
        }

        private val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
    }
}

/**
 * Opaque self-change state.
 *
 * There is deliberately no data-class `copy()` and no externally callable
 * constructor. A candidate can only move through the transition methods below;
 * VERIFIED/AUTHORIZED states can only be produced while verifying the required
 * signed authority material.
 */
internal class SelfChangeCandidate private constructor(
    val changeId: String,
    val problem: String,
    val proposal: String,
    val surfaces: Set<SelfChangeSurface>,
    val observationDigest: String,
    val stage: SelfChangeStage,
    val candidateDigest: String?,
    val baseCommitSha: String?,
    val evidence: SelfVerifiedEvidence?,
    val authorization: SelfAuthorizationEvidence?
) {
    val risk: SelfChangeRisk
        get() {
            requireObservationBinding()
            return SelfImprovementPolicy.classify(surfaces)
        }

    init {
        require(changeId.isNotBlank()) { "self_change_id_blank" }
        require(problem.isNotBlank()) { "self_change_problem_blank" }
        require(proposal.isNotBlank()) { "self_change_proposal_blank" }
        require(surfaces.isNotEmpty()) { "self_change_surface_empty" }
        require(SHA256_REF.matches(observationDigest)) { "self_change_observation_digest_invalid" }
        requireObservationBinding()

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
                requireEvidenceBinding(evidence)
            }
            SelfChangeStage.AUTHORIZED,
            SelfChangeStage.MERGE_READY -> {
                requirePatchBinding(candidateDigest, baseCommitSha)
                require(evidence != null) { "self_change_verified_evidence_missing" }
                require(authorization != null) { "self_change_authorization_missing" }
                requireEvidenceBinding(evidence)
                require(authorization.verificationAttestationDigest == evidence.attestationDigest) {
                    "self_change_authorization_verification_mismatch"
                }
            }
            SelfChangeStage.REJECTED -> Unit
        }
    }

    internal fun diagnose(actor: SelfChangeActor): SelfChangeCandidate {
        require(actor == SelfChangeActor.MORIMIL || actor == SelfChangeActor.EXTERNAL_EXECUTOR) {
            "self_change_diagnosis_actor_invalid"
        }
        require(stage == SelfChangeStage.DETECTED) { "self_change_stage_invalid" }
        return next(stage = SelfChangeStage.DIAGNOSED)
    }

    internal fun propose(actor: SelfChangeActor): SelfChangeCandidate {
        require(actor == SelfChangeActor.MORIMIL || actor == SelfChangeActor.EXTERNAL_EXECUTOR) {
            "self_change_proposal_actor_invalid"
        }
        require(stage == SelfChangeStage.DIAGNOSED) { "self_change_stage_invalid" }
        return next(stage = SelfChangeStage.PROPOSED)
    }

    internal fun registerPatch(
        digest: String,
        baseSha: String,
        actor: SelfChangeActor
    ): SelfChangeCandidate {
        require(actor == SelfChangeActor.MORIMIL || actor == SelfChangeActor.EXTERNAL_EXECUTOR) {
            "self_change_patch_actor_invalid"
        }
        require(stage == SelfChangeStage.PROPOSED) { "self_change_stage_invalid" }
        require(SHA256_REF.matches(digest)) { "self_change_patch_digest_missing_or_invalid" }
        require(COMMIT_SHA.matches(baseSha)) { "self_change_base_sha_missing_or_invalid" }
        return next(
            stage = SelfChangeStage.PATCH_CANDIDATE,
            candidateDigest = digest,
            baseCommitSha = baseSha
        )
    }

    internal fun verify(
        attestation: SelfSignedAuthorityAttestation,
        authorityVerifier: SelfImprovementAuthorityVerifier
    ): SelfChangeCandidate {
        require(stage == SelfChangeStage.PATCH_CANDIDATE) { "self_change_stage_invalid" }
        val digest = requireNotNull(candidateDigest)
        val base = requireNotNull(baseCommitSha)
        val verifiedEvidence = authorityVerifier.verifyIndependent(
            attestation = attestation,
            expectedObservationDigest = observationDigest,
            expectedCandidateDigest = digest,
            expectedBaseCommitSha = base
        )
        SelfImprovementPolicy.requireVerificationEvidence(this, verifiedEvidence)
        return next(
            stage = SelfChangeStage.VERIFIED,
            evidence = verifiedEvidence
        )
    }

    internal fun authorizeLowRiskFromIndependentVerification(): SelfChangeCandidate {
        require(stage == SelfChangeStage.VERIFIED) { "self_change_stage_invalid" }
        require(!SelfImprovementPolicy.requiresHumanAuthorization(this)) {
            "self_change_human_authorization_required"
        }
        val verifiedEvidence = requireNotNull(evidence)
        val authorizationEvidence = SelfAuthorizationEvidence(
            role = SelfAuthorityRole.INDEPENDENT_VERIFIER,
            signerId = verifiedEvidence.verifierId,
            signerPublicKeyRef = verifiedEvidence.verifierPublicKeyRef,
            authorizationAttestationDigest = verifiedEvidence.attestationDigest,
            verificationAttestationDigest = verifiedEvidence.attestationDigest,
            issuedAtMillis = verifiedEvidence.issuedAtMillis
        )
        return next(
            stage = SelfChangeStage.AUTHORIZED,
            authorization = authorizationEvidence
        )
    }

    internal fun authorizeHighRisk(
        attestation: SelfSignedAuthorityAttestation,
        authorityVerifier: SelfImprovementAuthorityVerifier
    ): SelfChangeCandidate {
        require(stage == SelfChangeStage.VERIFIED) { "self_change_stage_invalid" }
        require(SelfImprovementPolicy.requiresHumanAuthorization(this)) {
            "self_change_human_authorization_not_required_for_low_risk"
        }
        val verifiedEvidence = requireNotNull(evidence)
        val authorizationEvidence = authorityVerifier.verifyHumanAuthorization(
            attestation = attestation,
            expectedObservationDigest = observationDigest,
            expectedCandidateDigest = requireNotNull(candidateDigest),
            expectedBaseCommitSha = requireNotNull(baseCommitSha),
            expectedVerificationAttestationDigest = verifiedEvidence.attestationDigest,
            expectedVerifierId = verifiedEvidence.verifierId,
            expectedVerifierPublicKeyRef = verifiedEvidence.verifierPublicKeyRef
        )
        return next(
            stage = SelfChangeStage.AUTHORIZED,
            authorization = authorizationEvidence
        )
    }

    internal fun markMergeReady(): SelfChangeCandidate {
        require(stage == SelfChangeStage.AUTHORIZED) { "self_change_stage_invalid" }
        require(evidence != null) { "self_change_verified_evidence_missing" }
        require(authorization != null) { "self_change_authorization_missing" }
        return next(stage = SelfChangeStage.MERGE_READY)
    }

    internal fun reject(): SelfChangeCandidate {
        require(stage != SelfChangeStage.MERGE_READY) {
            "self_change_merge_ready_rejection_requires_new_candidate"
        }
        return next(stage = SelfChangeStage.REJECTED)
    }

    private fun next(
        stage: SelfChangeStage,
        candidateDigest: String? = this.candidateDigest,
        baseCommitSha: String? = this.baseCommitSha,
        evidence: SelfVerifiedEvidence? = this.evidence,
        authorization: SelfAuthorizationEvidence? = this.authorization
    ): SelfChangeCandidate {
        return SelfChangeCandidate(
            changeId = changeId,
            problem = problem,
            proposal = proposal,
            surfaces = surfaces,
            observationDigest = observationDigest,
            stage = stage,
            candidateDigest = candidateDigest,
            baseCommitSha = baseCommitSha,
            evidence = evidence,
            authorization = authorization
        )
    }

    private fun requireObservationBinding() {
        require(
            observationDigest == SelfImprovementHashProfile.observationDigest(
                changeId = changeId,
                problem = problem,
                proposal = proposal,
                surfaces = surfaces
            )
        ) { "self_change_observation_digest_mismatch" }
    }

    private fun requirePatchBinding(candidateDigest: String?, baseCommitSha: String?) {
        require(candidateDigest != null && SHA256_REF.matches(candidateDigest)) {
            "self_change_patch_digest_missing_or_invalid"
        }
        require(baseCommitSha != null && COMMIT_SHA.matches(baseCommitSha)) {
            "self_change_base_sha_missing_or_invalid"
        }
    }

    private fun requireEvidenceBinding(verifiedEvidence: SelfVerifiedEvidence) {
        require(verifiedEvidence.observationDigest == observationDigest) {
            "self_change_evidence_observation_mismatch"
        }
        require(verifiedEvidence.candidateDigest == candidateDigest) {
            "self_change_evidence_candidate_mismatch"
        }
        require(verifiedEvidence.baseCommitSha == baseCommitSha) {
            "self_change_evidence_base_mismatch"
        }
        SelfImprovementPolicy.requireVerificationEvidence(this, verifiedEvidence)
    }

    internal companion object {
        fun detected(observation: SelfChangeObservation): SelfChangeCandidate {
            return SelfChangeCandidate(
                changeId = observation.changeId,
                problem = observation.problem,
                proposal = observation.proposal,
                surfaces = observation.surfaces,
                observationDigest = observation.observationDigest,
                stage = SelfChangeStage.DETECTED,
                candidateDigest = null,
                baseCommitSha = null,
                evidence = null,
                authorization = null
            )
        }

        private val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
        private val COMMIT_SHA = Regex("^[a-f0-9]{40}$")
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
    fun detect(observation: SelfChangeObservation): SelfChangeCandidate =
        SelfChangeCandidate.detected(observation)

    fun diagnose(candidate: SelfChangeCandidate, actor: SelfChangeActor): SelfChangeCandidate =
        candidate.diagnose(actor)

    fun propose(candidate: SelfChangeCandidate, actor: SelfChangeActor): SelfChangeCandidate =
        candidate.propose(actor)

    fun registerPatchCandidate(
        candidate: SelfChangeCandidate,
        candidateDigest: String,
        baseCommitSha: String,
        actor: SelfChangeActor
    ): SelfChangeCandidate = candidate.registerPatch(candidateDigest, baseCommitSha, actor)

    fun verify(
        candidate: SelfChangeCandidate,
        attestation: SelfSignedAuthorityAttestation,
        authorityVerifier: SelfImprovementAuthorityVerifier
    ): SelfChangeCandidate = candidate.verify(attestation, authorityVerifier)

    fun authorizeLowRiskFromIndependentVerification(candidate: SelfChangeCandidate): SelfChangeCandidate =
        candidate.authorizeLowRiskFromIndependentVerification()

    fun authorizeHighRisk(
        candidate: SelfChangeCandidate,
        attestation: SelfSignedAuthorityAttestation,
        authorityVerifier: SelfImprovementAuthorityVerifier
    ): SelfChangeCandidate = candidate.authorizeHighRisk(attestation, authorityVerifier)

    fun markMergeReady(candidate: SelfChangeCandidate): SelfChangeCandidate =
        candidate.markMergeReady()

    fun reject(candidate: SelfChangeCandidate): SelfChangeCandidate = candidate.reject()
}
