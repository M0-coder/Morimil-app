package com.morimil.app.data.genesis.ultra

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
            add(artifact("birth/instance-identity.json", "instance_identity", fixture, "instance_identity"))
            add(
                artifact(
                    "birth/freedom-charter.json",
                    "freedom_charter",
                    vectors.getJSONObject("charter").toString()
                )
            )
            add(artifact("birth/initial-body-record.json", "initial_body_record", fixture, "initial_body_record"))
            add(
                artifact(
                    "birth/initial-body-registry.json",
                    "initial_body_registry",
                    fixture,
                    "initial_body_registry"
                )
            )
            add(
                artifact(
                    "birth/initial-body-key-epoch.json",
                    "initial_body_key_epoch",
                    fixture,
                    "initial_body_key_epoch"
                )
            )
            add(
                artifact(
                    "birth/initial-body-possession.json",
                    "initial_body_possession",
                    fixture,
                    "initial_body_possession"
                )
            )
            add(
                artifact(
                    "birth/first-memory-event.json",
                    "first_memory_event",
                    fixture,
                    "first_memory_event"
                )
            )
            add(artifact("birth/recovery-policy.json", "recovery_policy", fixture, "recovery_policy"))
            add(
                artifact(
                    "birth/birth-recovery-state.json",
                    "birth_recovery_state",
                    fixture,
                    "birth_recovery_state"
                )
            )
            add(artifact("birth/birth-state.json", "birth_state", fixture, "birth_state"))
            add(artifact("birth/birth-receipt.json", "birth_receipt", fixture, "birth_receipt"))
            releaseFiles.forEach { (path, bytes) ->
                add(GenesisUltraBirthArtifact(path, "seed_file", bytes.copyOf()))
            }
        }
        val journalArray = fixture.getJSONArray("journal_entries")
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
            fixture.getJSONObject("test_public_keys").getString("body")
        )
        val evaluatedAt = fixture.getJSONObject("instance_identity").getString("born_at")
        val evidenceRequest = GenesisUltraAtomicBirthEvidenceRequest(
            release = release,
            guardianKeyEpochRegistry = guardianRegistry,
            bodyRawPublicKey = bodyPublicKey,
            artifacts = artifacts,
            journal = journal,
            evaluatedAt = evaluatedAt
        )
        val verifiedBirth = GenesisUltraAtomicBirthEvidenceVerifier.verify(evidenceRequest)

        val identity = GenesisUltraContractParser.parseInstanceIdentity(
            artifactText(artifacts, "instance_identity")
        )
        val bodyRecord = GenesisUltraContractParser.parseBodyRecord(
            artifactText(artifacts, "initial_body_record")
        )
        val bodyRegistry = GenesisUltraContractParser.parseBodyRegistry(
            artifactText(artifacts, "initial_body_registry")
        )
        val keyEpoch = GenesisUltraContractParser.parseKeyEpoch(
            artifactText(artifacts, "initial_body_key_epoch")
        )
        val proof = GenesisUltraBodyPossessionProofParser.parse(
            artifactText(artifacts, "initial_body_possession")
        )
        val verifiedPossession = GenesisUltraBodyPossessionVerifier().verify(
            proof = proof,
            keyEpoch = keyEpoch,
            rawPublicKey = bodyPublicKey,
            evaluatedAt = evaluatedAt
        )
        val candidateModel = GenesisUltraBirthCandidate(
            release = release,
            guardianKeyEpochRegistry = guardianRegistry,
            instanceIdentity = identity,
            bodyRecord = bodyRecord,
            bodyRegistry = bodyRegistry,
            keyEpochs = listOf(keyEpoch),
            bodyPossession = verifiedPossession
        )
        val assessment = GenesisUltraBirthCandidateValidator.assess(candidateModel, evaluatedAt)
        val candidate = GenesisUltraConstructedBirthCandidate(
            candidate = candidateModel,
            assessment = assessment,
            candidateDigest = digest("official-candidate"),
            evaluatedAt = evaluatedAt
        )
        val consentedAt = evaluatedAt
        val expiresAt = minOf(
            Instant.parse(evaluatedAt).plusSeconds(60),
            Instant.parse(proof.expiresAt)
        ).toString()
        val consent = consent(
            candidate = candidate,
            consentedAt = consentedAt,
            expiresAt = expiresAt
        )

        return Fixture(candidate, consent, verifiedBirth, evaluatedAt)
    }

    private fun consent(
        candidate: GenesisUltraConstructedBirthCandidate,
        consentedAt: String,
        expiresAt: String
    ): GenesisUltraVerifiedHostBirthConsent {
        val model = candidate.candidate
        val consentId = "consent_" + digest("consent-id").removePrefix("sha256:")
        val fields = listOf(
            GenesisUltraVerifiedHostBirthConsent.CONSENT_SCHEMA,
            consentId,
            candidate.candidateDigest,
            model.instanceIdentity.instanceId,
            model.instanceIdentity.companionName,
            model.release.verifiedRootHash,
            model.bodyRecord.bodyId,
            model.release.signature.signerId,
            model.release.signature.keyEpochId,
            GenesisUltraHostBirthConsentRequest.APPROVE_DECISION,
            GenesisUltraHostBirthConsentRequest.INTERACTIVE_CONFIRMATION_MODE,
            GenesisUltraHostBirthConsentRequest.BIRTH_CONFIRMATION_PURPOSE,
            consentedAt,
            expiresAt,
            GenesisUltraVerifiedHostBirthConsent.PROTECTION_PROFILE
        )
        val consentDigest = GenesisUltraHashProfile.hashFields(
            GenesisUltraVerifiedHostBirthConsent.CONSENT_DIGEST_DOMAIN,
            fields
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

    private fun artifactText(
        artifacts: List<GenesisUltraBirthArtifact>,
        kind: String
    ): String = artifacts.single { artifact -> artifact.artifactKind == kind }
        .payload.toString(StandardCharsets.UTF_8)

    private fun artifact(
        path: String,
        kind: String,
        source: JSONObject,
        field: String
    ): GenesisUltraBirthArtifact = artifact(path, kind, source.getJSONObject(field).toString())

    private fun artifact(path: String, kind: String, json: String): GenesisUltraBirthArtifact {
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
}
