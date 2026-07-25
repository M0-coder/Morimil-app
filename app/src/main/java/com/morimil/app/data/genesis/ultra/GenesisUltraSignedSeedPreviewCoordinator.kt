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
        require(instanceId.startsWith("inst_") && instanceId.length > 5) {
            "seed_preview_instance_id_invalid"
        }
        require(bodyId.isNotBlank() && bodyId != instanceId) { "seed_preview_body_id_invalid" }
        require(SHA256_REF.matches(candidateDigest)) { "seed_preview_candidate_digest_invalid" }
        require(runCatching { Instant.parse(evaluatedAt) }.isSuccess) {
            "seed_preview_evaluated_at_invalid"
        }
    }

    private companion object {
        val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
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

    suspend fun preview(
        uri: Uri,
        companionName: String
    ): GenesisUltraSignedSeedCandidatePreview {
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

        return GenesisUltraSignedSeedCandidatePreview(
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
    }
}
