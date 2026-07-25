package com.morimil.app.data.genesis.ultra

import android.content.Context
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.repository.MemoryAppendGate

/**
 * Revokes a consent record whose exact candidate was intentionally lost with the
 * Android process. Revocation removes authority; it never reconstructs or
 * substitutes the missing candidate.
 */
internal class GenesisUltraHostBirthConsentRecoveryCoordinator(
    context: Context,
    private val database: MorimilDatabase,
    private val consentStore: GenesisUltraAndroidHostBirthConsentStore,
    preferencesName: String = GenesisUltraAndroidHostBirthConsentStore.DEFAULT_PREFERENCES_NAME,
    private val masterKeyAlias: String =
        GenesisUltraAndroidHostBirthConsentStore.DEFAULT_MASTER_KEY_ALIAS,
    private val recordKey: String = GenesisUltraAndroidHostBirthConsentStore.RECORD_KEY
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE
    )

    fun inspect(): GenesisUltraHostBirthConsentState = consentStore.readState()

    suspend fun revokeExistingBeforeBirth(): Boolean {
        return MemoryAppendGate.withAppendLock {
            require(
                GenesisUltraAtomicBirthStore(database).readState() ==
                    GenesisUltraPersistedBirthState.ABSENT
            ) { "host_birth_consent_recovery_requires_absent_birth" }

            synchronized(RECOVERY_LOCK) {
                when (val state = consentStore.readState()) {
                    GenesisUltraHostBirthConsentState.ABSENT -> false
                    GenesisUltraHostBirthConsentState.READY,
                    GenesisUltraHostBirthConsentState.EXPIRED -> {
                        require(preferences.contains(recordKey)) {
                            "host_birth_consent_recovery_record_missing"
                        }
                        check(preferences.edit().remove(recordKey).commit()) {
                            "host_birth_consent_recovery_record_revoke_failed"
                        }
                        if (AndroidKeystore.hasKey(masterKeyAlias)) {
                            AndroidKeystore.deleteKey(masterKeyAlias)
                        }
                        check(consentStore.readState() == GenesisUltraHostBirthConsentState.ABSENT) {
                            "host_birth_consent_recovery_incomplete"
                        }
                        true
                    }
                    GenesisUltraHostBirthConsentState.INCONSISTENT -> {
                        error("host_birth_consent_recovery_denied_for_inconsistent_state:$state")
                    }
                }
            }
        }
    }

    private companion object {
        val RECOVERY_LOCK = Any()
    }
}
