package com.morimil.app.data.repository

import com.morimil.app.data.genesis.ultra.CanonicalConsumerEvent
import com.morimil.app.data.genesis.ultra.CanonicalConsumerReadPort
import com.morimil.app.data.genesis.ultra.CanonicalPayloadState
import com.morimil.app.data.genesis.ultra.CanonicalReadDisposition
import com.morimil.app.data.genesis.ultra.CanonicalReadFailure
import com.morimil.app.data.genesis.ultra.CanonicalReadResult

internal data class CanonicalMemoryPresentationEvent(
    val eventHash: String,
    val sequence: Long,
    val eventType: String,
    val actor: String,
    val source: String,
    val privacy: String,
    val memoryKind: String,
    val importance: Int,
    val confidence: Int,
    val userConfirmed: Boolean,
    val body: String,
    val payloadState: CanonicalPayloadState
)

internal data class CanonicalMemoryPresentationSnapshot(
    val instanceId: String,
    val companionName: String,
    val birthRootEventHash: String,
    val lastEventHash: String,
    val lastSequence: Long,
    val totalEventCount: Int,
    val postBirthEventCount: Int,
    val events: List<CanonicalMemoryPresentationEvent>
)

/**
 * Read-only presentation boundary for verified Genesis Ultra living memory.
 *
 * This adapter cannot write canonical memory and never reads `memory_events`,
 * `memory_snapshots`, `genesis_core` or `local_instance_identity`.
 */
internal class CanonicalMemoryPresentationRepository(
    private val canonicalReadPort: CanonicalConsumerReadPort
) {
    suspend fun readSnapshot(
        limit: Int = DEFAULT_PRESENTATION_LIMIT
    ): CanonicalMemoryPresentationSnapshot? {
        require(limit in 1..MAX_PRESENTATION_LIMIT) {
            "canonical_memory_presentation_limit_invalid"
        }
        return when (val result = canonicalReadPort.readVerifiedSnapshot()) {
            is CanonicalReadResult.Ready -> result.value.let { snapshot ->
                val events = snapshot.events
                    .takeLast(limit)
                    .map(::toPresentationEvent)
                CanonicalMemoryPresentationSnapshot(
                    instanceId = snapshot.identity.instanceId,
                    companionName = snapshot.identity.companionName,
                    birthRootEventHash = snapshot.lineage.birthRootEventHash,
                    lastEventHash = snapshot.lineage.lastEventHash,
                    lastSequence = snapshot.lineage.lastSequence,
                    totalEventCount = 1 + snapshot.lineage.postBirthEventCount,
                    postBirthEventCount = snapshot.lineage.postBirthEventCount,
                    events = events
                )
            }

            is CanonicalReadResult.Blocked -> {
                if (result.failure.disposition == CanonicalReadDisposition.NOT_READY) {
                    null
                } else {
                    throw CanonicalMemoryPresentationReadException(result.failure)
                }
            }
        }
    }

    suspend fun loadEventsByHashes(
        hashes: Collection<String>
    ): List<CanonicalMemoryPresentationEvent> {
        val requested = hashes.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .take(MAX_PRESENTATION_LIMIT)
            .toSet()
        if (requested.isEmpty()) return emptyList()
        return readSnapshot(MAX_PRESENTATION_LIMIT)
            ?.events
            .orEmpty()
            .filter { event -> event.eventHash in requested }
    }

    private fun toPresentationEvent(event: CanonicalConsumerEvent): CanonicalMemoryPresentationEvent {
        val semantics = event.semantics
        val provenance = event.provenance
        return CanonicalMemoryPresentationEvent(
            eventHash = event.ref.eventHash,
            sequence = event.ref.sequence,
            eventType = event.ref.eventType,
            actor = event.ref.actor,
            source = provenance?.source ?: ACTIVATION_SOURCE,
            privacy = event.ref.privacy,
            memoryKind = semantics?.memoryKind ?: ACTIVATION_MEMORY_KIND,
            importance = semantics?.importance ?: ACTIVATION_IMPORTANCE,
            confidence = semantics?.confidence ?: ACTIVATION_CONFIDENCE,
            userConfirmed = semantics?.userConfirmed ?: provenance?.userConfirmed ?: false,
            body = event.content ?: ACTIVATION_PRESENTATION,
            payloadState = event.payloadState
        )
    }

    private companion object {
        const val DEFAULT_PRESENTATION_LIMIT = 80
        const val MAX_PRESENTATION_LIMIT = 240
        const val ACTIVATION_SOURCE = "genesis_ultra_activation"
        const val ACTIVATION_MEMORY_KIND = "activation"
        const val ACTIVATION_IMPORTANCE = 100
        const val ACTIVATION_CONFIDENCE = 100
        const val ACTIVATION_PRESENTATION = "Genesis Ultra activation metadata verified."
    }
}

internal class CanonicalMemoryPresentationReadException(
    val failure: CanonicalReadFailure
) : IllegalStateException(
    "canonical_memory_presentation_${failure.disposition.name.lowercase()}:${failure.diagnosticCode}"
)
