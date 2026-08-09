package com.morimil.app.data.repository

import com.morimil.app.data.genesis.ultra.CanonicalConsumerEvent
import com.morimil.app.data.genesis.ultra.CanonicalRestCycleSource
import java.time.Instant

internal data class RestCycleSourceEvent(
    val eventHash: String,
    val eventType: String,
    val actor: String,
    val source: String,
    val memoryKind: String,
    val tagsJson: String,
    val body: String,
    val importance: Int,
    val confidence: Int,
    val userConfirmed: Boolean,
    val observedAtMillis: Long
)

internal fun CanonicalRestCycleSource.toRestCycleSourceEvent(): RestCycleSourceEvent {
    val semantic = semantics
    return RestCycleSourceEvent(
        eventHash = event.eventHash,
        eventType = event.eventType,
        actor = event.actor,
        source = provenance.source,
        memoryKind = semantic?.memoryKind ?: "observation",
        tagsJson = "[]",
        body = content,
        importance = semantic?.importance ?: DEFAULT_IMPORTANCE,
        confidence = semantic?.confidence ?: DEFAULT_CONFIDENCE,
        userConfirmed = semantic?.userConfirmed ?: provenance.userConfirmed,
        observedAtMillis = Instant.parse(event.observedAt).toEpochMilli()
    )
}

internal fun CanonicalConsumerEvent.toRestCycleSourceEventOrNull(): RestCycleSourceEvent? {
    val contentValue = content ?: return null
    val provenanceValue = provenance ?: return null
    val semantic = semantics
    return RestCycleSourceEvent(
        eventHash = ref.eventHash,
        eventType = ref.eventType,
        actor = ref.actor,
        source = provenanceValue.source,
        memoryKind = semantic?.memoryKind ?: "observation",
        tagsJson = "[]",
        body = contentValue,
        importance = semantic?.importance ?: DEFAULT_IMPORTANCE,
        confidence = semantic?.confidence ?: DEFAULT_CONFIDENCE,
        userConfirmed = semantic?.userConfirmed ?: provenanceValue.userConfirmed,
        observedAtMillis = Instant.parse(ref.observedAt).toEpochMilli()
    )
}

private const val DEFAULT_IMPORTANCE = 50
private const val DEFAULT_CONFIDENCE = 50
