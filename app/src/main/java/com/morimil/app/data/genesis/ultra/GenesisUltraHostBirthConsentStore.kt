package com.morimil.app.data.genesis.ultra

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.repository.MemoryAppendGate
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit

internal enum class GenesisUltraHostBirthConsentState {
    ABSENT,
    READY,
    EXPIRED,
    INCONSISTENT
}

/**
 * Values that the local confirmation UI must present and confirm explicitly.
 * The store rejects generic booleans that are not bound to the exact candidate.
 */
internal data class GenesisUltraHostBirthConsentRequest(
    val presentedCandidateDigest: String,
    val presentedInstanceId: String,
    val presentedCompanionName: String,
    val presentedConfirmationCode: String,
    val decision: String,
    val confirmationMode: String,
    val confirmationPurpose: String,
    val userPresenceConfirmed: Boolean
) {
    init {
        GenesisUltraHashProfile.requireNfc(presentedInstanceId)
        GenesisUltraHashProfile.requireNfc(presentedCompanionName)
        require(SHA256_REF.matches(presentedCandidateDigest)) {
            "host_birth_consent_candidate_digest_invalid"
        }
        require(INSTANCE_ID.matches(presentedInstanceId)) {
            "host_birth_consent_instance_id_invalid"
        }
        require(presentedCompanionName.length in 1..128) {
            "host_birth_consent_companion_name_invalid"
        }
        require(CONFIRMATION_CODE.matches(presentedConfirmationCode)) {
            "host_birth_consent_confirmation_code_invalid"
        }
        require(decision == APPROVE_DECISION) { "host_birth_consent_decision_invalid" }
        require(confirmationMode == INTERACTIVE_CONFIRMATION_MODE) {
            "host_birth_consent_confirmation_mode_invalid"
        }
        require(confirmationPurpose == BIRTH_CONFIRMATION_PURPOSE) {
            "host_birth_consent_confirmation_purpose_invalid"
        }
        require(userPresenceConfirmed) { "host_birth_consent_user_presence_required" }
    }

    internal companion object {
        const val APPROVE_DECISION = "approve_birth"
        const val INTERACTIVE_CONFIRMATION_MODE = "interactive_local_user_confirmation"
        const val BIRTH_CONFIRMATION_PURPOSE = "genesis_ultra_atomic_birth"

        fun confirmationCode(candidateDigest: String): String {
            require(SHA256_REF.matches(candidateDigest)) {
                "host_birth_consent_candidate_digest_invalid"
            }
            return candidateDigest.removePrefix("sha256:").takeLast(CONFIRMATION_CODE_HEX_LENGTH)
        }

        private const val CONFIRMATION_CODE_HEX_LENGTH = 12
        private val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
        private val INSTANCE_ID = Regex("^inst_[a-f0-9]{64}$")
        private val CONFIRMATION_CODE = Regex("^[a-f0-9]{12}$")
    }
}

/**
 * Authenticated record of one explicit local approval. It is evidence of a UI
 * ceremony, not a Guardian grant, Body signature or authorization to commit.
 */
internal class GenesisUltraVerifiedHostBirthConsent(
    val schemaVersion: String,
    val consentId: String,
    val candidateDigest: String,
    val instanceId: String,
    val companionName: String,
    val seedRootHash: String,
    val bodyId: String,
    val guardianId: String,
    val guardianKeyEpochId: String,
    val decision: String,
    val confirmationMode: String,
    val confirmationPurpose: String,
    val consentedAt: String,
    val expiresAt: String,
    val protectionProfile: String,
    val consentDigest: String
) {
    val birthCommitAuthorized: Boolean = false

    init {
        require(schemaVersion == CONSENT_SCHEMA) { "host_birth_consent_schema_invalid" }
        require(CONSENT_ID.matches(consentId)) { "host_birth_consent_id_invalid" }
        require(SHA256_REF.matches(candidateDigest)) { "host_birth_consent_candidate_digest_invalid" }
        require(INSTANCE_ID.matches(instanceId)) { "host_birth_consent_instance_id_invalid" }
        GenesisUltraHashProfile.requireNfc(companionName)
        require(companionName.length in 1..128) { "host_birth_consent_companion_name_invalid" }
        require(SHA256_REF.matches(seedRootHash)) { "host_birth_consent_seed_root_invalid" }
        require(BODY_ID.matches(bodyId)) { "host_birth_consent_body_id_invalid" }
        GenesisUltraHashProfile.requireNfc(guardianId)
        GenesisUltraHashProfile.requireNfc(guardianKeyEpochId)
        require(guardianId.length in 1..128) { "host_birth_consent_guardian_id_invalid" }
        require(guardianKeyEpochId.length in 16..128) {
            "host_birth_consent_guardian_epoch_invalid"
        }
        require(decision == GenesisUltraHostBirthConsentRequest.APPROVE_DECISION) {
            "host_birth_consent_decision_invalid"
        }
        require(confirmationMode == GenesisUltraHostBirthConsentRequest.INTERACTIVE_CONFIRMATION_MODE) {
            "host_birth_consent_confirmation_mode_invalid"
        }
        require(confirmationPurpose == GenesisUltraHostBirthConsentRequest.BIRTH_CONFIRMATION_PURPOSE) {
            "host_birth_consent_confirmation_purpose_invalid"
        }
        val consented = requireCanonicalTimestamp(consentedAt, "host_birth_consent_time_invalid")
        val expires = requireCanonicalTimestamp(expiresAt, "host_birth_consent_expiry_invalid")
        require(consented < expires) { "host_birth_consent_expiry_order_invalid" }
        require(protectionProfile == PROTECTION_PROFILE) {
            "host_birth_consent_protection_profile_invalid"
        }
        require(!birthCommitAuthorized) { "host_birth_consent_cannot_authorize_commit" }
        require(consentDigest == digestFor(this)) { "host_birth_consent_digest_mismatch" }
    }

    fun isValidAt(evaluatedAt: String): Boolean {
        val evaluated = requireCanonicalTimestamp(evaluatedAt, "host_birth_consent_evaluation_time_invalid")
        return evaluated >= Instant.parse(consentedAt) && evaluated < Instant.parse(expiresAt)
    }

    internal fun matches(candidate: GenesisUltraConstructedBirthCandidate): Boolean {
        val model = candidate.candidate
        return candidateDigest == candidate.candidateDigest &&
            instanceId == model.instanceIdentity.instanceId &&
            companionName == model.instanceIdentity.companionName &&
            seedRootHash == model.release.verifiedRootHash &&
            bodyId == model.bodyRecord.bodyId &&
            guardianId == model.release.signature.signerId &&
            guardianKeyEpochId == model.release.signature.keyEpochId
    }

    internal companion object {
        const val CONSENT_SCHEMA = "genesis.host.birth.consent.v0.1"
        const val PROTECTION_PROFILE = "android-keystore.aes256-gcm.v0.1"
        const val CONSENT_DIGEST_DOMAIN = "genesis.host.birth.consent.digest.v0.1"

        fun digestFor(consent: GenesisUltraVerifiedHostBirthConsent): String {
            return GenesisUltraHashProfile.hashFields(
                CONSENT_DIGEST_DOMAIN,
                listOf(
                    consent.schemaVersion,
                    consent.consentId,
                    consent.candidateDigest,
                    consent.instanceId,
                    consent.companionName,
                    consent.seedRootHash,
                    consent.bodyId,
                    consent.guardianId,
                    consent.guardianKeyEpochId,
                    consent.decision,
                    consent.confirmationMode,
                    consent.confirmationPurpose,
                    consent.consentedAt,
                    consent.expiresAt,
                    consent.protectionProfile
                )
            )
        }

        private val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
        private val CONSENT_ID = Regex("^consent_[a-f0-9]{64}$")
        private val INSTANCE_ID = Regex("^inst_[a-f0-9]{64}$")
        private val BODY_ID = Regex("^body_[a-f0-9]{64}$")
    }
}

/**
 * Stores one short-lived consent record encrypted and authenticated by a
 * dedicated Android Keystore key. No automatic approval or replacement path
 * exists. Expired consent must be revoked before a new ceremony can occur.
 */
internal class GenesisUltraAndroidHostBirthConsentStore(
    context: Context,
    private val database: MorimilDatabase,
    private val preferencesName: String = DEFAULT_PREFERENCES_NAME,
    private val masterKeyAlias: String = DEFAULT_MASTER_KEY_ALIAS,
    private val clock: () -> Instant = { Instant.now().truncatedTo(ChronoUnit.SECONDS) },
    private val entropySource: (Int) -> ByteArray = { size ->
        ByteArray(size).also(SecureRandom()::nextBytes)
    }
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE
    )

    fun readState(evaluatedAt: String = canonicalNow()): GenesisUltraHostBirthConsentState {
        return synchronized(STORAGE_LOCK) {
            val recordExists = preferences.contains(RECORD_KEY)
            val masterKeyExists = runCatching { AndroidKeystore.hasKey(masterKeyAlias) }
                .getOrElse { return@synchronized GenesisUltraHostBirthConsentState.INCONSISTENT }
            when {
                !recordExists && !masterKeyExists -> GenesisUltraHostBirthConsentState.ABSENT
                recordExists && masterKeyExists -> {
                    val encoded = preferences.getString(RECORD_KEY, null)
                    val consent = encoded?.let { value -> runCatching { loadRecord(value) }.getOrNull() }
                    when {
                        consent == null -> GenesisUltraHostBirthConsentState.INCONSISTENT
                        consent.isValidAt(evaluatedAt) -> GenesisUltraHostBirthConsentState.READY
                        Instant.parse(evaluatedAt) >= Instant.parse(consent.expiresAt) -> {
                            GenesisUltraHostBirthConsentState.EXPIRED
                        }
                        else -> GenesisUltraHostBirthConsentState.INCONSISTENT
                    }
                }
                else -> GenesisUltraHostBirthConsentState.INCONSISTENT
            }
        }
    }

    suspend fun recordExplicitConsent(
        candidate: GenesisUltraConstructedBirthCandidate,
        request: GenesisUltraHostBirthConsentRequest
    ): GenesisUltraVerifiedHostBirthConsent {
        validateCandidateAndPresentation(candidate, request)
        return MemoryAppendGate.withAppendLock {
            require(
                GenesisUltraAtomicBirthStore(database).readState() ==
                    GenesisUltraPersistedBirthState.ABSENT
            ) { "host_birth_consent_requires_absent_birth" }
            synchronized(STORAGE_LOCK) {
                require(readState() == GenesisUltraHostBirthConsentState.ABSENT) {
                    "host_birth_consent_record_not_absent"
                }
                createRecord(candidate)
            }
        }
    }

    suspend fun loadForCandidate(
        candidate: GenesisUltraConstructedBirthCandidate,
        evaluatedAt: String = canonicalNow()
    ): GenesisUltraVerifiedHostBirthConsent {
        requireCandidateStillValid(candidate, evaluatedAt)
        return MemoryAppendGate.withAppendLock {
            require(
                GenesisUltraAtomicBirthStore(database).readState() ==
                    GenesisUltraPersistedBirthState.ABSENT
            ) { "host_birth_consent_load_requires_absent_birth" }
            synchronized(STORAGE_LOCK) {
                require(readState(evaluatedAt) == GenesisUltraHostBirthConsentState.READY) {
                    "host_birth_consent_not_ready"
                }
                val consent = loadRecord(requireNotNull(preferences.getString(RECORD_KEY, null)))
                require(consent.matches(candidate)) { "host_birth_consent_candidate_mismatch" }
                require(consent.isValidAt(evaluatedAt)) { "host_birth_consent_expired" }
                require(!consent.birthCommitAuthorized) {
                    "host_birth_consent_cannot_authorize_commit"
                }
                consent
            }
        }
    }

    suspend fun revokeBeforeBirth(expectedCandidateDigest: String): Boolean {
        require(SHA256_REF.matches(expectedCandidateDigest)) {
            "host_birth_consent_candidate_digest_invalid"
        }
        return MemoryAppendGate.withAppendLock {
            require(
                GenesisUltraAtomicBirthStore(database).readState() ==
                    GenesisUltraPersistedBirthState.ABSENT
            ) { "host_birth_consent_revoke_requires_absent_birth" }
            synchronized(STORAGE_LOCK) {
                if (readState() == GenesisUltraHostBirthConsentState.ABSENT) {
                    return@synchronized false
                }
                val encoded = requireNotNull(preferences.getString(RECORD_KEY, null)) {
                    "host_birth_consent_record_missing"
                }
                val consent = loadRecord(encoded)
                require(consent.candidateDigest == expectedCandidateDigest) {
                    "host_birth_consent_revoke_candidate_mismatch"
                }
                check(preferences.edit().remove(RECORD_KEY).commit()) {
                    "host_birth_consent_record_revoke_failed"
                }
                if (AndroidKeystore.hasKey(masterKeyAlias)) {
                    AndroidKeystore.deleteKey(masterKeyAlias)
                }
                check(readState() == GenesisUltraHostBirthConsentState.ABSENT) {
                    "host_birth_consent_revoke_incomplete"
                }
                true
            }
        }
    }

    private fun createRecord(
        candidate: GenesisUltraConstructedBirthCandidate
    ): GenesisUltraVerifiedHostBirthConsent {
        require(!preferences.contains(RECORD_KEY)) { "host_birth_consent_record_already_exists" }
        require(!AndroidKeystore.hasKey(masterKeyAlias)) {
            "host_birth_consent_master_key_exists_without_record"
        }

        var keyCreated = false
        try {
            val now = clock().truncatedTo(ChronoUnit.SECONDS)
            val possessionExpiry = Instant.parse(candidate.candidate.bodyPossession.proof.expiresAt)
            require(now < possessionExpiry) { "host_birth_consent_candidate_possession_expired" }
            val expires = minOf(now.plusSeconds(MAX_CONSENT_WINDOW_SECONDS), possessionExpiry)
            require(now < expires) { "host_birth_consent_expiry_invalid" }

            val entropy = entropySource(CONSENT_ENTROPY_BYTES)
            require(entropy.size == CONSENT_ENTROPY_BYTES && entropy.any { byte -> byte.toInt() != 0 }) {
                "host_birth_consent_entropy_invalid"
            }
            val entropyRef = GenesisUltraHashProfile.sha256(entropy)
            entropy.fill(0)
            val consentId = "consent_" + GenesisUltraHashProfile.hashFields(
                CONSENT_ID_DOMAIN,
                listOf(candidate.candidateDigest, now.toString(), entropyRef)
            ).removePrefix("sha256:")

            val model = candidate.candidate
            val withoutDigest = GenesisUltraVerifiedHostBirthConsent(
                schemaVersion = GenesisUltraVerifiedHostBirthConsent.CONSENT_SCHEMA,
                consentId = consentId,
                candidateDigest = candidate.candidateDigest,
                instanceId = model.instanceIdentity.instanceId,
                companionName = model.instanceIdentity.companionName,
                seedRootHash = model.release.verifiedRootHash,
                bodyId = model.bodyRecord.bodyId,
                guardianId = model.release.signature.signerId,
                guardianKeyEpochId = model.release.signature.keyEpochId,
                decision = GenesisUltraHostBirthConsentRequest.APPROVE_DECISION,
                confirmationMode = GenesisUltraHostBirthConsentRequest.INTERACTIVE_CONFIRMATION_MODE,
                confirmationPurpose = GenesisUltraHostBirthConsentRequest.BIRTH_CONFIRMATION_PURPOSE,
                consentedAt = now.toString(),
                expiresAt = expires.toString(),
                protectionProfile = GenesisUltraVerifiedHostBirthConsent.PROTECTION_PROFILE,
                consentDigest = ZERO_SHA256
            )
            val consent = copyWithDigest(
                withoutDigest,
                GenesisUltraVerifiedHostBirthConsent.digestFor(withoutDigest)
            )

            AndroidKeystore.generateNewAes256GcmKey(masterKeyAlias)
            keyCreated = true
            val ciphertext = AndroidKeystore.getAead(masterKeyAlias).encrypt(
                encodeConsent(consent).toByteArray(StandardCharsets.UTF_8),
                associatedData()
            )
            val record = JSONObject()
                .put("schema_version", RECORD_SCHEMA)
                .put("protection_profile", GenesisUltraVerifiedHostBirthConsent.PROTECTION_PROFILE)
                .put("consent_digest", consent.consentDigest)
                .put("encrypted_consent", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .toString()
            check(preferences.edit().putString(RECORD_KEY, record).commit()) {
                "host_birth_consent_record_commit_failed"
            }
            return loadRecord(requireNotNull(preferences.getString(RECORD_KEY, null)))
        } catch (failure: Exception) {
            if (!preferences.contains(RECORD_KEY) && keyCreated) {
                runCatching {
                    if (AndroidKeystore.hasKey(masterKeyAlias)) AndroidKeystore.deleteKey(masterKeyAlias)
                }
            }
            throw IllegalStateException("host_birth_consent_record_failed", failure)
        }
    }

    private fun validateCandidateAndPresentation(
        candidate: GenesisUltraConstructedBirthCandidate,
        request: GenesisUltraHostBirthConsentRequest
    ) {
        requireCandidateStillValid(candidate, canonicalNow())
        val model = candidate.candidate
        require(request.presentedCandidateDigest == candidate.candidateDigest) {
            "host_birth_consent_presented_digest_mismatch"
        }
        require(request.presentedInstanceId == model.instanceIdentity.instanceId) {
            "host_birth_consent_presented_instance_mismatch"
        }
        require(request.presentedCompanionName == model.instanceIdentity.companionName) {
            "host_birth_consent_presented_name_mismatch"
        }
        require(
            request.presentedConfirmationCode ==
                GenesisUltraHostBirthConsentRequest.confirmationCode(candidate.candidateDigest)
        ) { "host_birth_consent_presented_code_mismatch" }
    }

    private fun requireCandidateStillValid(
        candidate: GenesisUltraConstructedBirthCandidate,
        evaluatedAt: String
    ) {
        require(candidate.assessment.structurallyValid) {
            "host_birth_consent_candidate_not_structurally_valid"
        }
        require(!candidate.assessment.birthReady && !candidate.birthCommitAuthorized) {
            "host_birth_consent_candidate_already_authorized"
        }
        val reassessment = GenesisUltraBirthCandidateValidator.assess(candidate.candidate, evaluatedAt)
        require(reassessment.structurallyValid) {
            "host_birth_consent_candidate_expired_or_changed:${reassessment.issues}"
        }
    }

    private fun loadRecord(encodedRecord: String): GenesisUltraVerifiedHostBirthConsent {
        return try {
            val record = JSONObject(encodedRecord)
            require(record.keys().asSequence().toSet() == RECORD_FIELDS) {
                "host_birth_consent_record_fields_invalid"
            }
            require(record.getString("schema_version") == RECORD_SCHEMA) {
                "host_birth_consent_record_schema_invalid"
            }
            require(
                record.getString("protection_profile") ==
                    GenesisUltraVerifiedHostBirthConsent.PROTECTION_PROFILE
            ) { "host_birth_consent_record_profile_invalid" }
            val plaintext = AndroidKeystore.getAead(masterKeyAlias).decrypt(
                Base64.decode(record.getString("encrypted_consent"), Base64.NO_WRAP),
                associatedData()
            )
            val consent = decodeConsent(String(plaintext, StandardCharsets.UTF_8))
            require(record.getString("consent_digest") == consent.consentDigest) {
                "host_birth_consent_record_digest_changed"
            }
            consent
        } catch (failure: Exception) {
            throw IllegalStateException("host_birth_consent_load_failed", failure)
        }
    }

    private fun encodeConsent(consent: GenesisUltraVerifiedHostBirthConsent): String {
        return JSONObject()
            .put("schema_version", consent.schemaVersion)
            .put("consent_id", consent.consentId)
            .put("candidate_digest", consent.candidateDigest)
            .put("instance_id", consent.instanceId)
            .put("companion_name", consent.companionName)
            .put("seed_root_hash", consent.seedRootHash)
            .put("body_id", consent.bodyId)
            .put("guardian_id", consent.guardianId)
            .put("guardian_key_epoch_id", consent.guardianKeyEpochId)
            .put("decision", consent.decision)
            .put("confirmation_mode", consent.confirmationMode)
            .put("confirmation_purpose", consent.confirmationPurpose)
            .put("consented_at", consent.consentedAt)
            .put("expires_at", consent.expiresAt)
            .put("protection_profile", consent.protectionProfile)
            .put("consent_digest", consent.consentDigest)
            .toString()
    }

    private fun decodeConsent(encoded: String): GenesisUltraVerifiedHostBirthConsent {
        val root = JSONObject(encoded)
        require(root.keys().asSequence().toSet() == CONSENT_FIELDS) {
            "host_birth_consent_fields_invalid"
        }
        return GenesisUltraVerifiedHostBirthConsent(
            schemaVersion = root.getString("schema_version"),
            consentId = root.getString("consent_id"),
            candidateDigest = root.getString("candidate_digest"),
            instanceId = root.getString("instance_id"),
            companionName = root.getString("companion_name"),
            seedRootHash = root.getString("seed_root_hash"),
            bodyId = root.getString("body_id"),
            guardianId = root.getString("guardian_id"),
            guardianKeyEpochId = root.getString("guardian_key_epoch_id"),
            decision = root.getString("decision"),
            confirmationMode = root.getString("confirmation_mode"),
            confirmationPurpose = root.getString("confirmation_purpose"),
            consentedAt = root.getString("consented_at"),
            expiresAt = root.getString("expires_at"),
            protectionProfile = root.getString("protection_profile"),
            consentDigest = root.getString("consent_digest")
        )
    }

    private fun copyWithDigest(
        source: GenesisUltraVerifiedHostBirthConsent,
        digest: String
    ): GenesisUltraVerifiedHostBirthConsent {
        return GenesisUltraVerifiedHostBirthConsent(
            schemaVersion = source.schemaVersion,
            consentId = source.consentId,
            candidateDigest = source.candidateDigest,
            instanceId = source.instanceId,
            companionName = source.companionName,
            seedRootHash = source.seedRootHash,
            bodyId = source.bodyId,
            guardianId = source.guardianId,
            guardianKeyEpochId = source.guardianKeyEpochId,
            decision = source.decision,
            confirmationMode = source.confirmationMode,
            confirmationPurpose = source.confirmationPurpose,
            consentedAt = source.consentedAt,
            expiresAt = source.expiresAt,
            protectionProfile = source.protectionProfile,
            consentDigest = digest
        )
    }

    private fun associatedData(): ByteArray {
        return ByteArrayOutputStream().use { output ->
            output.write(GenesisUltraHashProfile.frame(RECORD_AAD_DOMAIN))
            output.write(GenesisUltraHashProfile.frame(RECORD_SCHEMA))
            output.write(
                GenesisUltraHashProfile.frame(
                    GenesisUltraVerifiedHostBirthConsent.PROTECTION_PROFILE
                )
            )
            output.toByteArray()
        }
    }

    private fun canonicalNow(): String = clock().truncatedTo(ChronoUnit.SECONDS).toString()

    internal companion object {
        const val DEFAULT_PREFERENCES_NAME = "genesis_ultra_host_birth_consent_v1"
        const val DEFAULT_MASTER_KEY_ALIAS =
            "com.morimil.app.genesis.ultra.host.birth.consent.kek.v1"
        const val RECORD_KEY = "active_host_birth_consent"

        private const val RECORD_SCHEMA = "genesis.host.birth.consent.record.v0.1"
        private const val RECORD_AAD_DOMAIN = "genesis.host.birth.consent.record.aad.v0.1"
        private const val CONSENT_ID_DOMAIN = "genesis.host.birth.consent.id.v0.1"
        private const val MAX_CONSENT_WINDOW_SECONDS = 120L
        private const val CONSENT_ENTROPY_BYTES = 32
        private val ZERO_SHA256 = "sha256:" + "0".repeat(64)
        private val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
        private val RECORD_FIELDS = setOf(
            "schema_version",
            "protection_profile",
            "consent_digest",
            "encrypted_consent"
        )
        private val CONSENT_FIELDS = setOf(
            "schema_version",
            "consent_id",
            "candidate_digest",
            "instance_id",
            "companion_name",
            "seed_root_hash",
            "body_id",
            "guardian_id",
            "guardian_key_epoch_id",
            "decision",
            "confirmation_mode",
            "confirmation_purpose",
            "consented_at",
            "expires_at",
            "protection_profile",
            "consent_digest"
        )
        private val STORAGE_LOCK = Any()
    }
}

private fun requireCanonicalTimestamp(value: String, errorCode: String): Instant {
    require(CANONICAL_TIMESTAMP.matches(value)) { errorCode }
    return runCatching { Instant.parse(value) }
        .getOrElse { failure -> throw IllegalArgumentException(errorCode, failure) }
}

private val CANONICAL_TIMESTAMP = Regex(
    "^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])T" +
        "([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]Z$"
)
