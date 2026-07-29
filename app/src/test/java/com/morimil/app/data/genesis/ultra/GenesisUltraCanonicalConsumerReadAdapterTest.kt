package com.morimil.app.data.genesis.ultra

import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraCanonicalConsumerReadAdapterTest {
    @Test
    fun coherentSnapshotProducesReadyAndReadsBeforeSnapshotAfter() = runBlocking {
        val fixture = fixture()
        val calls = mutableListOf<String>()
        val port = GenesisUltraCanonicalConsumerReadAdapter.forTest(
            readIdentity = {
                calls += "identity"
                fixture.identity
            },
            readMemorySnapshot = {
                calls += "snapshot"
                fixture.snapshot
            }
        )

        val ready = ready(port.readVerifiedSnapshot())

        assertEquals(listOf("identity", "snapshot", "identity"), calls)
        assertEquals(fixture.identity.instanceId, ready.identity.instanceId)
        assertEquals(1, ready.events.size)
        assertEquals(CanonicalPayloadState.VERIFIED_PAYLOAD, ready.events.single().payloadState)
        assertTrue(EVENT_HASH.matches(ready.lineage.birthRootEventHash))
        assertTrue(EVENT_HASH.matches(ready.events.single().ref.eventHash))
        assertTrue(EVENT_HASH.matches(ready.events.single().ref.previousEventHash))
    }

    @Test
    fun absentBirthReturnsNotReadyWithoutReadingSnapshot() = runBlocking {
        var snapshotReads = 0
        val port = GenesisUltraCanonicalConsumerReadAdapter.forTest(
            readIdentity = { null },
            readMemorySnapshot = {
                snapshotReads += 1
                error("must_not_read_snapshot")
            }
        )

        val blocked = blocked(port.readVerifiedSnapshot())

        assertEquals(CanonicalReadFailureCode.BIRTH_NOT_COMMITTED, blocked.code)
        assertEquals(CanonicalReadDisposition.NOT_READY, blocked.disposition)
        assertEquals(0, snapshotReads)
    }

    @Test
    fun inconsistentIdentityReturnsBlockedWithoutReadingSnapshot() = runBlocking {
        var snapshotReads = 0
        val port = GenesisUltraCanonicalConsumerReadAdapter.forTest(
            readIdentity = { throw IllegalStateException("genesis_ultra_runtime_identity_inconsistent") },
            readMemorySnapshot = {
                snapshotReads += 1
                error("must_not_read_snapshot")
            }
        )

        val blocked = blocked(port.readVerifiedSnapshot())

        assertEquals(CanonicalReadFailureCode.IDENTITY_INCONSISTENT, blocked.code)
        assertEquals(CanonicalReadDisposition.BLOCKED, blocked.disposition)
        assertEquals(0, snapshotReads)
    }

    @Test
    fun foreignSnapshotReturnsBlocked() = runBlocking {
        val fixture = fixture()
        val foreign = fixture.snapshot.copy(instanceId = "ins_foreign")
        val port = port(fixture.identity, foreign)

        val blocked = blocked(port.readVerifiedSnapshot())

        assertEquals(CanonicalReadFailureCode.FOREIGN_INSTANCE, blocked.code)
        assertEquals(CanonicalReadDisposition.BLOCKED, blocked.disposition)
    }

    @Test
    fun writerChangeBetweenReadsIsRetryable() = runBlocking {
        val fixture = fixture()
        val changed = fixture.identity.copy(
            activeBody = fixture.identity.activeBody.copy(
                keyEpochId = "epoch_changed",
                keyEpochDigest = digest('9')
            )
        )
        var identityReads = 0
        val port = GenesisUltraCanonicalConsumerReadAdapter.forTest(
            readIdentity = {
                identityReads += 1
                if (identityReads == 1) fixture.identity else changed
            },
            readMemorySnapshot = { fixture.snapshot }
        )

        val blocked = blocked(port.readVerifiedSnapshot())

        assertEquals(CanonicalReadFailureCode.SNAPSHOT_CHANGED_DURING_READ, blocked.code)
        assertEquals(CanonicalReadDisposition.RETRYABLE, blocked.disposition)
    }

    @Test
    fun duplicateSequenceBlocksTheWholeSnapshot() = runBlocking {
        val fixture = fixture()
        val first = fixture.snapshot.records.single()
        val second = regularRecord(
            root = fixture.snapshot.birthRoot,
            identity = fixture.identity,
            sequence = first.event.sequence,
            previousEventHash = first.event.eventHash,
            eventHash = eventHash('8')
        )
        val snapshot = fixture.snapshot.copy(records = listOf(first, second))

        val result = port(fixture.identity, snapshot).readVerifiedSnapshot()

        val blocked = blocked(result)
        assertEquals(CanonicalReadFailureCode.CHAIN_CORRUPT, blocked.code)
        assertFalse(result is CanonicalReadResult.Ready)
    }

    @Test
    fun payloadMissingOutsideActivationIsBlocked() = runBlocking {
        val fixture = fixture()
        val event = regularEvent(
            root = fixture.snapshot.birthRoot,
            sequence = 1L,
            previousEventHash = fixture.snapshot.birthRoot.eventHash,
            eventHash = eventHash('7'),
            contentDigest = digest('6'),
            provenanceDigest = digest('5')
        )
        val record = CanonicalMemoryRecord(
            event = event,
            contentBytes = null,
            provenanceBytes = null,
            provenanceType = "application/json"
        )
        val snapshot = fixture.snapshot.copy(records = listOf(record))

        val blocked = blocked(port(fixture.identity, snapshot).readVerifiedSnapshot())

        assertEquals(CanonicalReadFailureCode.PAYLOAD_MISSING, blocked.code)
    }

    @Test
    fun contentDigestMismatchIsBlocked() = runBlocking {
        val fixture = fixture()
        val valid = fixture.snapshot.records.single()
        val record = CanonicalMemoryRecord(
            event = valid.event,
            contentBytes = "tampered".toByteArray(StandardCharsets.UTF_8),
            provenanceBytes = valid.copyProvenanceBytes(),
            provenanceType = valid.provenanceType
        )
        val snapshot = fixture.snapshot.copy(records = listOf(record))

        val blocked = blocked(port(fixture.identity, snapshot).readVerifiedSnapshot())

        assertEquals(CanonicalReadFailureCode.PAYLOAD_INTEGRITY_INVALID, blocked.code)
    }

    @Test
    fun provenanceDigestMismatchIsBlocked() = runBlocking {
        val fixture = fixture()
        val valid = fixture.snapshot.records.single()
        val record = CanonicalMemoryRecord(
            event = valid.event,
            contentBytes = valid.copyContentBytes(),
            provenanceBytes = "{}".toByteArray(StandardCharsets.UTF_8),
            provenanceType = valid.provenanceType
        )
        val snapshot = fixture.snapshot.copy(records = listOf(record))

        val blocked = blocked(port(fixture.identity, snapshot).readVerifiedSnapshot())

        assertEquals(CanonicalReadFailureCode.PROVENANCE_UNVERIFIABLE, blocked.code)
    }

    @Test
    fun exactActivationMetadataOnlyIsValidButExcludedFromSelections() = runBlocking {
        val root = firstMemoryEvent()
        val identity = identityFor(root)
        val activation = regularEvent(
            root = root,
            sequence = 1L,
            previousEventHash = root.eventHash,
            eventHash = eventHash('4'),
            eventType = "instance.activation.confirmed",
            actor = "host_confirmed_system",
            contentType = "application/vnd.genesis.atomic-birth-authorization+json",
            contentDigest = identity.authorization.authorizationDigest,
            provenanceDigest = identity.authorization.receiptDigest
        )
        val snapshot = CanonicalMemorySnapshot(
            instanceId = identity.instanceId,
            companionName = identity.companionName,
            birthRoot = root,
            records = listOf(
                CanonicalMemoryRecord(
                    event = activation,
                    contentBytes = null,
                    provenanceBytes = null,
                    provenanceType = "metadata-only"
                )
            )
        )
        val port = port(identity, snapshot)

        val ready = ready(port.readVerifiedSnapshot())
        val recalls = ready(port.readRecallCandidates())
        val rest = ready(port.readRestCyclePlanningInput())

        assertEquals(CanonicalPayloadState.ACTIVATION_METADATA_ONLY, ready.events.single().payloadState)
        assertNull(ready.events.single().content)
        assertTrue(recalls.candidates.isEmpty())
        assertTrue(rest.sources.isEmpty())
        assertEquals(activation.eventHash, ready.lineage.lastEventHash)
        assertTrue(EVENT_HASH.matches(ready.lineage.lastEventHash))
    }

    @Test
    fun emptyCanonicalStreamUsesBirthRootWithoutArtificialEvent() = runBlocking {
        val root = firstMemoryEvent()
        val identity = identityFor(root)
        val snapshot = CanonicalMemorySnapshot(
            instanceId = identity.instanceId,
            companionName = identity.companionName,
            birthRoot = root,
            records = emptyList()
        )

        val ready = ready(port(identity, snapshot).readVerifiedSnapshot())

        assertTrue(ready.events.isEmpty())
        assertEquals(root.sequence, ready.lineage.lastSequence)
        assertEquals(root.eventHash, ready.lineage.lastEventHash)
        assertEquals(0, ready.lineage.postBirthEventCount)
        assertTrue(EVENT_HASH.matches(ready.lineage.birthRootEventHash))
        assertTrue(EVENT_HASH.matches(ready.lineage.lastEventHash))
    }

    @Test
    fun invalidLimitsFailBeforeAnyAuthorityRead() {
        var reads = 0
        val port = GenesisUltraCanonicalConsumerReadAdapter.forTest(
            readIdentity = {
                reads += 1
                error("must_not_read")
            },
            readMemorySnapshot = {
                reads += 1
                error("must_not_read")
            }
        )

        assertIllegalArgument { runBlocking { port.readRecallCandidates(0) } }
        assertIllegalArgument { runBlocking { port.readRestCyclePlanningInput(81) } }
        assertIllegalArgument { runBlocking { port.readHealthInput(-1) } }
        assertEquals(0, reads)
    }

    @Test
    fun projectedBytesAreDefensiveCopies() = runBlocking {
        val fixture = fixture()
        val ready = ready(port(fixture.identity, fixture.snapshot).readVerifiedSnapshot())
        val event = ready.events.single()
        val originalContent = requireNotNull(event.copyContentBytes())
        val originalProvenance = requireNotNull(event.copyProvenanceBytes())
        val mutatedContent = requireNotNull(event.copyContentBytes())
        val mutatedProvenance = requireNotNull(event.copyProvenanceBytes())

        mutatedContent[0] = (mutatedContent[0].toInt() xor 1).toByte()
        mutatedProvenance[0] = (mutatedProvenance[0].toInt() xor 1).toByte()

        assertArrayEquals(originalContent, event.copyContentBytes())
        assertArrayEquals(originalProvenance, event.copyProvenanceBytes())
    }

    @Test
    fun digestStringsRemainPrefixedAndDeterministic() = runBlocking {
        val fixture = fixture()
        val first = ready(port(fixture.identity, fixture.snapshot).readVerifiedSnapshot())
        val second = ready(port(fixture.identity, fixture.snapshot).readVerifiedSnapshot())
        val event = first.events.single().ref

        assertTrue(EVENT_HASH.matches(first.lineage.birthRootEventHash))
        assertTrue(EVENT_HASH.matches(first.lineage.lastEventHash))
        assertTrue(EVENT_HASH.matches(event.eventHash))
        assertTrue(EVENT_HASH.matches(event.previousEventHash))
        assertTrue(DIGEST.matches(first.lineage.snapshotDigest))
        assertTrue(DIGEST.matches(event.contentDigest))
        assertTrue(DIGEST.matches(event.provenanceDigest))
        assertFalse(DIGEST.matches(event.eventHash))
        assertFalse(EVENT_HASH.matches(event.contentDigest))
        assertNotEquals(first.lineage.birthRootEventHash, first.lineage.snapshotDigest)
        assertEquals(first.lineage.snapshotDigest, second.lineage.snapshotDigest)
    }

    @Test
    fun laterCorruptRecordCannotProducePartialReady() = runBlocking {
        val fixture = fixture()
        val first = fixture.snapshot.records.single()
        val corrupt = regularRecord(
            root = fixture.snapshot.birthRoot,
            identity = fixture.identity,
            sequence = 2L,
            previousEventHash = first.event.eventHash,
            eventHash = eventHash('2')
        )
        val corruptPayload = CanonicalMemoryRecord(
            event = corrupt.event,
            contentBytes = "corrupt".toByteArray(StandardCharsets.UTF_8),
            provenanceBytes = corrupt.copyProvenanceBytes(),
            provenanceType = corrupt.provenanceType
        )
        val snapshot = fixture.snapshot.copy(records = listOf(first, corruptPayload))

        val result = port(fixture.identity, snapshot).readVerifiedSnapshot()

        assertTrue(result is CanonicalReadResult.Blocked)
        assertFalse(result is CanonicalReadResult.Ready)
    }

    @Test
    fun restCycleSourceSetIsDeterministicAndRecallUsesCompleteSemanticsOnly() = runBlocking {
        val fixture = fixture()
        val port = port(fixture.identity, fixture.snapshot)

        val firstRest = ready(port.readRestCyclePlanningInput())
        val secondRest = ready(port.readRestCyclePlanningInput())
        val recalls = ready(port.readRecallCandidates())

        assertEquals(firstRest.sourceSetDigest, secondRest.sourceSetDigest)
        assertTrue(DIGEST.matches(firstRest.sourceSetDigest))
        assertEquals(1, firstRest.sources.size)
        assertEquals(1, recalls.candidates.size)
        assertEquals(fixture.snapshot.records.single().event.eventHash, recalls.candidates.single().event.eventHash)
        assertTrue(EVENT_HASH.matches(firstRest.sources.single().event.eventHash))
        assertTrue(DIGEST.matches(firstRest.sources.single().event.contentDigest))
        assertTrue(DIGEST.matches(firstRest.sources.single().event.provenanceDigest))
    }

    @Test
    fun generalDigestPrefixIsRejectedForEventHash() = runBlocking {
        val fixture = fixture()
        val record = fixture.snapshot.records.single()
        setField(record.event, "eventHash", digest('9'))

        val blocked = blocked(port(fixture.identity, fixture.snapshot).readVerifiedSnapshot())

        assertEquals(CanonicalReadFailureCode.CHAIN_CORRUPT, blocked.code)
        assertTrue(blocked.diagnosticCode.contains("event_hash"))
    }

    @Test
    fun generalDigestPrefixIsRejectedForPreviousEventHash() = runBlocking {
        val fixture = fixture()
        val record = fixture.snapshot.records.single()
        setField(record.event, "previousEventHash", digest('8'))

        val blocked = blocked(port(fixture.identity, fixture.snapshot).readVerifiedSnapshot())

        assertEquals(CanonicalReadFailureCode.CHAIN_CORRUPT, blocked.code)
        assertTrue(blocked.diagnosticCode.contains("previous_event_hash"))
    }

    @Test
    fun eventHashPrefixIsRejectedForGeneralDigest() = runBlocking {
        val fixture = fixture()
        val record = fixture.snapshot.records.single()
        setField(record.event, "contentDigest", eventHash('7'))

        val blocked = blocked(port(fixture.identity, fixture.snapshot).readVerifiedSnapshot())

        assertEquals(CanonicalReadFailureCode.CHAIN_CORRUPT, blocked.code)
        assertTrue(blocked.diagnosticCode.contains("content_digest"))
    }

    private fun fixture(): Fixture {
        val root = firstMemoryEvent()
        val identity = identityFor(root)
        val record = regularRecord(
            root = root,
            identity = identity,
            sequence = 1L,
            previousEventHash = root.eventHash,
            eventHash = eventHash('3')
        )
        return Fixture(
            identity = identity,
            snapshot = CanonicalMemorySnapshot(
                instanceId = identity.instanceId,
                companionName = identity.companionName,
                birthRoot = root,
                records = listOf(record)
            )
        )
    }

    private fun regularRecord(
        root: GenesisUltraFirstMemoryEvent,
        identity: GenesisUltraRuntimeIdentity,
        sequence: Long,
        previousEventHash: String,
        eventHash: String
    ): CanonicalMemoryRecord {
        val contentBytes = "Verified canonical memory $sequence".toByteArray(StandardCharsets.UTF_8)
        val note = JSONObject()
            .put("schema", "morimil.living_memory_write.v1")
            .put("source", "unit_test")
            .put("importance", 80)
            .put(
                "evidence",
                JSONObject()
                    .put("memory_kind", "episodic")
                    .put("confidence", 90)
            )
            .toString()
        val provenanceBytes = JSONObject()
            .put("schema", "morimil.canonical_memory.provenance.v1")
            .put("instance_id", identity.instanceId)
            .put("body_id", identity.activeBody.bodyId)
            .put("source", "unit_test")
            .put("classification", "memory.test")
            .put("user_confirmed", true)
            .put("source_id", "source-$sequence")
            .put("note", note)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        val event = regularEvent(
            root = root,
            sequence = sequence,
            previousEventHash = previousEventHash,
            eventHash = eventHash,
            contentDigest = GenesisUltraHashProfile.sha256(contentBytes),
            provenanceDigest = GenesisUltraHashProfile.sha256(provenanceBytes)
        )
        return CanonicalMemoryRecord(
            event = event,
            contentBytes = contentBytes,
            provenanceBytes = provenanceBytes,
            provenanceType = "application/json"
        )
    }

    private fun regularEvent(
        root: GenesisUltraFirstMemoryEvent,
        sequence: Long,
        previousEventHash: String,
        eventHash: String,
        eventType: String = "memory.test",
        actor: String = "morimil",
        contentType: String = "text/plain",
        contentDigest: String,
        provenanceDigest: String
    ): GenesisUltraFirstMemoryEvent {
        val event = firstMemoryEvent()
        setField(event, "eventId", "event-$sequence-${eventHash.takeLast(4)}")
        setField(event, "eventHash", eventHash)
        setField(event, "sequence", sequence)
        setField(event, "previousEventHash", previousEventHash)
        setField(event, "instanceId", root.instanceId)
        setField(event, "bodyId", root.bodyId)
        setField(event, "eventType", eventType)
        setField(event, "actor", actor)
        setField(event, "observedAt", "2026-07-29T00:00:0${sequence}Z")
        setField(event, "contentDigest", contentDigest)
        setField(event, "contentType", contentType)
        setField(event, "provenanceDigest", provenanceDigest)
        setField(event, "privacy", "private_local")
        return event
    }

    private fun identityFor(root: GenesisUltraFirstMemoryEvent): GenesisUltraRuntimeIdentity {
        val doctrineBytes = "doctrine".toByteArray(StandardCharsets.UTF_8)
        val charterBytes = "charter".toByteArray(StandardCharsets.UTF_8)
        val policyBytes = "policy".toByteArray(StandardCharsets.UTF_8)
        val doctrine = GenesisUltraRuntimeDocument(
            relativePath = "doctrine/test.md",
            documentKind = "doctrine",
            digest = GenesisUltraHashProfile.sha256(doctrineBytes),
            sourceBytes = doctrineBytes
        )
        val charter = GenesisUltraRuntimeDocument(
            relativePath = "policy/charter.json",
            documentKind = "freedom_charter",
            digest = GenesisUltraHashProfile.sha256(charterBytes),
            sourceBytes = charterBytes
        )
        val recovery = GenesisUltraRuntimeDocument(
            relativePath = "policy/recovery.json",
            documentKind = "recovery_policy",
            digest = GenesisUltraHashProfile.sha256(policyBytes),
            sourceBytes = policyBytes
        )
        return GenesisUltraRuntimeIdentity(
            instanceId = root.instanceId,
            companionName = "Morimil Test",
            bornAt = "2026-07-29T00:00:00Z",
            identityDigest = digest('a'),
            activeBody = GenesisUltraRuntimeActiveBody(
                bodyId = root.bodyId,
                status = "active_writer",
                platformProfile = "android_test",
                publicKeyFingerprint = root.signature.publicKeyRef,
                keyEpochId = root.signature.keyEpochId,
                keyEpochDigest = digest('b'),
                registryEpoch = 1L,
                registryDigest = digest('c')
            ),
            guardian = GenesisUltraRuntimeGuardian(
                guardianId = "guardian_test",
                keyEpochId = "guardian_epoch_1",
                publicKeyRef = digest('d'),
                status = "active",
                role = "guardian",
                anchorDigest = digest('e')
            ),
            seed = GenesisUltraRuntimeVerifiedSeed(
                seedId = "seed_test",
                rootHash = digest('f'),
                protocolVersion = "1",
                hashProfile = GenesisUltraHashProfile.FIELD_PROFILE,
                identityDigest = digest('a'),
                doctrineDigest = doctrine.digest
            ),
            doctrine = doctrine,
            policy = GenesisUltraRuntimePolicy(
                freedomCharter = charter,
                recoveryPolicy = recovery,
                freedomCharterDigest = charter.digest,
                recoveryPolicyDigest = recovery.digest
            ),
            authorization = GenesisUltraRuntimeAuthorization(
                state = GenesisUltraRuntimeAuthorizationState.COMMITTED,
                authorizationDigest = digest('1'),
                candidateDigest = digest('2'),
                consentDigest = digest('3'),
                authorizedAt = "2026-07-29T00:00:00Z",
                expiresAt = "2026-07-29T01:00:00Z",
                receiptDigest = digest('4'),
                birthStatus = "born",
                ownershipConferred = false
            )
        )
    }

    private fun firstMemoryEvent(): GenesisUltraFirstMemoryEvent {
        val vectors = JSONObject(resourceText("/genesis-ultra/atomic_birth_conformance.json"))
        val eventJson = vectors.getJSONObject("fixture")
            .getJSONObject("first_memory_event")
        val eventClass = GenesisUltraFirstMemoryEvent::class.java
        val parserClasses = listOf(
            "com.morimil.app.data.genesis.ultra.GenesisUltraAtomicBirthDocumentParser",
            "com.morimil.app.data.genesis.ultra.GenesisUltraContractParser"
        )
        parserClasses.forEach { className ->
            val parserClass = Class.forName(className)
            parserClass.declaredMethods.forEach { method ->
                if (method.returnType == eventClass && method.parameterCount == 1) {
                    val argument = when (method.parameterTypes.single()) {
                        String::class.java -> eventJson.toString()
                        JSONObject::class.java -> eventJson
                        ByteArray::class.java -> eventJson.toString().toByteArray(StandardCharsets.UTF_8)
                        else -> null
                    } ?: return@forEach
                    method.isAccessible = true
                    val receiver = if (Modifier.isStatic(method.modifiers)) {
                        null
                    } else {
                        parserClass.getDeclaredField("INSTANCE").also { field ->
                            field.isAccessible = true
                        }.get(null)
                    }
                    return method.invoke(receiver, argument) as GenesisUltraFirstMemoryEvent
                }
            }
        }
        error("first_memory_event_parser_not_found")
    }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        var current: Class<*>? = target.javaClass
        while (current != null) {
            val field = current.declaredFields.firstOrNull { candidate -> candidate.name == fieldName }
            if (field != null) {
                field.isAccessible = true
                field.set(target, value)
                return
            }
            current = current.superclass
        }
        error("field_not_found:$fieldName")
    }

    private fun resourceText(path: String): String {
        return requireNotNull(javaClass.getResourceAsStream(path)) { "resource_missing:$path" }
            .bufferedReader(StandardCharsets.UTF_8)
            .use { reader -> reader.readText() }
    }

    private fun port(
        identity: GenesisUltraRuntimeIdentity,
        snapshot: CanonicalMemorySnapshot
    ): CanonicalConsumerReadPort {
        return GenesisUltraCanonicalConsumerReadAdapter.forTest(
            readIdentity = { identity },
            readMemorySnapshot = { snapshot }
        )
    }

    private fun <T> ready(result: CanonicalReadResult<T>): T {
        assertTrue("Expected Ready but was $result", result is CanonicalReadResult.Ready)
        return (result as CanonicalReadResult.Ready).value
    }

    private fun blocked(result: CanonicalReadResult<*>): CanonicalReadFailure {
        assertTrue("Expected Blocked but was $result", result is CanonicalReadResult.Blocked)
        return (result as CanonicalReadResult.Blocked).failure
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        var failure: Throwable? = null
        try {
            block()
        } catch (caught: Throwable) {
            failure = caught
        }
        assertTrue("Expected IllegalArgumentException but was $failure", failure is IllegalArgumentException)
    }

    private fun digest(character: Char): String = "sha256:" + character.toString().repeat(64)

    private fun eventHash(character: Char): String = "evsha256:" + character.toString().repeat(64)

    private data class Fixture(
        val identity: GenesisUltraRuntimeIdentity,
        val snapshot: CanonicalMemorySnapshot
    )

    private companion object {
        val DIGEST = Regex("^sha256:[a-f0-9]{64}$")
        val EVENT_HASH = Regex("^evsha256:[a-f0-9]{64}$")
    }
}
