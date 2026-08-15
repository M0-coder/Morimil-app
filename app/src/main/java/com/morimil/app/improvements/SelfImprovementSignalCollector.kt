package com.morimil.app.improvements

import java.util.Locale

/**
 * Converts runtime health signals into content-bound self-improvement observations.
 *
 * Signals are recorded as DETECTED only. Occurrence counters are audit metadata,
 * never part of the stable observation identity used for cooldown deduplication.
 */
internal class SelfImprovementSignalCollector(
    private val auditStore: SelfImprovementAuditStore,
    private val duplicateCooldownMillis: Long = DEFAULT_DUPLICATE_COOLDOWN_MILLIS
) {
    init {
        require(duplicateCooldownMillis >= 0L) { "self_signal_cooldown_invalid" }
    }

    fun captureInternalRuntimeIssue(
        component: String,
        message: String,
        failureCount: Int,
        occurredAtMillis: Long
    ): SelfChangeObservation? {
        require(failureCount > 0) { "self_signal_failure_count_invalid" }
        val cleanComponent = component.cleanSignal(MAX_COMPONENT_LENGTH)
        val cleanMessage = message.cleanSignal(MAX_MESSAGE_LENGTH)
        require(cleanComponent.isNotEmpty()) { "self_signal_component_empty" }
        require(cleanMessage.isNotEmpty()) { "self_signal_message_empty" }
        val surfaces = classifySurfaces(cleanComponent)
        val observation = SelfChangeObservation.create(
            changeId = "runtime_issue_${stableId(cleanComponent)}",
            problem = "Runtime issue in $cleanComponent: $cleanMessage.",
            proposal = "Diagnose the exact runtime cause and prepare the smallest evidence-bound correction without widening authority.",
            surfaces = surfaces
        )
        return appendDetectedIfFresh(
            observation = observation,
            occurredAtMillis = occurredAtMillis,
            occurrenceCount = failureCount
        )
    }

    fun captureChatError(error: String, occurredAtMillis: Long): SelfChangeObservation? {
        val cleanError = error.cleanSignal(MAX_MESSAGE_LENGTH)
        require(cleanError.isNotEmpty()) { "self_signal_chat_error_empty" }
        val observation = SelfChangeObservation.create(
            changeId = "runtime_issue_chat_reasoning",
            problem = "Chat/reasoning runtime reported: $cleanError",
            proposal = "Diagnose endpoint, provider, model, local runtime and fallback evidence before preparing a bounded correction.",
            surfaces = setOf(SelfChangeSurface.REASONING_RUNTIME)
        )
        return appendDetectedIfFresh(observation, occurredAtMillis, occurrenceCount = 1)
    }

    fun captureMemoryAttention(occurredAtMillis: Long): SelfChangeObservation? {
        val observation = SelfChangeObservation.create(
            changeId = "runtime_issue_canonical_memory_attention",
            problem = "Verified runtime health reports canonical memory requires attention.",
            proposal = "Audit canonical memory integrity and quarantine evidence before proposing any memory-affecting correction.",
            surfaces = setOf(SelfChangeSurface.CANONICAL_MEMORY)
        )
        return appendDetectedIfFresh(observation, occurredAtMillis, occurrenceCount = 1)
    }

    private fun appendDetectedIfFresh(
        observation: SelfChangeObservation,
        occurredAtMillis: Long,
        occurrenceCount: Int
    ): SelfChangeObservation? {
        require(occurredAtMillis >= 0L) { "self_signal_time_invalid" }
        val latestMatching = auditStore.readVerifiedRecords()
            .asReversed()
            .firstOrNull { record ->
                record.observationDigest == observation.observationDigest
            }
        if (
            latestMatching != null &&
            occurredAtMillis >= latestMatching.recordedAtMillis &&
            occurredAtMillis - latestMatching.recordedAtMillis < duplicateCooldownMillis
        ) {
            return null
        }
        val candidate = SelfImprovementProtocol.detect(observation)
        auditStore.append(
            candidate = candidate,
            actor = SelfChangeActor.MORIMIL,
            recordedAtMillis = occurredAtMillis,
            occurrenceCount = occurrenceCount
        )
        return observation
    }

    private fun classifySurfaces(component: String): Set<SelfChangeSurface> {
        val value = component.lowercase(Locale.ROOT)
        val surfaces = linkedSetOf<SelfChangeSurface>()
        if (value.contains("genesis") || value.contains("birth")) {
            surfaces += SelfChangeSurface.GENESIS
            surfaces += SelfChangeSurface.INSTANCE_IDENTITY
        }
        if (
            value.contains("memory") || value.contains("recall") || value.contains("rest") ||
            value.contains("migration")
        ) {
            surfaces += SelfChangeSurface.CANONICAL_MEMORY
        }
        if (
            value.contains("sign") || value.contains("keystore") || value.contains("security") ||
            value.contains("secret")
        ) {
            surfaces += SelfChangeSurface.SECURITY_BOUNDARY
        }
        if (value.contains("build") || value.contains("release") || value.contains("supply")) {
            surfaces += SelfChangeSurface.BUILD_AND_SUPPLY_CHAIN
        }
        if (value.contains("body") || value.contains("writer") || value.contains("epoch")) {
            surfaces += SelfChangeSurface.WRITER_AUTHORITY
            surfaces += SelfChangeSurface.BODY_SUCCESSION
        }
        if (surfaces.isEmpty()) surfaces += SelfChangeSurface.REASONING_RUNTIME
        return surfaces
    }

    private fun stableId(value: String): String {
        return value.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "unknown" }
            .take(MAX_COMPONENT_LENGTH)
    }

    private fun String.cleanSignal(maxLength: Int): String {
        return replace(Regex("\\s+"), " ").trim().take(maxLength)
    }

    private companion object {
        const val DEFAULT_DUPLICATE_COOLDOWN_MILLIS = 15L * 60L * 1000L
        const val MAX_COMPONENT_LENGTH = 80
        const val MAX_MESSAGE_LENGTH = 180
    }
}
