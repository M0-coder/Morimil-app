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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GenesisUltraAndroidGuardianTrustAnchorStoreTest {
    private lateinit var database: MorimilDatabase
    private lateinit var preferencesName: String
    private lateinit var masterKeyAlias: String

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        val suffix = UUID.randomUUID().toString()
        preferencesName = "genesis-ultra-guardian-anchor-test-$suffix"
        masterKeyAlias = "com.morimil.app.test.genesis.ultra.guardian.$suffix"
        database = Room.inMemoryDatabaseBuilder(context, MorimilDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        preferences().edit().clear().commit()
        runCatching {
            if (AndroidKeystore.hasKey(masterKeyAlias)) {
                AndroidKeystore.deleteKey(masterKeyAlias)
            }
        }
    }

    @Test
    fun pinsOneConfirmedEpochAndReloadsTheExactAnchor() = runBlocking {
        val originalStore = store(clockMillis = { PINNED_AT_MILLIS })
        assertEquals(GenesisUltraGuardianTrustAnchorState.ABSENT, originalStore.readState())

        val original = originalStore.provisionBeforeBirth(request(RAW_PUBLIC_KEY))
        val restarted = store(clockMillis = { PINNED_AT_MILLIS + 99_999L }).loadExisting()

        assertEquals(GenesisUltraGuardianTrustAnchorState.READY, originalStore.readState())
        assertEquals(GUARDIAN_ID, restarted.guardianId)
        assertEquals(KEY_EPOCH_ID, restarted.keyEpochId)
        assertEquals(PUBLIC_KEY_REF, restarted.publicKeyRef)
        assertEquals(PINNED_AT_MILLIS, restarted.pinnedAtMillis)
        assertEquals(original.anchorDigest, restarted.anchorDigest)
        assertArrayEquals(original.copyRawPublicKey(), restarted.copyRawPublicKey())

        val registry = originalStore.loadExistingRegistry()
        val envelope = testEnvelope(restarted)
        assertTrue(registry.trusts(envelope))
        assertFalse(registry.trusts(envelope.copy(keyEpochId = "guardian_epoch_replacement_0001")))
        assertFalse(registry.trusts(envelope.copy(signerId = "guardian_other")))
    }

    @Test
    fun persistedRecordDoesNotExposeTheRawGuardianKey() = runBlocking {
        store().provisionBeforeBirth(request(RAW_PUBLIC_KEY))

        val encodedRecord = requireNotNull(preferences().getString(RECORD_KEY, null))
        val rawKeyBase64 = Base64.encodeToString(RAW_PUBLIC_KEY, Base64.NO_WRAP)
        val record = JSONObject(encodedRecord)

        assertEquals(
            setOf("schema_version", "protection_profile", "anchor_digest", "encrypted_anchor"),
            record.keys().asSequence().toSet()
        )
        assertFalse(encodedRecord.contains(rawKeyBase64))
        assertTrue(record.getString("encrypted_anchor").isNotBlank())
    }

    @Test
    fun mismatchedOutOfBandFingerprintIsRejectedBeforeAnyPinExists() {
        val wrongFingerprint = GenesisUltraHashProfile.sha256(OTHER_RAW_PUBLIC_KEY)

        val failure = runCatching {
            GenesisUltraGuardianTrustAnchorProvisioningRequest(
                guardianId = GUARDIAN_ID,
                keyEpochId = KEY_EPOCH_ID,
                confirmedPublicKeyRef = wrongFingerprint,
                confirmationPurpose = CONFIRMATION_PURPOSE,
                rawPublicKey = RAW_PUBLIC_KEY
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertFalse(preferences().contains(RECORD_KEY))
        assertFalse(AndroidKeystore.hasKey(masterKeyAlias))
    }

    @Test
    fun aDifferentGuardianEpochCannotReplaceThePinnedAnchor() = runBlocking {
        val trustStore = store()
        val original = trustStore.provisionBeforeBirth(request(RAW_PUBLIC_KEY))
        val replacementRequest = GenesisUltraGuardianTrustAnchorProvisioningRequest(
            guardianId = "guardian_replacement",
            keyEpochId = "guardian_epoch_replacement_0001",
            confirmedPublicKeyRef = GenesisUltraHashProfile.sha256(OTHER_RAW_PUBLIC_KEY),
            confirmationPurpose = CONFIRMATION_PURPOSE,
            rawPublicKey = OTHER_RAW_PUBLIC_KEY
        )

        val failure = runCatching {
            trustStore.provisionBeforeBirth(replacementRequest)
        }.exceptionOrNull()
        val reloaded = trustStore.loadExisting()

        assertNotNull(failure)
        assertEquals(original.anchorDigest, reloaded.anchorDigest)
        assertEquals(GUARDIAN_ID, reloaded.guardianId)
        assertArrayEquals(RAW_PUBLIC_KEY, reloaded.copyRawPublicKey())
    }

    @Test
    fun ciphertextTamperFailsClosedWithoutReplacingTheAnchor() = runBlocking {
        val trustStore = store()
        trustStore.provisionBeforeBirth(request(RAW_PUBLIC_KEY))
        val record = JSONObject(requireNotNull(preferences().getString(RECORD_KEY, null)))
        val encrypted = Base64.decode(record.getString("encrypted_anchor"), Base64.NO_WRAP)
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()
        val tampered = record
            .put("encrypted_anchor", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .toString()
        assertTrue(preferences().edit().putString(RECORD_KEY, tampered).commit())

        val loadFailure = runCatching { trustStore.loadExisting() }.exceptionOrNull()
        val reprovisionFailure = runCatching {
            trustStore.provisionBeforeBirth(request(RAW_PUBLIC_KEY))
        }.exceptionOrNull()

        assertEquals(GenesisUltraGuardianTrustAnchorState.INCONSISTENT, trustStore.readState())
        assertNotNull(loadFailure)
        assertNotNull(reprovisionFailure)
        assertEquals(tampered, preferences().getString(RECORD_KEY, null))
    }

    @Test
    fun lostAndroidKeystoreKeyDoesNotCreateAReplacementAnchor() = runBlocking {
        val trustStore = store()
        trustStore.provisionBeforeBirth(request(RAW_PUBLIC_KEY))
        val durableRecord = preferences().getString(RECORD_KEY, null)
        AndroidKeystore.deleteKey(masterKeyAlias)

        val loadFailure = runCatching { trustStore.loadExisting() }.exceptionOrNull()
        val reprovisionFailure = runCatching {
            trustStore.provisionBeforeBirth(request(RAW_PUBLIC_KEY))
        }.exceptionOrNull()

        assertEquals(GenesisUltraGuardianTrustAnchorState.INCONSISTENT, trustStore.readState())
        assertNotNull(loadFailure)
        assertNotNull(reprovisionFailure)
        assertEquals(durableRecord, preferences().getString(RECORD_KEY, null))
        assertFalse(AndroidKeystore.hasKey(masterKeyAlias))
    }

    @Test
    fun loadExistingNeverCreatesTrustOnFirstUse() = runBlocking {
        val trustStore = store()

        val failure = runCatching { trustStore.loadExisting() }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(GenesisUltraGuardianTrustAnchorState.ABSENT, trustStore.readState())
        assertFalse(preferences().contains(RECORD_KEY))
        assertFalse(AndroidKeystore.hasKey(masterKeyAlias))
    }

    private fun store(
        clockMillis: () -> Long = { PINNED_AT_MILLIS }
    ): GenesisUltraAndroidGuardianTrustAnchorStore {
        return GenesisUltraAndroidGuardianTrustAnchorStore(
            context = context,
            database = database,
            preferencesName = preferencesName,
            masterKeyAlias = masterKeyAlias,
            clockMillis = clockMillis
        )
    }

    private fun request(rawPublicKey: ByteArray): GenesisUltraGuardianTrustAnchorProvisioningRequest {
        return GenesisUltraGuardianTrustAnchorProvisioningRequest(
            guardianId = GUARDIAN_ID,
            keyEpochId = KEY_EPOCH_ID,
            confirmedPublicKeyRef = GenesisUltraHashProfile.sha256(rawPublicKey),
            confirmationPurpose = CONFIRMATION_PURPOSE,
            rawPublicKey = rawPublicKey
        )
    }

    private fun testEnvelope(anchor: GenesisUltraGuardianTrustAnchor): GenesisUltraSignatureEnvelope {
        return GenesisUltraSignatureEnvelope(
            schemaVersion = "genesis.signature.envelope.v0.1",
            signatureProfile = "genesis.signature.ed25519.v0.1",
            signerType = "guardian",
            signerId = anchor.guardianId,
            keyEpochId = anchor.keyEpochId,
            signedDomain = GenesisUltraHashProfile.SEED_ROOT_DOMAIN,
            signedDigest = "sha256:" + "a".repeat(64),
            signatureValue = "0".repeat(128),
            createdAt = "2026-07-24T00:00:00Z",
            publicKeyRef = anchor.publicKeyRef
        )
    }

    private fun preferences() = context.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE
    )

    private companion object {
        const val RECORD_KEY = GenesisUltraAndroidGuardianTrustAnchorStore.RECORD_KEY
        const val CONFIRMATION_PURPOSE =
            GenesisUltraGuardianTrustAnchorProvisioningRequest.CONFIRMATION_PURPOSE
        const val GUARDIAN_ID = "guardian_01HMORIMILCUSTODIAN0001"
        const val KEY_EPOCH_ID = "guardian_epoch_01HMORIMIL000001"
        const val PINNED_AT_MILLIS = 1_753_315_200_000L
        val RAW_PUBLIC_KEY = ByteArray(32) { index -> (index + 1).toByte() }
        val OTHER_RAW_PUBLIC_KEY = ByteArray(32) { index -> (index + 65).toByte() }
        val PUBLIC_KEY_REF = GenesisUltraHashProfile.sha256(RAW_PUBLIC_KEY)
    }
}
