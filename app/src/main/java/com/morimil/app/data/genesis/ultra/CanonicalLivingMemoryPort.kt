package com.morimil.app.data.genesis.ultra

import com.morimil.app.data.repository.LivingMemoryAppendRequest
import com.morimil.app.data.repository.LivingMemoryEventReceipt
import com.morimil.app.data.repository.LivingMemoryEventView
import com.morimil.app.data.repository.LivingMemoryPort
import java.time.Instant
import org.json.JSONObject

internal class CanonicalLivingMemoryPort(
    private val repository: CanonicalMemoryRepository
) : LivingMemoryPort {
    override suspend fun append(request: LivingMemoryAppendRequest): LivingMemoryEventReceipt {
        val body = request.body.trim()
        require(body.isNotEmpty()) { "living_memory_body_empty" }
        val eventType = request.eventType.trim()
        val actor = request.actor.trim()
        require(eventType.isNotEmpty()) { "living_memory_event_type_empty" }
        require(actor.isNotEmpty()) { "living_memory_actor_empty" }

        val note = JSONObject()
            .put("schema", "morimil.living_memory_write.v1")
            .put("source", request.source)
            .put("importance", request.importance.coerceIn(1, 100))
            .put("evidence", request.evidenceJson ?: JSONObject.NULL)
            .toString()
        val record = repository.appendText(
            CanonicalMemoryAppendCommand(
                eventType = eventType,
                actor = actor,
                content = body,
                provenance = CanonicalMemoryProvenance(
                    source = request.source,
                    classification = eventType,
                    userConfirmed = request.userConfirmed,
                    sourceId = request.eventId,
                    note = note
                ),
                eventId = request.eventId
            )
        )
        return LivingMemoryEventReceipt(
            eventHash = record.event.eventHash,
            sequence = record.event.sequence,
            eventType = record.event.eventType,
            observedAtMillis = Instant.parse(record.event.observedAt).toEpochMilli()
        )
    }

    override suspend fun loadLatestByType(eventType: String): LivingMemoryEventView? {
        val cleanType = eventType.trim()
        if (cleanType.isEmpty()) return null
        val record = repository.readVerifiedSnapshot().records
            .lastOrNull { candidate -> candidate.event.eventType == cleanType }
            ?: return null
        return LivingMemoryEventView(
            eventHash = record.event.eventHash,
            sequence = record.event.sequence,
            eventType = record.event.eventType,
            actor = record.event.actor,
            body = record.textContent,
            observedAtMillis = Instant.parse(record.event.observedAt).toEpochMilli()
        )
    }
}
