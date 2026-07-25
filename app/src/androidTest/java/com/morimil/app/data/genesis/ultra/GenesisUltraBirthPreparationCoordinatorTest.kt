package com.morimil.app.data.genesis.ultra

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.morimil.app.data.local.MorimilDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GenesisUltraBirthPreparationCoordinatorAndroidTest {
    private lateinit var database: MorimilDatabase
    private lateinit var bodyPreferencesName: String
    private lateinit var bodyMasterKeyAlias: String
    private lateinit var guardianPreferencesName: String
    private lateinit var guardianMasterKeyAlias: String

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        val suffix = UUID.randomUUID().toString()
        bodyPreferencesName = "genesis-ultra-preparation-body-$suffix"
        bodyMasterKeyAlias = "com.morimil.app.test.preparation.body.$suffix"
        guardianPreferencesName = "genesis-ultra-preparation-guardian-$suffix"
        guardianMasterKeyAlias = "com.morimil.app.test.preparation.guardian.$suffix"
        database = Room.inMemoryDatabaseBuilder(context, MorimilDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        context.getSharedPreferences(bodyPreferencesName, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(guardianPreferencesName, Context.MODE_PRIVATE).edit().clear().commit()
        deleteAndroidKey(bodyMasterKeyAlias)
        deleteAndroidKey(guardianMasterKeyAlias)
    }

    @Test
    fun progressesOnlyToSignedCandidatePreparationWithoutCommittingBirth() = runBlocking {
        val bodyStore = GenesisUltraAndroidBodyIdentityRootStore(
            context = context,
            database = database,
            preferencesName = bodyPreferencesName,
            masterKeyAlias = bodyMasterKeyAlias
        )
        val guardianStore = GenesisUltraAndroidGuardianTrustAnchorStore(
            context = context,
            database = database,
            preferencesName = guardianPreferencesName,
            masterKeyAlias = guardianMasterKeyAlias,
            clockMillis = { 1_753_315_200_000L }
        )
        val coordinator = GenesisUltraBirthPreparationCoordinator(
            database = database,
            bodyIdentityRootStore = bodyStore,
            guardianTrustAnchorStore = guardianStore
        )

        val initial = coordinator.inspect()
        assertEquals(GenesisUltraBirthPreparationStatus.BODY_IDENTITY_REQUIRED, initial.status)

        bodyStore.provisionBeforeBirth()
        val bodyReady = coordinator.inspect()
        assertEquals(GenesisUltraBirthPreparationStatus.GUARDIAN_TRUST_REQUIRED, bodyReady.status)

        guardianStore.provisionBeforeBirth(
            GenesisUltraGuardianTrustAnchorProvisioningRequest(
                guardianId = GUARDIAN_ID,
                keyEpochId = GUARDIAN_KEY_EPOCH_ID,
                confirmedPublicKeyRef = GenesisUltraHashProfile.sha256(GUARDIAN_RAW_PUBLIC_KEY),
                confirmationPurpose =
                    GenesisUltraGuardianTrustAnchorProvisioningRequest.CONFIRMATION_PURPOSE,
                rawPublicKey = GUARDIAN_RAW_PUBLIC_KEY
            )
        )
        val prepared = coordinator.inspect()

        assertEquals(
            GenesisUltraBirthPreparationStatus.READY_FOR_SIGNED_CANDIDATE,
            prepared.status
        )
        assertTrue(prepared.candidateConstructionReady)
        assertFalse(prepared.birthCommitAuthorized)
        assertEquals(GenesisUltraPersistedBirthState.ABSENT, prepared.facts.persistedBirthState)
        assertEquals(0, prepared.facts.canonicalMemoryEventCount)
        assertEquals(0, prepared.facts.legacyLocalIdentityCount)
        assertEquals(0, prepared.facts.legacyGenesisCoreCount)
        assertEquals(
            GenesisUltraPersistedBirthState.ABSENT,
            GenesisUltraAtomicBirthStore(database).readState()
        )
    }

    private fun deleteAndroidKey(alias: String) {
        runCatching {
            if (AndroidKeystore.hasKey(alias)) {
                AndroidKeystore.deleteKey(alias)
            }
        }
    }

    private companion object {
        const val GUARDIAN_ID = "guardian_01HMORIMILCUSTODIAN0001"
        const val GUARDIAN_KEY_EPOCH_ID = "guardian_epoch_01HMORIMIL000001"
        val GUARDIAN_RAW_PUBLIC_KEY = ByteArray(32) { index -> (index + 17).toByte() }
    }
}
