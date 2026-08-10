package com.morimil.app.data.genesis.ultra

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.google.crypto.tink.subtle.Ed25519Verify
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
import java.nio.charset.StandardCharsets
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GenesisUltraAndroidBodyIdentityRootStoreTest {
    private lateinit var database: MorimilDatabase
    private lateinit var preferencesName: String
    private lateinit var masterKeyAlias: String

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        val suffix = UUID.randomUUID().toString()
        preferencesName = "genesis-ultra-body-root-test-$suffix"
        masterKeyAlias = "com.morimil.app.test.genesis.ultra.body.root.$suffix"
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
    fun provisionsStableRootBeforeAnyLegacyOrUltraIdentityExists() = runBlocking {
        val legacyArchiveDao = database.legacyArchiveReadDao()
        assertEquals(0, legacyArchiveDao.countLocalIdentity())
        assertEquals(0, legacyArchiveDao.countGenesisCore())
        assertEquals(GenesisUltraBodyIdentityRootState.ABSENT, keyStore().readState())

        val original = keyStore().provisionBeforeBirth()
        val restarted = keyStore().loadExisting()

        assertEquals(GenesisUltraBodyIdentityRootState.READY, keyStore().readState())
        assertEquals(original.schemaVersion, restarted.schemaVersion)
        assertEquals(original.bodyId, restarted.bodyId)
        assertEquals(original.keyEpochId, restarted.keyEpochId)
        assertEquals(original.publicKeyRef, restarted.publicKeyRef)
        assertEquals(original.protectionProfile, restarted.protectionProfile)
        assertArrayEquals(original.copyRawPublicKey(), restarted.copyRawPublicKey())
        assertTrue(BODY_ID.matches(original.bodyId))
        assertTrue(KEY_EPOCH_ID.matches(original.keyEpochId))
        assertEquals(
            original.bodyId,
            GenesisUltraBodyIdentityRoot.bodyIdFor(original.publicKeyRef)
        )
        assertEquals(
            original.keyEpochId,
            GenesisUltraBodyIdentityRoot.keyEpochIdFor(original.publicKeyRef)
        )
        assertEquals(0, legacyArchiveDao.countLocalIdentity())
        assertEquals(0, legacyArchiveDao.countGenesisCore())
    }

    @Test
    fun signerForInstanceUsesTheExactDerivedBodyRoot() = runBlocking {
        val root = keyStore().provisionBeforeBirth()
        val signer = keyStore().signerForInstance(INSTANCE_ID)
        val message = "genesis-ultra-body-possession".toByteArray(StandardCharsets.UTF_8)
        val signature = signer.sign(message)

        assertEquals(INSTANCE_ID, signer.key.instanceId)
        assertEquals(root.bodyId, signer.key.bodyId)
        assertEquals(root.keyEpochId, signer.key.keyEpochId)
        assertEquals(root.publicKeyRef, signer.key.publicKeyRef)
        assertArrayEquals(root.copyRawPublicKey(), signer.key.copyRawPublicKey())
        assertEquals(64, signature.size)
        Ed25519Verify(root.copyRawPublicKey()).verify(signature, message)
    }

    @Test
    fun durableRecordContainsOnlyAnEncryptedKeysetAndDerivedPublicReferences() = runBlocking {
        val root = keyStore().provisionBeforeBirth()
        val encodedRecord = requireNotNull(preferences().getString(RECORD_KEY, null))
        val record = JSONObject(encodedRecord)
        val encrypted = Base64.decode(record.getString("encrypted_keyset"), Base64.NO_WRAP)
        val rawPublicKeyBase64 = Base64.encodeToString(
            root.copyRawPublicKey(),
            Base64.NO_WRAP
        )

        assertEquals(root.bodyId, record.getString("body_id"))
        assertEquals(root.keyEpochId, record.getString("key_epoch_id"))
        assertEquals(root.publicKeyRef, record.getString("public_key_ref"))
        assertFalse(encodedRecord.contains(rawPublicKeyBase64))
        assertTrue(
            runCatching {
                TinkProtoKeysetFormat.parseKeyset(
                    encrypted,
                    InsecureSecretKeyAccess.get(),
                    RegistryConfiguration.get()
                )
            }.isFailure
        )
    }

    @Test
    fun ciphertextTamperMakesTheRootInconsistentWithoutReplacement() = runBlocking {
        keyStore().provisionBeforeBirth()
        val record = JSONObject(requireNotNull(preferences().getString(RECORD_KEY, null)))
        val encrypted = Base64.decode(record.getString("encrypted_keyset"), Base64.NO_WRAP)
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()
        val tamperedRecord = record
            .put("encrypted_keyset", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .toString()
        assertTrue(preferences().edit().putString(RECORD_KEY, tamperedRecord).commit())

        assertEquals(GenesisUltraBodyIdentityRootState.INCONSISTENT, keyStore().readState())
        val loadFailure = runCatching { keyStore().loadExisting() }.exceptionOrNull()
        val reprovisionFailure = runCatching {
            keyStore().provisionBeforeBirth()
        }.exceptionOrNull()

        assertNotNull(loadFailure)
        assertNotNull(reprovisionFailure)
        assertEquals(tamperedRecord, preferences().getString(RECORD_KEY, null))
    }

    @Test
    fun missingRecordNeverCreatesAReplacementDuringLoad() = runBlocking {
        val failure = runCatching { keyStore().loadExisting() }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(GenesisUltraBodyIdentityRootState.ABSENT, keyStore().readState())
        assertFalse(preferences().contains(RECORD_KEY))
        assertFalse(AndroidKeystore.hasKey(masterKeyAlias))
    }

    @Test
    fun lostAndroidKeystoreKeyDoesNotRegenerateTheBodyIdentity() = runBlocking {
        val original = keyStore().provisionBeforeBirth()
        val record = preferences().getString(RECORD_KEY, null)
        AndroidKeystore.deleteKey(masterKeyAlias)

        assertEquals(GenesisUltraBodyIdentityRootState.INCONSISTENT, keyStore().readState())
        val loadFailure = runCatching { keyStore().loadExisting() }.exceptionOrNull()
        val reprovisionFailure = runCatching {
            keyStore().provisionBeforeBirth()
        }.exceptionOrNull()

        assertNotNull(original.publicKeyRef)
        assertNotNull(loadFailure)
        assertNotNull(reprovisionFailure)
        assertEquals(record, preferences().getString(RECORD_KEY, null))
        assertFalse(AndroidKeystore.hasKey(masterKeyAlias))
    }

    private fun keyStore(): GenesisUltraAndroidBodyIdentityRootStore {
        return GenesisUltraAndroidBodyIdentityRootStore(
            context = context,
            database = database,
            preferencesName = preferencesName,
            masterKeyAlias = masterKeyAlias
        )
    }

    private fun preferences() = context.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE
    )

    private companion object {
        const val RECORD_KEY = GenesisUltraAndroidBodyIdentityRootStore.RECORD_KEY
        const val INSTANCE_ID = "inst_01H_BODY_IDENTITY_ROOT_0001"
        val BODY_ID = Regex("^body_[a-f0-9]{64}$")
        val KEY_EPOCH_ID = Regex("^epoch_[a-f0-9]{64}$")
    }
}
