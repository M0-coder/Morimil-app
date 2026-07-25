package com.morimil.app.data.genesis.ultra

import com.morimil.app.data.local.GenesisUltraBirthArtifactEntity
import com.morimil.app.data.local.GenesisUltraBirthAuthorizationEntity
import com.morimil.app.data.local.GenesisUltraBirthCommitEntity
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * Canonical, non-secret audit evidence for the consent-bound authorization that
 * admitted one Genesis Ultra birth. It contains no private key material.
 */
internal data class GenesisUltraDurableBirthAuthorization(
    val schemaVersion: String,
    val candidateDigest: String,
    val consentDigest: String,
    val birthStateDigest: String,
    val receiptDigest: String,
    val bodyId: String,
    val guardianId: String,
    val guardianKeyEpochId: String,
    val authorizedAt: String,
    val expiresAt: String,
    val authorizationDigest: String
) {
    init {
        require(schemaVersion == SCHEMA_VERSION) { "durable_birth_authorization_schema_invalid" }
        require(SHA256_REF.matches(candidateDigest)) {
            "durable_birth_authorization_candidate_digest_invalid"
        }
        require(SHA256_REF.matches(consentDigest)) {
            "durable_birth_authorization_consent_digest_invalid"
        }
        require(SHA256_REF.matches(birthStateDigest)) {
            "durable_birth_authorization_state_digest_invalid"
        }
        require(SHA256_REF.matches(receiptDigest)) {
            "durable_birth_authorization_receipt_digest_invalid"
        }
        require(BODY_ID.matches(bodyId)) { "durable_birth_authorization_body_id_invalid" }
        GenesisUltraHashProfile.requireNfc(guardianId)
        GenesisUltraHashProfile.requireNfc(guardianKeyEpochId)
        require(guardianId.length in 1..128) { "durable_birth_authorization_guardian_id_invalid" }
        require(guardianKeyEpochId.length in 16..128) {
            "durable_birth_authorization_guardian_epoch_invalid"
        }
        val authorized = canonicalTimestamp(authorizedAt, "durable_birth_authorization_time_invalid")
        val expiry = canonicalTimestamp(expiresAt, "durable_birth_authorization_expiry_invalid")
        require(authorized < expiry) { "durable_birth_authorization_expiry_order_invalid" }
        require(authorizationDigest == digestFor(this)) {
            "durable_birth_authorization_digest_mismatch"
        }
    }

    fun copySourceBytes(): ByteArray = serialize().toByteArray(StandardCharsets.UTF_8)

    fun toEntity(slotId: String): GenesisUltraBirthAuthorizationEntity {
        require(slotId == GenesisUltraBirthCommitEntity.PRIMARY_SLOT) {
            "durable_birth_authorization_slot_invalid"
        }
        val source = copySourceBytes()
        return GenesisUltraBirthAuthorizationEntity(
            slotId = slotId,
            schemaVersion = schemaVersion,
            candidateDigest = candidateDigest,
            consentDigest = consentDigest,
            birthStateDigest = birthStateDigest,
            receiptDigest = receiptDigest,
            bodyId = bodyId,
            guardianId = guardianId,
            guardianKeyEpochId = guardianKeyEpochId,
            authorizedAt = authorizedAt,
            expiresAt = expiresAt,
            authorizationDigest = authorizationDigest,
            sourceDigest = GenesisUltraHashProfile.sha256(source),
            sourceBytes = source
        )
    }

    fun requireMatchesCommit(
        commit: GenesisUltraBirthCommitEntity,
        artifacts: List<GenesisUltraBirthArtifactEntity>
    ) {
        require(commit.slotId == GenesisUltraBirthCommitEntity.PRIMARY_SLOT) {
            "durable_birth_authorization_commit_slot_invalid"
        }
        require(
            birthStateDigest == commit.birthStateDigest &&
                receiptDigest == commit.receiptDigest &&
                bodyId == commit.initialBodyId &&
                bodyId == commit.activeWriterBodyId
        ) { "durable_birth_authorization_commit_mismatch" }

        val signatureArtifact = artifacts.singleOrNull { artifact ->
            artifact.artifactKind == "seed_signature"
        } ?: throw IllegalArgumentException("durable_birth_authorization_seed_signature_missing")
        val identityArtifact = artifacts.singleOrNull { artifact ->
            artifact.artifactKind == "instance_identity"
        } ?: throw IllegalArgumentException("durable_birth_authorization_identity_missing")
        val signature = GenesisUltraContractParser.parseSignatureEnvelope(
            decodeUtf8Strict(signatureArtifact.payload)
        )
        val identity = GenesisUltraContractParser.parseInstanceIdentity(
            decodeUtf8Strict(identityArtifact.payload)
        )
        require(
            guardianId == signature.signerId &&
                guardianKeyEpochId == signature.keyEpochId &&
                guardianId == identity.guardianId &&
                signature.signedDigest == commit.seedRootHash &&
                identity.instanceId == commit.instanceId
        ) { "durable_birth_authorization_guardian_or_identity_mismatch" }
    }

    private fun serialize(): String {
        return JSONObject()
            .put("schema_version", schemaVersion)
            .put("candidate_digest", candidateDigest)
            .put("consent_digest", consentDigest)
            .put("birth_state_digest", birthStateDigest)
            .put("receipt_digest", receiptDigest)
            .put("body_id", bodyId)
            .put("guardian_id", guardianId)
            .put("guardian_key_epoch_id", guardianKeyEpochId)
            .put("authorized_at", authorizedAt)
            .put("expires_at", expiresAt)
            .put("authorization_digest", authorizationDigest)
            .toString()
    }

    internal companion object {
        const val SCHEMA_VERSION = "genesis.atomic.birth.authorization.receipt.v0.1"
        private const val AUTHORIZATION_DOMAIN = "genesis.atomic.birth.authorization.v0.1"
        private val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
        private val BODY_ID = Regex("^body_[a-f0-9]{64}$")
        private val FIELDS = setOf(
            "schema_version",
            "candidate_digest",
            "consent_digest",
            "birth_state_digest",
            "receipt_digest",
            "body_id",
            "guardian_id",
            "guardian_key_epoch_id",
            "authorized_at",
            "expires_at",
            "authorization_digest"
        )

        fun from(authorization: GenesisUltraAuthorizedAtomicBirth): GenesisUltraDurableBirthAuthorization {
            val bundle = authorization.copyVerifiedBirth().copyPersistenceBundle()
            val signatureArtifact = bundle.artifacts.singleOrNull { artifact ->
                artifact.artifactKind == "seed_signature"
            } ?: throw IllegalArgumentException("durable_birth_authorization_seed_signature_missing")
            val signature = GenesisUltraContractParser.parseSignatureEnvelope(
                decodeUtf8Strict(signatureArtifact.payload)
            )
            require(signature.signerId == bundle.instanceIdentity.guardianId) {
                "durable_birth_authorization_guardian_identity_mismatch"
            }
            return create(
                candidateDigest = authorization.candidateDigest,
                consentDigest = authorization.consentDigest,
                birthStateDigest = authorization.birthStateDigest,
                receiptDigest = authorization.receiptDigest,
                bodyId = bundle.birthState.initialBodyId,
                guardianId = signature.signerId,
                guardianKeyEpochId = signature.keyEpochId,
                authorizedAt = authorization.authorizedAt,
                expiresAt = authorization.expiresAt,
                authorizationDigest = authorization.authorizationDigest
            )
        }

        fun fromEntity(entity: GenesisUltraBirthAuthorizationEntity): GenesisUltraDurableBirthAuthorization {
            require(entity.slotId == GenesisUltraBirthCommitEntity.PRIMARY_SLOT) {
                "durable_birth_authorization_slot_invalid"
            }
            require(entity.sourceBytes.isNotEmpty()) { "durable_birth_authorization_source_empty" }
            require(GenesisUltraHashProfile.sha256(entity.sourceBytes) == entity.sourceDigest) {
                "durable_birth_authorization_source_digest_mismatch"
            }
            val root = JSONObject(decodeUtf8Strict(entity.sourceBytes))
            require(root.keys().asSequence().toSet() == FIELDS) {
                "durable_birth_authorization_source_fields_invalid"
            }
            val parsed = GenesisUltraDurableBirthAuthorization(
                schemaVersion = root.getString("schema_version"),
                candidateDigest = root.getString("candidate_digest"),
                consentDigest = root.getString("consent_digest"),
                birthStateDigest = root.getString("birth_state_digest"),
                receiptDigest = root.getString("receipt_digest"),
                bodyId = root.getString("body_id"),
                guardianId = root.getString("guardian_id"),
                guardianKeyEpochId = root.getString("guardian_key_epoch_id"),
                authorizedAt = root.getString("authorized_at"),
                expiresAt = root.getString("expires_at"),
                authorizationDigest = root.getString("authorization_digest")
            )
            require(
                entity.schemaVersion == parsed.schemaVersion &&
                    entity.candidateDigest == parsed.candidateDigest &&
                    entity.consentDigest == parsed.consentDigest &&
                    entity.birthStateDigest == parsed.birthStateDigest &&
                    entity.receiptDigest == parsed.receiptDigest &&
                    entity.bodyId == parsed.bodyId &&
                    entity.guardianId == parsed.guardianId &&
                    entity.guardianKeyEpochId == parsed.guardianKeyEpochId &&
                    entity.authorizedAt == parsed.authorizedAt &&
                    entity.expiresAt == parsed.expiresAt &&
                    entity.authorizationDigest == parsed.authorizationDigest &&
                    parsed.copySourceBytes().contentEquals(entity.sourceBytes)
            ) { "durable_birth_authorization_entity_source_mismatch" }
            return parsed
        }

        fun create(
            candidateDigest: String,
            consentDigest: String,
            birthStateDigest: String,
            receiptDigest: String,
            bodyId: String,
            guardianId: String,
            guardianKeyEpochId: String,
            authorizedAt: String,
            expiresAt: String,
            authorizationDigest: String? = null
        ): GenesisUltraDurableBirthAuthorization {
            val computed = digestForFields(
                candidateDigest = candidateDigest,
                consentDigest = consentDigest,
                birthStateDigest = birthStateDigest,
                receiptDigest = receiptDigest,
                bodyId = bodyId,
                guardianId = guardianId,
                guardianKeyEpochId = guardianKeyEpochId,
                authorizedAt = authorizedAt,
                expiresAt = expiresAt
            )
            return GenesisUltraDurableBirthAuthorization(
                schemaVersion = SCHEMA_VERSION,
                candidateDigest = candidateDigest,
                consentDigest = consentDigest,
                birthStateDigest = birthStateDigest,
                receiptDigest = receiptDigest,
                bodyId = bodyId,
                guardianId = guardianId,
                guardianKeyEpochId = guardianKeyEpochId,
                authorizedAt = authorizedAt,
                expiresAt = expiresAt,
                authorizationDigest = authorizationDigest ?: computed
            )
        }

        private fun digestFor(value: GenesisUltraDurableBirthAuthorization): String {
            return digestForFields(
                candidateDigest = value.candidateDigest,
                consentDigest = value.consentDigest,
                birthStateDigest = value.birthStateDigest,
                receiptDigest = value.receiptDigest,
                bodyId = value.bodyId,
                guardianId = value.guardianId,
                guardianKeyEpochId = value.guardianKeyEpochId,
                authorizedAt = value.authorizedAt,
                expiresAt = value.expiresAt
            )
        }

        private fun digestForFields(
            candidateDigest: String,
            consentDigest: String,
            birthStateDigest: String,
            receiptDigest: String,
            bodyId: String,
            guardianId: String,
            guardianKeyEpochId: String,
            authorizedAt: String,
            expiresAt: String
        ): String {
            return GenesisUltraHashProfile.hashFields(
                AUTHORIZATION_DOMAIN,
                listOf(
                    candidateDigest,
                    consentDigest,
                    birthStateDigest,
                    receiptDigest,
                    bodyId,
                    guardianId,
                    guardianKeyEpochId,
                    authorizedAt,
                    expiresAt
                )
            )
        }

        private fun canonicalTimestamp(value: String, errorCode: String): Instant {
            val parsed = runCatching { Instant.parse(value) }
                .getOrElse { failure -> throw IllegalArgumentException(errorCode, failure) }
            require(parsed.toString() == value) { errorCode }
            return parsed
        }

        private fun decodeUtf8Strict(bytes: ByteArray): String {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return decoder.decode(ByteBuffer.wrap(bytes)).toString()
        }
    }
}
