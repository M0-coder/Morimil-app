package com.morimil.app.data.genesis.ultra

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.AccessesPartialKey
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.google.crypto.tink.signature.Ed25519Parameters
import com.google.crypto.tink.signature.Ed25519PrivateKey
import com.google.crypto.tink.signature.SignatureConfig
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.repository.MemoryAppendGate
import org.json.JSONObject
import java.io.ByteArrayOutputStream

internal enum class GenesisUltraBodyIdentityRootState {
    ABSENT,
    READY,
    INCONSISTENT
}

/** Public, non-secret identity derived from the one pre-birth Ed25519 Body key. */
internal class GenesisUltraBodyIdentityRoot(
    val schemaVersion: String,
    val bodyId: String,
    val keyEpochId: String,
    val publicKeyRef: String,
    val protectionProfile: String,
    rawPublicKey: ByteArray
) {
    private val publicKey = rawPublicKey.copyOf()

    init {
        require(schemaVersion == ROOT_SCHEMA) { "body_identity_root_schema_invalid" }
        require(publicKey.size == ED25519_PUBLIC_KEY_BYTES) { "body_identity_root_key_size_invalid" }
        require(GenesisUltraHashProfile.sha256(publicKey) == publicKeyRef) {
            "body_identity_root_public_key_ref_mismatch"
        }
        require(bodyId == bodyIdFor(publicKeyRef)) { "body_identity_root_body_id_mismatch" }
        require(keyEpochId == keyEpochIdFor(publicKeyRef)) {
            "body_identity_root_key_epoch_id_mismatch"
        }
        require(protectionProfile == PROTECTION_PROFILE) {
            "body_identity_root_protection_profile_invalid"
        }
    }

    fun copyRawPublicKey(): ByteArray = publicKey.copyOf()

    internal companion object {
        const val ROOT_SCHEMA = "genesis.body.identity.root.v0.1"
        const val PROTECTION_PROFILE =
            "tink.ed25519.raw+android-keystore.aes256-gcm.v0.1"
        private const val ED25519_PUBLIC_KEY_BYTES = 32

        fun bodyIdFor(publicKeyRef: String): String {
            val digest = requireSha256Ref(publicKeyRef)
            return "body_$digest"
        }

        fun keyEpochIdFor(publicKeyRef: String): String {
            val digest = requireSha256Ref(publicKeyRef)
            return "epoch_$digest"
        }

        private fun requireSha256Ref(value: String): String {
            require(SHA256_REF.matches(value)) { "body_identity_root_public_key_ref_invalid" }
            return value.removePrefix("sha256:")
        }

        private val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
    }
}

/**
 * Creates the Body identity before Genesis Ultra birth and never derives it
 * from the legacy alias, legacy instance id, Android package certificate or
 * memory-event signing key.
 *
 * Android Keystore does not expose Ed25519 uniformly across the supported API
 * range, so the RAW Ed25519 keyset is encrypted at rest by an AES-256-GCM key
 * held by Android Keystore. There is no plaintext, unsigned or replacement
 * fallback. The record is independent of an Instance id; the Instance is bound
 * only when a signer is requested for candidate construction or canonical
 * memory.
 */
internal class GenesisUltraAndroidBodyIdentityRootStore(
    context: Context,
    private val database: MorimilDatabase,
    private val preferencesName: String = DEFAULT_PREFERENCES_NAME,
    private val masterKeyAlias: String = DEFAULT_MASTER_KEY_ALIAS
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE
    )

    fun readState(): GenesisUltraBodyIdentityRootState {
        return synchronized(STORAGE_LOCK) {
            val recordExists = preferences.contains(RECORD_KEY)
            val masterKeyExists = runCatching { AndroidKeystore.hasKey(masterKeyAlias) }
                .getOrElse { return@synchronized GenesisUltraBodyIdentityRootState.INCONSISTENT }
            when {
                !recordExists && !masterKeyExists -> GenesisUltraBodyIdentityRootState.ABSENT
                recordExists && masterKeyExists -> {
                    val record = preferences.getString(RECORD_KEY, null)
                    if (record != null && runCatching { loadMaterial(record) }.isSuccess) {
                        GenesisUltraBodyIdentityRootState.READY
                    } else {
                        GenesisUltraBodyIdentityRootState.INCONSISTENT
                    }
                }
                else -> GenesisUltraBodyIdentityRootState.INCONSISTENT
            }
        }
    }

    /** Creates exactly one identity root while durable Ultra birth is absent. */
    suspend fun provisionBeforeBirth(): GenesisUltraBodyIdentityRoot {
        return MemoryAppendGate.withAppendLock {
            require(
                GenesisUltraAtomicBirthStore(database).readState() ==
                    GenesisUltraPersistedBirthState.ABSENT
            ) { "body_identity_root_provision_requires_absent_birth" }

            synchronized(STORAGE_LOCK) {
                when (readState()) {
                    GenesisUltraBodyIdentityRootState.ABSENT -> createRecord().root
                    GenesisUltraBodyIdentityRootState.READY -> {
                        loadMaterial(requireNotNull(preferences.getString(RECORD_KEY, null))).root
                    }
                    GenesisUltraBodyIdentityRootState.INCONSISTENT -> {
                        error("body_identity_root_inconsistent")
                    }
                }
            }
        }
    }

    /** Loads the exact existing root and never creates or replaces key material. */
    suspend fun loadExisting(): GenesisUltraBodyIdentityRoot {
        return loadExistingMaterial().root
    }

    /**
     * Binds the already provisioned Body root to one candidate Instance without
     * changing the Body id, key epoch or key material.
     */
    suspend fun signerForInstance(instanceId: String): GenesisUltraBodyMemorySigner {
        GenesisUltraHashProfile.requireNfc(instanceId)
        require(instanceId.length in 16..128) { "body_identity_instance_id_invalid" }
        val material = loadExistingMaterial()
        val key = GenesisUltraBodyMemoryKey(
            instanceId = instanceId,
            bodyId = material.root.bodyId,
            keyEpochId = material.root.keyEpochId,
            publicKeyRef = material.root.publicKeyRef,
            rawPublicKey = material.root.copyRawPublicKey()
        )
        return GenesisUltraTinkBodyMemorySigner(key, material.signer)
    }

    private suspend fun loadExistingMaterial(): LoadedMaterial {
        return MemoryAppendGate.withAppendLock {
            require(
                GenesisUltraAtomicBirthStore(database).readState() !=
                    GenesisUltraPersistedBirthState.INCONSISTENT
            ) { "body_identity_root_load_denied_for_inconsistent_birth" }

            synchronized(STORAGE_LOCK) {
                require(readState() == GenesisUltraBodyIdentityRootState.READY) {
                    "body_identity_root_not_ready"
                }
                loadMaterial(requireNotNull(preferences.getString(RECORD_KEY, null)))
            }
        }
    }

    private fun createRecord(): LoadedMaterial {
        require(!preferences.contains(RECORD_KEY)) { "body_identity_root_record_already_exists" }
        require(!AndroidKeystore.hasKey(masterKeyAlias)) {
            "body_identity_root_master_key_already_exists_without_record"
        }

        var masterKeyCreated = false
        try {
            SignatureConfig.register()
            AndroidKeystore.generateNewAes256GcmKey(masterKeyAlias)
            masterKeyCreated = true

            val handle = KeysetHandle.generateNew(Ed25519Parameters.create())
            val material = materialFromHandle(handle)
            val ciphertext = TinkProtoKeysetFormat.serializeEncryptedKeyset(
                handle,
                AndroidKeystore.getAead(masterKeyAlias),
                associatedData(),
                RegistryConfiguration.get()
            )
            val record = JSONObject()
                .put("schema_version", RECORD_SCHEMA)
                .put("protection_profile", GenesisUltraBodyIdentityRoot.PROTECTION_PROFILE)
                .put("body_id", material.root.bodyId)
                .put("key_epoch_id", material.root.keyEpochId)
                .put("public_key_ref", material.root.publicKeyRef)
                .put("encrypted_keyset", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .toString()
            check(preferences.edit().putString(RECORD_KEY, record).commit()) {
                "body_identity_root_record_commit_failed"
            }

            return loadMaterial(requireNotNull(preferences.getString(RECORD_KEY, null)))
        } catch (failure: Exception) {
            if (!preferences.contains(RECORD_KEY) && masterKeyCreated) {
                runCatching {
                    if (AndroidKeystore.hasKey(masterKeyAlias)) {
                        AndroidKeystore.deleteKey(masterKeyAlias)
                    }
                }
            }
            throw IllegalStateException("body_identity_root_provision_failed", failure)
        }
    }

    private fun loadMaterial(encodedRecord: String): LoadedMaterial {
        return try {
            SignatureConfig.register()
            val root = JSONObject(encodedRecord)
            require(root.keys().asSequence().toSet() == RECORD_FIELDS) {
                "body_identity_root_record_fields_invalid"
            }
            require(root.getString("schema_version") == RECORD_SCHEMA) {
                "body_identity_root_record_schema_invalid"
            }
            require(
                root.getString("protection_profile") ==
                    GenesisUltraBodyIdentityRoot.PROTECTION_PROFILE
            ) { "body_identity_root_record_profile_invalid" }

            val ciphertext = Base64.decode(root.getString("encrypted_keyset"), Base64.NO_WRAP)
            val handle = TinkProtoKeysetFormat.parseEncryptedKeyset(
                ciphertext,
                AndroidKeystore.getAead(masterKeyAlias),
                associatedData(),
                RegistryConfiguration.get()
            )
            val material = materialFromHandle(handle)
            require(root.getString("body_id") == material.root.bodyId) {
                "body_identity_root_record_body_id_changed"
            }
            require(root.getString("key_epoch_id") == material.root.keyEpochId) {
                "body_identity_root_record_key_epoch_changed"
            }
            require(root.getString("public_key_ref") == material.root.publicKeyRef) {
                "body_identity_root_record_public_key_ref_changed"
            }
            material
        } catch (failure: Exception) {
            throw IllegalStateException("body_identity_root_load_failed", failure)
        }
    }

    @AccessesPartialKey
    private fun materialFromHandle(handle: KeysetHandle): LoadedMaterial {
        require(handle.size() == 1) { "body_identity_root_keyset_size_invalid" }
        val privateKey = handle.getPrimary().getKey() as? Ed25519PrivateKey
            ?: throw IllegalArgumentException("body_identity_root_key_type_invalid")
        require(privateKey.parameters == Ed25519Parameters.create()) {
            "body_identity_root_key_variant_invalid"
        }
        val rawPublicKey = privateKey.publicKey.publicKeyBytes.toByteArray()
        val publicKeyRef = GenesisUltraHashProfile.sha256(rawPublicKey)
        val root = GenesisUltraBodyIdentityRoot(
            schemaVersion = GenesisUltraBodyIdentityRoot.ROOT_SCHEMA,
            bodyId = GenesisUltraBodyIdentityRoot.bodyIdFor(publicKeyRef),
            keyEpochId = GenesisUltraBodyIdentityRoot.keyEpochIdFor(publicKeyRef),
            publicKeyRef = publicKeyRef,
            protectionProfile = GenesisUltraBodyIdentityRoot.PROTECTION_PROFILE,
            rawPublicKey = rawPublicKey
        )
        val signer = handle.getPrimitive(
            RegistryConfiguration.get(),
            PublicKeySign::class.java
        )
        return LoadedMaterial(root = root, signer = signer)
    }

    private fun associatedData(): ByteArray {
        return ByteArrayOutputStream().use { output ->
            output.write(GenesisUltraHashProfile.frame(KEYSET_AAD_DOMAIN))
            output.write(GenesisUltraHashProfile.frame(RECORD_SCHEMA))
            output.write(
                GenesisUltraHashProfile.frame(
                    GenesisUltraBodyIdentityRoot.PROTECTION_PROFILE
                )
            )
            output.toByteArray()
        }
    }

    private data class LoadedMaterial(
        val root: GenesisUltraBodyIdentityRoot,
        val signer: PublicKeySign
    )

    internal companion object {
        const val DEFAULT_PREFERENCES_NAME = "genesis_ultra_body_identity_root_v1"
        const val DEFAULT_MASTER_KEY_ALIAS =
            "com.morimil.app.genesis.ultra.body.identity.kek.v1"
        const val RECORD_KEY = "body_identity_root"

        private const val RECORD_SCHEMA = "genesis.body.identity.key.record.v0.1"
        private const val KEYSET_AAD_DOMAIN = "genesis.body.identity.keyset.aad.v0.1"
        private val RECORD_FIELDS = setOf(
            "schema_version",
            "protection_profile",
            "body_id",
            "key_epoch_id",
            "public_key_ref",
            "encrypted_keyset"
        )
        private val STORAGE_LOCK = Any()
    }
}
