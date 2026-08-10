package com.morimil.app.data.genesis.ultra

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.morimil.app.data.local.MorimilDatabase
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenesisUltraPreBirthProvisioningCoordinatorTest {
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
        bodyPreferencesName = "genesis-ultra-birth01-body-$suffix"
        bodyMasterKeyAlias = "com.morimil.app.test.birth01.body.$suffix"
        guardianPreferencesName = "genesis-ultra-birth01-guardian-$suffix"
        guardianMasterKeyAlias = "com.morimil.app.test.birth01.guardian.$suffix"
        database = Room.inMemoryDatabaseBuilder(context, MorimilDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        context.getSharedPreferences(bodyPreferencesName, Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences(guardianPreferencesName, Context.MODE_PRIVATE)
            .edit().clear().commit()
        listOf(bodyMasterKeyAlias, guardianMasterKeyAlias).forEach { alias ->
            runCatching {
                if (AndroidKeystore.hasKey(alias)) AndroidKeystore.deleteKey(alias)
            }
        }
    }

    @Test
    fun cleanInstallCanReachSignedCandidateReadinessWithoutCommittingBirth() = runBlocking {
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
            clockMillis = { PINNED_AT_MILLIS }
        )
        val preparation = GenesisUltraBirthPreparationCoordinator(
            database = database,
            bodyIdentityRootStore = bodyStore,
            guardianTrustAnchorStore = guardianStore
        )
        val coordinator = GenesisUltraPreBirthProvisioningCoordinator.production(
            preparationCoordinator = preparation,
            bodyIdentityRootStore = bodyStore,
            guardianTrustAnchorStore = guardianStore
        )

        val initial = coordinator.inspect()
        assertEquals(
            GenesisUltraBirthPreparationStatus.BODY_IDENTITY_REQUIRED,
            initial.assessment.status
        )

        val bodyPrepared = coordinator.provisionBody(
            GenesisUltraBodyProvisioningCeremonyRequest(
                confirmationPurpose =
                    GenesisUltraBodyProvisioningCeremonyRequest.CONFIRMATION_PURPOSE,
                userPresenceConfirmed = true
            )
        )
        assertEquals(
            GenesisUltraBirthPreparationStatus.GUARDIAN_TRUST_REQUIRED,
            bodyPrepared.assessment.status
        )
        assertNotNull(bodyPrepared.bodyReceipt)

        val publicKeyPreview = GenesisUltraGuardianPublicKeyPreview(GUARDIAN_PUBLIC_KEY)
        val ready = coordinator.provisionGuardian(
            publicKeyPreview.ceremonyRequest(
                guardianId = GUARDIAN_ID,
                keyEpochId = GUARDIAN_EPOCH_ID,
                confirmedPublicKeyRef = publicKeyPreview.publicKeyRef,
                independentConfirmationAcknowledged = true,
                userPresenceConfirmed = true
            )
        )

        assertEquals(
            GenesisUltraBirthPreparationStatus.READY_FOR_SIGNED_CANDIDATE,
            ready.assessment.status
        )
        assertNotNull(ready.bodyReceipt)
        assertNotNull(ready.guardianReceipt)
        assertEquals(PINNED_AT_MILLIS, ready.guardianReceipt?.pinnedAtMillis)
        assertFalse(ready.assessment.birthCommitAuthorized)
        assertEquals(GenesisUltraPersistedBirthState.ABSENT, ready.assessment.facts.persistedBirthState)
        val legacyArchiveDao = database.legacyArchiveReadDao()
        assertEquals(0, legacyArchiveDao.countLocalIdentity())
        assertEquals(0, legacyArchiveDao.countGenesisCore())
        assertEquals(0, database.genesisUltraMemoryDao().countAll())
    }

    private companion object {
        const val GUARDIAN_ID = "guardian_01HMORIMILCUSTODIAN0001"
        const val GUARDIAN_EPOCH_ID = "guardian_epoch_01HMORIMIL000001"
        const val PINNED_AT_MILLIS = 1_786_000_000_000L
        val GUARDIAN_PUBLIC_KEY = ByteArray(32) { index -> (index + 1).toByte() }
    }
}
