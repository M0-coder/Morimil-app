package com.morimil.app.data.genesis.ultra

import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * Final witness package returned after the Guardian has signed every document
 * assigned to its custody role. Caller-owned byte arrays are never retained.
 */
internal class GenesisUltraAtomicBirthWitnessPackage(
    artifacts: List<GenesisUltraBirthArtifact>,
    journal: List<GenesisUltraBirthJournalEvidence>,
    val evaluatedAt: String
) {
    private val artifactSnapshot = artifacts.map { artifact ->
        artifact.copy(payload = artifact.payload.copyOf())
    }
    private val journalSnapshot = journal.map { evidence ->
        GenesisUltraBirthJournalEvidence(
            entry = evidence.entry,
            sourceBytes = evidence.sourceBytes.copyOf()
        )
    }

    init {
        requireCanonicalTimestamp(evaluatedAt, "atomic_birth_authorization_time_invalid")
        require(artifactSnapshot.isNotEmpty()) { "atomic_birth_authorization_artifacts_empty" }
        require(journalSnapshot.isNotEmpty()) { "atomic_birth_authorization_journal_empty" }
    }

    fun copyArtifacts(): List<GenesisUltraBirthArtifact> = artifactSnapshot.map { artifact ->
        artifact.copy(payload = artifact.payload.copyOf())
    }

    fun copyJournal(): List<GenesisUltraBirthJournalEvidence> = journalSnapshot.map { evidence ->
        GenesisUltraBirthJournalEvidence(
            entry = evidence.entry,
            sourceBytes = evidence.sourceBytes.copyOf()
        )
    }
}

/**
 * The only type that represents all currently required authorization evidence.
 * It authorizes entry into the atomic activation boundary; it does not persist
 * birth, append memory or modify onboarding by itself.
 */
internal class GenesisUltraAuthorizedAtomicBirth private constructor(
    private val verifiedBirth: GenesisUltraVerifiedAtomicBirth,
    val candidateDigest: String,
    val consentDigest: String,
    val birthStateDigest: String,
    val receiptDigest: String,
    val authorizationDigest: String,
    val authorizedAt: String,
    val expiresAt: String
) {
    val birthCommitAuthorized: Boolean = true

    init {
        require(SHA256_REF.matches(candidateDigest)) { "atomic_birth_authorization_candidate_digest_invalid" }
        require(SHA256_REF.matches(consentDigest)) { "atomic_birth_authorization_consent_digest_invalid" }
        require(SHA256_REF.matches(birthStateDigest)) { "atomic_birth_authorization_state_digest_invalid" }
        require(SHA256_REF.matches(receiptDigest)) { "atomic_birth_authorization_receipt_digest_invalid" }
        require(SHA256_REF.matches(authorizationDigest)) { "atomic_birth_authorization_digest_invalid" }
        val authorized = requireCanonicalTimestamp(
            authorizedAt,
            "atomic_birth_authorization_time_invalid"
        )
        val expires = requireCanonicalTimestamp(
            expiresAt,
            "atomic_birth_authorization_expiry_invalid"
        )
        require(authorized < expires) { "atomic_birth_authorization_expiry_order_invalid" }
        require(birthCommitAuthorized) { "atomic_birth_authorization_not_authorized" }
    }

    internal fun copyVerifiedBirth(): GenesisUltraVerifiedAtomicBirth = verifiedBirth

    internal fun requireUsableAt(evaluatedAt: String) {
        val evaluated = requireCanonicalTimestamp(
            evaluatedAt,
            "atomic_birth_activation_time_invalid"
        )
        val start = Instant.parse(authorizedAt)
        val expiry = Instant.parse(expiresAt)
        require(evaluated >= start && evaluated < expiry) {
            "atomic_birth_authorization_expired"
        }
    }

    internal companion object {
        private const val AUTHORIZATION_DOMAIN = "genesis.atomic.birth.authorization.v0.1"
        private val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")

        fun verifyAndIssue(
            candidate: GenesisUltraConstructedBirthCandidate,
            consent: GenesisUltraVerifiedHostBirthConsent,
            verifiedBirth: GenesisUltraVerifiedAtomicBirth,
            evaluatedAt: String
        ): GenesisUltraAuthorizedAtomicBirth {
            val evaluated = requireCanonicalTimestamp(
                evaluatedAt,
                "atomic_birth_authorization_time_invalid"
            )
            require(!candidate.birthCommitAuthorized) {
                "atomic_birth_candidate_cannot_self_authorize"
            }
            require(!consent.birthCommitAuthorized) {
                "host_birth_consent_cannot_self_authorize"
            }
            require(consent.matches(candidate)) {
                "atomic_birth_authorization_consent_candidate_mismatch"
            }
            require(consent.isValidAt(evaluatedAt)) {
                "atomic_birth_authorization_consent_expired"
            }

            val model = candidate.candidate
            val assessment = GenesisUltraBirthCandidateValidator.assess(model, evaluatedAt)
            require(assessment.structurallyValid) {
                "atomic_birth_authorization_candidate_invalid:${assessment.issues}"
            }

            val persistence = verifiedBirth.copyPersistenceBundle()
            require(persistence.seedManifest == model.release.manifest) {
                "atomic_birth_authorization_seed_mismatch"
            }
            require(persistence.instanceIdentity == model.instanceIdentity) {
                "atomic_birth_authorization_identity_mismatch"
            }

            val bodyRecord = parseArtifact(
                persistence,
                "initial_body_record",
                GenesisUltraContractParser::parseBodyRecord
            )
            val bodyRegistry = parseArtifact(
                persistence,
                "initial_body_registry",
                GenesisUltraContractParser::parseBodyRegistry
            )
            val keyEpoch = parseArtifact(
                persistence,
                "initial_body_key_epoch",
                GenesisUltraContractParser::parseKeyEpoch
            )
            val possession = parseArtifact(
                persistence,
                "initial_body_possession",
                GenesisUltraBodyPossessionProofParser::parse
            )

            require(bodyRecord == model.bodyRecord) {
                "atomic_birth_authorization_body_record_mismatch"
            }
            require(bodyRegistry == model.bodyRegistry) {
                "atomic_birth_authorization_body_registry_mismatch"
            }
            require(model.keyEpochs.size == 1 && keyEpoch == model.keyEpochs.single()) {
                "atomic_birth_authorization_key_epoch_mismatch"
            }
            require(possession == model.bodyPossession.proof) {
                "atomic_birth_authorization_possession_mismatch"
            }

            val state = persistence.birthState
            val receipt = persistence.birthReceipt
            require(
                state.instanceId == model.instanceIdentity.instanceId &&
                    state.seedRootHash == model.release.verifiedRootHash &&
                    state.identityDigest == model.instanceIdentity.identityDigest &&
                    state.initialBodyId == model.bodyRecord.bodyId &&
                    state.initialBodyRegistryDigest == model.bodyRegistry.registryDigest &&
                    state.initialBodyKeyEpochDigest == model.keyEpochs.single().epochDigest &&
                    state.initialBodyPossessionDigest == model.bodyPossession.proof.proofDigest
            ) { "atomic_birth_authorization_state_candidate_mismatch" }
            require(
                receipt.instanceId == state.instanceId &&
                    receipt.birthStateDigest == state.stateDigest &&
                    receipt.seedRootHash == state.seedRootHash &&
                    receipt.identityDigest == state.identityDigest &&
                    receipt.activeWriterBodyId == state.initialBodyId &&
                    receipt.birthStatus == "born" &&
                    !receipt.ownershipConferred
            ) { "atomic_birth_authorization_receipt_state_mismatch" }

            val possessionExpiry = Instant.parse(model.bodyPossession.proof.expiresAt)
            val consentExpiry = Instant.parse(consent.expiresAt)
            val authorizationExpiry = minOf(possessionExpiry, consentExpiry)
            require(evaluated < authorizationExpiry) {
                "atomic_birth_authorization_expired"
            }

            val authorizationDigest = GenesisUltraHashProfile.hashFields(
                AUTHORIZATION_DOMAIN,
                listOf(
                    candidate.candidateDigest,
                    consent.consentDigest,
                    state.stateDigest,
                    receipt.receiptDigest,
                    model.bodyRecord.bodyId,
                    model.release.signature.signerId,
                    model.release.signature.keyEpochId,
                    evaluatedAt,
                    authorizationExpiry.toString()
                )
            )

            return GenesisUltraAuthorizedAtomicBirth(
                verifiedBirth = verifiedBirth,
                candidateDigest = candidate.candidateDigest,
                consentDigest = consent.consentDigest,
                birthStateDigest = state.stateDigest,
                receiptDigest = receipt.receiptDigest,
                authorizationDigest = authorizationDigest,
                authorizedAt = evaluatedAt,
                expiresAt = authorizationExpiry.toString()
            )
        }

        private fun <T> parseArtifact(
            persistence: GenesisUltraAtomicBirthPersistenceBundle,
            kind: String,
            parser: (String) -> T
        ): T {
            val artifact = persistence.artifacts.singleOrNull { item -> item.artifactKind == kind }
                ?: throw IllegalArgumentException("atomic_birth_authorization_artifact_missing:$kind")
            val text = artifact.payload.toString(StandardCharsets.UTF_8)
            return parser(text)
        }
    }
}

/**
 * Loads trust and consent from local authenticated stores, then verifies the
 * externally witnessed evidence. No caller can inject a replacement Guardian
 * registry or Body public key into this boundary.
 */
internal class GenesisUltraAtomicBirthAuthorizationCoordinator(
    private val preparationCoordinator: GenesisUltraBirthPreparationCoordinator,
    private val bodyIdentityRootStore: GenesisUltraAndroidBodyIdentityRootStore,
    private val guardianTrustAnchorStore: GenesisUltraAndroidGuardianTrustAnchorStore,
    private val hostBirthConsentStore: GenesisUltraAndroidHostBirthConsentStore
) {
    suspend fun authorize(
        candidate: GenesisUltraConstructedBirthCandidate,
        witnessPackage: GenesisUltraAtomicBirthWitnessPackage
    ): GenesisUltraAuthorizedAtomicBirth {
        requirePreparationReady(preparationCoordinator.inspect())
        val model = candidate.candidate
        val evaluatedAt = witnessPackage.evaluatedAt

        val consent = hostBirthConsentStore.loadForCandidate(candidate, evaluatedAt)
        val bodyRoot = bodyIdentityRootStore.loadExisting()
        val guardianRegistry = guardianTrustAnchorStore.loadExistingRegistry()

        require(
            model.bodyRecord.bodyId == bodyRoot.bodyId &&
                model.bodyRecord.publicKeyFingerprint == bodyRoot.publicKeyRef &&
                model.keyEpochs.singleOrNull()?.keyEpochId == bodyRoot.keyEpochId &&
                model.keyEpochs.singleOrNull()?.publicKeyFingerprint == bodyRoot.publicKeyRef
        ) { "atomic_birth_authorization_local_body_mismatch" }
        require(guardianRegistry.trusts(model.release.signature)) {
            "atomic_birth_authorization_local_guardian_mismatch"
        }

        val verifiedBirth = GenesisUltraAtomicBirthEvidenceVerifier.verify(
            GenesisUltraAtomicBirthEvidenceRequest(
                release = model.release,
                guardianKeyEpochRegistry = guardianRegistry,
                bodyRawPublicKey = bodyRoot.copyRawPublicKey(),
                artifacts = witnessPackage.copyArtifacts(),
                journal = witnessPackage.copyJournal(),
                evaluatedAt = evaluatedAt
            )
        )
        val authorization = GenesisUltraAuthorizedAtomicBirth.verifyAndIssue(
            candidate = candidate,
            consent = consent,
            verifiedBirth = verifiedBirth,
            evaluatedAt = evaluatedAt
        )

        // Reject a concurrent commit, legacy insertion or trust-state loss.
        requirePreparationReady(preparationCoordinator.inspect())
        return authorization
    }

    private fun requirePreparationReady(assessment: GenesisUltraBirthPreparationAssessment) {
        require(
            assessment.status == GenesisUltraBirthPreparationStatus.READY_FOR_SIGNED_CANDIDATE &&
                assessment.candidateConstructionReady &&
                !assessment.birthCommitAuthorized
        ) {
            "atomic_birth_authorization_not_prepared:${assessment.status}:${assessment.blockers}"
        }
    }
}

private fun requireCanonicalTimestamp(value: String, errorCode: String): Instant {
    val parsed = runCatching { Instant.parse(value) }
        .getOrElse { failure -> throw IllegalArgumentException(errorCode, failure) }
    require(parsed.toString() == value) { errorCode }
    return parsed
}
