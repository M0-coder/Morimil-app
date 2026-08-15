package com.morimil.app.data.genesis.ultra

import java.security.SecureRandom
import java.time.Instant

/** Inputs confirmed for one in-memory Genesis Ultra candidate construction. */
internal data class GenesisUltraBirthCandidateConstructionRequest(
    val release: GenesisUltraVerifiedRelease,
    val companionName: String,
    val bornAt: String,
    val platformProfile: String = "android-kotlin"
)

/**
 * A structurally verified candidate tied to one exact Seed, Body root and name.
 * Construction never authorizes or persists birth.
 */
internal class GenesisUltraConstructedBirthCandidate(
    val candidate: GenesisUltraBirthCandidate,
    val assessment: GenesisUltraBirthCandidateAssessment,
    val candidateDigest: String,
    val evaluatedAt: String
) {
    val birthCommitAuthorized: Boolean = false

    init {
        require(assessment.structurallyValid) { "constructed_birth_candidate_not_structurally_valid" }
        require(!assessment.birthReady) { "constructed_birth_candidate_cannot_be_birth_ready" }
        require(!birthCommitAuthorized) { "constructed_birth_candidate_cannot_authorize_commit" }
        require(SHA256_REF.matches(candidateDigest)) { "constructed_birth_candidate_digest_invalid" }
    }

    private companion object {
        val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
    }
}

/**
 * Builds one signed candidate from already-provisioned local trust material.
 *
 * It does not write Room, install a Seed, invoke atomic activation, record host
 * consent or modify onboarding. Entropy is local and never derived from legacy
 * identity, alias, Android identifiers or the APK signing certificate.
 *
 * The permanent Instance identifier is deliberately derived before and
 * independently from Body binding. The Body then proves possession and becomes
 * the initial active writer without becoming part of the permanent Instance id.
 */
internal class GenesisUltraBirthCandidateConstructionCoordinator(
    private val preparationCoordinator: GenesisUltraBirthPreparationCoordinator,
    private val bodyIdentityRootStore: GenesisUltraAndroidBodyIdentityRootStore,
    private val guardianTrustAnchorStore: GenesisUltraAndroidGuardianTrustAnchorStore,
    private val entropySource: (Int) -> ByteArray = { size ->
        ByteArray(size).also(SecureRandom()::nextBytes)
    }
) {
    suspend fun construct(
        request: GenesisUltraBirthCandidateConstructionRequest
    ): GenesisUltraConstructedBirthCandidate {
        requirePreparationReady(preparationCoordinator.inspect())
        val companionName = requireCanonicalCompanionName(request.companionName)
        val platformProfile = requirePlatformProfile(request.platformProfile)
        val bornAtInstant = requireCanonicalTimestamp(request.bornAt, "birth_candidate_born_at_invalid")

        val bodyRoot = bodyIdentityRootStore.loadExisting()
        val guardianRegistry = guardianTrustAnchorStore.loadExistingRegistry()
        require(guardianRegistry.trusts(request.release.signature)) {
            "birth_candidate_release_not_trusted_by_pinned_guardian"
        }
        require(request.release.signature.signedDigest == request.release.verifiedRootHash) {
            "birth_candidate_release_digest_mismatch"
        }

        val instanceEntropy = requireEntropy(entropySource(ENTROPY_BYTES), "instance")
        val instanceId = GenesisUltraInstanceIdProfile.derive(
            releaseRoot = request.release.verifiedRootHash,
            companionName = companionName,
            bornAt = request.bornAt,
            entropyRef = GenesisUltraHashProfile.sha256(instanceEntropy)
        )
        instanceEntropy.fill(0)
        require(instanceId != bodyRoot.bodyId) { "birth_candidate_instance_body_collision" }

        val signer = bodyIdentityRootStore.signerForInstance(instanceId)
        requireSignerMatchesRoot(signer, bodyRoot, instanceId)

        val identityWithoutDigest = GenesisUltraInstanceIdentity(
            schemaVersion = INSTANCE_IDENTITY_SCHEMA,
            instanceId = instanceId,
            seedId = request.release.manifest.seedId,
            seedRootHash = request.release.verifiedRootHash,
            companionName = companionName,
            guardianId = request.release.signature.signerId,
            bornAt = request.bornAt,
            identityDigest = ZERO_SHA256
        )
        val identity = identityWithoutDigest.copy(
            identityDigest = GenesisUltraHashProfile.instanceIdentityDigest(identityWithoutDigest)
        )

        val bodyRecord = GenesisUltraBodyRecord(
            schemaVersion = BODY_RECORD_SCHEMA,
            instanceId = instanceId,
            bodyId = bodyRoot.bodyId,
            status = "active_writer",
            createdAt = request.bornAt,
            platformProfile = platformProfile,
            publicKeyFingerprint = bodyRoot.publicKeyRef,
            revokedAt = null,
            revocationReason = null
        )
        val bodyRegistryWithoutDigest = GenesisUltraBodyRegistry(
            schemaVersion = BODY_REGISTRY_SCHEMA,
            instanceId = instanceId,
            registryEpoch = 0L,
            bodies = listOf(
                GenesisUltraRegisteredBody(
                    bodyId = bodyRecord.bodyId,
                    status = bodyRecord.status,
                    platformProfile = bodyRecord.platformProfile,
                    publicKeyFingerprint = bodyRecord.publicKeyFingerprint,
                    createdAt = bodyRecord.createdAt,
                    lastSeenAt = null,
                    revocationRef = null
                )
            ),
            updatedAt = request.bornAt,
            registryDigest = ZERO_SHA256
        )
        val bodyRegistry = bodyRegistryWithoutDigest.copy(
            registryDigest = GenesisUltraHashProfile.bodyRegistryDigest(bodyRegistryWithoutDigest)
        )

        val keyEpochWithoutDigest = GenesisUltraKeyEpoch(
            schemaVersion = KEY_EPOCH_SCHEMA,
            keyEpochId = bodyRoot.keyEpochId,
            instanceId = instanceId,
            bodyId = bodyRoot.bodyId,
            epochNumber = 0L,
            publicKeyFingerprint = bodyRoot.publicKeyRef,
            createdAt = request.bornAt,
            status = "active",
            previousEpochId = null,
            rotationAuthorizationRef = null,
            epochDigest = ZERO_SHA256,
            signature = null
        )
        val keyEpoch = keyEpochWithoutDigest.copy(
            epochDigest = GenesisUltraHashProfile.keyEpochDigest(keyEpochWithoutDigest)
        )

        val challengeEntropy = requireEntropy(entropySource(ENTROPY_BYTES), "possession")
        val challengeNonce = identifier(
            prefix = "nonce_",
            domain = POSSESSION_NONCE_DOMAIN,
            fields = listOf(
                instanceId,
                bodyRoot.bodyId,
                request.release.verifiedRootHash,
                GenesisUltraHashProfile.sha256(challengeEntropy)
            )
        )
        challengeEntropy.fill(0)
        val proofId = identifier(
            prefix = "proof_",
            domain = POSSESSION_PROOF_ID_DOMAIN,
            fields = listOf(instanceId, bodyRoot.bodyId, challengeNonce, request.bornAt)
        )
        val expiresAt = bornAtInstant.plusSeconds(POSSESSION_VALIDITY_SECONDS).toString()
        val proofWithoutDigest = GenesisUltraBodyPossessionProof(
            schemaVersion = BODY_POSSESSION_SCHEMA,
            proofId = proofId,
            instanceId = instanceId,
            bodyId = bodyRoot.bodyId,
            challengeNonce = challengeNonce,
            issuedAt = request.bornAt,
            expiresAt = expiresAt,
            publicKeyFingerprint = bodyRoot.publicKeyRef,
            proofDigest = ZERO_SHA256,
            signature = GenesisUltraBodyPossessionSignature(
                profile = SIGNATURE_PROFILE,
                keyEpochId = bodyRoot.keyEpochId,
                value = ZERO_ED25519_SIGNATURE
            )
        )
        val proofDigest = GenesisUltraHashProfile.bodyPossessionDigest(proofWithoutDigest)
        val unsignedEnvelope = GenesisUltraSignatureEnvelope(
            schemaVersion = SIGNATURE_ENVELOPE_SCHEMA,
            signatureProfile = SIGNATURE_PROFILE,
            signerType = "body",
            signerId = bodyRoot.bodyId,
            keyEpochId = bodyRoot.keyEpochId,
            signedDomain = GenesisUltraBodyPossessionVerifier.BODY_POSSESSION_SIGNATURE_DOMAIN,
            signedDigest = proofDigest,
            signatureValue = ZERO_ED25519_SIGNATURE,
            createdAt = request.bornAt,
            publicKeyRef = bodyRoot.publicKeyRef
        )
        val signatureBytes = signer.sign(
            GenesisUltraHashProfile.signatureEnvelopePreimage(unsignedEnvelope)
        )
        require(signatureBytes.size == ED25519_SIGNATURE_BYTES) {
            "birth_candidate_possession_signature_size_invalid"
        }
        val proof = proofWithoutDigest.copy(
            proofDigest = proofDigest,
            signature = proofWithoutDigest.signature.copy(value = signatureBytes.toLowerHex())
        )
        signatureBytes.fill(0)
        val verifiedPossession = GenesisUltraBodyPossessionVerifier().verify(
            proof = proof,
            keyEpoch = keyEpoch,
            rawPublicKey = bodyRoot.copyRawPublicKey(),
            evaluatedAt = request.bornAt
        )

        val candidate = GenesisUltraBirthCandidate(
            release = request.release,
            guardianKeyEpochRegistry = guardianRegistry,
            instanceIdentity = identity,
            bodyRecord = bodyRecord,
            bodyRegistry = bodyRegistry,
            keyEpochs = listOf(keyEpoch),
            bodyPossession = verifiedPossession
        )
        val assessment = GenesisUltraBirthCandidateValidator.assess(candidate, request.bornAt)
        require(assessment.structurallyValid) {
            "constructed_birth_candidate_invalid:${assessment.issues}"
        }
        require(!assessment.birthReady) { "constructed_birth_candidate_unexpectedly_birth_ready" }

        // Detect a concurrent birth commit or trust-state change before returning the candidate.
        requirePreparationReady(preparationCoordinator.inspect())

        return GenesisUltraConstructedBirthCandidate(
            candidate = candidate,
            assessment = assessment,
            candidateDigest = candidateDigest(candidate, request.bornAt),
            evaluatedAt = request.bornAt
        )
    }

    private fun requirePreparationReady(
        assessment: GenesisUltraBirthPreparationAssessment
    ) {
        require(
            assessment.status == GenesisUltraBirthPreparationStatus.READY_FOR_SIGNED_CANDIDATE &&
                assessment.candidateConstructionReady &&
                !assessment.birthCommitAuthorized
        ) {
            "birth_candidate_construction_not_prepared:${assessment.status}:${assessment.blockers}"
        }
    }

    private fun requireSignerMatchesRoot(
        signer: GenesisUltraBodyMemorySigner,
        root: GenesisUltraBodyIdentityRoot,
        instanceId: String
    ) {
        require(
            signer.key.instanceId == instanceId &&
                signer.key.bodyId == root.bodyId &&
                signer.key.keyEpochId == root.keyEpochId &&
                signer.key.publicKeyRef == root.publicKeyRef &&
                signer.key.copyRawPublicKey().contentEquals(root.copyRawPublicKey())
        ) { "birth_candidate_signer_root_mismatch" }
    }

    private fun requireCanonicalCompanionName(value: String): String {
        GenesisUltraHashProfile.requireNfc(value)
        require(value == value.trim()) { "birth_candidate_companion_name_not_canonical" }
        require(value.length in 1..128) { "birth_candidate_companion_name_invalid" }
        require(value.none { character -> character.isISOControl() }) {
            "birth_candidate_companion_name_control_character"
        }
        return value
    }

    private fun requirePlatformProfile(value: String): String {
        GenesisUltraHashProfile.requireNfc(value)
        require(value == value.trim() && value.length in 1..128) {
            "birth_candidate_platform_profile_invalid"
        }
        return value
    }

    private fun requireCanonicalTimestamp(value: String, errorCode: String): Instant {
        require(CANONICAL_TIMESTAMP.matches(value)) { errorCode }
        return runCatching { Instant.parse(value) }
            .getOrElse { failure -> throw IllegalArgumentException(errorCode, failure) }
    }

    private fun requireEntropy(value: ByteArray, purpose: String): ByteArray {
        require(value.size == ENTROPY_BYTES) { "birth_candidate_${purpose}_entropy_size_invalid" }
        require(value.any { byte -> byte.toInt() != 0 }) {
            "birth_candidate_${purpose}_entropy_all_zero"
        }
        return value.copyOf()
    }

    private fun identifier(prefix: String, domain: String, fields: List<String>): String {
        return prefix + GenesisUltraHashProfile.hashFields(domain, fields).removePrefix("sha256:")
    }

    private fun candidateDigest(candidate: GenesisUltraBirthCandidate, evaluatedAt: String): String {
        val epoch = candidate.keyEpochs.single()
        return GenesisUltraHashProfile.hashFields(
            CANDIDATE_DIGEST_DOMAIN,
            listOf(
                candidate.release.verifiedRootHash,
                candidate.release.signature.signerId,
                candidate.release.signature.keyEpochId,
                candidate.release.signature.publicKeyRef,
                candidate.instanceIdentity.identityDigest,
                candidate.bodyRecord.bodyId,
                candidate.bodyRegistry.registryDigest,
                epoch.epochDigest,
                candidate.bodyPossession.proof.proofDigest,
                evaluatedAt
            )
        )
    }

    private fun ByteArray.toLowerHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

    private companion object {
        const val INSTANCE_IDENTITY_SCHEMA = "genesis.instance.identity.v0.1"
        const val BODY_RECORD_SCHEMA = "genesis.body.record.v0.1"
        const val BODY_REGISTRY_SCHEMA = "genesis.body.registry.v0.1"
        const val KEY_EPOCH_SCHEMA = "genesis.key.epoch.v0.1"
        const val BODY_POSSESSION_SCHEMA = "genesis.body.possession.v0.1"
        const val SIGNATURE_ENVELOPE_SCHEMA = "genesis.signature.envelope.v0.1"
        const val SIGNATURE_PROFILE = "genesis.signature.ed25519.v0.1"
        const val POSSESSION_NONCE_DOMAIN = "genesis.body.possession.nonce.v0.1"
        const val POSSESSION_PROOF_ID_DOMAIN = "genesis.body.possession.proof.id.v0.1"
        const val CANDIDATE_DIGEST_DOMAIN = "genesis.birth.candidate.digest.v0.1"
        const val ENTROPY_BYTES = 32
        const val ED25519_SIGNATURE_BYTES = 64
        const val POSSESSION_VALIDITY_SECONDS = 300L
        val ZERO_SHA256 = "sha256:" + "0".repeat(64)
        val ZERO_ED25519_SIGNATURE = "0".repeat(128)
        val CANONICAL_TIMESTAMP = Regex(
            "^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])T" +
                "([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]Z$"
        )
    }
}
