package com.morimil.app.data.genesis.ultra

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GenesisUltraAtomicBirthWitnessArchiveReaderTest {
    @Test
    fun readsExactBoundWitnessPackageWithDefensiveCopies() {
        val fixture = archiveFixture()

        val witness = GenesisUltraAtomicBirthWitnessArchiveReader().read(
            input = ByteArrayInputStream(fixture.archive),
            expectedCandidateDigest = CANDIDATE_DIGEST,
            expectedConsentDigest = CONSENT_DIGEST
        )

        assertEquals(EVALUATED_AT, witness.evaluatedAt)
        assertEquals(
            GenesisUltraAtomicBirthPersistenceValidator.mandatoryArtifactKinds.size,
            witness.copyArtifacts().size
        )
        assertEquals(1, witness.copyJournal().size)

        val first = witness.copyArtifacts().first()
        val expected = first.payload.copyOf()
        first.payload[0] = (first.payload[0].toInt() xor 0x7f).toByte()
        assertEquals(expected.toList(), witness.copyArtifacts().first().payload.toList())
    }

    @Test
    fun rejectsCandidateOrConsentSubstitution() {
        val fixture = archiveFixture()
        val reader = GenesisUltraAtomicBirthWitnessArchiveReader()

        val candidateError = assertThrows(IllegalArgumentException::class.java) {
            reader.read(
                ByteArrayInputStream(fixture.archive),
                "sha256:" + "c".repeat(64),
                CONSENT_DIGEST
            )
        }
        assertEquals("witness_archive_candidate_digest_mismatch", candidateError.message)

        val consentError = assertThrows(IllegalArgumentException::class.java) {
            reader.read(
                ByteArrayInputStream(fixture.archive),
                CANDIDATE_DIGEST,
                "sha256:" + "d".repeat(64)
            )
        }
        assertEquals("witness_archive_consent_digest_mismatch", consentError.message)
    }

    @Test
    fun rejectsTraversalInDeclaredArtifactPath() {
        val fixture = archiveFixture(
            artifactPathOverride = "../escape.json"
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            GenesisUltraAtomicBirthWitnessArchiveReader().read(
                ByteArrayInputStream(fixture.archive),
                CANDIDATE_DIGEST,
                CONSENT_DIGEST
            )
        }

        assertEquals("invalid_relative_path", error.message)
    }

    @Test
    fun rejectsUnexpectedArchiveFile() {
        val fixture = archiveFixture(extraFiles = mapOf("unexpected.bin" to byteArrayOf(1, 2, 3)))

        val error = assertThrows(IllegalArgumentException::class.java) {
            GenesisUltraAtomicBirthWitnessArchiveReader().read(
                ByteArrayInputStream(fixture.archive),
                CANDIDATE_DIGEST,
                CONSENT_DIGEST
            )
        }

        assertTrue(error.message.orEmpty().startsWith("witness_archive_file_set_mismatch:"))
    }

    @Test
    fun rejectsArtifactBytesChangedAfterManifestDigest() {
        val fixture = archiveFixture(tamperFirstArtifact = true)

        val error = assertThrows(IllegalArgumentException::class.java) {
            GenesisUltraAtomicBirthWitnessArchiveReader().read(
                ByteArrayInputStream(fixture.archive),
                CANDIDATE_DIGEST,
                CONSENT_DIGEST
            )
        }

        assertTrue(error.message.orEmpty().startsWith("witness_archive_artifact_digest_mismatch:"))
    }

    @Test
    fun rejectsManifestWithDuplicateJsonKey() {
        val fixture = archiveFixture()
        val duplicateManifest = fixture.manifest.replaceFirst(
            "\"candidate_digest\":\"$CANDIDATE_DIGEST\"",
            "\"candidate_digest\":\"$CANDIDATE_DIGEST\",\"candidate_digest\":\"$CANDIDATE_DIGEST\""
        )
        val files = fixture.files.toMutableMap().apply {
            this[GenesisUltraAtomicBirthWitnessArchiveReader.MANIFEST_ENTRY] = duplicateManifest.toByteArray()
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            GenesisUltraAtomicBirthWitnessArchiveReader().read(
                ByteArrayInputStream(zip(files)),
                CANDIDATE_DIGEST,
                CONSENT_DIGEST
            )
        }

        assertEquals("invalid_strict_json", error.message)
    }

    private fun archiveFixture(
        artifactPathOverride: String? = null,
        extraFiles: Map<String, ByteArray> = emptyMap(),
        tamperFirstArtifact: Boolean = false
    ): Fixture {
        val artifacts = GenesisUltraAtomicBirthPersistenceValidator.mandatoryArtifactKinds
            .sorted()
            .mapIndexed { index, kind ->
                val path = if (index == 0 && artifactPathOverride != null) {
                    artifactPathOverride
                } else {
                    "artifacts/${index.toString().padStart(2, '0')}-$kind.json"
                }
                val bytes = "artifact:$kind\n".toByteArray()
                ArtifactFixture(path = path, kind = kind, bytes = bytes)
            }
        val journalPath = "journal/000-prepared.json"
        val journalBytes = journalBytes()

        val manifest = JSONObject()
            .put("schema_version", GenesisUltraAtomicBirthWitnessArchiveReader.MANIFEST_SCHEMA)
            .put("candidate_digest", CANDIDATE_DIGEST)
            .put("consent_digest", CONSENT_DIGEST)
            .put("evaluated_at", EVALUATED_AT)
            .put(
                "artifacts",
                JSONArray().apply {
                    artifacts.forEach { artifact ->
                        put(
                            JSONObject()
                                .put("path", artifact.path)
                                .put("kind", artifact.kind)
                                .put("digest", GenesisUltraHashProfile.sha256(artifact.bytes))
                        )
                    }
                }
            )
            .put(
                "journal",
                JSONArray().put(
                    JSONObject()
                        .put("path", journalPath)
                        .put("digest", GenesisUltraHashProfile.sha256(journalBytes))
                )
            )
            .toString()

        val files = linkedMapOf<String, ByteArray>()
        files[GenesisUltraAtomicBirthWitnessArchiveReader.MANIFEST_ENTRY] = manifest.toByteArray()
        artifacts.forEachIndexed { index, artifact ->
            files[artifact.path] = if (index == 0 && tamperFirstArtifact) {
                artifact.bytes + byteArrayOf(0x01)
            } else {
                artifact.bytes.copyOf()
            }
        }
        files[journalPath] = journalBytes
        files.putAll(extraFiles.mapValues { (_, bytes) -> bytes.copyOf() })

        return Fixture(
            archive = zip(files),
            manifest = manifest,
            files = files
        )
    }

    private fun journalBytes(): ByteArray {
        val signatureWithoutDigest = GenesisUltraSignatureEnvelope(
            schemaVersion = "genesis.signature.envelope.v0.1",
            signatureProfile = "genesis.signature.ed25519.v0.1",
            signerType = "body",
            signerId = BODY_ID,
            keyEpochId = BODY_EPOCH_ID,
            signedDomain = "genesis.transaction.journal.signature.v0.1",
            signedDigest = ZERO_SHA256,
            signatureValue = "0".repeat(128),
            createdAt = EVALUATED_AT,
            publicKeyRef = BODY_KEY_REF
        )
        val withoutDigest = GenesisUltraBirthJournalEntry(
            schemaVersion = "genesis.transaction.journal.v0.1",
            journalId = JOURNAL_ID,
            sequence = 0L,
            previousJournalDigest = "GENESIS",
            operationKind = "birth",
            operationId = BIRTH_ID,
            instanceId = INSTANCE_ID,
            coordinatorBodyId = BODY_ID,
            phase = "prepared",
            status = "complete",
            previousStateDigest = PREVIOUS_STATE_DIGEST,
            candidateStateDigest = null,
            finalizationDigest = null,
            commitMarkerDigest = null,
            updatedAt = EVALUATED_AT,
            journalDigest = ZERO_SHA256,
            signature = signatureWithoutDigest
        )
        val digest = GenesisUltraAtomicBirthHashProfile.journalDigest(withoutDigest)
        val entry = withoutDigest.copy(
            journalDigest = digest,
            signature = signatureWithoutDigest.copy(signedDigest = digest)
        )

        return JSONObject()
            .put("schema_version", entry.schemaVersion)
            .put("journal_id", entry.journalId)
            .put("sequence", entry.sequence)
            .put("previous_journal_digest", entry.previousJournalDigest)
            .put("operation_kind", entry.operationKind)
            .put("operation_id", entry.operationId)
            .put("instance_id", entry.instanceId)
            .put("coordinator_body_id", entry.coordinatorBodyId)
            .put("phase", entry.phase)
            .put("status", entry.status)
            .put("previous_state_digest", entry.previousStateDigest)
            .put("candidate_state_digest", JSONObject.NULL)
            .put("finalization_digest", JSONObject.NULL)
            .put("commit_marker_digest", JSONObject.NULL)
            .put("updated_at", entry.updatedAt)
            .put("journal_digest", entry.journalDigest)
            .put("signature", signatureJson(entry.signature))
            .toString()
            .toByteArray()
    }

    private fun signatureJson(signature: GenesisUltraSignatureEnvelope): JSONObject {
        return JSONObject()
            .put("schema_version", signature.schemaVersion)
            .put("signature_profile", signature.signatureProfile)
            .put("signer_type", signature.signerType)
            .put("signer_id", signature.signerId)
            .put("key_epoch_id", signature.keyEpochId)
            .put("signed_domain", signature.signedDomain)
            .put("signed_digest", signature.signedDigest)
            .put("signature_value", signature.signatureValue)
            .put("created_at", signature.createdAt)
            .put("public_key_ref", signature.publicKeyRef)
    }

    private fun zip(files: Map<String, ByteArray>): ByteArray {
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                files.forEach { (path, bytes) ->
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
    }

    private data class ArtifactFixture(val path: String, val kind: String, val bytes: ByteArray)
    private data class Fixture(
        val archive: ByteArray,
        val manifest: String,
        val files: Map<String, ByteArray>
    )

    private companion object {
        const val EVALUATED_AT = "2026-07-25T12:00:00Z"
        val CANDIDATE_DIGEST = "sha256:" + "a".repeat(64)
        val CONSENT_DIGEST = "sha256:" + "b".repeat(64)
        val ZERO_SHA256 = "sha256:" + "0".repeat(64)
        val PREVIOUS_STATE_DIGEST = "sha256:" + "1".repeat(64)
        val BODY_KEY_REF = "sha256:" + "2".repeat(64)
        val INSTANCE_ID = "inst_" + "3".repeat(64)
        val BODY_ID = "body_" + "4".repeat(64)
        val BODY_EPOCH_ID = "epoch_" + "5".repeat(64)
        val BIRTH_ID = "birth_" + "6".repeat(64)
        val JOURNAL_ID = "journal_" + "7".repeat(64)
    }
}
