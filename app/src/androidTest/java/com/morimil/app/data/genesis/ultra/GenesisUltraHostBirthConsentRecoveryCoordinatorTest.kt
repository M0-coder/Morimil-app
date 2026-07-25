package com.morimil.app.data.genesis.ultra

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.morimil.app.data.local.MorimilDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GenesisUltraHostBirthConsentRecoveryCoordinatorTest {
    private lateinit var database: MorimilDatabase
    private lateinit var preferencesName: String
    private lateinit var masterKeyAlias: String
    private lateinit var consentStore: GenesisUltraAndroidHostBirthConsentStore
    private lateinit var recoveryCoordinator: GenesisUltraHostBirthConsentRecoveryCoordinator

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        val suffix = UUID.randomUUID().toString()
        preferencesName = "genesis-ultra-consent-recovery-test-$suffix"
        masterKeyAlias = "com.morimil.app.test.genesis.ultra.consent.recovery.$suffix"
        database = Room.inMemoryDatabaseBuilder(context, MorimilDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        consentStore = GenesisUltraAndroidHostBirthConsentStore(
            context = context,
            database = database,
            preferencesName = preferencesName,
            masterKeyAlias = masterKeyAlias,
            clock = { Instant.parse(CONSENTED_AT) }
        )
        recoveryCoordinator = GenesisUltraHostBirthConsentRecoveryCoordinator(
            context = context,
            database = database,
            consentStore = consentStore,
            preferencesName = preferencesName,
            masterKeyAlias = masterKeyAlias
        )
    }

    @After
    fun tearDown() {
        database.close()
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit().clear().commit()
        if (runCatching { AndroidKeystore.hasKey(masterKeyAlias) }.getOrDefault(false)) {
            AndroidKeystore.deleteKey(masterKeyAlias)
        }
    }

    @Test
    fun revokesReadyConsentAfterExactCandidateSessionWasLost() = runBlocking {
        installValidConsentRecord(validConsent())
        assertEquals(GenesisUltraHostBirthConsentState.READY, consentStore.readState())
        assertTrue(AndroidKeystore.hasKey(masterKeyAlias))

        assertTrue(recoveryCoordinator.revokeExistingBeforeBirth())

        assertEquals(GenesisUltraHostBirthConsentState.ABSENT, consentStore.readState())
        assertFalse(
            context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .contains(GenesisUltraAndroidHostBirthConsentStore.RECORD_KEY)
        )
        assertFalse(AndroidKeystore.hasKey(masterKeyAlias))
    }

    @Test
    fun refusesToEraseAnInconsistentConsentStateAutomatically() {
        AndroidKeystore.generateNewAes256GcmKey(masterKeyAlias)
        assertEquals(GenesisUltraHostBirthConsentState.INCONSISTENT, consentStore.readState())

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { recoveryCoordinator.revokeExistingBeforeBirth() }
        }

        assertTrue(error.message.orEmpty().contains("denied_for_inconsistent_state"))
        assertTrue(AndroidKeystore.hasKey(masterKeyAlias))
    }

    private fun validConsent(): GenesisUltraVerifiedHostBirthConsent {
        val schemaVersion = GenesisUltraVerifiedHostBirthConsent.CONSENT_SCHEMA
        val consentId = "consent_" + "1".repeat(64)
        val candidateDigest = "sha256:" + "2".repeat(64)
        val instanceId = "inst_" + "3".repeat(64)
        val companionName = "Morimil"
        val seedRootHash = "sha256:" + "4".repeat(64)
        val bodyId = "body_" + "5".repeat(64)
        val guardianId = "guardian_recovery_test"
        val guardianKeyEpochId = "guardian_epoch_recovery_0001"
        val decision = GenesisUltraHostBirthConsentRequest.APPROVE_DECISION
        val confirmationMode = GenesisUltraHostBirthConsentRequest.INTERACTIVE_CONFIRMATION_MODE
        val confirmationPurpose = GenesisUltraHostBirthConsentRequest.BIRTH_CONFIRMATION_PURPOSE
        val protectionProfile = GenesisUltraVerifiedHostBirthConsent.PROTECTION_PROFILE
        val digest = GenesisUltraVerifiedHostBirthConsent.digestForFields(
            schemaVersion = schemaVersion,
            consentId = consentId,
            candidateDigest = candidateDigest,
            instanceId = instanceId,
            companionName = companionName,
            seedRootHash = seedRootHash,
            bodyId = bodyId,
            guardianId = guardianId,
            guardianKeyEpochId = guardianKeyEpochId,
            decision = decision,
            confirmationMode = confirmationMode,
            confirmationPurpose = confirmationPurpose,
            consentedAt = CONSENTED_AT,
            expiresAt = EXPIRES_AT,
            protectionProfile = protectionProfile
        )
        return GenesisUltraVerifiedHostBirthConsent(
            schemaVersion = schemaVersion,
            consentId = consentId,
            candidateDigest = candidateDigest,
            instanceId = instanceId,
            companionName = companionName,
            seedRootHash = seedRootHash,
            bodyId = bodyId,
            guardianId = guardianId,
            guardianKeyEpochId = guardianKeyEpochId,
            decision = decision,
            confirmationMode = confirmationMode,
            confirmationPurpose = confirmationPurpose,
            consentedAt = CONSENTED_AT,
            expiresAt = EXPIRES_AT,
            protectionProfile = protectionProfile,
            consentDigest = digest
        )
    }

    private fun installValidConsentRecord(consent: GenesisUltraVerifiedHostBirthConsent) {
        AndroidKeystore.generateNewAes256GcmKey(masterKeyAlias)
        val ciphertext = AndroidKeystore.getAead(masterKeyAlias).encrypt(
            encodeConsent(consent).toByteArray(StandardCharsets.UTF_8),
            associatedData()
        )
        val outerRecord = JSONObject()
            .put("schema_version", RECORD_SCHEMA)
            .put("protection_profile", GenesisUltraVerifiedHostBirthConsent.PROTECTION_PROFILE)
            .put("consent_digest", consent.consentDigest)
            .put("encrypted_consent", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .toString()
        check(
            context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .edit()
                .putString(GenesisUltraAndroidHostBirthConsentStore.RECORD_KEY, outerRecord)
                .commit()
        )
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

    private companion object {
        const val CONSENTED_AT = "2026-07-25T00:00:00Z"
        const val EXPIRES_AT = "2026-07-25T00:02:00Z"
        const val RECORD_SCHEMA = "genesis.host.birth.consent.record.v0.1"
        const val RECORD_AAD_DOMAIN = "genesis.host.birth.consent.record.aad.v0.1"
    }
}
