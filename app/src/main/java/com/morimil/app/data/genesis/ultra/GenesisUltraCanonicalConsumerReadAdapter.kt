package com.morimil.app.data.genesis.ultra

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.CancellationException
import org.json.JSONObject

internal class GenesisUltraCanonicalConsumerReadAdapter private constructor(
    private val readIdentity: suspend () -> GenesisUltraRuntimeIdentity?,
    private val readMemorySnapshot: suspend () -> CanonicalMemorySnapshot
) : CanonicalConsumerReadPort {

    override suspend fun readVerifiedSnapshot(): CanonicalReadResult<CanonicalConsumerSnapshot> {
        val before = try {
            readIdentity()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            return CanonicalReadResult.Blocked(mapIdentityFailure(failure))
        } ?: return blocked(
            code = CanonicalReadFailureCode.BIRTH_NOT_COMMITTED,
            disposition = CanonicalReadDisposition.NOT_READY,
            diagnosticCode = "canonical_read_birth_not_committed"
        )

        val snapshot = try {
            readMemorySnapshot()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            return CanonicalReadResult.Blocked(mapMemoryFailure(failure))
        }

        val after = try {
            readIdentity()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            return CanonicalReadResult.Blocked(mapAfterIdentityFailure(failure))
        } ?: return blocked(
            code = CanonicalReadFailureCode.SNAPSHOT_CHANGED_DURING_READ,
            disposition = CanonicalReadDisposition.RETRYABLE,
            diagnosticCode = "canonical_read_snapshot_changed_during_read"
        )

        return try {
            validateIdentity(before)
            validateIdentity(after)
            validateStableIdentity(before, after)
            CanonicalReadResult.Ready(projectSnapshot(before, snapshot))
        } catch (failure: ProjectionFailure) {
            CanonicalReadResult.Blocked(failure.failure)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            blocked(
                code = CanonicalReadFailureCode.UNCLASSIFIED_VERIFICATION_FAILURE,
                disposition = CanonicalReadDisposition.BLOCKED,
                diagnosticCode = "canonical_read_unclassified_verification_failure"
            )
        }
    }

    override suspend fun readRecallCandidates(
        limit: Int
    ): CanonicalReadResult<CanonicalRecallCandidateBatch> {
        require(limit in 1..MAX_RECALL_LIMIT) { "canonical_recall_limit_invalid" }
        return when (val result = readVerifiedSnapshot()) {
            is CanonicalReadResult.Blocked -> result
            is CanonicalReadResult.Ready -> {
                val snapshot = result.value
                val candidates = snapshot.events
                    .asSequence()
                    .filter { event -> event.payloadState == CanonicalPayloadState.VERIFIED_PAYLOAD }
                    .mapNotNull { event -> event.toRecallCandidateOrNull() }
                    .sortedByDescending { candidate -> candidate.event.sequence }
                    .take(limit)
                    .toList()
                CanonicalReadResult.Ready(
                    CanonicalRecallCandidateBatch(
                        snapshot = snapshot.lineage,
                        instanceId = snapshot.identity.instanceId,
                        writerBodyId = snapshot.writer.writerBodyId,
                        writerEpochId = snapshot.writer.writerEpochId,
                        candidates = immutableList(candidates)
                    )
                )
            }
        }
    }

    override suspend fun readRestCyclePlanningInput(
        limit: Int
    ): CanonicalReadResult<CanonicalRestCyclePlanningInput> {
        require(limit in 1..MAX_REST_CYCLE_LIMIT) { "canonical_rest_cycle_limit_invalid" }
        return when (val result = readVerifiedSnapshot()) {
            is CanonicalReadResult.Blocked -> result
            is CanonicalReadResult.Ready -> {
                val snapshot = result.value
                val eligible = snapshot.events
                    .asSequence()
                    .filter { event -> event.payloadState == CanonicalPayloadState.VERIFIED_PAYLOAD }
                    .filterNot { event -> event.ref.eventType == REST_CYCLE_EVENT_TYPE }
                    .toList()
                val bounded = eligible.takeLast(limit)
                val sources = bounded.map { event ->
                    CanonicalRestCycleSource(
                        event = event.ref,
                        content = requireNotNull(event.content),
                        provenance = requireNotNull(event.provenance),
                        semantics = event.semantics
                    )
                }
                val latestRestCycle = snapshot.events
                    .lastOrNull { event -> event.ref.eventType == REST_CYCLE_EVENT_TYPE }
                    ?.ref
                val sourceSetDigest = GenesisUltraHashProfile.hashFields(
                    REST_CYCLE_SOURCE_SET_DOMAIN,
                    buildList {
                        add(snapshot.lineage.snapshotDigest)
                        add(sources.size.toString())
                        sources.forEach { source ->
                            add(source.event.sequence.toString())
                            add(source.event.eventHash)
                            add(source.event.contentDigest)
                            add(source.event.provenanceDigest)
                        }
                    }
                )
                CanonicalReadResult.Ready(
                    CanonicalRestCyclePlanningInput(
                        identity = snapshot.identity,
                        writer = snapshot.writer,
                        snapshot = snapshot.lineage,
                        sources = immutableList(sources),
                        latestRestCycle = latestRestCycle,
                        sourceSetDigest = sourceSetDigest
                    )
                )
            }
        }
    }

    override suspend fun readHealthInput(
        recentLimit: Int
    ): CanonicalReadResult<CanonicalHealthInput> {
        require(recentLimit in 1..MAX_HEALTH_LIMIT) { "canonical_health_limit_invalid" }
        return when (val result = readVerifiedSnapshot()) {
            is CanonicalReadResult.Blocked -> result
            is CanonicalReadResult.Ready -> {
                val snapshot = result.value
                val latestRestCycle = snapshot.events
                    .lastOrNull { event -> event.ref.eventType == REST_CYCLE_EVENT_TYPE }
                    ?.ref
                CanonicalReadResult.Ready(
                    CanonicalHealthInput(
                        instanceId = snapshot.identity.instanceId,
                        writerBodyId = snapshot.writer.writerBodyId,
                        writerEpochId = snapshot.writer.writerEpochId,
                        snapshotDigest = snapshot.lineage.snapshotDigest,
                        birthRootPresent = true,
                        canonicalMemoryVerified = true,
                        totalCanonicalEventCount = 1 + snapshot.lineage.postBirthEventCount,
                        postBirthEventCount = snapshot.lineage.postBirthEventCount,
                        recentVerifiedEventCount = minOf(recentLimit, snapshot.events.size),
                        latestRestCycle = latestRestCycle,
                        quarantineEventCount = snapshot.events.count { event ->
                            event.ref.eventType.contains(QUARANTINE_TOKEN)
                        }
                    )
                )
            }
        }
    }

    private fun projectSnapshot(
        identity: GenesisUltraRuntimeIdentity,
        snapshot: CanonicalMemorySnapshot
    ): CanonicalConsumerSnapshot {
        if (snapshot.instanceId != identity.instanceId) {
            fail(
                CanonicalReadFailureCode.FOREIGN_INSTANCE,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_foreign_instance"
            )
        }
        if (snapshot.companionName != identity.companionName) {
            fail(
                CanonicalReadFailureCode.CHAIN_CORRUPT,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_snapshot_identity_mismatch"
            )
        }

        val birthRoot = snapshot.birthRoot
        if (birthRoot.sequence != 0L) {
            fail(
                CanonicalReadFailureCode.CHAIN_CORRUPT,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_birth_root_invalid"
            )
        }
        validateEventAuthority(birthRoot, identity)
        requireDigest(birthRoot.eventHash, "birth_root_event_hash")
        requireDigest(birthRoot.contentDigest, "birth_root_content_digest")
        requireDigest(birthRoot.provenanceDigest, "birth_root_provenance_digest")

        var previousSequence = birthRoot.sequence
        var previousEventHash = birthRoot.eventHash
        val eventIds = mutableSetOf<String>()
        val eventHashes = mutableSetOf<String>()
        val projectedEvents = ArrayList<CanonicalConsumerEvent>(snapshot.records.size)

        snapshot.records.forEach { record ->
            val event = record.event
            if (event.sequence <= previousSequence) {
                fail(
                    CanonicalReadFailureCode.CHAIN_CORRUPT,
                    CanonicalReadDisposition.BLOCKED,
                    "canonical_read_sequence_not_ascending"
                )
            }
            if (event.previousEventHash != previousEventHash) {
                fail(
                    CanonicalReadFailureCode.CHAIN_CORRUPT,
                    CanonicalReadDisposition.BLOCKED,
                    "canonical_read_previous_hash_mismatch"
                )
            }
            if (!eventIds.add(event.eventId) || !eventHashes.add(event.eventHash)) {
                fail(
                    CanonicalReadFailureCode.CHAIN_CORRUPT,
                    CanonicalReadDisposition.BLOCKED,
                    "canonical_read_event_duplicate"
                )
            }
            validateEventAuthority(event, identity)
            projectedEvents += projectRecord(record, identity)
            previousSequence = event.sequence
            previousEventHash = event.eventHash
        }

        val writer = CanonicalWriterRef(
            writerBodyId = identity.activeBody.bodyId,
            writerEpochId = identity.activeBody.keyEpochId,
            writerEpochDigest = identity.activeBody.keyEpochDigest,
            writerPublicKeyRef = identity.activeBody.publicKeyFingerprint,
            registryEpoch = identity.activeBody.registryEpoch,
            registryDigest = identity.activeBody.registryDigest
        )
        val snapshotDigest = GenesisUltraHashProfile.hashFields(
            SNAPSHOT_DOMAIN,
            buildList {
                add(identity.instanceId)
                add(identity.identityDigest)
                add(writer.writerBodyId)
                add(writer.writerEpochId)
                add(writer.writerEpochDigest)
                add(birthRoot.eventHash)
                add(birthRoot.sequence.toString())
                add(projectedEvents.size.toString())
                projectedEvents.forEach { event ->
                    add(event.ref.sequence.toString())
                    add(event.ref.eventHash)
                    add(event.ref.contentDigest)
                    add(event.ref.provenanceDigest)
                }
            }
        )
        requireDigest(snapshotDigest, "snapshot_digest")
        val lineage = CanonicalSnapshotRef(
            instanceId = identity.instanceId,
            birthRootEventHash = birthRoot.eventHash,
            birthRootSequence = birthRoot.sequence,
            lastEventHash = projectedEvents.lastOrNull()?.ref?.eventHash ?: birthRoot.eventHash,
            lastSequence = projectedEvents.lastOrNull()?.ref?.sequence ?: birthRoot.sequence,
            postBirthEventCount = projectedEvents.size,
            snapshotDigest = snapshotDigest
        )
        return CanonicalConsumerSnapshot(
            identity = CanonicalInstanceRef(
                instanceId = identity.instanceId,
                companionName = identity.companionName,
                identityDigest = identity.identityDigest
            ),
            writer = writer,
            lineage = lineage,
            events = immutableList(projectedEvents)
        )
    }

    private fun projectRecord(
        record: CanonicalMemoryRecord,
        identity: GenesisUltraRuntimeIdentity
    ): CanonicalConsumerEvent {
        val event = record.event
        val ref = event.toCanonicalRef()
        val contentBytes = record.copyContentBytes()
        val provenanceBytes = record.copyProvenanceBytes()

        if (contentBytes == null || provenanceBytes == null) {
            if (contentBytes == null && provenanceBytes == null && isActivationMetadataOnly(record, identity)) {
                return CanonicalConsumerEvent.activationMetadataOnly(ref)
            }
            fail(
                CanonicalReadFailureCode.PAYLOAD_MISSING,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_payload_missing"
            )
        }

        if (GenesisUltraHashProfile.sha256(contentBytes) != event.contentDigest) {
            fail(
                CanonicalReadFailureCode.PAYLOAD_INTEGRITY_INVALID,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_payload_integrity_invalid"
            )
        }
        if (GenesisUltraHashProfile.sha256(provenanceBytes) != event.provenanceDigest) {
            fail(
                CanonicalReadFailureCode.PROVENANCE_UNVERIFIABLE,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_provenance_digest_invalid"
            )
        }

        val content = decodeUtf8Strict(contentBytes, CanonicalReadFailureCode.PAYLOAD_INTEGRITY_INVALID)
        val parsed = parseProvenance(provenanceBytes, identity, event)
        return CanonicalConsumerEvent.verified(
            ref = ref,
            content = content,
            provenance = parsed.provenance,
            semantics = parsed.semantics,
            contentBytes = contentBytes,
            provenanceBytes = provenanceBytes
        )
    }

    private fun validateIdentity(identity: GenesisUltraRuntimeIdentity) {
        if (
            identity.instanceId.isBlank() ||
            identity.companionName.isBlank() ||
            identity.activeBody.bodyId.isBlank() ||
            identity.instanceId == identity.activeBody.bodyId ||
            identity.activeBody.status != ACTIVE_WRITER ||
            identity.activeBody.keyEpochId.isBlank() ||
            identity.activeBody.registryEpoch < 0L ||
            identity.authorization.state != GenesisUltraRuntimeAuthorizationState.COMMITTED ||
            identity.authorization.birthStatus != BORN ||
            identity.authorization.ownershipConferred
        ) {
            fail(
                CanonicalReadFailureCode.IDENTITY_INCONSISTENT,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_identity_inconsistent"
            )
        }
        requireDigest(identity.identityDigest, "identity_digest")
        requireDigest(identity.activeBody.keyEpochDigest, "writer_epoch_digest")
        requireDigest(identity.activeBody.publicKeyFingerprint, "writer_public_key_ref")
        requireDigest(identity.activeBody.registryDigest, "registry_digest")
        requireDigest(identity.authorization.authorizationDigest, "authorization_digest")
        requireDigest(identity.authorization.receiptDigest, "authorization_receipt_digest")
    }

    private fun validateStableIdentity(
        before: GenesisUltraRuntimeIdentity,
        after: GenesisUltraRuntimeIdentity
    ) {
        if (
            before.instanceId != after.instanceId ||
            before.companionName != after.companionName ||
            before.identityDigest != after.identityDigest ||
            before.activeBody.bodyId != after.activeBody.bodyId ||
            before.activeBody.keyEpochId != after.activeBody.keyEpochId ||
            before.activeBody.keyEpochDigest != after.activeBody.keyEpochDigest ||
            before.activeBody.publicKeyFingerprint != after.activeBody.publicKeyFingerprint ||
            before.activeBody.registryEpoch != after.activeBody.registryEpoch ||
            before.activeBody.registryDigest != after.activeBody.registryDigest ||
            before.authorization.authorizationDigest != after.authorization.authorizationDigest ||
            before.authorization.receiptDigest != after.authorization.receiptDigest
        ) {
            fail(
                CanonicalReadFailureCode.SNAPSHOT_CHANGED_DURING_READ,
                CanonicalReadDisposition.RETRYABLE,
                "canonical_read_snapshot_changed_during_read"
            )
        }
    }

    private fun validateEventAuthority(
        event: GenesisUltraFirstMemoryEvent,
        identity: GenesisUltraRuntimeIdentity
    ) {
        if (event.instanceId != identity.instanceId) {
            fail(
                CanonicalReadFailureCode.FOREIGN_INSTANCE,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_foreign_instance"
            )
        }
        if (event.bodyId != identity.activeBody.bodyId || event.signature.signerId != identity.activeBody.bodyId) {
            fail(
                CanonicalReadFailureCode.WRONG_BODY,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_wrong_body"
            )
        }
        if (
            event.signature.keyEpochId != identity.activeBody.keyEpochId ||
            event.signature.publicKeyRef != identity.activeBody.publicKeyFingerprint
        ) {
            fail(
                CanonicalReadFailureCode.STALE_WRITER_EPOCH,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_stale_writer_epoch"
            )
        }
        if (
            event.eventId.isBlank() ||
            event.eventType.isBlank() ||
            event.actor.isBlank() ||
            event.observedAt.isBlank() ||
            event.contentType.isBlank() ||
            event.privacy.isBlank()
        ) {
            fail(
                CanonicalReadFailureCode.CHAIN_CORRUPT,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_event_metadata_invalid"
            )
        }
        requireDigest(event.eventHash, "event_hash")
        requireDigest(event.contentDigest, "content_digest")
        requireDigest(event.provenanceDigest, "provenance_digest")
    }

    private fun GenesisUltraFirstMemoryEvent.toCanonicalRef(): CanonicalEventRef {
        return CanonicalEventRef(
            eventId = eventId,
            eventHash = eventHash,
            sequence = sequence,
            previousEventHash = previousEventHash,
            instanceId = instanceId,
            bodyId = bodyId,
            signerId = signature.signerId,
            signerEpochId = signature.keyEpochId,
            signerPublicKeyRef = signature.publicKeyRef,
            eventType = eventType,
            actor = actor,
            observedAt = observedAt,
            contentDigest = contentDigest,
            contentType = contentType,
            provenanceDigest = provenanceDigest,
            privacy = privacy
        )
    }

    private fun parseProvenance(
        bytes: ByteArray,
        identity: GenesisUltraRuntimeIdentity,
        event: GenesisUltraFirstMemoryEvent
    ): ParsedProvenance {
        val text = decodeUtf8Strict(bytes, CanonicalReadFailureCode.PROVENANCE_UNVERIFIABLE)
        val json = try {
            JSONObject(text)
        } catch (_: Throwable) {
            fail(
                CanonicalReadFailureCode.PROVENANCE_UNVERIFIABLE,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_provenance_json_invalid"
            )
        }
        requireExactKeys(json, PROVENANCE_KEYS, "canonical_read_provenance_fields_invalid")
        val schema = requiredString(json, "schema")
        val instanceId = requiredString(json, "instance_id")
        val bodyId = requiredString(json, "body_id")
        val source = requiredString(json, "source")
        val classification = requiredString(json, "classification")
        val userConfirmed = requiredBoolean(json, "user_confirmed")
        val sourceId = optionalString(json, "source_id")
        val noteJson = optionalString(json, "note")

        if (
            schema != PROVENANCE_SCHEMA ||
            instanceId != identity.instanceId ||
            bodyId != identity.activeBody.bodyId ||
            bodyId != event.bodyId
        ) {
            fail(
                CanonicalReadFailureCode.PROVENANCE_UNVERIFIABLE,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_provenance_binding_invalid"
            )
        }

        val parsedNote = parseNote(noteJson, source, userConfirmed)
        return ParsedProvenance(
            provenance = CanonicalEventProvenance(
                schema = schema,
                instanceId = instanceId,
                bodyId = bodyId,
                source = source,
                classification = classification,
                userConfirmed = userConfirmed,
                sourceId = sourceId,
                noteSchema = parsedNote.schema,
                noteJson = noteJson
            ),
            semantics = parsedNote.semantics
        )
    }

    private fun parseNote(
        noteJson: String?,
        provenanceSource: String,
        provenanceUserConfirmed: Boolean
    ): ParsedNote {
        if (noteJson == null) return ParsedNote(schema = null, semantics = null)
        val json = try {
            JSONObject(noteJson)
        } catch (_: Throwable) {
            fail(
                CanonicalReadFailureCode.PROVENANCE_UNVERIFIABLE,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_provenance_note_invalid"
            )
        }
        val schema = requiredString(json, "schema")
        return when (schema) {
            LIVING_MEMORY_NOTE_SCHEMA -> {
                requireExactKeys(json, LIVING_MEMORY_NOTE_KEYS, "canonical_read_living_note_fields_invalid")
                if (requiredString(json, "source") != provenanceSource) {
                    fail(
                        CanonicalReadFailureCode.PROVENANCE_UNVERIFIABLE,
                        CanonicalReadDisposition.BLOCKED,
                        "canonical_read_living_note_source_invalid"
                    )
                }
                val importance = requiredInt(json, "importance", 1..100)
                val evidence = if (json.isNull("evidence")) null else {
                    val raw = json.get("evidence")
                    if (raw !is JSONObject) {
                        fail(
                            CanonicalReadFailureCode.PROVENANCE_UNVERIFIABLE,
                            CanonicalReadDisposition.BLOCKED,
                            "canonical_read_living_note_evidence_invalid"
                        )
                    }
                    raw
                }
                val memoryKind = evidence?.let { optionalNonBlankString(it, "memory_kind") }
                val confidence = evidence?.let { optionalInt(it, "confidence", 0..100) }
                ParsedNote(
                    schema = schema,
                    semantics = CanonicalMemorySemantics(
                        memoryKind = memoryKind,
                        importance = importance,
                        confidence = confidence,
                        userConfirmed = provenanceUserConfirmed
                    )
                )
            }

            LEGACY_MEMORY_NOTE_SCHEMA -> {
                requireExactKeys(json, LEGACY_MEMORY_NOTE_KEYS, "canonical_read_legacy_note_fields_invalid")
                val legacyConfirmed = requiredBoolean(json, "legacy_user_confirmed")
                if (legacyConfirmed != provenanceUserConfirmed) {
                    fail(
                        CanonicalReadFailureCode.PROVENANCE_UNVERIFIABLE,
                        CanonicalReadDisposition.BLOCKED,
                        "canonical_read_legacy_confirmation_invalid"
                    )
                }
                ParsedNote(
                    schema = schema,
                    semantics = CanonicalMemorySemantics(
                        memoryKind = requiredString(json, "legacy_memory_kind"),
                        importance = requiredInt(json, "legacy_importance", 1..100),
                        confidence = requiredInt(json, "legacy_confidence", 0..100),
                        userConfirmed = legacyConfirmed
                    )
                )
            }

            else -> ParsedNote(schema = schema, semantics = null)
        }
    }

    private fun isActivationMetadataOnly(
        record: CanonicalMemoryRecord,
        identity: GenesisUltraRuntimeIdentity
    ): Boolean {
        val event = record.event
        return record.provenanceType == METADATA_ONLY &&
            event.sequence == 1L &&
            event.eventType == ACTIVATION_EVENT_TYPE &&
            event.actor == ACTIVATION_ACTOR &&
            event.contentType == ACTIVATION_CONTENT_TYPE &&
            event.contentDigest == identity.authorization.authorizationDigest &&
            event.provenanceDigest == identity.authorization.receiptDigest &&
            event.privacy == PRIVATE_LOCAL
    }

    private fun CanonicalConsumerEvent.toRecallCandidateOrNull(): CanonicalRecallCandidate? {
        val contentValue = content ?: return null
        val provenanceValue = provenance ?: return null
        val semanticsValue = semantics ?: return null
        val memoryKind = semanticsValue.memoryKind ?: return null
        val importance = semanticsValue.importance ?: return null
        val confidence = semanticsValue.confidence ?: return null
        return CanonicalRecallCandidate(
            event = ref,
            content = contentValue,
            provenance = provenanceValue,
            memoryKind = memoryKind,
            importance = importance,
            confidence = confidence,
            userConfirmed = semanticsValue.userConfirmed
        )
    }

    private fun requireDigest(value: String, field: String) {
        if (!DIGEST_PATTERN.matches(value)) {
            fail(
                CanonicalReadFailureCode.CHAIN_CORRUPT,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_digest_invalid_$field"
            )
        }
    }

    private fun decodeUtf8Strict(
        bytes: ByteArray,
        code: CanonicalReadFailureCode
    ): String {
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.copyOf()))
                .toString()
        } catch (_: Throwable) {
            fail(
                code,
                CanonicalReadDisposition.BLOCKED,
                if (code == CanonicalReadFailureCode.PROVENANCE_UNVERIFIABLE) {
                    "canonical_read_provenance_utf8_invalid"
                } else {
                    "canonical_read_payload_utf8_invalid"
                }
            )
        }
    }

    private fun requireExactKeys(json: JSONObject, expected: Set<String>, diagnostic: String) {
        val actual = json.keys().asSequence().toSet()
        if (actual != expected) {
            fail(
                CanonicalReadFailureCode.PROVENANCE_UNVERIFIABLE,
                CanonicalReadDisposition.BLOCKED,
                diagnostic
            )
        }
    }

    private fun requiredString(json: JSONObject, key: String): String {
        val value = try {
            json.getString(key)
        } catch (_: Throwable) {
            fail(
                CanonicalReadFailureCode.PROVENANCE_UNVERIFIABLE,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_provenance_string_invalid"
            )
        }
        if (value.isBlank()) {
            fail(
                CanonicalReadFailureCode.PROVENANCE_UNVERIFIABLE,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_provenance_string_invalid"
            )
        }
        return value
    }

    private fun optionalString(json: JSONObject, key: String): String? {
        if (json.isNull(key)) return null
        return requiredString(json, key)
    }

    private fun optionalNonBlankString(json: JSONObject, key: String): String? {
        if (!json.has(key) || json.isNull(key)) return null
        return requiredString(json, key)
    }

    private fun requiredBoolean(json: JSONObject, key: String): Boolean {
        return try {
            json.getBoolean(key)
        } catch (_: Throwable) {
            fail(
                CanonicalReadFailureCode.PROVENANCE_UNVERIFIABLE,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_provenance_boolean_invalid"
            )
        }
    }

    private fun requiredInt(json: JSONObject, key: String, range: IntRange): Int {
        val value = try {
            json.getInt(key)
        } catch (_: Throwable) {
            fail(
                CanonicalReadFailureCode.PROVENANCE_UNVERIFIABLE,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_provenance_integer_invalid"
            )
        }
        if (value !in range) {
            fail(
                CanonicalReadFailureCode.PROVENANCE_UNVERIFIABLE,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_provenance_integer_invalid"
            )
        }
        return value
    }

    private fun optionalInt(json: JSONObject, key: String, range: IntRange): Int? {
        if (!json.has(key) || json.isNull(key)) return null
        return requiredInt(json, key, range)
    }

    private fun mapIdentityFailure(failure: Throwable): CanonicalReadFailure {
        val message = failure.message.orEmpty()
        return when {
            isTransient(failure, message) -> CanonicalReadFailure(
                CanonicalReadFailureCode.TRANSIENT_STORE_UNAVAILABLE,
                CanonicalReadDisposition.RETRYABLE,
                "canonical_read_transient_store_unavailable"
            )

            message == "genesis_ultra_runtime_identity_inconsistent" -> CanonicalReadFailure(
                CanonicalReadFailureCode.IDENTITY_INCONSISTENT,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_identity_inconsistent"
            )

            message == "genesis_ultra_runtime_identity_committed_birth_missing" ||
                message.startsWith("runtime_identity_") -> CanonicalReadFailure(
                CanonicalReadFailureCode.IDENTITY_INCONSISTENT,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_identity_verification_failed"
            )

            else -> CanonicalReadFailure(
                CanonicalReadFailureCode.UNCLASSIFIED_VERIFICATION_FAILURE,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_unclassified_verification_failure"
            )
        }
    }

    private fun mapAfterIdentityFailure(failure: Throwable): CanonicalReadFailure {
        val message = failure.message.orEmpty()
        return if (isTransient(failure, message)) {
            CanonicalReadFailure(
                CanonicalReadFailureCode.TRANSIENT_STORE_UNAVAILABLE,
                CanonicalReadDisposition.RETRYABLE,
                "canonical_read_transient_store_unavailable"
            )
        } else {
            CanonicalReadFailure(
                CanonicalReadFailureCode.SNAPSHOT_CHANGED_DURING_READ,
                CanonicalReadDisposition.RETRYABLE,
                "canonical_read_snapshot_changed_during_read"
            )
        }
    }

    private fun mapMemoryFailure(failure: Throwable): CanonicalReadFailure {
        val message = failure.message.orEmpty()
        return when {
            isTransient(failure, message) -> CanonicalReadFailure(
                CanonicalReadFailureCode.TRANSIENT_STORE_UNAVAILABLE,
                CanonicalReadDisposition.RETRYABLE,
                "canonical_read_transient_store_unavailable"
            )

            message == "canonical_memory_birth_not_committed" -> CanonicalReadFailure(
                CanonicalReadFailureCode.CANONICAL_MEMORY_ABSENT,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_canonical_memory_absent"
            )

            message == "canonical_memory_identity_not_recoverable" -> CanonicalReadFailure(
                CanonicalReadFailureCode.SNAPSHOT_CHANGED_DURING_READ,
                CanonicalReadDisposition.RETRYABLE,
                "canonical_read_snapshot_changed_during_read"
            )

            message == "canonical_memory_body_root_identity_mismatch" ||
                message == "canonical_memory_signer_identity_mismatch" -> CanonicalReadFailure(
                CanonicalReadFailureCode.WRITER_BINDING_MISMATCH,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_writer_binding_mismatch"
            )

            message == "canonical_memory_foreign_instance_payloads" -> CanonicalReadFailure(
                CanonicalReadFailureCode.FOREIGN_INSTANCE,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_foreign_instance"
            )

            message.startsWith("canonical_memory_payload_missing:") -> CanonicalReadFailure(
                CanonicalReadFailureCode.PAYLOAD_MISSING,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_payload_missing"
            )

            message == "canonical_memory_payload_without_verified_event" ||
                message == "canonical_memory_payload_event_hash_duplicate" ||
                message.startsWith("canonical_memory_payload_event_mismatch:") -> CanonicalReadFailure(
                CanonicalReadFailureCode.PAYLOAD_INTEGRITY_INVALID,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_payload_integrity_invalid"
            )

            message.contains("provenance") -> CanonicalReadFailure(
                CanonicalReadFailureCode.PROVENANCE_UNVERIFIABLE,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_provenance_unverifiable"
            )

            message.contains("signature") ||
                message.contains("sequence") ||
                message.contains("event_hash") ||
                message.contains("canonical_memory") -> CanonicalReadFailure(
                CanonicalReadFailureCode.CHAIN_CORRUPT,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_chain_corrupt"
            )

            else -> CanonicalReadFailure(
                CanonicalReadFailureCode.UNCLASSIFIED_VERIFICATION_FAILURE,
                CanonicalReadDisposition.BLOCKED,
                "canonical_read_unclassified_verification_failure"
            )
        }
    }

    private fun isTransient(failure: Throwable, message: String): Boolean {
        return failure is IOException ||
            message.contains("database is locked", ignoreCase = true) ||
            message.contains("database locked", ignoreCase = true) ||
            message.contains("temporarily unavailable", ignoreCase = true) ||
            message.contains("keystore unavailable", ignoreCase = true)
    }

    private fun blocked(
        code: CanonicalReadFailureCode,
        disposition: CanonicalReadDisposition,
        diagnosticCode: String
    ): CanonicalReadResult.Blocked {
        return CanonicalReadResult.Blocked(
            CanonicalReadFailure(
                code = code,
                disposition = disposition,
                diagnosticCode = diagnosticCode
            )
        )
    }

    private fun fail(
        code: CanonicalReadFailureCode,
        disposition: CanonicalReadDisposition,
        diagnosticCode: String
    ): Nothing {
        throw ProjectionFailure(
            CanonicalReadFailure(
                code = code,
                disposition = disposition,
                diagnosticCode = diagnosticCode
            )
        )
    }

    private data class ParsedProvenance(
        val provenance: CanonicalEventProvenance,
        val semantics: CanonicalMemorySemantics?
    )

    private data class ParsedNote(
        val schema: String?,
        val semantics: CanonicalMemorySemantics?
    )

    private class ProjectionFailure(
        val failure: CanonicalReadFailure
    ) : RuntimeException()

    internal companion object {
        fun production(
            identityRepository: GenesisUltraRuntimeIdentityRepository,
            memoryRepository: CanonicalMemoryRepository
        ): CanonicalConsumerReadPort {
            return GenesisUltraCanonicalConsumerReadAdapter(
                readIdentity = identityRepository::readCommittedIdentity,
                readMemorySnapshot = memoryRepository::readVerifiedSnapshot
            )
        }

        fun forTest(
            readIdentity: suspend () -> GenesisUltraRuntimeIdentity?,
            readMemorySnapshot: suspend () -> CanonicalMemorySnapshot
        ): CanonicalConsumerReadPort {
            return GenesisUltraCanonicalConsumerReadAdapter(
                readIdentity = readIdentity,
                readMemorySnapshot = readMemorySnapshot
            )
        }

        private const val ACTIVE_WRITER = "active_writer"
        private const val BORN = "born"
        private const val SNAPSHOT_DOMAIN = "morimil.canonical_consumer_snapshot.v1"
        private const val REST_CYCLE_SOURCE_SET_DOMAIN = "morimil.rest_cycle_source_set.v1"
        private const val REST_CYCLE_EVENT_TYPE = "rest_cycle.local_consolidation"
        private const val PROVENANCE_SCHEMA = "morimil.canonical_memory.provenance.v1"
        private const val LIVING_MEMORY_NOTE_SCHEMA = "morimil.living_memory_write.v1"
        private const val LEGACY_MEMORY_NOTE_SCHEMA = "morimil.legacy_memory_import.v1"
        private const val METADATA_ONLY = "metadata-only"
        private const val ACTIVATION_EVENT_TYPE = "instance.activation.confirmed"
        private const val ACTIVATION_ACTOR = "host_confirmed_system"
        private const val ACTIVATION_CONTENT_TYPE =
            "application/vnd.genesis.atomic-birth-authorization+json"
        private const val PRIVATE_LOCAL = "private_local"
        private const val QUARANTINE_TOKEN = "quarantine"
        private const val MAX_RECALL_LIMIT = 60
        private const val MAX_REST_CYCLE_LIMIT = 80
        private const val MAX_HEALTH_LIMIT = 20

        private val DIGEST_PATTERN = Regex("^sha256:[a-f0-9]{64}$")
        private val PROVENANCE_KEYS = setOf(
            "schema",
            "instance_id",
            "body_id",
            "source",
            "classification",
            "user_confirmed",
            "source_id",
            "note"
        )
        private val LIVING_MEMORY_NOTE_KEYS = setOf(
            "schema",
            "source",
            "importance",
            "evidence"
        )
        private val LEGACY_MEMORY_NOTE_KEYS = setOf(
            "schema",
            "legacy_memory_kind",
            "legacy_importance",
            "legacy_confidence",
            "legacy_user_confirmed"
        )

        private fun <T> immutableList(values: List<T>): List<T> {
            return Collections.unmodifiableList(ArrayList(values))
        }
    }
}
