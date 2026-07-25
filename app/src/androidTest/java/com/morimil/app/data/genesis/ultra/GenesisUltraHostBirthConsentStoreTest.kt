package com.morimil.app.data.genesis.ultra

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.crypto.tink.AccessesPartialKey
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.google.crypto.tink.signature.Ed25519Parameters
import com.google.crypto.tink.signature.Ed25519PrivateKey
import com.google.crypto.tink.signature.SignatureConfig
import com.morimil.app.data.local.MorimilDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GenesisUltraHostBirthConsentStoreTest {
    private lateinit var database: MorimilDatabase
    private lateinit var bodyPreferencesName: String
    private lateinit var bodyMasterKeyAlias: String
    private lateinit var guardianPreferencesName: String
    private lateinit var guardianMasterKeyAlias: String
    private lateinit var consentPreferencesName: String
    private lateinit var consentMasterKeyAlias: String
    private lateinit var bodyStore: GenesisUltraAndroidBodyIdentityRootStore
    private lateinit var guardianStore: GenesisUltraAndroidGuardianTrustAnchorStore
    private lateinit var preparationCoordinator: GenesisUltraBirthPreparationCoordinator
    private lateinit var consentStore: GenesisUltraAndroidHostBirthConsentStore
    private lateinit var verifiedSeedRelease: GenesisUltraVerifiedRelease
    private var clock: Instant = Instant.parse(CONSENTED_AT)

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        val suffix = UUID.randomUUID().toString()
        bodyPreferencesName = "genesis-ultra-consent-body-test-$suffix"
        bodyMasterKeyAlias = "com.morimil.app.test.genesis.ultra.consent.body.$suffix"
        guardianPreferencesName = "genesis-ultra-consent-guardian-test-$suffix"
        guardianMasterKeyAlias = "com.morimil.app.test.genesis.ultra.consent.guardian.$suffix"
        consentPreferencesName = "genesis-ultra-consent-test-$suffix"
        consentMasterKeyAlias = "com.morimil.app.test.genesis.ultra.consent.$suffix"
        database = Room.inMemoryDatabaseBuilder(context, MorimilDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bodyStore = GenesisUltraAndroidBodyIdentityRootStore(
            context = context,
            database = database,
            preferencesName = bodyPreferencesName,
            masterKeyAlias = bodyMasterKeyAlias
        )
        guardianStore = GenesisUltraAndroidGuardianTrustAnchorStore(
            context = context,
            database = database,
            preferencesName = guardianPreferencesName,
            masterKeyAlias = guardianMasterKeyAlias,
            clockMillis = { PINNED_AT_MILLIS }
        )
        preparationCoordinator = GenesisUltraBirthPreparationCoordinator(
            database = database,
            bodyIdentityRootStore = bodyStore,
            guardianTrustAnchorStore = guardianStore
        )
        consentStore = newConsentStore()
    }

    @After
    fun tearDown() {
        database.close()
        context.getSharedPreferences(bodyPreferencesName, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(guardianPreferencesName, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(consentPreferencesName, Context.MODE_PRIVATE).edit().clear().commit()
        deleteAndroidKey(bodyMasterKeyAlias)
        deleteAndroidKey(guardianMasterKeyAlias)
        deleteAndroidKey(consentMasterKeyAlias)
    }

    @Test
    fun recordsLoadsExpiresAndRevokesOnlyForExactCandidate() = runBlocking {
        val candidate = preparedCandidate(0x11, 0x31)
        val request = confirmationRequest(candidate)

        val consent = consentStore.recordExplicitConsent(candidate, request)

        assertEquals(GenesisUltraHostBirthConsentState.READY, consentStore.readState())
        assertEquals(candidate.candidateDigest, consent.candidateDigest)
        assertEquals(candidate.candidate.instanceIdentity.instanceId, consent.instanceId)
        assertEquals(candidate.candidate.instanceIdentity.companionName, consent.companionName)
        assertEquals(candidate.candidate.release.verifiedRootHash, consent.seedRootHash)
        assertEquals(candidate.candidate.bodyRecord.bodyId, consent.bodyId)
        assertEquals(CONSENTED_AT, consent.consentedAt)
        assertEquals(CONSENT_EXPIRES_AT, consent.expiresAt)
        assertFalse(consent.birthCommitAuthorized)

        val outerRecord = requireNotNull(
            context.getSharedPreferences(consentPreferencesName, Context.MODE_PRIVATE)
                .getString(GenesisUltraAndroidHostBirthConsentStore.RECORD_KEY, null)
        )
        assertFalse(outerRecord.contains("Morimil"))
        assertFalse(outerRecord.contains(consent.instanceId))
        assertFalse(outerRecord.contains(consent.bodyId))

        val reloaded = newConsentStore().loadForCandidate(candidate, CONSENTED_AT)
        assertEquals(consent.consentDigest, reloaded.consentDigest)
        assertFalse(reloaded.birthCommitAuthorized)

        val differentCandidate = constructedCandidate(0x12, 0x32, verifiedSeedRelease)
        val mismatch = runCatching {
            newConsentStore().loadForCandidate(differentCandidate, CONSENTED_AT)
        }.exceptionOrNull()
        assertNotNull(mismatch)
        assertTrue(mismatch?.message.orEmpty().contains("candidate_mismatch"))

        clock = Instant.parse(CONSENT_EXPIRES_AT)
        assertEquals(GenesisUltraHostBirthConsentState.EXPIRED, consentStore.readState())
        val expired = runCatching {
            consentStore.loadForCandidate(candidate, CONSENT_EXPIRES_AT)
        }.exceptionOrNull()
        assertNotNull(expired)

        assertTrue(consentStore.revokeBeforeBirth(candidate.candidateDigest))
        assertEquals(GenesisUltraHostBirthConsentState.ABSENT, consentStore.readState())
        assertFalse(AndroidKeystore.hasKey(consentMasterKeyAlias))
        assertEquals(GenesisUltraPersistedBirthState.ABSENT, GenesisUltraAtomicBirthStore(database).readState())
        assertEquals(0, database.genesisUltraMemoryDao().countAll())
        assertEquals(0, database.memoryDao().countLocalIdentity())
    }

    @Test
    fun rejectsIncompletePresentationAndFailsClosedAfterTampering() = runBlocking {
        val candidate = preparedCandidate(0x21, 0x41)
        val wrongRequest = confirmationRequest(candidate).copy(
            presentedConfirmationCode = "0".repeat(12)
        )

        val rejected = runCatching {
            consentStore.recordExplicitConsent(candidate, wrongRequest)
        }.exceptionOrNull()
        assertNotNull(rejected)
        assertTrue(rejected?.message.orEmpty().contains("presented_code_mismatch"))
        assertEquals(GenesisUltraHostBirthConsentState.ABSENT, consentStore.readState())
        assertFalse(AndroidKeystore.hasKey(consentMasterKeyAlias))

        consentStore.recordExplicitConsent(candidate, confirmationRequest(candidate))
        val duplicate = runCatching {
            consentStore.recordExplicitConsent(candidate, confirmationRequest(candidate))
        }.exceptionOrNull()
        assertNotNull(duplicate)
        assertTrue(duplicate?.message.orEmpty().contains("record_not_absent"))

        val preferences = context.getSharedPreferences(consentPreferencesName, Context.MODE_PRIVATE)
        val original = JSONObject(requireNotNull(
            preferences.getString(GenesisUltraAndroidHostBirthConsentStore.RECORD_KEY, null)
        ))
        val encrypted = original.getString("encrypted_consent")
        val replacement = if (encrypted.first() == 'A') 'B' else 'A'
        original.put("encrypted_consent", replacement + encrypted.drop(1))
        assertTrue(
            preferences.edit()
                .putString(GenesisUltraAndroidHostBirthConsentStore.RECORD_KEY, original.toString())
                .commit()
        )

        assertEquals(GenesisUltraHostBirthConsentState.INCONSISTENT, consentStore.readState())
        val tampered = runCatching {
            consentStore.loadForCandidate(candidate, CONSENTED_AT)
        }.exceptionOrNull()
        assertNotNull(tampered)
        assertEquals(GenesisUltraPersistedBirthState.ABSENT, GenesisUltraAtomicBirthStore(database).readState())
        assertEquals(0, database.genesisUltraMemoryDao().countAll())
    }

    private suspend fun preparedCandidate(
        instanceSeed: Int,
        possessionSeed: Int
    ): GenesisUltraConstructedBirthCandidate {
        bodyStore.provisionBeforeBirth()
        val guardian = guardianMaterial()
        pinGuardian(guardian)
        verifiedSeedRelease = verifiedRelease(guardian)
        return constructedCandidate(instanceSeed, possessionSeed, verifiedSeedRelease)
    }

    private suspend fun constructedCandidate(
        instanceSeed: Int,
        possessionSeed: Int,
        release: GenesisUltraVerifiedRelease
    ): GenesisUltraConstructedBirthCandidate {
        return GenesisUltraBirthCandidateConstructionCoordinator(
            preparationCoordinator = preparationCoordinator,
            bodyIdentityRootStore = bodyStore,
            guardianTrustAnchorStore = guardianStore,
            entropySource = entropySource(instanceSeed, possessionSeed)
        ).construct(
            GenesisUltraBirthCandidateConstructionRequest(
                release = release,
                companionName = "Morimil",
                bornAt = BORN_AT,
                platformProfile = "android-kotlin"
            )
        )
    }

    private fun confirmationRequest(
        candidate: GenesisUltraConstructedBirthCandidate
    ): GenesisUltraHostBirthConsentRequest {
        return GenesisUltraHostBirthConsentRequest(
            presentedCandidateDigest = candidate.candidateDigest,
            presentedInstanceId = candidate.candidate.instanceIdentity.instanceId,
            presentedCompanionName = candidate.candidate.instanceIdentity.companionName,
            presentedConfirmationCode =
                GenesisUltraHostBirthConsentRequest.confirmationCode(candidate.candidateDigest),
            decision = GenesisUltraHostBirthConsentRequest.APPROVE_DECISION,
            confirmationMode = GenesisUltraHostBirthConsentRequest.INTERACTIVE_CONFIRMATION_MODE,
            confirmationPurpose = GenesisUltraHostBirthConsentRequest.BIRTH_CONFIRMATION_PURPOSE,
            userPresenceConfirmed = true
        )
    }

    private fun newConsentStore(): GenesisUltraAndroidHostBirthConsentStore {
        return GenesisUltraAndroidHostBirthConsentStore(
            context = context,
            database = database,
            preferencesName = consentPreferencesName,
            masterKeyAlias = consentMasterKeyAlias,
            clock = { clock },
            entropySource = { size -> ByteArray(size) { index -> (index + 1).toByte() } }
        )
    }

    private fun entropySource(instanceSeed: Int, possessionSeed: Int): (Int) -> ByteArray {
        var call = 0
        return { size ->
            val seed = if (call++ == 0) instanceSeed else possessionSeed
            ByteArray(size) { index -> ((seed + index) and 0xff).toByte() }
        }
    }

    private suspend fun pinGuardian(material: GuardianMaterial) {
        guardianStore.provisionBeforeBirth(
            GenesisUltraGuardianTrustAnchorProvisioningRequest(
                guardianId = GUARDIAN_ID,
                keyEpochId = GUARDIAN_KEY_EPOCH_ID,
                confirmedPublicKeyRef = GenesisUltraHashProfile.sha256(material.rawPublicKey),
                confirmationPurpose =
                    GenesisUltraGuardianTrustAnchorProvisioningRequest.CONFIRMATION_PURPOSE,
                rawPublicKey = material.rawPublicKey
            )
        )
    }

    private suspend fun verifiedRelease(material: GuardianMaterial): GenesisUltraVerifiedRelease {
        val identityBytes = "{\"schema_version\":\"genesis.seed.identity.v0.1\"}".toByteArray()
        val doctrineBytes = "Genesis Ultra host consent test doctrine\n".toByteArray()
        val identityDigest = GenesisUltraHashProfile.sha256(identityBytes)
        val doctrineDigest = GenesisUltraHashProfile.sha256(doctrineBytes)
        val manifestWithoutRoot = GenesisUltraSeedManifest(
            schemaVersion = "genesis.seed.manifest.v0.1",
            protocolVersion = "genesis.protocol.v0.1",
            hashProfile = GenesisUltraHashProfile.FIELD_PROFILE,
            seedId = SEED_ID,
            identityDigest = identityDigest,
            doctrineDigest = doctrineDigest,
            files = listOf(
                GenesisUltraSeedFileRecord(IDENTITY_PATH, "identity", true, identityDigest),
                GenesisUltraSeedFileRecord(DOCTRINE_PATH, "doctrine", true, doctrineDigest)
            ),
            rootHash = ZERO_SHA256
        )
        val rootHash = GenesisUltraHashProfile.seedRoot(manifestWithoutRoot)
        val manifestJson = JSONObject()
            .put("schema_version", manifestWithoutRoot.schemaVersion)
            .put("protocol_version", manifestWithoutRoot.protocolVersion)
            .put("hash_profile", manifestWithoutRoot.hashProfile)
            .put("seed_id", manifestWithoutRoot.seedId)
            .put("identity_digest", identityDigest)
            .put("doctrine_digest", doctrineDigest)
            .put(
                "files",
                JSONArray().apply {
                    manifestWithoutRoot.files.forEach { file ->
                        put(
                            JSONObject()
                                .put("path", file.path)
                                .put("kind", file.kind)
                                .put("required", file.required)
                                .put("digest", file.digest)
                        )
                    }
                }
            )
            .put("root_hash", rootHash)

        val unsignedEnvelope = GenesisUltraSignatureEnvelope(
            schemaVersion = "genesis.signature.envelope.v0.1",
            signatureProfile = "genesis.signature.ed25519.v0.1",
            signerType = "guardian",
            signerId = GUARDIAN_ID,
            keyEpochId = GUARDIAN_KEY_EPOCH_ID,
            signedDomain = GenesisUltraHashProfile.SEED_ROOT_DOMAIN,
            signedDigest = rootHash,
            signatureValue = ZERO_SIGNATURE,
            createdAt = RELEASE_SIGNED_AT,
            publicKeyRef = GenesisUltraHashProfile.sha256(material.rawPublicKey)
        )
        val signature = material.signer.sign(
            GenesisUltraHashProfile.signatureEnvelopePreimage(unsignedEnvelope)
        )
        val signedEnvelope = unsignedEnvelope.copy(signatureValue = signature.toLowerHex())
        signature.fill(0)
        val registry = guardianStore.loadExistingRegistry()

        return GenesisUltraReleaseVerifier(registry.signatureVerifier()).verify(
            GenesisUltraReleaseBundle(
                manifestJson = manifestJson.toString(),
                signatureJson = envelopeJson(signedEnvelope).toString(),
                files = mapOf(
                    IDENTITY_PATH to identityBytes,
                    DOCTRINE_PATH to doctrineBytes
                )
            )
        )
    }

    private fun envelopeJson(envelope: GenesisUltraSignatureEnvelope): JSONObject {
        return JSONObject()
            .put("schema_version", envelope.schemaVersion)
            .put("signature_profile", envelope.signatureProfile)
            .put("signer_type", envelope.signerType)
            .put("signer_id", envelope.signerId)
            .put("key_epoch_id", envelope.keyEpochId)
            .put("signed_domain", envelope.signedDomain)
            .put("signed_digest", envelope.signedDigest)
            .put("signature_value", envelope.signatureValue)
            .put("created_at", envelope.createdAt)
            .put("public_key_ref", envelope.publicKeyRef)
    }

    @AccessesPartialKey
    private fun guardianMaterial(): GuardianMaterial {
        SignatureConfig.register()
        val handle = KeysetHandle.generateNew(Ed25519Parameters.create())
        val privateKey = handle.getPrimary().getKey() as Ed25519PrivateKey
        val rawPublicKey = privateKey.publicKey.publicKeyBytes.toByteArray()
        val signer = handle.getPrimitive(
            RegistryConfiguration.get(),
            PublicKeySign::class.java
        )
        return GuardianMaterial(rawPublicKey = rawPublicKey, signer = signer)
    }

    private fun deleteAndroidKey(alias: String) {
        runCatching {
            if (AndroidKeystore.hasKey(alias)) AndroidKeystore.deleteKey(alias)
        }
    }

    private fun ByteArray.toLowerHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

    private data class GuardianMaterial(
        val rawPublicKey: ByteArray,
        val signer: PublicKeySign
    )

    private companion object {
        const val GUARDIAN_ID = "guardian_01HMORIMILCONSENT0001"
        const val GUARDIAN_KEY_EPOCH_ID = "guardian_epoch_01HMORIMILCONSENT0001"
        const val SEED_ID = "seed_01HMORIMILCONSENT00000001"
        const val IDENTITY_PATH = "seed/identity.json"
        const val DOCTRINE_PATH = "seed/doctrine.md"
        const val RELEASE_SIGNED_AT = "2026-07-25T03:00:00Z"
        const val BORN_AT = "2026-07-25T03:01:00Z"
        const val CONSENTED_AT = "2026-07-25T03:02:00Z"
        const val CONSENT_EXPIRES_AT = "2026-07-25T03:04:00Z"
        const val PINNED_AT_MILLIS = 1_753_412_400_000L
        val ZERO_SHA256 = "sha256:" + "0".repeat(64)
        val ZERO_SIGNATURE = "0".repeat(128)
    }
}
