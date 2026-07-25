package com.morimil.app.data.genesis.ultra

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.crypto.tink.integration.android.AndroidKeystore
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
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GenesisUltraCommittedConsentRetirementCoordinatorTest {
    private lateinit var context: Context
    private lateinit var preferencesName: String
    private lateinit var keyAlias: String
    private lateinit var recordKey: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val suffix = UUID.randomUUID().toString()
        preferencesName = "test_genesis_committed_consent_$suffix"
        keyAlias = "test.genesis.committed.consent.$suffix"
        recordKey = "active_consent_$suffix"
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        runCatching {
            if (AndroidKeystore.hasKey(keyAlias)) AndroidKeystore.deleteKey(keyAlias)
        }
    }

    @Test
    fun absentBirthNeverTouchesPreBirthConsent() = runBlocking {
        writeRecord(CONSENT_DIGEST)
        AndroidKeystore.generateNewAes256GcmKey(keyAlias)
        val coordinator = coordinator(GenesisUltraPersistedBirthState.ABSENT)

        val result = coordinator.retireIfCommitted()

        assertEquals(GenesisUltraCommittedConsentRetirementResult.NOT_APPLICABLE, result)
        assertTrue(preferences().contains(recordKey))
        assertTrue(AndroidKeystore.hasKey(keyAlias))
    }

    @Test
    fun committedBirthRetiresMatchingRecordAndKey() = runBlocking {
        writeRecord(CONSENT_DIGEST)
        AndroidKeystore.generateNewAes256GcmKey(keyAlias)
        val coordinator = coordinator(GenesisUltraPersistedBirthState.COMMITTED)

        val result = coordinator.retireIfCommitted()

        assertEquals(GenesisUltraCommittedConsentRetirementResult.RETIRED, result)
        assertFalse(preferences().contains(recordKey))
        assertFalse(AndroidKeystore.hasKey(keyAlias))
    }

    @Test
    fun committedBirthRepairsRecordOnlyPartialCleanup() = runBlocking {
        writeRecord(CONSENT_DIGEST)
        val coordinator = coordinator(GenesisUltraPersistedBirthState.COMMITTED)

        val result = coordinator.retireIfCommitted()

        assertEquals(GenesisUltraCommittedConsentRetirementResult.RETIRED, result)
        assertFalse(preferences().contains(recordKey))
        assertFalse(AndroidKeystore.hasKey(keyAlias))
    }

    @Test
    fun committedBirthRepairsKeyOnlyPartialCleanup() = runBlocking {
        AndroidKeystore.generateNewAes256GcmKey(keyAlias)
        val coordinator = coordinator(GenesisUltraPersistedBirthState.COMMITTED)

        val result = coordinator.retireIfCommitted()

        assertEquals(GenesisUltraCommittedConsentRetirementResult.RETIRED, result)
        assertFalse(preferences().contains(recordKey))
        assertFalse(AndroidKeystore.hasKey(keyAlias))
    }

    @Test
    fun committedBirthWithNoResidueIsIdempotent() = runBlocking {
        val coordinator = coordinator(GenesisUltraPersistedBirthState.COMMITTED)

        val result = coordinator.retireIfCommitted()

        assertEquals(GenesisUltraCommittedConsentRetirementResult.ALREADY_ABSENT, result)
    }

    @Test
    fun changedConsentDigestFailsClosedAndPreservesResidue() {
        writeRecord(digest('9'))
        AndroidKeystore.generateNewAes256GcmKey(keyAlias)
        val coordinator = coordinator(GenesisUltraPersistedBirthState.COMMITTED)

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { coordinator.retireIfCommitted() }
        }

        assertEquals("committed_consent_retirement_digest_mismatch", error.message)
        assertTrue(preferences().contains(recordKey))
        assertTrue(AndroidKeystore.hasKey(keyAlias))
    }

    @Test
    fun inconsistentBirthFailsBeforeTouchingResidue() {
        writeRecord(CONSENT_DIGEST)
        AndroidKeystore.generateNewAes256GcmKey(keyAlias)
        val coordinator = coordinator(GenesisUltraPersistedBirthState.INCONSISTENT)

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.retireIfCommitted() }
        }

        assertEquals("committed_consent_retirement_birth_inconsistent", error.message)
        assertTrue(preferences().contains(recordKey))
        assertTrue(AndroidKeystore.hasKey(keyAlias))
    }

    private fun coordinator(
        state: GenesisUltraPersistedBirthState
    ): GenesisUltraCommittedConsentRetirementCoordinator {
        return GenesisUltraCommittedConsentRetirementCoordinator.forTest(
            context = context,
            readAuthorizedBirthState = { state },
            loadCommittedAuthorization = { durableAuthorization() },
            preferencesName = preferencesName,
            masterKeyAlias = keyAlias,
            recordKey = recordKey
        )
    }

    private fun writeRecord(consentDigest: String) {
        val record = JSONObject()
            .put("schema_version", "genesis.host.birth.consent.record.v0.1")
            .put(
                "protection_profile",
                GenesisUltraVerifiedHostBirthConsent.PROTECTION_PROFILE
            )
            .put("consent_digest", consentDigest)
            .put("encrypted_consent", "opaque-retirement-target")
            .toString()
        check(preferences().edit().putString(recordKey, record).commit())
    }

    private fun durableAuthorization(): GenesisUltraDurableBirthAuthorization {
        return GenesisUltraDurableBirthAuthorization.create(
            candidateDigest = digest('1'),
            consentDigest = CONSENT_DIGEST,
            birthStateDigest = digest('2'),
            receiptDigest = digest('3'),
            bodyId = "body_" + "4".repeat(64),
            guardianId = "guardian_test_identity",
            guardianKeyEpochId = "guardian_epoch_test_0001",
            authorizedAt = "2026-07-25T12:00:00Z",
            expiresAt = "2026-07-25T12:02:00Z"
        )
    }

    private fun preferences() =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    private fun digest(character: Char): String =
        "sha256:" + character.toString().repeat(64)

    private companion object {
        val CONSENT_DIGEST = "sha256:" + "8".repeat(64)
    }
}
