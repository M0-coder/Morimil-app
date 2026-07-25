package com.morimil.app.data.genesis.ultra

import java.time.Instant
import java.time.temporal.ChronoUnit

internal data class GenesisUltraAtomicBirthExecutionCeremonyRequest(
    val presentedAuthorizationDigest: String,
    val presentedCandidateDigest: String,
    val presentedConsentDigest: String,
    val presentedConfirmationCode: String,
    val decision: String,
    val confirmationMode: String,
    val confirmationPurpose: String,
    val userPresenceConfirmed: Boolean
) {
    init {
        require(SHA256_REF.matches(presentedAuthorizationDigest)) {
            "birth_execution_presented_authorization_digest_invalid"
        }
        require(SHA256_REF.matches(presentedCandidateDigest)) {
            "birth_execution_presented_candidate_digest_invalid"
        }
        require(SHA256_REF.matches(presentedConsentDigest)) {
            "birth_execution_presented_consent_digest_invalid"
        }
        require(CONFIRMATION_CODE.matches(presentedConfirmationCode)) {
            "birth_execution_confirmation_code_invalid"
        }
    }

    internal companion object {
        const val COMMIT_DECISION = "commit_genesis_ultra_birth"
        const val INTERACTIVE_CONFIRMATION_MODE = "interactive_local_presence"
        const val EXECUTION_CONFIRMATION_PURPOSE = "atomic_birth_execution"

        private val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
        private val CONFIRMATION_CODE = Regex("^[a-f0-9]{12}$")

        fun confirmationCode(authorizationDigest: String): String {
            require(SHA256_REF.matches(authorizationDigest)) {
                "birth_execution_authorization_digest_invalid"
            }
            return authorizationDigest.takeLast(12)
        }
    }
}

internal enum class GenesisUltraAtomicBirthExecutionOutcome {
    COMMITTED_CLEAN,
    COMMITTED_MAINTENANCE_PENDING
}

internal data class GenesisUltraAtomicBirthExecutionCeremonyResult(
    val outcome: GenesisUltraAtomicBirthExecutionOutcome,
    val birthId: String,
    val instanceId: String,
    val companionName: String,
    val authorizationDigest: String,
    val birthStateDigest: String,
    val receiptDigest: String,
    val firstPostBirthEventHash: String?,
    val committedAt: String,
    val maintenanceError: String?
) {
    val birthCommitted: Boolean = true

    init {
        require(birthId.length in 16..128) { "birth_execution_result_birth_id_invalid" }
        require(instanceId.length in 16..128) { "birth_execution_result_instance_id_invalid" }
        require(companionName.isNotBlank()) { "birth_execution_result_companion_name_invalid" }
        require(SHA256_REF.matches(authorizationDigest)) {
            "birth_execution_result_authorization_digest_invalid"
        }
        require(SHA256_REF.matches(birthStateDigest)) {
            "birth_execution_result_state_digest_invalid"
        }
        require(SHA256_REF.matches(receiptDigest)) {
            "birth_execution_result_receipt_digest_invalid"
        }
        require(firstPostBirthEventHash == null || EVENT_HASH.matches(firstPostBirthEventHash)) {
            "birth_execution_result_event_hash_invalid"
        }
        requireCanonicalTimestamp(committedAt, "birth_execution_result_time_invalid")
        when (outcome) {
            GenesisUltraAtomicBirthExecutionOutcome.COMMITTED_CLEAN -> {
                require(maintenanceError == null && firstPostBirthEventHash != null) {
                    "birth_execution_result_clean_state_invalid"
                }
            }
            GenesisUltraAtomicBirthExecutionOutcome.COMMITTED_MAINTENANCE_PENDING -> {
                require(!maintenanceError.isNullOrBlank()) {
                    "birth_execution_result_pending_error_missing"
                }
            }
        }
        require(birthCommitted) { "birth_execution_result_not_committed" }
    }

    private companion object {
        val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
        val EVENT_HASH = Regex("^evsha256:[a-f0-9]{64}$")
    }
}

internal data class GenesisUltraAtomicBirthCommittedEvidence(
    val birthId: String,
    val instanceId: String,
    val companionName: String,
    val authorizationDigest: String,
    val birthStateDigest: String,
    val receiptDigest: String,
    val firstPostBirthEventHash: String,
    val firstPostBirthSequence: Long,
    val firstPostBirthContentDigest: String,
    val firstPostBirthProvenanceDigest: String,
    val firstPostBirthObservedAt: String
)

/** Explicitly marks that the transactional executor has already returned. */
internal data class GenesisUltraAtomicBirthCommittedReturn(
    val evidence: GenesisUltraAtomicBirthCommittedEvidence?,
    val postCommitFailure: Throwable?
) {
    init {
        require(evidence != null || postCommitFailure != null) {
            "birth_execution_committed_return_empty"
        }
    }
}

/**
 * The only onboarding-facing boundary allowed to invoke atomic birth execution.
 *
 * A second explicit local ceremony is required after candidate consent and final
 * witness authorization. Execution failure is propagated as an uncommitted
 * failure. Once the executor returns, birth is committed; every later anomaly is
 * represented as committed-with-maintenance-pending and never as retryable birth.
 */
internal class GenesisUltraAtomicBirthExecutionCeremonyCoordinator private constructor(
    private val executeBirth: suspend (
        GenesisUltraAuthorizedAtomicBirth,
        String,
        GenesisUltraCanonicalMemoryAppendRequest
    ) -> GenesisUltraAtomicBirthCommittedReturn,
    private val retireCommittedConsent: suspend () -> GenesisUltraCommittedConsentRetirementResult,
    private val clock: () -> Instant
) {
    suspend fun execute(
        authorization: GenesisUltraAuthorizedAtomicBirth,
        request: GenesisUltraAtomicBirthExecutionCeremonyRequest
    ): GenesisUltraAtomicBirthExecutionCeremonyResult {
        require(authorization.birthCommitAuthorized) {
            "birth_execution_authorization_required"
        }
        validatePresentation(authorization, request)

        val committedAt = clock().truncatedTo(ChronoUnit.SECONDS)
        val committedAtText = committedAt.toString()
        authorization.requireUsableAt(committedAtText)
        val expected = expectedCommit(authorization)
        val memoryRequest = firstPostBirthMemoryRequest(authorization, committedAtText)

        // An exception before this call returns is an uncommitted execution failure.
        val committedReturn = executeBirth(
            authorization,
            committedAtText,
            memoryRequest
        )

        val postCommitFailures = mutableListOf<Throwable>()
        committedReturn.postCommitFailure?.let(postCommitFailures::add)
        val evidence = committedReturn.evidence
        if (evidence == null) {
            if (committedReturn.postCommitFailure == null) {
                postCommitFailures += IllegalStateException("birth_execution_committed_evidence_missing")
            }
        } else {
            runCatching {
                requireCommittedEvidence(expected, authorization, memoryRequest, evidence)
            }.exceptionOrNull()?.let(postCommitFailures::add)
        }
        runCatching {
            retireCommittedConsent()
        }.exceptionOrNull()?.let(postCommitFailures::add)

        val validEventHash = evidence?.firstPostBirthEventHash?.takeIf(EVENT_HASH::matches)
        if (validEventHash == null) {
            postCommitFailures += IllegalStateException("birth_execution_committed_event_hash_unavailable")
        }
        val maintenanceError = postCommitFailures
            .takeIf(List<Throwable>::isNotEmpty)
            ?.joinToString(" | ") { failure ->
                val type = failure::class.java.simpleName.ifBlank { "Failure" }
                "$type:${failure.message.orEmpty().take(160)}"
            }
        return GenesisUltraAtomicBirthExecutionCeremonyResult(
            outcome = if (maintenanceError == null) {
                GenesisUltraAtomicBirthExecutionOutcome.COMMITTED_CLEAN
            } else {
                GenesisUltraAtomicBirthExecutionOutcome.COMMITTED_MAINTENANCE_PENDING
            },
            birthId = expected.birthId,
            instanceId = expected.instanceId,
            companionName = expected.companionName,
            authorizationDigest = authorization.authorizationDigest,
            birthStateDigest = authorization.birthStateDigest,
            receiptDigest = authorization.receiptDigest,
            firstPostBirthEventHash = validEventHash,
            committedAt = committedAtText,
            maintenanceError = maintenanceError
        )
    }

    private fun validatePresentation(
        authorization: GenesisUltraAuthorizedAtomicBirth,
        request: GenesisUltraAtomicBirthExecutionCeremonyRequest
    ) {
        require(request.presentedAuthorizationDigest == authorization.authorizationDigest) {
            "birth_execution_presented_authorization_mismatch"
        }
        require(request.presentedCandidateDigest == authorization.candidateDigest) {
            "birth_execution_presented_candidate_mismatch"
        }
        require(request.presentedConsentDigest == authorization.consentDigest) {
            "birth_execution_presented_consent_mismatch"
        }
        require(
            request.presentedConfirmationCode ==
                GenesisUltraAtomicBirthExecutionCeremonyRequest.confirmationCode(
                    authorization.authorizationDigest
                )
        ) { "birth_execution_presented_code_mismatch" }
        require(request.decision == GenesisUltraAtomicBirthExecutionCeremonyRequest.COMMIT_DECISION) {
            "birth_execution_decision_invalid"
        }
        require(
            request.confirmationMode ==
                GenesisUltraAtomicBirthExecutionCeremonyRequest.INTERACTIVE_CONFIRMATION_MODE
        ) { "birth_execution_confirmation_mode_invalid" }
        require(
            request.confirmationPurpose ==
                GenesisUltraAtomicBirthExecutionCeremonyRequest.EXECUTION_CONFIRMATION_PURPOSE
        ) { "birth_execution_confirmation_purpose_invalid" }
        require(request.userPresenceConfirmed) { "birth_execution_user_presence_required" }
    }

    private fun expectedCommit(
        authorization: GenesisUltraAuthorizedAtomicBirth
    ): GenesisUltraAtomicBirthExpectedCommit {
        val bundle = authorization.copyVerifiedBirth().copyPersistenceBundle()
        require(bundle.birthState.stateDigest == authorization.birthStateDigest) {
            "birth_execution_expected_state_mismatch"
        }
        require(bundle.birthReceipt.receiptDigest == authorization.receiptDigest) {
            "birth_execution_expected_receipt_mismatch"
        }
        return GenesisUltraAtomicBirthExpectedCommit(
            birthId = bundle.birthState.birthId,
            instanceId = bundle.instanceIdentity.instanceId,
            companionName = bundle.instanceIdentity.companionName
        )
    }

    private fun firstPostBirthMemoryRequest(
        authorization: GenesisUltraAuthorizedAtomicBirth,
        committedAt: String
    ): GenesisUltraCanonicalMemoryAppendRequest {
        val eventId = "event_" + GenesisUltraHashProfile.hashFields(
            ACTIVATION_EVENT_ID_DOMAIN,
            listOf(
                authorization.authorizationDigest,
                authorization.candidateDigest,
                authorization.consentDigest,
                committedAt
            )
        ).removePrefix("sha256:")
        return GenesisUltraCanonicalMemoryAppendRequest(
            eventId = eventId,
            eventType = ACTIVATION_EVENT_TYPE,
            actor = ACTIVATION_ACTOR,
            contentDigest = authorization.authorizationDigest,
            contentType = ACTIVATION_CONTENT_TYPE,
            contentRef = null,
            observedAt = committedAt,
            provenanceDigest = authorization.receiptDigest,
            provenanceRef = null,
            privacy = "private_local"
        )
    }

    private fun requireCommittedEvidence(
        expected: GenesisUltraAtomicBirthExpectedCommit,
        authorization: GenesisUltraAuthorizedAtomicBirth,
        request: GenesisUltraCanonicalMemoryAppendRequest,
        evidence: GenesisUltraAtomicBirthCommittedEvidence
    ) {
        require(
            evidence.birthId == expected.birthId &&
                evidence.instanceId == expected.instanceId &&
                evidence.companionName == expected.companionName
        ) { "birth_execution_committed_identity_mismatch" }
        require(evidence.authorizationDigest == authorization.authorizationDigest) {
            "birth_execution_committed_authorization_mismatch"
        }
        require(evidence.birthStateDigest == authorization.birthStateDigest) {
            "birth_execution_committed_state_mismatch"
        }
        require(evidence.receiptDigest == authorization.receiptDigest) {
            "birth_execution_committed_receipt_mismatch"
        }
        require(evidence.firstPostBirthSequence == 1L) {
            "birth_execution_committed_memory_sequence_invalid"
        }
        require(evidence.firstPostBirthContentDigest == request.contentDigest) {
            "birth_execution_committed_memory_content_mismatch"
        }
        require(evidence.firstPostBirthProvenanceDigest == request.provenanceDigest) {
            "birth_execution_committed_memory_provenance_mismatch"
        }
        require(evidence.firstPostBirthObservedAt == request.observedAt) {
            "birth_execution_committed_memory_time_mismatch"
        }
    }

    internal companion object {
        private const val ACTIVATION_EVENT_ID_DOMAIN =
            "genesis.atomic.birth.activation.event.id.v0.1"
        private const val ACTIVATION_EVENT_TYPE = "instance.activation.confirmed"
        private const val ACTIVATION_ACTOR = "host_confirmed_system"
        private const val ACTIVATION_CONTENT_TYPE =
            "application/vnd.genesis.atomic-birth-authorization+json"
        private val EVENT_HASH = Regex("^evsha256:[a-f0-9]{64}$")

        fun production(
            executionCoordinator: GenesisUltraAtomicBirthExecutionCoordinator,
            retirementCoordinator: GenesisUltraCommittedConsentRetirementCoordinator,
            clock: () -> Instant = Instant::now
        ): GenesisUltraAtomicBirthExecutionCeremonyCoordinator {
            return GenesisUltraAtomicBirthExecutionCeremonyCoordinator(
                executeBirth = { authorization, activatedAt, memoryRequest ->
                    val result = executionCoordinator.execute(
                        authorization = authorization,
                        activatedAt = activatedAt,
                        firstPostBirthRequest = memoryRequest
                    )
                    runCatching {
                        val event = result.firstPostBirthMemory.event
                        GenesisUltraAtomicBirthCommittedEvidence(
                            birthId = result.commit.birthId,
                            instanceId = result.commit.instanceId,
                            companionName = result.commit.companionName,
                            authorizationDigest = result.authorization.authorizationDigest,
                            birthStateDigest = result.commit.birthStateDigest,
                            receiptDigest = result.commit.receiptDigest,
                            firstPostBirthEventHash = event.eventHash,
                            firstPostBirthSequence = event.sequence,
                            firstPostBirthContentDigest = event.contentDigest,
                            firstPostBirthProvenanceDigest = event.provenanceDigest,
                            firstPostBirthObservedAt = event.observedAt
                        )
                    }.fold(
                        onSuccess = { evidence ->
                            GenesisUltraAtomicBirthCommittedReturn(evidence, null)
                        },
                        onFailure = { failure ->
                            GenesisUltraAtomicBirthCommittedReturn(null, failure)
                        }
                    )
                },
                retireCommittedConsent = retirementCoordinator::retireIfCommitted,
                clock = clock
            )
        }

        fun forTest(
            executeBirth: suspend (
                GenesisUltraAuthorizedAtomicBirth,
                String,
                GenesisUltraCanonicalMemoryAppendRequest
            ) -> GenesisUltraAtomicBirthCommittedEvidence,
            retireCommittedConsent: suspend () -> GenesisUltraCommittedConsentRetirementResult,
            clock: () -> Instant
        ): GenesisUltraAtomicBirthExecutionCeremonyCoordinator {
            return forCommittedReturnTest(
                executeBirth = { authorization, activatedAt, request ->
                    GenesisUltraAtomicBirthCommittedReturn(
                        evidence = executeBirth(authorization, activatedAt, request),
                        postCommitFailure = null
                    )
                },
                retireCommittedConsent = retireCommittedConsent,
                clock = clock
            )
        }

        fun forCommittedReturnTest(
            executeBirth: suspend (
                GenesisUltraAuthorizedAtomicBirth,
                String,
                GenesisUltraCanonicalMemoryAppendRequest
            ) -> GenesisUltraAtomicBirthCommittedReturn,
            retireCommittedConsent: suspend () -> GenesisUltraCommittedConsentRetirementResult,
            clock: () -> Instant
        ): GenesisUltraAtomicBirthExecutionCeremonyCoordinator {
            return GenesisUltraAtomicBirthExecutionCeremonyCoordinator(
                executeBirth = executeBirth,
                retireCommittedConsent = retireCommittedConsent,
                clock = clock
            )
        }
    }
}

private data class GenesisUltraAtomicBirthExpectedCommit(
    val birthId: String,
    val instanceId: String,
    val companionName: String
)

private fun requireCanonicalTimestamp(value: String, errorCode: String): Instant {
    val parsed = runCatching { Instant.parse(value) }
        .getOrElse { failure -> throw IllegalArgumentException(errorCode, failure) }
    require(parsed.toString() == value) { errorCode }
    return parsed
}
