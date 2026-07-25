package com.morimil.app.data.genesis.ultra

import android.content.Context
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.repository.MemoryAppendGate

internal enum class GenesisUltraCommittedConsentRetirementResult {
    NOT_APPLICABLE,
    ALREADY_ABSENT,
    RETIRED
}

/**
 * Removes the short-lived pre-birth consent residue only after a fully audited,
 * consent-bound Genesis Ultra birth is durably committed.
 *
 * Room is the source of truth. SharedPreferences and the dedicated Keystore key
 * are cleanup targets, never authority. The operation is idempotent so a process
 * death between deleting the record and deleting the key can be repaired safely.
 */
internal class GenesisUltraCommittedConsentRetirementCoordinator private constructor(
    context: Context,
    private val readAuthorizedBirthState: suspend () -> GenesisUltraPersistedBirthState,
    private val loadCommittedAuthorization: suspend () -> GenesisUltraDurableBirthAuthorization,
    preferencesName: String,
    private val masterKeyAlias: String,
    private val recordKey: String
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE
    )

    suspend fun retireIfCommitted(): GenesisUltraCommittedConsentRetirementResult {
        return MemoryAppendGate.withAppendLock {
            when (readAuthorizedBirthState()) {
                GenesisUltraPersistedBirthState.ABSENT -> {
                    GenesisUltraCommittedConsentRetirementResult.NOT_APPLICABLE
                }

                GenesisUltraPersistedBirthState.INCONSISTENT -> {
                    error("committed_consent_retirement_birth_inconsistent")
                }

                GenesisUltraPersistedBirthState.COMMITTED -> {
                    val authorization = loadCommittedAuthorization()
                    synchronized(RETIREMENT_LOCK) {
                        retireResidue(authorization.consentDigest)
                    }
                }
            }
        }
    }

    private fun retireResidue(
        expectedConsentDigest: String
    ): GenesisUltraCommittedConsentRetirementResult {
        require(SHA256_REF.matches(expectedConsentDigest)) {
            "committed_consent_retirement_digest_invalid"
        }
        val recordExists = preferences.contains(recordKey)
        val keyExists = AndroidKeystore.hasKey(masterKeyAlias)
        if (!recordExists && !keyExists) {
            return GenesisUltraCommittedConsentRetirementResult.ALREADY_ABSENT
        }

        if (recordExists) {
            val rawRecord = requireNotNull(preferences.getString(recordKey, null)) {
                "committed_consent_retirement_record_missing"
            }
            val record = GenesisUltraStrictJson.parseObject(rawRecord)
            require(record.keys().asSequence().toSet() == RECORD_FIELDS) {
                "committed_consent_retirement_record_fields_invalid"
            }
            require(record.getString("schema_version") == RECORD_SCHEMA) {
                "committed_consent_retirement_record_schema_invalid"
            }
            require(
                record.getString("protection_profile") ==
                    GenesisUltraVerifiedHostBirthConsent.PROTECTION_PROFILE
            ) { "committed_consent_retirement_record_profile_invalid" }
            require(record.getString("consent_digest") == expectedConsentDigest) {
                "committed_consent_retirement_digest_mismatch"
            }
            require(record.getString("encrypted_consent").isNotBlank()) {
                "committed_consent_retirement_ciphertext_missing"
            }
            check(preferences.edit().remove(recordKey).commit()) {
                "committed_consent_retirement_record_delete_failed"
            }
        }

        if (AndroidKeystore.hasKey(masterKeyAlias)) {
            AndroidKeystore.deleteKey(masterKeyAlias)
        }
        check(!preferences.contains(recordKey)) {
            "committed_consent_retirement_record_still_present"
        }
        check(!AndroidKeystore.hasKey(masterKeyAlias)) {
            "committed_consent_retirement_key_still_present"
        }
        return GenesisUltraCommittedConsentRetirementResult.RETIRED
    }

    internal companion object {
        private const val RECORD_SCHEMA = "genesis.host.birth.consent.record.v0.1"
        private val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
        private val RECORD_FIELDS = setOf(
            "schema_version",
            "protection_profile",
            "consent_digest",
            "encrypted_consent"
        )
        private val RETIREMENT_LOCK = Any()

        fun production(
            context: Context,
            database: MorimilDatabase
        ): GenesisUltraCommittedConsentRetirementCoordinator {
            val audit = GenesisUltraAuthorizedBirthStateAudit(database)
            return GenesisUltraCommittedConsentRetirementCoordinator(
                context = context,
                readAuthorizedBirthState = audit::readState,
                loadCommittedAuthorization = audit::loadCommittedAuthorization,
                preferencesName = GenesisUltraAndroidHostBirthConsentStore.DEFAULT_PREFERENCES_NAME,
                masterKeyAlias = GenesisUltraAndroidHostBirthConsentStore.DEFAULT_MASTER_KEY_ALIAS,
                recordKey = GenesisUltraAndroidHostBirthConsentStore.RECORD_KEY
            )
        }

        fun forTest(
            context: Context,
            readAuthorizedBirthState: suspend () -> GenesisUltraPersistedBirthState,
            loadCommittedAuthorization: suspend () -> GenesisUltraDurableBirthAuthorization,
            preferencesName: String,
            masterKeyAlias: String,
            recordKey: String
        ): GenesisUltraCommittedConsentRetirementCoordinator {
            return GenesisUltraCommittedConsentRetirementCoordinator(
                context = context,
                readAuthorizedBirthState = readAuthorizedBirthState,
                loadCommittedAuthorization = loadCommittedAuthorization,
                preferencesName = preferencesName,
                masterKeyAlias = masterKeyAlias,
                recordKey = recordKey
            )
        }
    }
}
