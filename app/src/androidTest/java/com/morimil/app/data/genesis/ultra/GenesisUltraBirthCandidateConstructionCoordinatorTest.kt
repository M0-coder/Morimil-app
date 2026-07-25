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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GenesisUltraBirthCandidateConstructionCoordinatorTest {
    private lateinit var database: MorimilDatabase
    private lateinit var bodyPreferencesName: String
    private lateinit var bodyMasterKeyAlias: String
    private lateinit var guardianPreferencesName: String
    private lateinit var guardianMasterKeyAlias: String
    private lateinit var bodyStore: GenesisUltraAndroidBodyIdentityRootStore
    private lateinit var guardianStore: GenesisUltraAndroidGuardianTrustAnchorStore
    private lateinit var preparationCoordinator: GenesisUltraBirthPreparationCoordinator

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        val suffix = UUID.randomUUID().toString()
        bodyPreferencesName = "genesis-ultra-candidate-body-test-$suffix"
        bodyMasterKeyAlias = "com.morimil.app.test.genesis.ultra.candidate.body.$suffix"
        guardianPreferencesName = "genesis-ultra-candidate-guardian-test-$suffix"
        guardianMasterKeyAlias = "com.morimil.app.test.genesis.ultra.candidate.guardian.$suffix"
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
    fun constructsVerifiedCandidateWithoutPersistingBirth() = runBlocking {
        val bodyRoot = bodyStore.provisionBeforeBirth()
        val guardian = guardianMaterial()
        pinGuardian(guardian)
        val release = verifiedRelease(guardian)
        val request = GenesisUltraBirthCandidateConstructionRequest(
            release = release,
            companionName = "Morimil",
            bornAt = BORN_AT,
            platformProfile = "android-kotlin"
        )

        val first = coordinator(entropySource(0x11, 0x31)).construct(request)
        val repeated = coordinator(entropySource(0x11, 0x31)).construct(request)
        val different = coordinator(entropySource(0x12, 0x32)).construct(request)

        assertTrue(first.assessment.structurallyValid)
        assertFalse(first.assessment.birthReady)
        assertFalse(first.birthCommitAuthorized)
        assertEquals(
            listOf("transactional_birth_commit_not_integrated"),
            first.assessment.remainingBlockers
        )
        assertEquals("Morimil", first.candidate.instanceIdentity.companionName)
        assertTrue(first.candidate.instanceIdentity.instanceId.startsWith("inst_"))
        assertNotEquals(bodyRoot.bodyId, first.candidate.instanceIdentity.instanceId)
        assertEquals(bodyRoot.bodyId, first.candidate.bodyRecord.bodyId)
        assertEquals(bodyRoot.publicKeyRef, first.candidate.bodyRecord.publicKeyFingerprint)
        assertEquals(bodyRoot.keyEpochId, first.candidate.keyEpochs.single().keyEpochId)
        assertEquals(bodyRoot.bodyId, first.candidate.bodyPossession.proof.bodyId)
        assertEquals(BORN_AT, first.candidate.bodyPossession.proof.issuedAt)
        assertEquals("2026-07-25T03:06:00Z", first.candidate.bodyPossession.proof.expiresAt)
        assertTrue(first.candidate.guardianKeyEpochRegistry.trusts(release.signature))

        assertEquals(first.candidate.instanceIdentity.instanceId, repeated.candidate.instanceIdentity.instanceId)
        assertEquals(first.candidate.bodyPossession.proof.proofDigest, repeated.candidate.bodyPossession.proof.proofDigest)
        assertEquals(first.candidateDigest, repeated.candidateDigest)
        assertNotEquals(first.candidate.instanceIdentity.instanceId, different.candidate.instanceIdentity.instanceId)
        assertNotEquals(first.candidateDigest, different.candidateDigest)

        assertEquals(GenesisUltraPersistedBirthState.ABSENT, GenesisUltraAtomicBirthStore(database).readState())
        assertEquals(0, database.genesisUltraMemoryDao().countAll())
        assertEquals(0, database.memoryDao().countLocalIdentity())
        assertEquals(0, database.memoryDao().countGenesisCore())
        assertEquals(
            GenesisUltraBirthPreparationStatus.READY_FOR_SIGNED_CANDIDATE,
            preparationCoordinator.inspect().status
        )
    }

    @Test
    fun rejectsNonCanonicalNameAndZeroEntropyWithoutWritingState() = runBlocking {
        bodyStore.provisionBeforeBirth()
        val guardian = guardianMaterial()
        pinGuardian(guardian)
        val release = verifiedRelease(guardian)

        val nameFailure = runCatching {
            coordinator(entropySource(0x11, 0x31)).construct(
                GenesisUltraBirthCandidateConstructionRequest(
                    release = release,
                    companionName = " Morimil ",
                    bornAt = BORN_AT
                )
            )
        }.exceptionOrNull()
        val entropyFailure = runCatching {
            coordinator { size -> ByteArray(size) }.construct(
                GenesisUltraBirthCandidateConstructionRequest(
                    release = release,
                    companionName = "Morimil",
                    bornAt = BORN_AT
                )
            )
        }.exceptionOrNull()

        assertNotNull(nameFailure)
        assertTrue(nameFailure?.message.orEmpty().contains("companion_name_not_canonical"))
        assertNotNull(entropyFailure)
        assertTrue(entropyFailure?.message.orEmpty().contains("entropy_all_zero"))
        assertEquals(GenesisUltraPersistedBirthState.ABSENT, GenesisUltraAtomicBirthStore(database).readState())
        assertEquals(0, database.genesisUltraMemoryDao().countAll())
        assertEquals(0, database.memoryDao().countLocalIdentity())
    }

    private fun coordinator(
        entropySource: (Int) -> ByteArray
    ): GenesisUltraBirthCandidateConstructionCoordinator {
        return GenesisUltraBirthCandidateConstructionCoordinator(
            preparationCoordinator = preparationCoordinator,
            bodyIdentityRootStore = bodyStore,
            guardianTrustAnchorStore = guardianStore,
            entropySource = entropySource
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
        val doctrineBytes = "Genesis Ultra candidate construction test doctrine\n".toByteArray()
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
        val signatureJson = envelopeJson(signedEnvelope)
        val registry = guardianStore.loadExistingRegistry()

        return GenesisUltraReleaseVerifier(registry.signatureVerifier()).verify(
            GenesisUltraReleaseBundle(
                manifestJson = manifestJson.toString(),
                signatureJson = signatureJson.toString(),
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
            if (AndroidKeystore.hasKey(alias)) {
                AndroidKeystore.deleteKey(alias)
            }
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
        const val GUARDIAN_ID = "guardian_01HMORIMILCANDIDATE001"
        const val GUARDIAN_KEY_EPOCH_ID = "guardian_epoch_01HMORIMILCANDIDATE001"
        const val SEED_ID = "seed_01HMORIMILCANDIDATE000001"
        const val IDENTITY_PATH = "seed/identity.json"
        const val DOCTRINE_PATH = "seed/doctrine.md"
        const val RELEASE_SIGNED_AT = "2026-07-25T03:00:00Z"
        const val BORN_AT = "2026-07-25T03:01:00Z"
        const val PINNED_AT_MILLIS = 1_753_412_400_000L
        val ZERO_SHA256 = "sha256:" + "0".repeat(64)
        val ZERO_SIGNATURE = "0".repeat(128)
    }
}
