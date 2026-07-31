package com.morimil.app.data.genesis.ultra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalCognitiveMigrationReadPortTest {
    @Test
    fun planningAcceptsOnlyRecognizedMemoryNoteSchemas() {
        assertTrue(
            CanonicalCognitiveMigrationReadPort.isAllowedPlanningSource(
                event(noteSchema = LIVING_MEMORY_NOTE_SCHEMA)
            )
        )
        assertTrue(
            CanonicalCognitiveMigrationReadPort.isAllowedPlanningSource(
                event(noteSchema = LEGACY_MEMORY_NOTE_SCHEMA)
            )
        )
    }

    @Test
    fun planningRejectsMissingSemanticsAndUnknownNoteSchemas() {
        assertFalse(
            CanonicalCognitiveMigrationReadPort.isAllowedPlanningSource(
                event(memoryKind = null)
            )
        )
        assertFalse(
            CanonicalCognitiveMigrationReadPort.isAllowedPlanningSource(
                event(noteSchema = "unknown.note.v1")
            )
        )
    }

    @Test
    fun planningRejectsNoiseAndNonPayloadMetadata() {
        assertFalse(
            CanonicalCognitiveMigrationReadPort.isAllowedPlanningSource(
                event(memoryKind = "chat_noise")
            )
        )
        assertFalse(
            CanonicalCognitiveMigrationReadPort.isAllowedPlanningSource(
                CanonicalConsumerEvent.activationMetadataOnly(event().ref)
            )
        )
    }

    @Test
    fun planningRejectsEveryProtocolMarkerEvenWithRecognizedMemorySemantics() {
        val protocolEvents = listOf(
            event(eventType = "cognitive_migration.executed"),
            event(actor = "cognitive_migration_protocol"),
            event(source = "cross_database_operations"),
            event(classification = "durable_cognitive_migration_transition"),
            event(noteSchema = "morimil.cross_database_operation.canonical_commit.v1")
        )
        protocolEvents.forEach { candidate ->
            assertFalse(
                CanonicalCognitiveMigrationReadPort.isAllowedPlanningSource(candidate)
            )
        }
    }

    @Test
    fun planningOrderIsConfirmedThenImportanceThenConfidenceThenSequence() {
        val selected = CanonicalCognitiveMigrationReadPort.selectPlanningSources(
            listOf(
                event(sequence = 10, userConfirmed = false, importance = 100, confidence = 100),
                event(sequence = 11, userConfirmed = true, importance = 80, confidence = 100),
                event(sequence = 12, userConfirmed = true, importance = 90, confidence = 10),
                event(sequence = 13, userConfirmed = true, importance = 90, confidence = 90),
                event(sequence = 14, userConfirmed = true, importance = 90, confidence = 90)
            )
        )
        assertEquals(listOf(14L, 13L, 12L, 11L, 10L), selected.map { it.sequence })
    }

    @Test
    fun planningLimitAndDigestDependOnlyOnSelectedCanonicalDescriptors() {
        val eligible = (1L..18L).map { sequence -> event(sequence = sequence) }
        val selected = CanonicalCognitiveMigrationReadPort.selectPlanningSources(eligible)
        assertEquals(16, selected.size)
        assertEquals((18L downTo 3L).toList(), selected.map { it.sequence })

        val digest = CanonicalCognitiveMigrationReadPort.sourceSetDigest(
            instanceId = "instance-test",
            sources = selected
        )
        val withExcluded = CanonicalCognitiveMigrationReadPort.selectPlanningSources(
            eligible + listOf(
                event(sequence = 98, noteSchema = "unknown.note.v1"),
                event(sequence = 99, actor = "cognitive_migration_protocol")
            )
        )
        assertEquals(
            digest,
            CanonicalCognitiveMigrationReadPort.sourceSetDigest(
                instanceId = "instance-test",
                sources = withExcluded
            )
        )
        assertEquals(
            digest,
            CanonicalCognitiveMigrationReadPort.sourceSetDigest(
                instanceId = "instance-test",
                sources = selected.reversed()
            )
        )
        assertNotEquals(
            digest,
            CanonicalCognitiveMigrationReadPort.sourceSetDigest(
                instanceId = "instance-test",
                sources = selected.toMutableList().apply {
                    this[0] = this[0].copy(eventHash = "evsha256:" + "9".repeat(64))
                }
            )
        )
        assertNotEquals(
            digest,
            CanonicalCognitiveMigrationReadPort.sourceSetDigest(
                instanceId = "other-instance",
                sources = selected
            )
        )
    }

    @Test
    fun recordSetDigestBindsFullCanonicalEventDescriptor() {
        val original = event(sequence = 7)
        val digest = CanonicalCognitiveMigrationReadPort.canonicalRecordSetDigest(
            listOf(original)
        )
        assertNotEquals(
            digest,
            CanonicalCognitiveMigrationReadPort.canonicalRecordSetDigest(
                listOf(event(sequence = 7, actor = "guardian"))
            )
        )
        assertNotEquals(
            digest,
            CanonicalCognitiveMigrationReadPort.canonicalRecordSetDigest(
                listOf(copyWithContentDigest(original, "sha256:" + "8".repeat(64)))
            )
        )
        assertNotEquals(
            digest,
            CanonicalCognitiveMigrationReadPort.canonicalRecordSetDigest(
                listOf(copyWithProvenanceDigest(original, "sha256:" + "9".repeat(64)))
            )
        )
    }

    @Test
    fun preSnapshotHashBindsIdentityWriterLineageAndRecordSet() {
        val original = snapshot()
        val digest = CanonicalCognitiveMigrationReadPort.canonicalPreSnapshotHash(original)
        assertNotEquals(
            digest,
            CanonicalCognitiveMigrationReadPort.canonicalPreSnapshotHash(
                original.copy(
                    writer = original.writer.copy(writerEpochId = "epoch-successor")
                )
            )
        )
        assertNotEquals(
            digest,
            CanonicalCognitiveMigrationReadPort.canonicalPreSnapshotHash(
                original.copy(
                    lineage = original.lineage.copy(
                        lastEventHash = "evsha256:" + "7".repeat(64)
                    )
                )
            )
        )
        assertNotEquals(
            digest,
            CanonicalCognitiveMigrationReadPort.canonicalPreSnapshotHash(
                original.copy(events = listOf(event(sequence = 3)))
            )
        )
    }

    private fun snapshot(): CanonicalConsumerSnapshot {
        return CanonicalConsumerSnapshot(
            identity = CanonicalInstanceRef(
                instanceId = "instance-test",
                companionName = "Morimil",
                identityDigest = "sha256:" + "1".repeat(64)
            ),
            writer = CanonicalWriterRef(
                writerBodyId = "body-test",
                writerEpochId = "epoch-test",
                writerEpochDigest = "sha256:" + "2".repeat(64),
                writerPublicKeyRef = "sha256:" + "3".repeat(64),
                registryEpoch = 1,
                registryDigest = "sha256:" + "4".repeat(64)
            ),
            lineage = CanonicalSnapshotRef(
                instanceId = "instance-test",
                birthRootEventHash = "evsha256:" + "0".repeat(64),
                birthRootSequence = 0,
                lastEventHash = "evsha256:" + "2".repeat(64),
                lastSequence = 2,
                postBirthEventCount = 2,
                snapshotDigest = "sha256:" + "5".repeat(64)
            ),
            events = listOf(event(sequence = 2))
        )
    }

    private fun copyWithContentDigest(
        event: CanonicalConsumerEvent,
        digest: String
    ): CanonicalConsumerEvent {
        return rebuild(event, event.ref.copy(contentDigest = digest))
    }

    private fun copyWithProvenanceDigest(
        event: CanonicalConsumerEvent,
        digest: String
    ): CanonicalConsumerEvent {
        return rebuild(event, event.ref.copy(provenanceDigest = digest))
    }

    private fun rebuild(
        event: CanonicalConsumerEvent,
        ref: CanonicalEventRef
    ): CanonicalConsumerEvent {
        return CanonicalConsumerEvent.verified(
            ref = ref,
            content = requireNotNull(event.content),
            provenance = requireNotNull(event.provenance),
            semantics = event.semantics,
            contentBytes = requireNotNull(event.copyContentBytes()),
            provenanceBytes = requireNotNull(event.copyProvenanceBytes())
        )
    }

    private fun event(
        memoryKind: String? = "decision",
        eventType: String = "memory.user_confirmed",
        actor: String = "user",
        source: String = "living_memory",
        classification: String = "living_memory",
        noteSchema: String = LIVING_MEMORY_NOTE_SCHEMA,
        sequence: Long = 2,
        importance: Int = 90,
        confidence: Int = 90,
        userConfirmed: Boolean = true
    ): CanonicalConsumerEvent {
        val content = "verified canonical memory"
        val provenanceJson = """{"schema":"$noteSchema"}"""
        val hashDigit = (sequence % 10).toString()
        return CanonicalConsumerEvent.verified(
            ref = CanonicalEventRef(
                eventId = "event-$sequence",
                eventHash = "evsha256:" + hashDigit.repeat(64),
                sequence = sequence,
                previousEventHash = "evsha256:" + "2".repeat(64),
                instanceId = "instance-test",
                bodyId = "body-test",
                signerId = "body-test",
                signerEpochId = "epoch-test",
                signerPublicKeyRef = "sha256:" + "3".repeat(64),
                eventType = eventType,
                actor = actor,
                observedAt = "2026-07-30T00:00:00Z",
                contentDigest = "sha256:" + "4".repeat(64),
                contentType = "text/plain",
                provenanceDigest = "sha256:" + "5".repeat(64),
                privacy = "private_local"
            ),
            content = content,
            provenance = CanonicalEventProvenance(
                schema = "morimil.canonical_memory.provenance.v1",
                instanceId = "instance-test",
                bodyId = "body-test",
                source = source,
                classification = classification,
                userConfirmed = userConfirmed,
                sourceId = "source-test",
                noteSchema = noteSchema,
                noteJson = provenanceJson
            ),
            semantics = memoryKind?.let {
                CanonicalMemorySemantics(
                    memoryKind = it,
                    importance = importance,
                    confidence = confidence,
                    userConfirmed = userConfirmed
                )
            },
            contentBytes = content.toByteArray(),
            provenanceBytes = provenanceJson.toByteArray()
        )
    }

    private companion object {
        const val LIVING_MEMORY_NOTE_SCHEMA = "morimil.living_memory_write.v1"
        const val LEGACY_MEMORY_NOTE_SCHEMA = "morimil.legacy_memory_import.v1"
    }
}
