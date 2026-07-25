package com.morimil.app.data.genesis.ultra

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Imports one witness archive and verifies the complete atomic-birth evidence
 * graph against the exact process-local candidate and locally persisted consent.
 *
 * This coordinator may issue [GenesisUltraAuthorizedAtomicBirth], but it never
 * persists that authorization, invokes the execution coordinator or opens the
 * runtime. The returned type remains process-local and expires deterministically.
 */
internal class GenesisUltraAtomicBirthWitnessAuthorizationCoordinator(
    context: Context,
    private val authorizationCoordinator: GenesisUltraAtomicBirthAuthorizationCoordinator,
    private val archiveReader: GenesisUltraAtomicBirthWitnessArchiveReader =
        GenesisUltraAtomicBirthWitnessArchiveReader(),
    private val clock: () -> Instant = Instant::now
) {
    private val contentResolver = context.applicationContext.contentResolver

    suspend fun authorize(
        uri: Uri,
        candidate: GenesisUltraConstructedBirthCandidate,
        expectedConsentDigest: String
    ): GenesisUltraAuthorizedAtomicBirth {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT) {
            "witness_authorization_content_uri_required"
        }
        require(!candidate.birthCommitAuthorized) {
            "witness_authorization_candidate_already_authorized"
        }
        require(SHA256_REF.matches(expectedConsentDigest)) {
            "witness_authorization_consent_digest_invalid"
        }

        val evaluatedAt = clock().truncatedTo(ChronoUnit.SECONDS).toString()
        val witnessPackage = contentResolver.openInputStream(uri)?.use { input ->
            archiveReader.read(
                input = input,
                expectedCandidateDigest = candidate.candidateDigest,
                expectedConsentDigest = expectedConsentDigest,
                evaluatedAt = evaluatedAt
            )
        } ?: error("witness_authorization_archive_unreadable")

        val authorization = authorizationCoordinator.authorize(
            candidate = candidate,
            witnessPackage = witnessPackage
        )
        require(authorization.birthCommitAuthorized) {
            "witness_authorization_not_authorized"
        }
        require(authorization.candidateDigest == candidate.candidateDigest) {
            "witness_authorization_candidate_result_mismatch"
        }
        require(authorization.consentDigest == expectedConsentDigest) {
            "witness_authorization_consent_result_mismatch"
        }
        authorization.requireUsableAt(evaluatedAt)
        return authorization
    }

    private companion object {
        val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
    }
}
