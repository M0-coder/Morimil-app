package com.morimil.app.data.genesis.ultra

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Non-secret summary of one verified, in-memory candidate construction. */
internal data class GenesisUltraSignedSeedCandidatePreview(
    val seedId: String,
    val seedRootHash: String,
    val verifiedFileCount: Int,
    val guardianId: String,
    val guardianKeyEpochId: String,
    val companionName: String,
    val instanceId: String,
    val bodyId: String,
    val candidateDigest: String,
    val evaluatedAt: String
) {
    init {
        require(seedId.isNotBlank()) { "seed_preview_seed_id_invalid" }
        require(SHA256_REF.matches(seedRootHash)) { "seed_preview_root_hash_invalid" }
        require(verifiedFileCount > 0) { "seed_preview_file_count_invalid" }
        require(guardianId.isNotBlank()) { "seed_preview_guardian_id_invalid" }
        require(guardianKeyEpochId.isNotBlank()) { "seed_preview_guardian_epoch_invalid" }
        require(GenesisUltraCompanionNamePolicy.validate(companionName).isValid) {
            "seed_preview_companion_name_invalid"
        }
        require(INSTANCE_ID.matches(instanceId)) { "seed_preview_instance_id_invalid" }
        require(BODY_ID.matches(bodyId) && bodyId != instanceId) { "seed_preview_body_id_invalid" }
        require(SHA256_REF.matches(candidateDigest)) { "seed_preview_candidate_digest_invalid" }
        require(runCatching { Instant.parse(evaluatedAt) }.isSuccess) {
            "seed_preview_evaluated_at_invalid"
        }
    }

    private companion object {
        val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
        val INSTANCE_ID = Regex("^inst_[a-f0-9]{64}$")
        val BODY_ID = Regex("^body_[a-f0-9]{64}$")
    }
}

/**
 * One exact candidate retained only in process memory for a short confirmation
 * ceremony. The full candidate is intentionally not serializable and is never
 * exposed through UI state.
 */
internal class GenesisUltraSignedSeedCandidateSession(
    internal val constructedCandidate: GenesisUltraConstructedBirthCandidate,
    val preview: GenesisUltraSignedSeedCandidatePreview
) {
    val confirmationCode: String =
        GenesisUltraHostBirthConsentRequest.confirmationCode(preview.candidateDigest)
    val expiresAt: String = constructedCandidate.candidate.bodyPossession.proof.expiresAt
    val birthCommitAuthorized: Boolean = false

    init {
        val candidate = constructedCandidate.candidate
        require(preview.candidateDigest == constructedCandidate.candidateDigest) {
            "seed_candidate_session_digest_mismatch"
        }
        require(preview.seedId == candidate.release.manifest.seedId) {
            "seed_candidate_session_seed_id_mismatch"
        }
        require(preview.seedRootHash == candidate.release.verifiedRootHash) {
            "seed_candidate_session_seed_root_mismatch"
        }
        require(preview.guardianId == candidate.release.signature.signerId) {
            "seed_candidate_session_guardian_mismatch"
        }
        require(preview.guardianKeyEpochId == candidate.release.signature.keyEpochId) {
            "seed_candidate_session_guardian_epoch_mismatch"
        }
        require(preview.companionName == candidate.instanceIdentity.companionName) {
            "seed_candidate_session_name_mismatch"
        }
        require(preview.instanceId == candidate.instanceIdentity.instanceId) {
            "seed_candidate_session_instance_mismatch"
        }
        require(preview.bodyId == candidate.bodyRecord.bodyId) {
            "seed_candidate_session_body_mismatch"
        }
        require(Instant.parse(preview.evaluatedAt) < Instant.parse(expiresAt)) {
            "seed_candidate_session_expiry_invalid"
        }
        require(!constructedCandidate.birthCommitAuthorized && !birthCommitAuthorized) {
            "seed_candidate_session_cannot_authorize_birth"
        }
    }

    fun isValidAt(evaluatedAt: String): Boolean {
        val instant = runCatching { Instant.parse(evaluatedAt) }.getOrNull() ?: return false
        if (instant < Instant.parse(preview.evaluatedAt) || instant >= Instant.parse(expiresAt)) {
            return false
        }
        return GenesisUltraBirthCandidateValidator
            .assess(constructedCandidate.candidate, evaluatedAt)
            .structurallyValid
    }
}

/**
 * Imports one user-selected Seed archive and constructs an ephemeral candidate.
 *
 * This boundary never persists the archive or candidate, records no consent,
 * accepts no Guardian testimony and cannot invoke birth authorization or
 * execution. The locally pinned Guardian epoch is the only release trust root.
 */
internal class GenesisUltraSignedSeedPreviewCoordinator(
    context: Context,
    private val preparationCoordinator: GenesisUltraBirthPreparationCoordinator,
    private val guardianTrustAnchorStore: GenesisUltraAndroidGuardianTrustAnchorStore,
    private val candidateConstructionCoordinator: GenesisUltraBirthCandidateConstructionCoordinator,
    private val archiveReader: GenesisUltraReleaseArchiveReader = GenesisUltraReleaseArchiveReader(),
    private val clock: () -> Instant = Instant::now
) {
    private val contentResolver = context.applicationContext.contentResolver

    suspend fun prepareSession(
        uri: Uri,
        companionName: String
    ): GenesisUltraSignedSeedCandidateSession {
        val canonicalName = GenesisUltraCompanionNamePolicy.requireCanonical(companionName)
        require(uri.scheme == ContentResolver.SCHEME_CONTENT) {
            "seed_preview_content_uri_required"
        }

        val preparation = preparationCoordinator.inspect()
        require(
            preparation.status == GenesisUltraBirthPreparationStatus.READY_FOR_SIGNED_CANDIDATE &&
                preparation.candidateConstructionReady &&
                !preparation.birthCommitAuthorized
        ) {
            "seed_preview_preparation_not_ready:${preparation.status}:${preparation.blockers}"
        }

        val bundle = contentResolver.openInputStream(uri)?.use(archiveReader::read)
            ?: error("seed_preview_archive_unreadable")
        val release = guardianTrustAnchorStore.verifyRelease(bundle)
        val evaluatedAt = clock().truncatedTo(ChronoUnit.SECONDS).toString()
        val constructed = candidateConstructionCoordinator.construct(
            GenesisUltraBirthCandidateConstructionRequest(
                release = release,
                companionName = canonicalName,
                bornAt = evaluatedAt
            )
        )
        val candidate = constructed.candidate
        val preview = GenesisUltraSignedSeedCandidatePreview(
            seedId = release.manifest.seedId,
            seedRootHash = release.verifiedRootHash,
            verifiedFileCount = release.verifiedFileCount,
            guardianId = release.signature.signerId,
            guardianKeyEpochId = release.signature.keyEpochId,
            companionName = candidate.instanceIdentity.companionName,
            instanceId = candidate.instanceIdentity.instanceId,
            bodyId = candidate.bodyRecord.bodyId,
            candidateDigest = constructed.candidateDigest,
            evaluatedAt = constructed.evaluatedAt
        )
        return GenesisUltraSignedSeedCandidateSession(
            constructedCandidate = constructed,
            preview = preview
        )
    }
}
