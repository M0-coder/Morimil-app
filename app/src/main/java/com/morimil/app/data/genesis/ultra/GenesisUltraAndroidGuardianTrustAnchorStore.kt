package com.morimil.app.data.genesis.ultra

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.repository.MemoryAppendGate
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

internal enum class GenesisUltraGuardianTrustAnchorState {
    ABSENT,
    READY,
    INCONSISTENT
}

/**
 * Explicit out-of-band confirmation required before one Guardian public key can
 * become a local trust anchor. A key discovered inside the release being
 * verified is not sufficient input for this boundary.
 */
internal class GenesisUltraGuardianTrustAnchorProvisioningRequest(
    val guardianId: String,
    val keyEpochId: String,
    val confirmedPublicKeyRef: String,
    val confirmationPurpose: String,
    rawPublicKey: ByteArray
) {
    private val publicKey = rawPublicKey.copyOf()

    init {
        GenesisUltraHashProfile.requireNfc(guardianId)
        GenesisUltraHashProfile.requireNfc(keyEpochId)
        GenesisUltraHashProfile.requireNfc(confirmedPublicKeyRef)
        GenesisUltraHashProfile.requireNfc(confirmationPurpose)
        require(guardianId.length in 1..128) { "guardian_trust_anchor_guardian_id_invalid" }
        require(keyEpochId.length in 16..128) { "guardian_trust_anchor_key_epoch_id_invalid" }
        require(publicKey.size == ED25519_PUBLIC_KEY_BYTES) {
            "guardian_trust_anchor_ed25519_key_size_invalid"
        }
        require(SHA256_REF.matches(confirmedPublicKeyRef)) {
            "guardian_trust_anchor_confirmed_fingerprint_invalid"
        }
        require(GenesisUltraHashProfile.sha256(publicKey) == confirmedPublicKeyRef) {
            "guardian_trust_anchor_confirmation_mismatch"
        }
        require(confirmationPurpose == CONFIRMATION_PURPOSE) {
            "guardian_trust_anchor_confirmation_purpose_invalid"
        }
    }

    fun copyRawPublicKey(): ByteArray = publicKey.copyOf()

    internal companion object {
        const val CONFIRMATION_PURPOSE = "birth_witness_and_recovery_custody"
        private const val ED25519_PUBLIC_KEY_BYTES = 32
        private val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
    }
}

/** Public, non-secret Guardian trust anchor protected against local record tampering. */
internal class GenesisUltraGuardianTrustAnchor(
    val schemaVersion: String,
    val guardianId: String,
    val keyEpochId: String,
    val publicKeyRef: String,
    val status: String,
    val confirmationMode: String,
    val confirmationPurpose: String,
    val protectionProfile: String,
    val pinnedAtMillis: Long,
    rawPublicKey: ByteArray
) {
    private val publicKey = rawPublicKey.copyOf()

    val anchorDigest: String = GenesisUltraHashProfile.hashFields(
        TRUST_ANCHOR_DOMAIN,
        listOf(
            schemaVersion,
            guardianId,
            keyEpochId,
            publicKeyRef,
            status,
            confirmationMode,
            confirmationPurpose,
            protectionProfile,
            pinnedAtMillis.toString()
        )
    )

    init {
        require(schemaVersion == ANCHOR_SCHEMA) { "guardian_trust_anchor_schema_invalid" }
        GenesisUltraHashProfile.requireNfc(guardianId)
        GenesisUltraHashProfile.requireNfc(keyEpochId)
        require(guardianId.length in 1..128) { "guardian_trust_anchor_guardian_id_invalid" }
        require(keyEpochId.length in 16..128) { "guardian_trust_anchor_key_epoch_id_invalid" }
        require(publicKey.size == ED25519_PUBLIC_KEY_BYTES) {
            "guardian_trust_anchor_ed25519_key_size_invalid"
        }
        require(GenesisUltraHashProfile.sha256(publicKey) == publicKeyRef) {
            "guardian_trust_anchor_public_key_ref_mismatch"
        }
        require(status == ACTIVE_STATUS) { "guardian_trust_anchor_status_invalid" }
        require(confirmationMode == CONFIRMATION_MODE) {
            "guardian_trust_anchor_confirmation_mode_invalid"
        }
        require(confirmationPurpose == GenesisUltraGuardianTrustAnchorProvisioningRequest.CONFIRMATION_PURPOSE) {
            "guardian_trust_anchor_confirmation_purpose_invalid"
        }
        require(protectionProfile == PROTECTION_PROFILE) {
            "guardian_trust_anchor_protection_profile_invalid"
        }
        require(pinnedAtMillis >= 0L) { "guardian_trust_anchor_pinned_at_invalid" }
    }

    fun copyRawPublicKey(): ByteArray = publicKey.copyOf()

    fun toTrustedEpoch(): GenesisUltraTrustedGuardianKeyEpoch {
        return GenesisUltraTrustedGuardianKeyEpoch(
            guardianId = guardianId,
            keyEpochId = keyEpochId,
            publicKeyRef = publicKeyRef,
            status = status,
            rawPublicKey = copyRawPublicKey()
        )
    }

    internal fun sameIdentity(request: GenesisUltraGuardianTrustAnchorProvisioningRequest): Boolean {
        return guardianId == request.guardianId &&
            keyEpochId == request.keyEpochId &&
            publicKeyRef == request.confirmedPublicKeyRef &&
            publicKey.contentEquals(request.copyRawPublicKey())
    }

    internal companion object {
        const val ANCHOR_SCHEMA = "genesis.guardian.trust.anchor.v0.1"
        const val ACTIVE_STATUS = "active"
        const val CONFIRMATION_MODE = "out_of_band_user_confirmed"
        const val PROTECTION_PROFILE = "android-keystore.aes256-gcm.v0.1"
        const val TRUST_ANCHOR_DOMAIN = "genesis.guardian.trust.anchor.digest.v0.1"
        private const val ED25519_PUBLIC_KEY_BYTES = 32
    }
}

/**
 * Pins one active Guardian public-key epoch before Genesis Ultra birth.
 *
 * The anchor is public, but accepting a substituted public key would transfer
 * Guardian authority. Its record is therefore encrypted and authenticated by a
 * dedicated AES-256-GCM key in Android Keystore. There is no trust-on-first-use
 * from a Seed bundle, no plaintext fallback and no replacement API.
 */
internal class GenesisUltraAndroidGuardianTrustAnchorStore(
    context: Context,
    private val database: MorimilDatabase,
    private val preferencesName: String = DEFAULT_PREFERENCES_NAME,
    private val masterKeyAlias: String = DEFAULT_MASTER_KEY_ALIAS,
    private val clockMillis: () -> Long = System::currentTimeMillis
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE
    )

    fun readState(): GenesisUltraGuardianTrustAnchorState {
        return synchronized(STORAGE_LOCK) {
            val recordExists = preferences.contains(RECORD_KEY)
            val masterKeyExists = runCatching { AndroidKeystore.hasKey(masterKeyAlias) }
                .getOrElse { return@synchronized GenesisUltraGuardianTrustAnchorState.INCONSISTENT }
            when {
                !recordExists && !masterKeyExists -> GenesisUltraGuardianTrustAnchorState.ABSENT
                recordExists && masterKeyExists -> {
                    val record = preferences.getString(RECORD_KEY, null)
                    if (record != null && runCatching { loadAnchor(record) }.isSuccess) {
                        GenesisUltraGuardianTrustAnchorState.READY
                    } else {
                        GenesisUltraGuardianTrustAnchorState.INCONSISTENT
                    }
                }
                else -> GenesisUltraGuardianTrustAnchorState.INCONSISTENT
            }
        }
    }

    /** Pins one explicitly confirmed Guardian epoch while durable Ultra birth is absent. */
    suspend fun provisionBeforeBirth(
        request: GenesisUltraGuardianTrustAnchorProvisioningRequest
    ): GenesisUltraGuardianTrustAnchor {
        // Validate and defensively copy all caller-controlled bytes before any key is generated.
        val validatedRequest = GenesisUltraGuardianTrustAnchorProvisioningRequest(
            guardianId = request.guardianId,
            keyEpochId = request.keyEpochId,
            confirmedPublicKeyRef = request.confirmedPublicKeyRef,
            confirmationPurpose = request.confirmationPurpose,
            rawPublicKey = request.copyRawPublicKey()
        )
        return MemoryAppendGate.withAppendLock {
            require(
                GenesisUltraAtomicBirthStore(database).readState() ==
                    GenesisUltraPersistedBirthState.ABSENT
            ) { "guardian_trust_anchor_provision_requires_absent_birth" }

            synchronized(STORAGE_LOCK) {
                when (readState()) {
                    GenesisUltraGuardianTrustAnchorState.ABSENT -> createRecord(validatedRequest)
                    GenesisUltraGuardianTrustAnchorState.READY -> {
                        val existing = loadAnchor(requireNotNull(preferences.getString(RECORD_KEY, null)))
                        require(existing.sameIdentity(validatedRequest)) {
                            "guardian_trust_anchor_already_pinned"
                        }
                        existing
                    }
                    GenesisUltraGuardianTrustAnchorState.INCONSISTENT -> {
                        error("guardian_trust_anchor_inconsistent")
                    }
                }
            }
        }
    }

    /** Loads the exact pinned anchor and never creates or replaces trust material. */
    suspend fun loadExisting(): GenesisUltraGuardianTrustAnchor {
        return MemoryAppendGate.withAppendLock {
            require(
                GenesisUltraAtomicBirthStore(database).readState() !=
                    GenesisUltraPersistedBirthState.INCONSISTENT
            ) { "guardian_trust_anchor_load_denied_for_inconsistent_birth" }
            synchronized(STORAGE_LOCK) {
                require(readState() == GenesisUltraGuardianTrustAnchorState.READY) {
                    "guardian_trust_anchor_not_ready"
                }
                loadAnchor(requireNotNull(preferences.getString(RECORD_KEY, null)))
            }
        }
    }

    suspend fun loadExistingRegistry(): GenesisUltraTrustedGuardianKeyEpochRegistry {
        return GenesisUltraTrustedGuardianKeyEpochRegistry(
            listOf(loadExisting().toTrustedEpoch())
        )
    }

    /** Verifies a Seed release only through the locally pinned Guardian epoch. */
    suspend fun verifyRelease(bundle: GenesisUltraReleaseBundle): GenesisUltraVerifiedRelease {
        val registry = loadExistingRegistry()
        return GenesisUltraReleaseVerifier(registry.signatureVerifier()).verify(bundle)
    }

    private fun createRecord(
        request: GenesisUltraGuardianTrustAnchorProvisioningRequest
    ): GenesisUltraGuardianTrustAnchor {
        require(!preferences.contains(RECORD_KEY)) { "guardian_trust_anchor_record_already_exists" }
        require(!AndroidKeystore.hasKey(masterKeyAlias)) {
            "guardian_trust_anchor_master_key_already_exists_without_record"
        }

        var masterKeyCreated = false
        try {
            AndroidKeystore.generateNewAes256GcmKey(masterKeyAlias)
            masterKeyCreated = true
            val anchor = GenesisUltraGuardianTrustAnchor(
                schemaVersion = GenesisUltraGuardianTrustAnchor.ANCHOR_SCHEMA,
                guardianId = request.guardianId,
                keyEpochId = request.keyEpochId,
                publicKeyRef = request.confirmedPublicKeyRef,
                status = GenesisUltraGuardianTrustAnchor.ACTIVE_STATUS,
                confirmationMode = GenesisUltraGuardianTrustAnchor.CONFIRMATION_MODE,
                confirmationPurpose = request.confirmationPurpose,
                protectionProfile = GenesisUltraGuardianTrustAnchor.PROTECTION_PROFILE,
                pinnedAtMillis = clockMillis(),
                rawPublicKey = request.copyRawPublicKey()
            )
            val ciphertext = AndroidKeystore.getAead(masterKeyAlias).encrypt(
                encodeAnchor(anchor).toByteArray(StandardCharsets.UTF_8),
                associatedData()
            )
            val record = JSONObject()
                .put("schema_version", RECORD_SCHEMA)
                .put("protection_profile", GenesisUltraGuardianTrustAnchor.PROTECTION_PROFILE)
                .put("anchor_digest", anchor.anchorDigest)
                .put("encrypted_anchor", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .toString()
            check(preferences.edit().putString(RECORD_KEY, record).commit()) {
                "guardian_trust_anchor_record_commit_failed"
            }
            return loadAnchor(requireNotNull(preferences.getString(RECORD_KEY, null)))
        } catch (failure: Exception) {
            if (!preferences.contains(RECORD_KEY) && masterKeyCreated) {
                runCatching {
                    if (AndroidKeystore.hasKey(masterKeyAlias)) {
                        AndroidKeystore.deleteKey(masterKeyAlias)
                    }
                }
            }
            throw IllegalStateException("guardian_trust_anchor_provision_failed", failure)
        }
    }

    private fun loadAnchor(encodedRecord: String): GenesisUltraGuardianTrustAnchor {
        return try {
            val record = JSONObject(encodedRecord)
            require(record.keys().asSequence().toSet() == RECORD_FIELDS) {
                "guardian_trust_anchor_record_fields_invalid"
            }
            require(record.getString("schema_version") == RECORD_SCHEMA) {
                "guardian_trust_anchor_record_schema_invalid"
            }
            require(
                record.getString("protection_profile") ==
                    GenesisUltraGuardianTrustAnchor.PROTECTION_PROFILE
            ) { "guardian_trust_anchor_record_profile_invalid" }
            val plaintext = AndroidKeystore.getAead(masterKeyAlias).decrypt(
                Base64.decode(record.getString("encrypted_anchor"), Base64.NO_WRAP),
                associatedData()
            )
            val anchor = decodeAnchor(String(plaintext, StandardCharsets.UTF_8))
            require(record.getString("anchor_digest") == anchor.anchorDigest) {
                "guardian_trust_anchor_record_digest_changed"
            }
            anchor
        } catch (failure: Exception) {
            throw IllegalStateException("guardian_trust_anchor_load_failed", failure)
        }
    }

    private fun encodeAnchor(anchor: GenesisUltraGuardianTrustAnchor): String {
        return JSONObject()
            .put("schema_version", anchor.schemaVersion)
            .put("guardian_id", anchor.guardianId)
            .put("key_epoch_id", anchor.keyEpochId)
            .put("public_key_ref", anchor.publicKeyRef)
            .put("status", anchor.status)
            .put("confirmation_mode", anchor.confirmationMode)
            .put("confirmation_purpose", anchor.confirmationPurpose)
            .put("protection_profile", anchor.protectionProfile)
            .put("pinned_at_millis", anchor.pinnedAtMillis)
            .put(
                "raw_public_key",
                Base64.encodeToString(anchor.copyRawPublicKey(), Base64.NO_WRAP)
            )
            .toString()
    }

    private fun decodeAnchor(encoded: String): GenesisUltraGuardianTrustAnchor {
        val root = JSONObject(encoded)
        require(root.keys().asSequence().toSet() == ANCHOR_FIELDS) {
            "guardian_trust_anchor_fields_invalid"
        }
        return GenesisUltraGuardianTrustAnchor(
            schemaVersion = root.getString("schema_version"),
            guardianId = root.getString("guardian_id"),
            keyEpochId = root.getString("key_epoch_id"),
            publicKeyRef = root.getString("public_key_ref"),
            status = root.getString("status"),
            confirmationMode = root.getString("confirmation_mode"),
            confirmationPurpose = root.getString("confirmation_purpose"),
            protectionProfile = root.getString("protection_profile"),
            pinnedAtMillis = root.getLong("pinned_at_millis"),
            rawPublicKey = Base64.decode(root.getString("raw_public_key"), Base64.NO_WRAP)
        )
    }

    private fun associatedData(): ByteArray {
        return ByteArrayOutputStream().use { output ->
            output.write(GenesisUltraHashProfile.frame(RECORD_AAD_DOMAIN))
            output.write(GenesisUltraHashProfile.frame(RECORD_SCHEMA))
            output.write(
                GenesisUltraHashProfile.frame(
                    GenesisUltraGuardianTrustAnchor.PROTECTION_PROFILE
                )
            )
            output.toByteArray()
        }
    }

    internal companion object {
        const val DEFAULT_PREFERENCES_NAME = "genesis_ultra_guardian_trust_anchor_v1"
        const val DEFAULT_MASTER_KEY_ALIAS =
            "com.morimil.app.genesis.ultra.guardian.trust.anchor.kek.v1"
        const val RECORD_KEY = "guardian_trust_anchor"

        private const val RECORD_SCHEMA = "genesis.guardian.trust.anchor.record.v0.1"
        private const val RECORD_AAD_DOMAIN = "genesis.guardian.trust.anchor.record.aad.v0.1"
        private val RECORD_FIELDS = setOf(
            "schema_version",
            "protection_profile",
            "anchor_digest",
            "encrypted_anchor"
        )
        private val ANCHOR_FIELDS = setOf(
            "schema_version",
            "guardian_id",
            "key_epoch_id",
            "public_key_ref",
            "status",
            "confirmation_mode",
            "confirmation_purpose",
            "protection_profile",
            "pinned_at_millis",
            "raw_public_key"
        )
        private val STORAGE_LOCK = Any()
    }
}
