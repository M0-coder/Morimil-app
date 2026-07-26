package com.morimil.app.data.genesis.ultra

import androidx.room.withTransaction
import com.morimil.app.data.local.GenesisUltraMemoryPayloadEntity
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.repository.MemoryAppendGate
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.json.JSONObject

internal data class CanonicalMemoryProvenance(
    val source: String,
    val classification: String,
    val userConfirmed: Boolean,
    val sourceId: String? = null,
    val note: String? = null
)

internal data class CanonicalMemoryAppendCommand(
    val eventType: String,
    val actor: String,
    val content: String,
    val contentType: String = "text/plain",
    val observedAt: String = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
    val provenance: CanonicalMemoryProvenance,
    val eventId: String? = null
)

internal class CanonicalMemoryRecord internal constructor(
    val event: GenesisUltraFirstMemoryEvent,
    contentBytes: ByteArray?,
    provenanceBytes: ByteArray?,
    val provenanceType: String,
    private val metadataSummary: String? = null
) {
    private val content = contentBytes?.copyOf()
    private val provenance = provenanceBytes?.copyOf()

    val hasPayload: Boolean
        get() = content != null && provenance != null

    val textContent: String
        get() = content?.let(::decodeUtf8Strict)
            ?: requireNotNull(metadataSummary) { "canonical_memory_record_content_unavailable" }

    fun copyContentBytes(): ByteArray? = content?.copyOf()

    fun copyProvenanceBytes(): ByteArray? = provenance?.copyOf()
}

internal data class CanonicalMemorySnapshot(
    val instanceId: String,
    val companionName: String,
    val birthRoot: GenesisUltraFirstMemoryEvent,
    val records: List<CanonicalMemoryRecord>
) {
    val postBirthEventCount: Int
        get() = records.size

    val lastSequence: Long
        get() = records.lastOrNull()?.event?.sequence ?: birthRoot.sequence
}

/**
 * The single product boundary for Genesis Ultra living memory.
 *
 * Event metadata is signed by the active Body. Recoverable private content and
 * provenance are stored atomically beside the event and are accepted only when
 * their digests, instance, sequence and event hash match the verified chain.
 */
internal class CanonicalMemoryRepository private constructor(
    private val database: MorimilDatabase,
    private val bodyIdentityRootStore: GenesisUltraAndroidBodyIdentityRootStore,
    private val guardianTrustAnchorStore: GenesisUltraAndroidGuardianTrustAnchorStore,
    private val identityRepository: GenesisUltraRuntimeIdentityRepository,
    private val clockMillis: () -> Long = System::currentTimeMillis
) {
    private val payloadDao = database.genesisUltraMemoryPayloadDao()
    private val canonicalStore = GenesisUltraCanonicalMemoryStore(database)

    suspend fun appendText(command: CanonicalMemoryAppendCommand): CanonicalMemoryRecord {
        val cleanContent = command.content.trim()
        require(cleanContent.isNotEmpty()) { "canonical_memory_content_empty" }
        require(command.contentType in TEXT_CONTENT_TYPES) {
            "canonical_memory_content_type_not_supported"
        }
        require(cleanContent.toByteArray(StandardCharsets.UTF_8).size <= MAX_CONTENT_BYTES) {
            "canonical_memory_content_too_large"
        }

        val session = prepareSession()
        val contentBytes = cleanContent.toByteArray(StandardCharsets.UTF_8)
        val provenanceBytes = encodeProvenance(command.provenance, session.identity)
        require(provenanceBytes.size <= MAX_PROVENANCE_BYTES) {
            "canonical_memory_provenance_too_large"
        }
        val contentDigest = GenesisUltraHashProfile.sha256(contentBytes)
        val provenanceDigest = GenesisUltraHashProfile.sha256(provenanceBytes)
        val request = GenesisUltraCanonicalMemoryAppendRequest(
            eventId = command.eventId?.trim()?.takeIf { value -> value.isNotEmpty() }
                ?: "memory_${UUID.randomUUID().toString().replace("-", "")}",
            eventType = command.eventType.trim(),
            actor = command.actor.trim(),
            contentDigest = contentDigest,
            contentType = command.contentType,
            observedAt = command.observedAt,
            provenanceDigest = provenanceDigest,
            privacy = PRIVATE_LOCAL
        )

        return MemoryAppendGate.withAppendLock {
            database.withTransaction {
                val recovered = requireNotNull(
                    GenesisUltraAtomicBirthStore(database).recoverCommittedInsideTransaction(
                        session.recoveryRequest
                    )
                ) { "canonical_memory_birth_not_committed" }
                val append = canonicalStore.appendInsideTransaction(
                    recoveredBirth = recovered,
                    signer = session.signer,
                    request = request
                )
                val event = append.event
                val payload = GenesisUltraMemoryPayloadEntity(
                    eventHash = event.eventHash,
                    instanceId = event.instanceId,
                    sequence = event.sequence,
                    contentDigest = contentDigest,
                    contentType = command.contentType,
                    contentByteCount = contentBytes.size.toLong(),
                    contentBytes = contentBytes.copyOf(),
                    provenanceDigest = provenanceDigest,
                    provenanceType = PROVENANCE_TYPE,
                    provenanceByteCount = provenanceBytes.size.toLong(),
                    provenanceBytes = provenanceBytes.copyOf(),
                    privacy = PRIVATE_LOCAL,
                    persistedAtMillis = clockMillis()
                )
                payloadDao.insert(payload)
                val persisted = requireNotNull(payloadDao.loadByEventHash(event.eventHash)) {
                    "canonical_memory_payload_commit_missing"
                }
                requirePayloadMatchesEvent(persisted, event)
                CanonicalMemoryRecord(
                    event = event,
                    contentBytes = persisted.contentBytes,
                    provenanceBytes = persisted.provenanceBytes,
                    provenanceType = persisted.provenanceType
                )
            }
        }
    }

    suspend fun readVerifiedSnapshot(): CanonicalMemorySnapshot {
        val session = prepareSession()
        val recovered = requireNotNull(
            GenesisUltraAtomicBirthStore(database).recoverVerifiedBirth(session.recoveryRequest)
        ) { "canonical_memory_birth_not_committed" }
        val stream = canonicalStore.recoverStream(
            recoveredBirth = recovered,
            trustedBodyKey = session.signer.key
        )
        val events = stream.copyPostBirthEvents()
        val payloads = payloadDao.loadAscending(session.identity.instanceId)
        require(payloadDao.countAll() == payloadDao.countForInstance(session.identity.instanceId)) {
            "canonical_memory_foreign_instance_payloads"
        }
        val eventHashes = events.map { event -> event.eventHash }.toSet()
        require(payloads.all { payload -> payload.eventHash in eventHashes }) {
            "canonical_memory_payload_without_verified_event"
        }
        val payloadByEventHash = payloads.associateBy { payload -> payload.eventHash }
        require(payloadByEventHash.size == payloads.size) {
            "canonical_memory_payload_event_hash_duplicate"
        }
        val records = events.map { event ->
            val payload = payloadByEventHash[event.eventHash]
            if (payload != null) {
                requirePayloadMatchesEvent(payload, event)
                CanonicalMemoryRecord(
                    event = event,
                    contentBytes = payload.contentBytes,
                    provenanceBytes = payload.provenanceBytes,
                    provenanceType = payload.provenanceType
                )
            } else {
                requireActivationMetadataEvent(event, session.identity)
                CanonicalMemoryRecord(
                    event = event,
                    contentBytes = null,
                    provenanceBytes = null,
                    provenanceType = METADATA_ONLY,
                    metadataSummary =
                        "Genesis Ultra activation confirmed; " +
                            "authorization_digest=${event.contentDigest}; " +
                            "receipt_digest=${event.provenanceDigest}."
                )
            }
        }
        return CanonicalMemorySnapshot(
            instanceId = session.identity.instanceId,
            companionName = session.identity.companionName,
            birthRoot = stream.livingRoot.event,
            records = records
        )
    }

    suspend fun buildVerifiedContext(query: String, limit: Int = DEFAULT_CONTEXT_LIMIT): String {
        require(limit in 1..MAX_CONTEXT_LIMIT) { "canonical_memory_context_limit_invalid" }
        val snapshot = readVerifiedSnapshot()
        val cleanQuery = query.trim()
        val selected = selectRecords(snapshot.records, cleanQuery, limit)
        return buildString {
            appendLine("CANONICAL GENESIS ULTRA MEMORY")
            appendLine("instance_id=${snapshot.instanceId}")
            appendLine("birth_root_sequence=${snapshot.birthRoot.sequence}")
            appendLine("birth_root_hash=${snapshot.birthRoot.eventHash}")
            appendLine("post_birth_events=${snapshot.postBirthEventCount}")
            appendLine("verification=full_chain_payload_digests_and_ceremony_links_verified")
            appendLine()
            appendLine("RELEVANT VERIFIED EVENTS:")
            if (selected.isEmpty()) {
                append("- No verified post-birth memory matched this query.")
            } else {
                selected.sortedBy { record -> record.event.sequence }.forEach { record ->
                    appendLine(
                        "- sequence=${record.event.sequence}; type=${record.event.eventType}; " +
                            "actor=${record.event.actor}; observed_at=${record.event.observedAt}; " +
                            "payload=${if (record.hasPayload) "present" else "metadata_only"}"
                    )
                    appendLine("  ${record.textContent.replace('\n', ' ').take(MAX_CONTEXT_EVENT_CHARS)}")
                }
            }
        }.trim()
    }

    private suspend fun prepareSession(): Session {
        val identity = requireNotNull(identityRepository.readCommittedIdentity()) {
            "canonical_memory_identity_not_recoverable"
        }
        val root = bodyIdentityRootStore.loadExisting()
        require(
            root.bodyId == identity.activeBody.bodyId &&
                root.keyEpochId == identity.activeBody.keyEpochId &&
                root.publicKeyRef == identity.activeBody.publicKeyFingerprint
        ) { "canonical_memory_body_root_identity_mismatch" }
        val signer = bodyIdentityRootStore.signerForInstance(identity.instanceId)
        require(
            signer.key.instanceId == identity.instanceId &&
                signer.key.bodyId == identity.activeBody.bodyId &&
                signer.key.keyEpochId == identity.activeBody.keyEpochId &&
                signer.key.publicKeyRef == identity.activeBody.publicKeyFingerprint
        ) { "canonical_memory_signer_identity_mismatch" }
        val guardianRegistry = guardianTrustAnchorStore.loadExistingRegistry()
        return Session(
            identity = identity,
            signer = signer,
            recoveryRequest = GenesisUltraAtomicBirthRecoveryRequest(
                guardianKeyEpochRegistry = guardianRegistry,
                bodyRawPublicKey = root.copyRawPublicKey()
            )
        )
    }

    private fun requireActivationMetadataEvent(
        event: GenesisUltraFirstMemoryEvent,
        identity: GenesisUltraRuntimeIdentity
    ) {
        require(
            event.sequence == 1L &&
                event.eventType == ACTIVATION_EVENT_TYPE &&
                event.actor == ACTIVATION_ACTOR &&
                event.contentType == ACTIVATION_CONTENT_TYPE &&
                event.contentDigest == identity.authorization.authorizationDigest &&
                event.provenanceDigest == identity.authorization.receiptDigest &&
                event.privacy == PRIVATE_LOCAL
        ) { "canonical_memory_payload_missing:${event.sequence}" }
    }

    private fun requirePayloadMatchesEvent(
        payload: GenesisUltraMemoryPayloadEntity,
        event: GenesisUltraFirstMemoryEvent
    ) {
        require(
            payload.eventHash == event.eventHash &&
                payload.instanceId == event.instanceId &&
                payload.sequence == event.sequence &&
                payload.contentDigest == event.contentDigest &&
                payload.contentType == event.contentType &&
                payload.provenanceDigest == event.provenanceDigest &&
                payload.privacy == event.privacy &&
                payload.provenanceType == PROVENANCE_TYPE &&
                payload.contentByteCount == payload.contentBytes.size.toLong() &&
                payload.provenanceByteCount == payload.provenanceBytes.size.toLong() &&
                GenesisUltraHashProfile.sha256(payload.contentBytes) == payload.contentDigest &&
                GenesisUltraHashProfile.sha256(payload.provenanceBytes) == payload.provenanceDigest
        ) { "canonical_memory_payload_event_mismatch:${event.sequence}" }
        decodeUtf8Strict(payload.contentBytes)
        decodeUtf8Strict(payload.provenanceBytes)
    }

    private fun encodeProvenance(
        provenance: CanonicalMemoryProvenance,
        identity: GenesisUltraRuntimeIdentity
    ): ByteArray {
        val source = provenance.source.trim()
        val classification = provenance.classification.trim()
        require(source.isNotEmpty()) { "canonical_memory_provenance_source_empty" }
        require(classification.isNotEmpty()) { "canonical_memory_provenance_classification_empty" }
        return JSONObject()
            .put("schema", PROVENANCE_SCHEMA)
            .put("instance_id", identity.instanceId)
            .put("body_id", identity.activeBody.bodyId)
            .put("source", source)
            .put("classification", classification)
            .put("user_confirmed", provenance.userConfirmed)
            .put("source_id", provenance.sourceId ?: JSONObject.NULL)
            .put("note", provenance.note ?: JSONObject.NULL)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
    }

    private fun selectRecords(
        records: List<CanonicalMemoryRecord>,
        query: String,
        limit: Int
    ): List<CanonicalMemoryRecord> {
        if (query.isBlank()) return records.takeLast(limit)
        val terms = query.lowercase()
            .split(NON_WORD)
            .filter { term -> term.length >= 2 }
            .distinct()
        if (terms.isEmpty()) return records.takeLast(limit)
        return records.map { record ->
            val searchable = buildString {
                append(record.event.eventType)
                append(' ')
                append(record.event.actor)
                append(' ')
                append(record.textContent)
            }.lowercase()
            record to terms.count { term -> searchable.contains(term) }
        }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<CanonicalMemoryRecord, Int>> { (_, score) -> score }
                    .thenByDescending { (record, _) -> record.event.sequence }
            )
            .take(limit)
            .map { (record, _) -> record }
    }

    private data class Session(
        val identity: GenesisUltraRuntimeIdentity,
        val signer: GenesisUltraBodyMemorySigner,
        val recoveryRequest: GenesisUltraAtomicBirthRecoveryRequest
    )

    internal companion object {
        fun production(
            database: MorimilDatabase,
            bodyIdentityRootStore: GenesisUltraAndroidBodyIdentityRootStore,
            guardianTrustAnchorStore: GenesisUltraAndroidGuardianTrustAnchorStore,
            identityRepository: GenesisUltraRuntimeIdentityRepository,
            clockMillis: () -> Long = System::currentTimeMillis
        ): CanonicalMemoryRepository {
            return CanonicalMemoryRepository(
                database = database,
                bodyIdentityRootStore = bodyIdentityRootStore,
                guardianTrustAnchorStore = guardianTrustAnchorStore,
                identityRepository = identityRepository,
                clockMillis = clockMillis
            )
        }

        private val TEXT_CONTENT_TYPES = setOf(
            "text/plain",
            "text/markdown",
            "application/json"
        )
        private val NON_WORD = Regex("[^\\p{L}\\p{N}_-]+")
        private const val PRIVATE_LOCAL = "private_local"
        private const val PROVENANCE_TYPE = "application/json"
        private const val PROVENANCE_SCHEMA = "morimil.canonical_memory.provenance.v1"
        private const val METADATA_ONLY = "metadata-only"
        private const val ACTIVATION_EVENT_TYPE = "instance.activation.confirmed"
        private const val ACTIVATION_ACTOR = "host_confirmed_system"
        private const val ACTIVATION_CONTENT_TYPE =
            "application/vnd.genesis.atomic-birth-authorization+json"
        private const val MAX_CONTENT_BYTES = 64 * 1024
        private const val MAX_PROVENANCE_BYTES = 16 * 1024
        private const val DEFAULT_CONTEXT_LIMIT = 12
        private const val MAX_CONTEXT_LIMIT = 40
        private const val MAX_CONTEXT_EVENT_CHARS = 1200
    }
}

private fun decodeUtf8Strict(bytes: ByteArray): String {
    val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return decoder.decode(ByteBuffer.wrap(bytes)).toString()
}
