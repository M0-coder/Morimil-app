package com.morimil.app.data.genesis.ultra

import com.morimil.app.data.local.ReasoningTurnAuthor
import com.morimil.app.data.local.ReasoningTurnEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMemoryPromotionCoordinatorTest {
    @Test
    fun previewDoesNotWriteAndApprovalAppendsExactGuardianConfirmedCandidate() = runBlocking {
        val authority = authority()
        var eventExists = false
        var appendCalls = 0
        var captured: CanonicalMemoryAppendCommand? = null
        val appended = ConversationMemoryAppendedEvent(
            eventId = "pending",
            eventHash = "sha256:event",
            sequence = 7L
        )
        val coordinator = coordinator(
            authority = authority,
            canonicalEventExists = { _, _ -> eventExists },
            appendCanonical = { command ->
                appendCalls += 1
                captured = command
                eventExists = true
                appended.copy(eventId = requireNotNull(command.eventId))
            },
            verifyCanonical = { _, eventId, eventHash ->
                assertTrue(eventExists)
                ConversationMemoryAppendedEvent(eventId, eventHash, 7L)
            }
        )
        val turn = trustedTurn()

        val preview = coordinator.preview(turn)

        assertEquals(0, appendCalls)
        assertFalse(eventExists)
        assertEquals("guardian_statement", preview.classification)
        assertEquals(turn.body, preview.content)
        assertEquals(authority.guardianId, preview.guardianId)

        val receipt = coordinator.approve(preview.previewId, preview.candidateDigest)

        assertEquals(1, appendCalls)
        assertNotNull(captured)
        val command = requireNotNull(captured)
        assertEquals("conversation.turn.promoted", command.eventType)
        assertEquals(ReasoningTurnAuthor.USER, command.actor)
        assertEquals(turn.body, command.content)
        assertEquals(preview.deterministicEventId, command.eventId)
        assertEquals("reasoning_transcript", command.provenance.source)
        assertEquals("guardian_statement", command.provenance.classification)
        assertTrue(command.provenance.userConfirmed)
        assertEquals("reasoning_turn:${turn.id}", command.provenance.sourceId)
        assertTrue(command.provenance.note.orEmpty().contains(preview.candidateDigest))
        assertEquals(preview.deterministicEventId, receipt.eventId)
        assertEquals(7L, receipt.sequence)
        assertTrue(receipt.verifiedInCanonicalChain)
    }

    @Test
    fun auxiliaryAdvisoryCannotBecomeCandidate() = runBlocking {
        var appendCalls = 0
        val coordinator = coordinator(
            appendCanonical = {
                appendCalls += 1
                error("must_not_append")
            }
        )

        val error = runCatching {
            coordinator.preview(
                trustedTurn().copy(author = ReasoningTurnAuthor.AUXILIARY_ADVISORY)
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("conversation_memory_untrusted_transcript_author", error?.message)
        assertEquals(0, appendCalls)
    }

    @Test
    fun duplicateCandidateIsRejectedBeforePreviewAndBeforeAppend() = runBlocking {
        var existing = true
        var appendCalls = 0
        val coordinator = coordinator(
            canonicalEventExists = { _, _ -> existing },
            appendCanonical = {
                appendCalls += 1
                error("must_not_append")
            }
        )

        val first = runCatching { coordinator.preview(trustedTurn()) }.exceptionOrNull()
        assertTrue(first is IllegalArgumentException)
        assertEquals("conversation_memory_candidate_already_promoted", first?.message)

        existing = false
        val preview = coordinator.preview(trustedTurn())
        existing = true
        val second = runCatching {
            coordinator.approve(preview.previewId, preview.candidateDigest)
        }.exceptionOrNull()
        assertTrue(second is IllegalArgumentException)
        assertEquals("conversation_memory_candidate_already_promoted", second?.message)
        assertEquals(0, appendCalls)
    }

    @Test
    fun approvalIsDigestBoundSingleUseAndFailsClosed() = runBlocking {
        var appendCalls = 0
        val coordinator = coordinator(
            appendCanonical = { command ->
                appendCalls += 1
                ConversationMemoryAppendedEvent(
                    eventId = requireNotNull(command.eventId),
                    eventHash = "sha256:event",
                    sequence = 2L
                )
            }
        )
        val preview = coordinator.preview(trustedTurn())

        val mismatch = runCatching {
            coordinator.approve(preview.previewId, "sha256:wrong")
        }.exceptionOrNull()
        assertTrue(mismatch is IllegalArgumentException)
        assertEquals("conversation_memory_candidate_digest_mismatch", mismatch?.message)
        assertEquals(0, appendCalls)

        val receipt = coordinator.approve(preview.previewId, preview.candidateDigest)
        assertTrue(receipt.verifiedInCanonicalChain)
        assertEquals(1, appendCalls)

        val replay = runCatching {
            coordinator.approve(preview.previewId, preview.candidateDigest)
        }.exceptionOrNull()
        assertTrue(replay is IllegalArgumentException)
        assertEquals("conversation_memory_preview_missing", replay?.message)
        assertEquals(1, appendCalls)
    }

    private fun coordinator(
        authority: ConversationMemoryAuthority = authority(),
        canonicalEventExists: suspend (String, String) -> Boolean = { _, _ -> false },
        appendCanonical: suspend (CanonicalMemoryAppendCommand) ->
            ConversationMemoryAppendedEvent = { command ->
                ConversationMemoryAppendedEvent(
                    eventId = requireNotNull(command.eventId),
                    eventHash = "sha256:event",
                    sequence = 2L
                )
            },
        verifyCanonical: suspend (String, String, String) ->
            ConversationMemoryAppendedEvent = { _, eventId, eventHash ->
                ConversationMemoryAppendedEvent(eventId, eventHash, 2L)
            }
    ): ConversationMemoryPromotionCoordinator {
        return ConversationMemoryPromotionCoordinator.forTest(
            readAuthority = { authority },
            canonicalEventExists = canonicalEventExists,
            appendCanonical = appendCanonical,
            verifyCanonical = verifyCanonical,
            clockMillis = { 1_700_000_000_000L },
            nextPreviewId = { "preview-1" }
        )
    }

    private fun authority(): ConversationMemoryAuthority {
        return ConversationMemoryAuthority(
            instanceId = "instance-1",
            guardianId = "guardian-1",
            guardianKeyEpochId = "guardian-epoch-1",
            bodyId = "body-1"
        )
    }

    private fun trustedTurn(): ReasoningTurnEntity {
        return ReasoningTurnEntity(
            id = 42L,
            author = ReasoningTurnAuthor.USER,
            body = "Recuerda que el proyecto se llama Morimil.",
            createdAtMillis = 1_700_000_000_000L
        )
    }
}
