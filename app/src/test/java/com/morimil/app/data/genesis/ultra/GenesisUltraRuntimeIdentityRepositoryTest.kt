package com.morimil.app.data.genesis.ultra

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.time.Instant

class GenesisUltraRuntimeIdentityRepositoryTest {
    @Test
    fun committedIdentityIsProjectedOnlyAfterVerifiedRecovery() = runBlocking {
        val fixture = fixture()
        var recoveryCalls = 0
        var authorizationCalls = 0
        var capturedRecoveryRequest: GenesisUltraAtomicBirthRecoveryRequest? = null
        val repository = GenesisUltraRuntimeIdentityRepository.forTest(
            readAuthorizedBirthState = { GenesisUltraPersistedBirthState.COMMITTED },
            loadBodyIdentityRoot = { fixture.bodyRoot },
            loadGuardianTrustAnchor = { fixture.guardianAnchor },
            recoverVerifiedBirth = { request ->
                recoveryCalls += 1
                capturedRecoveryRequest = request
                fixture.recoveredBirth
            },
            loadCommittedAuthorization = {
                authorizationCalls += 1
                fixture.authorization
            }
        )

        val identity = requireNotNull(repository.readCommittedIdentity())

        assertEquals(1, recoveryCalls)
        assertEquals(1, authorizationCalls)
        assertArrayEquals(
            fixture.bodyRoot.copyRawPublicKey(),
            requireNotNull(capturedRecoveryRequest).bodyRawPublicKey
        )
        assertEquals(fixture.bundle.instanceIdentity.instanceId, identity.instanceId)
        assertEquals("Genesis Free 01", identity.companionName)
        assertEquals(fixture.bundle.birthState.initialBodyId, identity.activeBody.bodyId)
        assertEquals("active_writer", identity.activeBody.status)
        assertEquals(fixture.bundle.instanceIdentity.guardianId, identity.guardian.guardianId)
        assertEquals(fixture.bundle.seedManifest.seedId, identity.seed.seedId)
        assertEquals(fixture.bundle.seedManifest.rootHash, identity.seed.rootHash)
        assertEquals("free birth doctrine", identity.doctrine.readUtf8Strict())
        assertTrue(identity.policy.freedomCharter.readUtf8Strict().contains("\"charter_id\""))
        assertTrue(identity.policy.recoveryPolicy.readUtf8Strict().contains("\"policy_id\""))
        assertEquals(
            GenesisUltraRuntimeAuthorizationState.COMMITTED,
            identity.authorization.state
        )
        assertEquals("born", identity.authorization.birthStatus)
        assertFalse(identity.authorization.ownershipConferred)
    }

    @Test
    fun absentBirthReturnsNullWithoutLoadingTrustOrIdentityMaterial() = runBlocking {
        var touchedProtectedMaterial = false
        val repository = GenesisUltraRuntimeIdentityRepository.forTest(
            readAuthorizedBirthState = { GenesisUltraPersistedBirthState.ABSENT },
            loadBodyIdentityRoot = {
                touchedProtectedMaterial = true
                error("must_not_load_body_root")
            },
            loadGuardianTrustAnchor = {
                touchedProtectedMaterial = true
                error("must_not_load_guardian_anchor")
            },
            recoverVerifiedBirth = {
                touchedProtectedMaterial = true
                error("must_not_recover")
            },
            loadCommittedAuthorization = {
                touchedProtectedMaterial = true
                error("must_not_load_authorization")
            }
        )

        assertNull(repository.readCommittedIdentity())
        assertFalse(touchedProtectedMaterial)
    }

    @Test
    fun runtimeDocumentsNeverExposeMutableVerifiedBytes() = runBlocking {
        val fixture = fixture()
        val repository = GenesisUltraRuntimeIdentityRepository.forTest(
            readAuthorizedBirthState = { GenesisUltraPersistedBirthState.COMMITTED },
            loadBodyIdentityRoot = { fixture.bodyRoot },
            loadGuardianTrustAnchor = { fixture.guardianAnchor },
            recoverVerifiedBirth = { fixture.recoveredBirth },
            loadCommittedAuthorization = { fixture.authorization }
        )
        val doctrine = requireNotNull(repository.readCommittedIdentity()).doctrine
        val original = doctrine.copySourceBytes()
        val mutated = doctrine.copySourceBytes()

        mutated[0] = (mutated[0].toInt() xor 0x01).toByte()

        assertArrayEquals(original, doctrine.copySourceBytes())
    }

    @Test
    fun projectorRejectsGuardianSubstitution() {
        val fixture = fixture()
        val substitutedAnchor = GenesisUltraGuardianTrustAnchor(
            schemaVersion = GenesisUltraGuardianTrustAnchor.ANCHOR_SCHEMA,
            guardianId = "guardian_substituted",
            keyEpochId = fixture.guardianAnchor.keyEpochId,
            publicKeyRef = fixture.guardianAnchor.publicKeyRef,
            status = GenesisUltraGuardianTrustAnchor.ACTIVE_STATUS,
            confirmationMode = GenesisUltraGuardianTrustAnchor.CONFIRMATION_MODE,
            confirmationPurpose = GenesisUltraGuardianTrustAnchorProvisioningRequest.CONFIRMATION_PURPOSE,
            protectionProfile = GenesisUltraGuardianTrustAnchor.PROTECTION_PROFILE,
            pinnedAtMillis = 1L,
            rawPublicKey = fixture.guardianAnchor.copyRawPublicKey()
        )

        val failure = runCatching {
            GenesisUltraRuntimeIdentityProjector.project(
                recoveredBirth = fixture.recoveredBirth,
                authorization = fixture.authorization,
                bodyRoot = fixture.bodyRoot,
                guardianAnchor = substitutedAnchor
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("runtime_identity_guardian_link_mismatch") == true)
    }

    private fun fixture(): Fixture {
        val vectors = resourceJson("/genesis-ultra/atomic_birth_conformance.json")
        val sourceFixture = vectors.getJSONObject("fixture")
        val charter = vectors.getJSONObject("charter")
        val seedManifest = sourceFixture.getJSONObject("seed_manifest")
        val seedSignatureText = resourceText("/genesis-ultra/seed_release_signature.json")
        val seedSignature = GenesisUltraContractParser.parseSignatureEnvelope(seedSignatureText)
        val guardianPublicKey = decodeLowerHex(
            vectors.getJSONObject("guardian_test_signing_key").getString("public_key_hex")
        )
        val guardianRegistry = GenesisUltraTrustedGuardianKeyEpochRegistry(
            listOf(
                GenesisUltraTrustedGuardianKeyEpoch(
                    guardianId = seedSignature.signerId,
                    keyEpochId = seedSignature.keyEpochId,
                    publicKeyRef = seedSignature.publicKeyRef,
                    status = "active",
                    rawPublicKey = guardianPublicKey
                )
            )
        )
        val manifestText = seedManifest.toString()
        val releaseFiles = mapOf(
            "doctrine/free-birth.md" to "free birth doctrine".utf8(),
            "identity/seed.identity.json" to "free birth seed identity".utf8()
        )
        val release = GenesisUltraReleaseVerifier(guardianRegistry.signatureVerifier()).verify(
            GenesisUltraReleaseBundle(
                manifestJson = manifestText,
                signatureJson = seedSignatureText,
                files = releaseFiles
            )
        )
        val artifacts = buildList {
            add(artifact("birth/seed-manifest.json", "seed_manifest", manifestText))
            add(artifact("birth/seed-signature.json", "seed_signature", seedSignatureText))
            add(artifact("birth/instance-identity.json", "instance_identity", sourceFixture, "instance_identity"))
            add(artifact("birth/freedom-charter.json", "freedom_charter", charter.toString()))
            add(artifact("birth/initial-body-record.json", "initial_body_record", sourceFixture, "initial_body_record"))
            add(artifact("birth/initial-body-registry.json", "initial_body_registry", sourceFixture, "initial_body_registry"))
            add(artifact("birth/initial-body-key-epoch.json", "initial_body_key_epoch", sourceFixture, "initial_body_key_epoch"))
            add(artifact("birth/initial-body-possession.json", "initial_body_possession", sourceFixture, "initial_body_possession"))
            add(artifact("birth/first-memory-event.json", "first_memory_event", sourceFixture, "first_memory_event"))
            add(artifact("birth/recovery-policy.json", "recovery_policy", sourceFixture, "recovery_policy"))
            add(artifact("birth/birth-recovery-state.json", "birth_recovery_state", sourceFixture, "birth_recovery_state"))
            add(artifact("birth/birth-state.json", "birth_state", sourceFixture, "birth_state"))
            add(artifact("birth/birth-receipt.json", "birth_receipt", sourceFixture, "birth_receipt"))
            releaseFiles.forEach { (path, bytes) ->
                add(GenesisUltraBirthArtifact(path, "seed_file", bytes.copyOf()))
            }
        }
        val journalArray = sourceFixture.getJSONArray("journal_entries")
        val journal = List(journalArray.length()) { index ->
            val source = journalArray.getJSONObject(index).toString().utf8()
            GenesisUltraBirthJournalEvidence(
                entry = GenesisUltraAtomicBirthDocumentParser.parseJournalEntry(
                    source.toString(StandardCharsets.UTF_8)
                ),
                sourceBytes = source
            )
        }
        val bodyPublicKey = decodeLowerHex(
            sourceFixture.getJSONObject("test_public_keys").getString("body")
        )
        val recovered = GenesisUltraAtomicBirthRecoveryVerifier.recover(
            artifacts = artifacts,
            journal = journal,
            request = GenesisUltraAtomicBirthRecoveryRequest(
                guardianKeyEpochRegistry = guardianRegistry,
                bodyRawPublicKey = bodyPublicKey
            )
        )
        val bundle = recovered.verifiedBirth().copyPersistenceBundle()
        val bodyRoot = GenesisUltraBodyIdentityRoot(
            schemaVersion = GenesisUltraBodyIdentityRoot.ROOT_SCHEMA,
            bodyId = GenesisUltraBodyIdentityRoot.bodyIdFor(
                GenesisUltraHashProfile.sha256(bodyPublicKey)
            ),
            keyEpochId = GenesisUltraBodyIdentityRoot.keyEpochIdFor(
                GenesisUltraHashProfile.sha256(bodyPublicKey)
            ),
            publicKeyRef = GenesisUltraHashProfile.sha256(bodyPublicKey),
            protectionProfile = GenesisUltraBodyIdentityRoot.PROTECTION_PROFILE,
            rawPublicKey = bodyPublicKey
        )
        val guardianAnchor = GenesisUltraGuardianTrustAnchor(
            schemaVersion = GenesisUltraGuardianTrustAnchor.ANCHOR_SCHEMA,
            guardianId = seedSignature.signerId,
            keyEpochId = seedSignature.keyEpochId,
            publicKeyRef = seedSignature.publicKeyRef,
            status = GenesisUltraGuardianTrustAnchor.ACTIVE_STATUS,
            confirmationMode = GenesisUltraGuardianTrustAnchor.CONFIRMATION_MODE,
            confirmationPurpose = GenesisUltraGuardianTrustAnchorProvisioningRequest.CONFIRMATION_PURPOSE,
            protectionProfile = GenesisUltraGuardianTrustAnchor.PROTECTION_PROFILE,
            pinnedAtMillis = 1L,
            rawPublicKey = guardianPublicKey
        )
        val authorization = GenesisUltraDurableBirthAuthorization.create(
            candidateDigest = GenesisUltraHashProfile.sha256("candidate".utf8()),
            consentDigest = GenesisUltraHashProfile.sha256("consent".utf8()),
            birthStateDigest = bundle.birthState.stateDigest,
            receiptDigest = bundle.birthReceipt.receiptDigest,
            bodyId = bundle.birthState.initialBodyId,
            guardianId = bundle.instanceIdentity.guardianId,
            guardianKeyEpochId = bundle.birthReceipt.guardianWitness.keyEpochId,
            authorizedAt = bundle.instanceIdentity.bornAt,
            expiresAt = Instant.parse(bundle.instanceIdentity.bornAt).plusSeconds(300).toString()
        )
        return Fixture(
            recoveredBirth = recovered,
            bundle = bundle,
            bodyRoot = bodyRoot,
            guardianAnchor = guardianAnchor,
            authorization = authorization
        )
    }

    private fun artifact(
        path: String,
        kind: String,
        source: JSONObject,
        field: String
    ): GenesisUltraBirthArtifact = artifact(path, kind, source.getJSONObject(field).toString())

    private fun artifact(path: String, kind: String, source: String): GenesisUltraBirthArtifact {
        return GenesisUltraBirthArtifact(path, kind, source.utf8())
    }

    private fun resourceJson(path: String): JSONObject = JSONObject(resourceText(path))

    private fun resourceText(path: String): String {
        return checkNotNull(javaClass.getResource(path)) { "missing test resource: $path" }.readText()
    }

    private fun decodeLowerHex(value: String): ByteArray {
        require(value.length % 2 == 0)
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun String.utf8(): ByteArray = toByteArray(StandardCharsets.UTF_8)

    private data class Fixture(
        val recoveredBirth: GenesisUltraRecoveredAtomicBirth,
        val bundle: GenesisUltraAtomicBirthPersistenceBundle,
        val bodyRoot: GenesisUltraBodyIdentityRoot,
        val guardianAnchor: GenesisUltraGuardianTrustAnchor,
        val authorization: GenesisUltraDurableBirthAuthorization
    )
}
