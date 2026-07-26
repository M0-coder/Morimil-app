package com.morimil.app.data.genesis.ultra

import com.morimil.app.data.local.ReasoningTurnAuthor
import com.morimil.app.data.local.ReasoningTurnEntity
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

internal enum class ConversationMemoryClassification(val value: String) {
    GUARDIAN_STATEMENT("guardian_statement"),
    MORIMIL_REFLECTION("morimil_reflection")
}

internal data class ConversationMemoryAuthority(
    val instanceId: String,
    val guardianId: String,
    val guardianKeyEpochId: String,
    val bodyId: String
)

internal data class ConversationMemoryPromotionPreview(
    val previewId: String,
    val candidateDigest: String,
    val deterministicEventId: String,
    val sourceTurnId: Long,
    val sourceAuthor: String,
    val content: String,
    val classification: String,
    val guardianId: String,
    val guardianKeyEpochId: String,
    val bodyId: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long
)

internal data class ConversationMemoryAppendedEvent(
    val eventId: String,
    val eventHash: String,
    val sequence: Long
)

internal data class ConversationMemoryPromotionReceipt(
    val candidateDigest: String,
    val eventId: String,
    val eventHash: String,
    val sequence: Long,
    val bodyId: String,
    val guardianId: String,
    val verifiedInCanonicalChain: Boolean
)

/**
 * Process-local, fail-closed ceremony that converts one trusted transcript turn
 * into a signed Genesis Ultra memory event only after an exact Guardian approval.
 *
 * Requesting a preview never writes memory. A preview is single-use, expires, and
 * is bound to the exact transcript bytes, Instance, Guardian and active Body.
 */
internal class ConversationMemoryPromotionCoordinator private constructor(
    private val readAuthority: suspend () -> ConversationMemoryAuthority,
    private val canonicalEventExists: suspend (String, String) -> Boolean,
    private val appendCanonical: suspend (CanonicalMemoryAppendCommand) ->
        ConversationMemoryAppendedEvent,
    private val verifyCanonical: suspend (String, String, String) ->
        ConversationMemoryAppendedEvent,
    private val clockMillis: () -> Long,
    private val nextPreviewId: () -> String
) {
    private data class PendingPromotion(
        val turn: ReasoningTurnEntity,
        val authority: ConversationMemoryAuthority,
        val preview: ConversationMemoryPromotionPreview
    )

    private val pendingMutex = Mutex()
    private var pending: PendingPromotion? = null

    suspend fun preview(turn: ReasoningTurnEntity): ConversationMemoryPromotionPreview {
        require(ReasoningTurnAuthor.isTrustedConversationAuthor(turn.author)) {
            "conversation_memory_untrusted_transcript_author"
        }
        require(turn.id > 0L) { "conversation_memory_turn_not_persisted" }
        val content = turn.body.trim()
        require(content.isNotEmpty()) { "conversation_memory_turn_empty" }

        val authority = readAuthority()
        val classification = classificationFor(turn.author)
        val candidateDigest = candidateDigest(authority, turn, content, classification)
        val eventId = deterministicEventId(candidateDigest)
        require(!canonicalEventExists(authority.instanceId, eventId)) {
            "conversation_memory_candidate_already_promoted"
        }

        val now = clockMillis()
        val preview = ConversationMemoryPromotionPreview(
            previewId = nextPreviewId(),
            candidateDigest = candidateDigest,
            deterministicEventId = eventId,
            sourceTurnId = turn.id,
            sourceAuthor = turn.author,
            content = content,
            classification = classification.value,
            guardianId = authority.guardianId,
            guardianKeyEpochId = authority.guardianKeyEpochId,
            bodyId = authority.bodyId,
            createdAtMillis = turn.createdAtMillis,
            expiresAtMillis = now + PREVIEW_TTL_MILLIS
        )
        pendingMutex.withLock {
            pending = PendingPromotion(
                turn = turn.copy(body = content),
                authority = authority,
                preview = preview
            )
        }
        return preview
    }

    suspend fun approve(
        previewId: String,
        expectedCandidateDigest: String
    ): ConversationMemoryPromotionReceipt {
        val selected = pendingMutex.withLock {
            val current = requireNotNull(pending) { "conversation_memory_preview_missing" }
            require(current.preview.previewId == previewId) {
                "conversation_memory_preview_id_mismatch"
            }
            require(current.preview.candidateDigest == expectedCandidateDigest) {
                "conversation_memory_candidate_digest_mismatch"
            }
            require(clockMillis() <= current.preview.expiresAtMillis) {
                "conversation_memory_preview_expired"
            }
            pending = null
            current
        }

        val authority = readAuthority()
        require(authority == selected.authority) { "conversation_memory_authority_changed" }

        val classification = classificationFor(selected.turn.author)
        val recomputedDigest = candidateDigest(
            authority = authority,
            turn = selected.turn,
            content = selected.preview.content,
            classification = classification
        )
        require(recomputedDigest == selected.preview.candidateDigest) {
            "conversation_memory_candidate_changed_after_preview"
        }
        require(deterministicEventId(recomputedDigest) == selected.preview.deterministicEventId) {
            "conversation_memory_event_id_changed_after_preview"
        }
        require(!canonicalEventExists(authority.instanceId, selected.preview.deterministicEventId)) {
            "conversation_memory_candidate_already_promoted"
        }

        val approvalNote = JSONObject()
            .put("schema", APPROVAL_NOTE_SCHEMA)
            .put("candidate_digest", selected.preview.candidateDigest)
            .put("source_turn_id", selected.preview.sourceTurnId)
            .put("source_author", selected.preview.sourceAuthor)
            .put("source_created_at_millis", selected.preview.createdAtMillis)
            .put("guardian_id", selected.preview.guardianId)
            .put("guardian_key_epoch_id", selected.preview.guardianKeyEpochId)
            .put("body_id", selected.preview.bodyId)
            .put("approval", "explicit_guardian_confirmation")
            .toString()

        val appended = appendCanonical(
            CanonicalMemoryAppendCommand(
                eventType = PROMOTION_EVENT_TYPE,
                actor = selected.preview.sourceAuthor,
                content = selected.preview.content,
                observedAt = Instant.ofEpochMilli(selected.preview.createdAtMillis)
                    .truncatedTo(ChronoUnit.SECONDS)
                    .toString(),
                provenance = CanonicalMemoryProvenance(
                    source = TRANSCRIPT_SOURCE,
                    classification = selected.preview.classification,
                    userConfirmed = true,
                    sourceId = "reasoning_turn:${selected.preview.sourceTurnId}",
                    note = approvalNote
                ),
                eventId = selected.preview.deterministicEventId
            )
        )
        require(appended.eventId == selected.preview.deterministicEventId) {
            "conversation_memory_persisted_event_id_mismatch"
        }

        val verified = verifyCanonical(
            authority.instanceId,
            selected.preview.deterministicEventId,
            appended.eventHash
        )
        require(verified == appended) { "conversation_memory_verified_event_mismatch" }

        return ConversationMemoryPromotionReceipt(
            candidateDigest = selected.preview.candidateDigest,
            eventId = verified.eventId,
            eventHash = verified.eventHash,
            sequence = verified.sequence,
            bodyId = selected.preview.bodyId,
            guardianId = selected.preview.guardianId,
            verifiedInCanonicalChain = true
        )
    }

    suspend fun dismiss(previewId: String) {
        pendingMutex.withLock {
            if (pending?.preview?.previewId == previewId) pending = null
        }
    }

    private fun classificationFor(author: String): ConversationMemoryClassification {
        return when (author) {
            ReasoningTurnAuthor.USER -> ConversationMemoryClassification.GUARDIAN_STATEMENT
            ReasoningTurnAuthor.MORIMIL -> ConversationMemoryClassification.MORIMIL_REFLECTION
            else -> error("conversation_memory_untrusted_transcript_author")
        }
    }

    private fun candidateDigest(
        authority: ConversationMemoryAuthority,
        turn: ReasoningTurnEntity,
        content: String,
        classification: ConversationMemoryClassification
    ): String {
        return GenesisUltraHashProfile.hashFields(
            CANDIDATE_DIGEST_DOMAIN,
            listOf(
                authority.instanceId,
                authority.guardianId,
                authority.guardianKeyEpochId,
                authority.bodyId,
                turn.id.toString(),
                turn.author,
                turn.createdAtMillis.toString(),
                classification.value,
                content
            )
        )
    }

    private fun deterministicEventId(candidateDigest: String): String {
        return EVENT_ID_PREFIX + GenesisUltraHashProfile
            .sha256(candidateDigest.toByteArray(StandardCharsets.UTF_8))
            .removePrefix("sha256:")
    }

    internal companion object {
        fun production(
            canonicalRepository: CanonicalMemoryRepository,
            identityRepository: GenesisUltraRuntimeIdentityRepository,
            clockMillis: () -> Long = System::currentTimeMillis
        ): ConversationMemoryPromotionCoordinator {
            return ConversationMemoryPromotionCoordinator(
                readAuthority = {
                    val identity = requireNotNull(identityRepository.readCommittedIdentity()) {
                        "conversation_memory_identity_not_committed"
                    }
                    ConversationMemoryAuthority(
                        instanceId = identity.instanceId,
                        guardianId = identity.guardian.guardianId,
                        guardianKeyEpochId = identity.guardian.keyEpochId,
                        bodyId = identity.activeBody.bodyId
                    )
                },
                canonicalEventExists = { instanceId, eventId ->
                    val snapshot = canonicalRepository.readVerifiedSnapshot()
                    require(snapshot.instanceId == instanceId) {
                        "conversation_memory_snapshot_instance_mismatch"
                    }
                    snapshot.records.any { record -> record.event.eventId == eventId }
                },
                appendCanonical = { command ->
                    val record = canonicalRepository.appendText(command)
                    ConversationMemoryAppendedEvent(
                        eventId = record.event.eventId,
                        eventHash = record.event.eventHash,
                        sequence = record.event.sequence
                    )
                },
                verifyCanonical = { instanceId, eventId, eventHash ->
                    val snapshot = canonicalRepository.readVerifiedSnapshot()
                    require(snapshot.instanceId == instanceId) {
                        "conversation_memory_verified_instance_mismatch"
                    }
                    val record = requireNotNull(
                        snapshot.records.singleOrNull { candidate ->
                            candidate.event.eventId == eventId
                        }
                    ) { "conversation_memory_verified_event_missing" }
                    require(record.event.eventHash == eventHash) {
                        "conversation_memory_verified_event_hash_mismatch"
                    }
                    ConversationMemoryAppendedEvent(
                        eventId = record.event.eventId,
                        eventHash = record.event.eventHash,
                        sequence = record.event.sequence
                    )
                },
                clockMillis = clockMillis,
                nextPreviewId = { UUID.randomUUID().toString() }
            )
        }

        fun forTest(
            readAuthority: suspend () -> ConversationMemoryAuthority,
            canonicalEventExists: suspend (String, String) -> Boolean,
            appendCanonical: suspend (CanonicalMemoryAppendCommand) ->
                ConversationMemoryAppendedEvent,
            verifyCanonical: suspend (String, String, String) ->
                ConversationMemoryAppendedEvent,
            clockMillis: () -> Long,
            nextPreviewId: () -> String
        ): ConversationMemoryPromotionCoordinator {
            return ConversationMemoryPromotionCoordinator(
                readAuthority = readAuthority,
                canonicalEventExists = canonicalEventExists,
                appendCanonical = appendCanonical,
                verifyCanonical = verifyCanonical,
                clockMillis = clockMillis,
                nextPreviewId = nextPreviewId
            )
        }

        private const val PREVIEW_TTL_MILLIS = 5 * 60 * 1_000L
        private const val PROMOTION_EVENT_TYPE = "conversation.turn.promoted"
        private const val TRANSCRIPT_SOURCE = "reasoning_transcript"
        private const val APPROVAL_NOTE_SCHEMA = "morimil.conversation_memory_guardian_approval.v1"
        private const val CANDIDATE_DIGEST_DOMAIN = "morimil.conversation_memory_candidate.v1"
        private const val EVENT_ID_PREFIX = "conversation_memory_"
    }
}
