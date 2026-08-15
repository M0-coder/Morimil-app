package com.morimil.app.improvements

import com.google.crypto.tink.subtle.Ed25519Verify
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Collections
import java.util.LinkedHashSet

/** External authority roles that Morimil cannot satisfy by merely naming itself as the actor. */
internal enum class SelfAuthorityRole {
    INDEPENDENT_VERIFIER,
    HUMAN_AUTHORIZER
}

/** Machine-verifiable claims carried by one signed independent-verifier attestation. */
internal enum class SelfVerificationClaim {
    PATCH_CONTENT_RECOMPUTED,
    EXACT_BASE,
    ARCHITECTURE_REVIEW,
    COMPILATION,
    UNIT_TESTS,
    INSTRUMENTED_TESTS,
    STATIC_ANALYSIS,
    SECURITY_CHECKS,
    REPRODUCIBILITY,
    COVERAGE_REVIEW,
    MUTATION_REVIEW,
    CROSS_LANGUAGE_VECTORS,
    SANDBOX_ISOLATION,
    SECRET_ISOLATION,
    BLAST_RADIUS_REVIEW,
    ROLLBACK_PLAN_REVIEW,
    AUDIT_TRAIL,
    HUMAN_AUTHORIZATION
}

/**
 * Signed evidence envelope produced outside Morimil's self-change state machine.
 *
 * A role string or boolean is not authority. The transition is accepted only if
 * this envelope verifies under a separately supplied trusted public key.
 */
internal data class SelfSignedAuthorityAttestation(
    val schemaVersion: String = SelfImprovementAuthorityProfile.SCHEMA,
    val role: SelfAuthorityRole,
    val signerId: String,
    val publicKeyRef: String,
    val observationDigest: String,
    val candidateDigest: String,
    val baseCommitSha: String,
    val evidenceBundleDigest: String,
    val claims: Set<SelfVerificationClaim>,
    val issuedAtMillis: Long,
    val nonce: String,
    val signatureValue: String
) {
    init {
        require(schemaVersion == SelfImprovementAuthorityProfile.SCHEMA) {
            "self_authority_schema_invalid"
        }
        require(signerId.isNotBlank()) { "self_authority_signer_id_blank" }
        require(SHA256_REF.matches(publicKeyRef)) { "self_authority_public_key_ref_invalid" }
        require(SHA256_REF.matches(observationDigest)) { "self_authority_observation_digest_invalid" }
        require(SHA256_REF.matches(candidateDigest)) { "self_authority_candidate_digest_invalid" }
        require(COMMIT_SHA.matches(baseCommitSha)) { "self_authority_base_sha_invalid" }
        require(SHA256_REF.matches(evidenceBundleDigest)) { "self_authority_evidence_digest_invalid" }
        require(claims.isNotEmpty()) { "self_authority_claims_empty" }
        require(issuedAtMillis >= 0L) { "self_authority_time_invalid" }
        require(nonce.isNotBlank()) { "self_authority_nonce_blank" }
        require(signatureValue.matches(LOWER_HEX_SIGNATURE)) { "self_authority_signature_invalid" }
        listOf(signerId, nonce).forEach { value ->
            require(value == Normalizer.normalize(value, Normalizer.Form.NFC)) {
                "self_authority_text_not_nfc"
            }
        }
    }

    private companion object {
        val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
        val COMMIT_SHA = Regex("^[a-f0-9]{40}$")
        val LOWER_HEX_SIGNATURE = Regex("^[a-f0-9]{128}$")
    }
}

internal class SelfImprovementTrustedAuthorityKey(
    val role: SelfAuthorityRole,
    val signerId: String,
    val publicKeyRef: String,
    rawPublicKey: ByteArray
) {
    val rawPublicKey: ByteArray = rawPublicKey.copyOf()

    init {
        require(signerId.isNotBlank()) { "self_authority_trusted_signer_blank" }
        require(rawPublicKey.size == ED25519_PUBLIC_KEY_BYTES) { "self_authority_public_key_size_invalid" }
        require(SelfImprovementAuthorityProfile.sha256(rawPublicKey) == publicKeyRef) {
            "self_authority_public_key_ref_mismatch"
        }
    }

    private companion object {
        const val ED25519_PUBLIC_KEY_BYTES = 32
    }
}

/** Evidence retained after a signed independent-verifier attestation has been validated. */
internal data class SelfVerifiedEvidence(
    val observationDigest: String,
    val candidateDigest: String,
    val baseCommitSha: String,
    val verifierId: String,
    val verifierPublicKeyRef: String,
    val evidenceBundleDigest: String,
    val claims: Set<SelfVerificationClaim>,
    val attestationDigest: String,
    val issuedAtMillis: Long
)

/** Authorization retained after a signed human attestation or low-risk verifier policy transition. */
internal data class SelfAuthorizationEvidence(
    val role: SelfAuthorityRole,
    val signerId: String,
    val signerPublicKeyRef: String,
    val authorizationAttestationDigest: String,
    val verificationAttestationDigest: String,
    val issuedAtMillis: Long
)

/**
 * Cryptographic verifier for external self-change authorities.
 *
 * Production composition must supply trusted public keys from outside Morimil's
 * self-generated patch. This class stores no private key and cannot sign its own
 * verification or authorization receipt.
 */
internal class SelfImprovementAuthorityVerifier(
    trustedKeys: Collection<SelfImprovementTrustedAuthorityKey>
) {
    private val trustedByIdentity: Map<AuthorityIdentity, ByteArray>

    init {
        val trusted = trustedKeys.toList()
        require(trusted.map { it.signerId }.distinct().size == trusted.size) {
            "self_authority_signer_identity_reuse_forbidden"
        }
        require(trusted.map { it.publicKeyRef }.distinct().size == trusted.size) {
            "self_authority_public_key_reuse_forbidden"
        }
        val prepared = trusted.map { item ->
            AuthorityIdentity(
                role = item.role,
                signerId = item.signerId,
                publicKeyRef = item.publicKeyRef
            ) to item.rawPublicKey.copyOf()
        }
        require(prepared.map { it.first }.distinct().size == prepared.size) {
            "self_authority_duplicate_trusted_identity"
        }
        trustedByIdentity = prepared.toMap()
    }

    fun verifyIndependent(
        attestation: SelfSignedAuthorityAttestation,
        expectedObservationDigest: String,
        expectedCandidateDigest: String,
        expectedBaseCommitSha: String
    ): SelfVerifiedEvidence {
        val snapshot = immutableSnapshot(attestation)
        require(snapshot.role == SelfAuthorityRole.INDEPENDENT_VERIFIER) {
            "self_authority_independent_verifier_required"
        }
        require(SelfVerificationClaim.HUMAN_AUTHORIZATION !in snapshot.claims) {
            "self_authority_verifier_cannot_claim_human_authorization"
        }
        require(snapshot.observationDigest == expectedObservationDigest) {
            "self_authority_observation_mismatch"
        }
        require(snapshot.candidateDigest == expectedCandidateDigest) {
            "self_authority_candidate_mismatch"
        }
        require(snapshot.baseCommitSha == expectedBaseCommitSha) {
            "self_authority_base_mismatch"
        }
        val attestationDigest = requireValidSignature(snapshot)
        return SelfVerifiedEvidence(
            observationDigest = snapshot.observationDigest,
            candidateDigest = snapshot.candidateDigest,
            baseCommitSha = snapshot.baseCommitSha,
            verifierId = snapshot.signerId,
            verifierPublicKeyRef = snapshot.publicKeyRef,
            evidenceBundleDigest = snapshot.evidenceBundleDigest,
            claims = snapshot.claims,
            attestationDigest = attestationDigest,
            issuedAtMillis = snapshot.issuedAtMillis
        )
    }

    fun verifyHumanAuthorization(
        attestation: SelfSignedAuthorityAttestation,
        expectedObservationDigest: String,
        expectedCandidateDigest: String,
        expectedBaseCommitSha: String,
        expectedVerificationAttestationDigest: String,
        expectedVerifierId: String,
        expectedVerifierPublicKeyRef: String
    ): SelfAuthorizationEvidence {
        val snapshot = immutableSnapshot(attestation)
        require(snapshot.role == SelfAuthorityRole.HUMAN_AUTHORIZER) {
            "self_authority_human_authorizer_required"
        }
        require(snapshot.claims == setOf(SelfVerificationClaim.HUMAN_AUTHORIZATION)) {
            "self_authority_human_claim_set_invalid"
        }
        require(snapshot.signerId != expectedVerifierId) {
            "self_authority_human_verifier_identity_separation_required"
        }
        require(snapshot.publicKeyRef != expectedVerifierPublicKeyRef) {
            "self_authority_human_verifier_key_separation_required"
        }
        require(snapshot.observationDigest == expectedObservationDigest) {
            "self_authority_observation_mismatch"
        }
        require(snapshot.candidateDigest == expectedCandidateDigest) {
            "self_authority_candidate_mismatch"
        }
        require(snapshot.baseCommitSha == expectedBaseCommitSha) {
            "self_authority_base_mismatch"
        }
        require(snapshot.evidenceBundleDigest == expectedVerificationAttestationDigest) {
            "self_authority_authorization_not_bound_to_verification"
        }
        val attestationDigest = requireValidSignature(snapshot)
        return SelfAuthorizationEvidence(
            role = SelfAuthorityRole.HUMAN_AUTHORIZER,
            signerId = snapshot.signerId,
            signerPublicKeyRef = snapshot.publicKeyRef,
            authorizationAttestationDigest = attestationDigest,
            verificationAttestationDigest = expectedVerificationAttestationDigest,
            issuedAtMillis = snapshot.issuedAtMillis
        )
    }

    private fun immutableSnapshot(
        attestation: SelfSignedAuthorityAttestation
    ): SelfSignedAuthorityAttestation {
        val claimsSnapshot: Set<SelfVerificationClaim> = Collections.unmodifiableSet(
            LinkedHashSet(attestation.claims)
        )
        return attestation.copy(claims = claimsSnapshot)
    }

    private fun requireValidSignature(attestation: SelfSignedAuthorityAttestation): String {
        val identity = AuthorityIdentity(
            role = attestation.role,
            signerId = attestation.signerId,
            publicKeyRef = attestation.publicKeyRef
        )
        val rawPublicKey = trustedByIdentity[identity]?.copyOf()
            ?: error("self_authority_untrusted_identity")
        try {
            Ed25519Verify(rawPublicKey).verify(
                SelfImprovementAuthorityProfile.decodeLowerHex(attestation.signatureValue),
                SelfImprovementAuthorityProfile.signingBytes(attestation)
            )
        } catch (error: GeneralSecurityException) {
            throw IllegalArgumentException("self_authority_signature_verification_failed", error)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("self_authority_signature_verification_failed", error)
        } catch (error: IllegalStateException) {
            throw IllegalArgumentException("self_authority_signature_verification_failed", error)
        }
        return SelfImprovementAuthorityProfile.attestationDigest(attestation)
    }

    private data class AuthorityIdentity(
        val role: SelfAuthorityRole,
        val signerId: String,
        val publicKeyRef: String
    )
}

internal object SelfImprovementAuthorityProfile {
    const val SCHEMA = "morimil.self_improvement.authority_attestation.v1"
    private const val SIGNING_DOMAIN = "morimil.self_improvement.authority_attestation.signing.v1"
    private const val ATTESTATION_DOMAIN = "morimil.self_improvement.authority_attestation.digest.v1"

    fun signingBytes(attestation: SelfSignedAuthorityAttestation): ByteArray {
        val claims = attestation.claims.map { it.name }.sorted()
        return frameFields(
            SIGNING_DOMAIN,
            buildList {
                add(attestation.schemaVersion)
                add(attestation.role.name)
                add(attestation.signerId)
                add(attestation.publicKeyRef)
                add(attestation.observationDigest)
                add(attestation.candidateDigest)
                add(attestation.baseCommitSha)
                add(attestation.evidenceBundleDigest)
                add(claims.size.toString())
                addAll(claims)
                add(attestation.issuedAtMillis.toString())
                add(attestation.nonce)
            }
        )
    }

    fun attestationDigest(attestation: SelfSignedAuthorityAttestation): String {
        return sha256(
            frameFields(
                ATTESTATION_DOMAIN,
                listOf(
                    sha256(signingBytes(attestation)),
                    attestation.signatureValue
                )
            )
        )
    }

    fun sha256(value: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value)
        return "sha256:" + digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    fun decodeLowerHex(value: String): ByteArray {
        require(value.length % 2 == 0 && value.matches(Regex("^[a-f0-9]+$"))) {
            "self_authority_hex_invalid"
        }
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun frameFields(domain: String, fields: List<String>): ByteArray {
        return ByteArrayOutputStream().use { output ->
            output.write(frame(domain))
            fields.forEach { field -> output.write(frame(field)) }
            output.toByteArray()
        }
    }

    private fun frame(value: String): ByteArray {
        require(value == Normalizer.normalize(value, Normalizer.Form.NFC)) {
            "self_authority_text_not_nfc"
        }
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        return ByteArrayOutputStream(bytes.size + 24).use { output ->
            output.write(bytes.size.toString().toByteArray(StandardCharsets.US_ASCII))
            output.write(':'.code)
            output.write(bytes)
            output.write('\n'.code)
            output.toByteArray()
        }
    }
}
