package com.morimil.app.data.genesis.ultra

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.time.Instant

class GenesisUltraAtomicBirthAuthorizationTest {
    @Test
    fun issuesAuthorizationOnlyForExactCandidateConsentAndVerifiedEvidence() {
        val fixture = fixture()

        val authorization = GenesisUltraAuthorizedAtomicBirth.verifyAndIssue(
            candidate = fixture.candidate,
            consent = fixture.consent,
            verifiedBirth = fixture.verifiedBirth,
            evaluatedAt = fixture.evaluatedAt
        )

        assertTrue(authorization.birthCommitAuthorized)
        assertEquals(fixture.candidate.candidateDigest, authorization.candidateDigest)
        assertEquals(fixture.consent.consentDigest, authorization.consentDigest)
        assertEquals(
            fixture.verifiedBirth.copyPersistenceBundle().birthState.stateDigest,
            authorization.birthStateDigest
        )
        authorization.requireUsableAt(fixture.evaluatedAt)
    }

    @Test
    fun rejectsConsentBoundToAnotherCandidateDigest() {
        val fixture = fixture()
        val changedCandidate = GenesisUltraConstructedBirthCandidate(
            candidate = fixture.candidate.candidate,
            assessment = fixture.candidate.assessment,
            candidateDigest = digest("different-candidate"),
            evaluatedAt = fixture.candidate.evaluatedAt
        )

        val failure = runCatching {
            GenesisUltraAuthorizedAtomicBirth.verifyAndIssue(
                candidate = changedCandidate,
                consent = fixture.consent,
                verifiedBirth = fixture.verifiedBirth,
                evaluatedAt = fixture.evaluatedAt
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(
            failure?.message.orEmpty().contains(
                "atomic_birth_authorization_consent_candidate_mismatch"
            )
        )
    }

    @Test
    fun activationPublicBoundaryRequiresAuthorizedTypeState() {
        val activationMethods = GenesisUltraAtomicBirthActivationCoordinator::class.java
            .declaredMethods
            .filter { method -> method.name == "activate" }

        assertEquals(1, activationMethods.size)
        val parameterTypes = activationMethods.single().parameterTypes.toList()
        assertEquals(GenesisUltraAuthorizedAtomicBirth::class.java, parameterTypes.first())
        assertFalse(parameterTypes.contains(GenesisUltraVerifiedAtomicBirth::class.java))
    }

    private fun fixture(): Fixture {
        val releaseFixture = verifiedReleaseFixture()
        val release = releaseFixture.release
        val guardianRegistry = releaseFixture.guardianRegistry
        val evaluatedAt = "2026-07-16T00:00:00Z"
        val expiresAt = Instant.parse(evaluatedAt).plusSeconds(600).toString()

        val rawBodyPublicKey = ByteArray(32) { index -> (index + 1).toByte() }
        val publicKeyRef = GenesisUltraHashProfile.sha256(rawBodyPublicKey)
        val instanceId = "inst_" + digest("canonical-instance").removePrefix("sha256:")
        val bodyId = "body_" + publicKeyRef.removePrefix("sha256:")
        val keyEpochId = "epoch_" + publicKeyRef.removePrefix("sha256:")

        val identityDraft = GenesisUltraInstanceIdentity(
            schemaVersion = "genesis.instance.identity.v0.1",
            instanceId = instanceId,
            seedId = release.manifest.seedId,
            seedRootHash = release.verifiedRootHash,
            companionName = "Genesis Libre",
            guardianId = release.signature.signerId,
            bornAt = evaluatedAt,
            identityDigest = ZERO_SHA256
        )
        val identity = identityDraft.copy(
            identityDigest = GenesisUltraHashProfile.instanceIdentityDigest(identityDraft)
        )
        val bodyRecord = GenesisUltraBodyRecord(
            schemaVersion = "genesis.body.record.v0.1",
            instanceId = instanceId,
            bodyId = bodyId,
            status = "active_writer",
            createdAt = evaluatedAt,
            platformProfile = "android-kotlin",
            publicKeyFingerprint = publicKeyRef,
            revokedAt = null,
            revocationReason = null
        )
        val registeredBody = GenesisUltraRegisteredBody(
            bodyId = bodyId,
            status = "active_writer",
            platformProfile = bodyRecord.platformProfile,
            publicKeyFingerprint = publicKeyRef,
            createdAt = evaluatedAt,
            lastSeenAt = null,
            revocationRef = null
        )
        val registryDraft = GenesisUltraBodyRegistry(
            schemaVersion = "genesis.body.registry.v0.1",
            instanceId = instanceId,
            registryEpoch = 0L,
            bodies = listOf(registeredBody),
            updatedAt = evaluatedAt,
            registryDigest = ZERO_SHA256
        )
        val registry = registryDraft.copy(
            registryDigest = GenesisUltraHashProfile.bodyRegistryDigest(registryDraft)
        )
        val epochDraft = GenesisUltraKeyEpoch(
            schemaVersion = "genesis.key.epoch.v0.1",
            keyEpochId = keyEpochId,
            instanceId = instanceId,
            bodyId = bodyId,
            epochNumber = 0L,
            publicKeyFingerprint = publicKeyRef,
            createdAt = evaluatedAt,
            status = "active",
            previousEpochId = null,
            rotationAuthorizationRef = null,
            epochDigest = ZERO_SHA256,
            signature = null
        )
        val epoch = epochDraft.copy(
            epochDigest = GenesisUltraHashProfile.keyEpochDigest(epochDraft)
        )
        val proofDraft = GenesisUltraBodyPossessionProof(
            schemaVersion = "genesis.body.possession.v0.1",
            proofId = "proof_" + digest("proof").removePrefix("sha256:"),
            instanceId = instanceId,
            bodyId = bodyId,
            challengeNonce = "nonce_" + digest("challenge").removePrefix("sha256:"),
            issuedAt = evaluatedAt,
            expiresAt = expiresAt,
            publicKeyFingerprint = publicKeyRef,
            proofDigest = ZERO_SHA256,
            signature = GenesisUltraBodyPossessionSignature(
                profile = "genesis.signature.ed25519.v0.1",
                keyEpochId = keyEpochId,
                value = "0".repeat(128)
            )
        )
        val proof = proofDraft.copy(
            proofDigest = GenesisUltraHashProfile.bodyPossessionDigest(proofDraft)
        )
        val verifiedPossession = testOnlyVerifiedPossession(proof, evaluatedAt)
        val candidateModel = GenesisUltraBirthCandidate(
            release = release,
            guardianKeyEpochRegistry = guardianRegistry,
            instanceIdentity = identity,
            bodyRecord = bodyRecord,
            bodyRegistry = registry,
            keyEpochs = listOf(epoch),
            bodyPossession = verifiedPossession
        )
        val assessment = GenesisUltraBirthCandidateValidator.assess(candidateModel, evaluatedAt)
        assertTrue(assessment.structurallyValid)
        assertFalse(assessment.birthReady)
        val candidate = GenesisUltraConstructedBirthCandidate(
            candidate = candidateModel,
            assessment = assessment,
            candidateDigest = digest("canonical-candidate"),
            evaluatedAt = evaluatedAt
        )
        val consent = consent(candidate, evaluatedAt, expiresAt)

        val birthStateDraft = GenesisUltraBirthState(
            schemaVersion = "genesis.birth.state.v0.1",
            birthId = "birth_" + digest("birth").removePrefix("sha256:"),
            instanceId = instanceId,
            seedId = release.manifest.seedId,
            seedRootHash = release.verifiedRootHash,
            identityDigest = identity.identityDigest,
            freedomCharterDigest = digest("freedom-charter"),
            initialBodyId = bodyId,
            initialBodyRegistryDigest = registry.registryDigest,
            initialBodyKeyEpochDigest = epoch.epochDigest,
            initialBodyPossessionDigest = proof.proofDigest,
            firstMemoryEventHash = "evsha256:" + digest("first-memory").removePrefix("sha256:"),
            recoveryStateDigest = digest("recovery-state"),
            bornAt = evaluatedAt,
            activeWriterCount = 1L,
            stateDigest = ZERO_SHA256
        )
        val birthState = birthStateDraft.copy(
            stateDigest = GenesisUltraAtomicBirthHashProfile.birthStateDigest(birthStateDraft)
        )
        val receiptDraft = GenesisUltraBirthReceipt(
            schemaVersion = "genesis.birth.receipt.v0.1",
            birthId = birthState.birthId,
            instanceId = instanceId,
            journalId = "journal_" + digest("journal").removePrefix("sha256:"),
            birthStateDigest = birthState.stateDigest,
            seedRootHash = birthState.seedRootHash,
            identityDigest = birthState.identityDigest,
            freedomCharterDigest = birthState.freedomCharterDigest,
            initialBodyRegistryDigest = birthState.initialBodyRegistryDigest,
            initialBodyKeyEpochDigest = birthState.initialBodyKeyEpochDigest,
            initialBodyPossessionDigest = birthState.initialBodyPossessionDigest,
            firstMemoryEventHash = birthState.firstMemoryEventHash,
            recoveryStateDigest = birthState.recoveryStateDigest,
            bornAt = evaluatedAt,
            birthStatus = "born",
            activeWriterBodyId = bodyId,
            activeWriterCount = 1L,
            guardianRole = "custodian_witness",
            ownershipConferred = false,
            receiptDigest = ZERO_SHA256,
            bodyAcknowledgement = placeholderEnvelope(
                signerType = "body",
                signerId = bodyId,
                keyEpochId = keyEpochId,
                publicKeyRef = publicKeyRef
            ),
            guardianWitness = placeholderEnvelope(
                signerType = "guardian",
                signerId = release.signature.signerId,
                keyEpochId = release.signature.keyEpochId,
                publicKeyRef = release.signature.publicKeyRef
            )
        )
        val receipt = receiptDraft.copy(
            receiptDigest = GenesisUltraAtomicBirthHashProfile.birthReceiptDigest(receiptDraft)
        )
        val persistence = GenesisUltraAtomicBirthPersistenceBundle(
            seedManifest = release.manifest,
            instanceIdentity = identity,
            birthState = birthState,
            birthReceipt = receipt,
            artifacts = listOf(
                artifact("birth/body-record.json", "initial_body_record", bodyRecordJson(bodyRecord)),
                artifact("birth/body-registry.json", "initial_body_registry", bodyRegistryJson(registry)),
                artifact("birth/key-epoch.json", "initial_body_key_epoch", keyEpochJson(epoch)),
                artifact("birth/possession.json", "initial_body_possession", possessionJson(proof))
            ),
            journal = emptyList()
        )
        val verifiedBirth = testOnlyVerifiedBirth(persistence)

        return Fixture(candidate, consent, verifiedBirth, evaluatedAt)
    }

    private fun verifiedReleaseFixture(): ReleaseFixture {
        val vectors = resourceJson("/genesis-ultra/atomic_birth_conformance.json")
        val fixture = vectors.getJSONObject("fixture")
        val seedManifest = fixture.getJSONObject("seed_manifest")
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
        val releaseFiles = mapOf(
            "doctrine/free-birth.md" to "free birth doctrine".utf8(),
            "identity/seed.identity.json" to "free birth seed identity".utf8()
        )
        val release = GenesisUltraReleaseVerifier(guardianRegistry.signatureVerifier()).verify(
            GenesisUltraReleaseBundle(
                manifestJson = seedManifest.toString(),
                signatureJson = seedSignatureText,
                files = releaseFiles
            )
        )
        return ReleaseFixture(release, guardianRegistry)
    }

    private fun consent(
        candidate: GenesisUltraConstructedBirthCandidate,
        consentedAt: String,
        expiresAt: String
    ): GenesisUltraVerifiedHostBirthConsent {
        val model = candidate.candidate
        val consentId = "consent_" + digest("consent-id").removePrefix("sha256:")
        val consentDigest = GenesisUltraVerifiedHostBirthConsent.digestForFields(
            schemaVersion = GenesisUltraVerifiedHostBirthConsent.CONSENT_SCHEMA,
            consentId = consentId,
            candidateDigest = candidate.candidateDigest,
            instanceId = model.instanceIdentity.instanceId,
            companionName = model.instanceIdentity.companionName,
            seedRootHash = model.release.verifiedRootHash,
            bodyId = model.bodyRecord.bodyId,
            guardianId = model.release.signature.signerId,
            guardianKeyEpochId = model.release.signature.keyEpochId,
            decision = GenesisUltraHostBirthConsentRequest.APPROVE_DECISION,
            confirmationMode = GenesisUltraHostBirthConsentRequest.INTERACTIVE_CONFIRMATION_MODE,
            confirmationPurpose = GenesisUltraHostBirthConsentRequest.BIRTH_CONFIRMATION_PURPOSE,
            consentedAt = consentedAt,
            expiresAt = expiresAt,
            protectionProfile = GenesisUltraVerifiedHostBirthConsent.PROTECTION_PROFILE
        )
        return GenesisUltraVerifiedHostBirthConsent(
            schemaVersion = GenesisUltraVerifiedHostBirthConsent.CONSENT_SCHEMA,
            consentId = consentId,
            candidateDigest = candidate.candidateDigest,
            instanceId = model.instanceIdentity.instanceId,
            companionName = model.instanceIdentity.companionName,
            seedRootHash = model.release.verifiedRootHash,
            bodyId = model.bodyRecord.bodyId,
            guardianId = model.release.signature.signerId,
            guardianKeyEpochId = model.release.signature.keyEpochId,
            decision = GenesisUltraHostBirthConsentRequest.APPROVE_DECISION,
            confirmationMode = GenesisUltraHostBirthConsentRequest.INTERACTIVE_CONFIRMATION_MODE,
            confirmationPurpose = GenesisUltraHostBirthConsentRequest.BIRTH_CONFIRMATION_PURPOSE,
            consentedAt = consentedAt,
            expiresAt = expiresAt,
            protectionProfile = GenesisUltraVerifiedHostBirthConsent.PROTECTION_PROFILE,
            consentDigest = consentDigest
        )
    }

    private fun testOnlyVerifiedPossession(
        proof: GenesisUltraBodyPossessionProof,
        verifiedAt: String
    ): GenesisUltraVerifiedBodyPossession {
        val constructor = GenesisUltraVerifiedBodyPossession::class.java.getDeclaredConstructor(
            GenesisUltraBodyPossessionProof::class.java,
            String::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(proof, verifiedAt)
    }

    private fun testOnlyVerifiedBirth(
        persistence: GenesisUltraAtomicBirthPersistenceBundle
    ): GenesisUltraVerifiedAtomicBirth {
        val constructor = GenesisUltraVerifiedAtomicBirth::class.java.getDeclaredConstructor(
            GenesisUltraAtomicBirthPersistenceBundle::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(persistence)
    }

    private fun placeholderEnvelope(
        signerType: String,
        signerId: String,
        keyEpochId: String,
        publicKeyRef: String
    ): GenesisUltraSignatureEnvelope {
        return GenesisUltraSignatureEnvelope(
            schemaVersion = "genesis.signature.envelope.v0.1",
            signatureProfile = "genesis.signature.ed25519.v0.1",
            signerType = signerType,
            signerId = signerId,
            keyEpochId = keyEpochId,
            signedDomain = "test.only",
            signedDigest = digest("placeholder-$signerType"),
            signatureValue = "0".repeat(128),
            createdAt = "2026-07-16T00:00:00Z",
            publicKeyRef = publicKeyRef
        )
    }

    private fun bodyRecordJson(body: GenesisUltraBodyRecord): String {
        return JSONObject()
            .put("schema_version", body.schemaVersion)
            .put("instance_id", body.instanceId)
            .put("body_id", body.bodyId)
            .put("status", body.status)
            .put("created_at", body.createdAt)
            .put("platform_profile", body.platformProfile)
            .put("public_key_fingerprint", body.publicKeyFingerprint)
            .put("revoked_at", JSONObject.NULL)
            .put("revocation_reason", JSONObject.NULL)
            .toString()
    }

    private fun bodyRegistryJson(registry: GenesisUltraBodyRegistry): String {
        val bodies = JSONArray()
        registry.bodies.forEach { body ->
            bodies.put(
                JSONObject()
                    .put("body_id", body.bodyId)
                    .put("status", body.status)
                    .put("platform_profile", body.platformProfile)
                    .put("public_key_fingerprint", body.publicKeyFingerprint)
                    .put("created_at", body.createdAt)
                    .put("last_seen_at", JSONObject.NULL)
                    .put("revocation_ref", JSONObject.NULL)
            )
        }
        return JSONObject()
            .put("schema_version", registry.schemaVersion)
            .put("instance_id", registry.instanceId)
            .put("registry_epoch", registry.registryEpoch)
            .put("bodies", bodies)
            .put("updated_at", registry.updatedAt)
            .put("registry_digest", registry.registryDigest)
            .toString()
    }

    private fun keyEpochJson(epoch: GenesisUltraKeyEpoch): String {
        return JSONObject()
            .put("schema_version", epoch.schemaVersion)
            .put("key_epoch_id", epoch.keyEpochId)
            .put("instance_id", epoch.instanceId)
            .put("body_id", epoch.bodyId)
            .put("epoch_number", epoch.epochNumber)
            .put("public_key_fingerprint", epoch.publicKeyFingerprint)
            .put("created_at", epoch.createdAt)
            .put("status", epoch.status)
            .put("previous_epoch_id", JSONObject.NULL)
            .put("rotation_authorization_ref", JSONObject.NULL)
            .put("epoch_digest", epoch.epochDigest)
            .put("signature", JSONObject.NULL)
            .toString()
    }

    private fun possessionJson(proof: GenesisUltraBodyPossessionProof): String {
        return JSONObject()
            .put("schema_version", proof.schemaVersion)
            .put("proof_id", proof.proofId)
            .put("instance_id", proof.instanceId)
            .put("body_id", proof.bodyId)
            .put("challenge_nonce", proof.challengeNonce)
            .put("issued_at", proof.issuedAt)
            .put("expires_at", proof.expiresAt)
            .put("public_key_fingerprint", proof.publicKeyFingerprint)
            .put("proof_digest", proof.proofDigest)
            .put(
                "signature",
                JSONObject()
                    .put("profile", proof.signature.profile)
                    .put("key_epoch_id", proof.signature.keyEpochId)
                    .put("value", proof.signature.value)
            )
            .toString()
    }

    private fun artifact(
        path: String,
        kind: String,
        json: String
    ): GenesisUltraBirthArtifact {
        return GenesisUltraBirthArtifact(path, kind, json.utf8())
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

    private fun digest(value: String): String = GenesisUltraHashProfile.sha256(value.utf8())

    private fun String.utf8(): ByteArray = toByteArray(StandardCharsets.UTF_8)

    private data class Fixture(
        val candidate: GenesisUltraConstructedBirthCandidate,
        val consent: GenesisUltraVerifiedHostBirthConsent,
        val verifiedBirth: GenesisUltraVerifiedAtomicBirth,
        val evaluatedAt: String
    )

    private data class ReleaseFixture(
        val release: GenesisUltraVerifiedRelease,
        val guardianRegistry: GenesisUltraTrustedGuardianKeyEpochRegistry
    )

    private companion object {
        const val ZERO_SHA256 =
            "sha256:0000000000000000000000000000000000000000000000000000000000000000"
    }
}
