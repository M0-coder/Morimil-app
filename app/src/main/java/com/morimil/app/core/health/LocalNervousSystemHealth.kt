package com.morimil.app.core.health

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

enum class LocalHealthStatus {
    HEALTHY,
    DEGRADED,
    CRITICAL
}

enum class LivingMemoryReadStatus {
    READY,
    NOT_READY,
    RETRYABLE,
    BLOCKED
}

data class LocalHealthSignal(
    val name: String,
    val status: LocalHealthStatus,
    val probableCause: String,
    val note: String
)

data class LocalLivingMemoryHealthInput(
    val readStatus: LivingMemoryReadStatus,
    val failureCode: String? = null,
    val diagnosticCode: String? = null,
    val instanceId: String? = null,
    val writerBodyId: String? = null,
    val writerEpochId: String? = null,
    val snapshotDigest: String? = null,
    val birthRootPresent: Boolean = false,
    val canonicalMemoryVerified: Boolean = false,
    val totalCanonicalEventCount: Int = 0,
    val postBirthEventCount: Int = 0,
    val quarantineEventCount: Int = 0
)

data class LocalNervousSystemInput(
    val livingMemory: LocalLivingMemoryHealthInput,
    val canonicalReadLatencyMillis: Long
)

data class LocalHealthTelemetry(
    val type: String,
    val body: String,
    val evidenceJson: String
)

data class LocalNervousSystemObservation(
    val report: LocalNervousSystemReport,
    val telemetry: LocalHealthTelemetry
)

data class LocalNervousSystemReport(
    val status: LocalHealthStatus,
    val riskLevel: String,
    val signals: List<LocalHealthSignal>,
    val generatedAtMillis: Long
) {
    val hasAlert: Boolean
        get() = status != LocalHealthStatus.HEALTHY

    fun operationalTelemetry(source: String): LocalHealthTelemetry {
        require(source.isNotBlank()) { "local_health_source_blank" }
        val findings = signals
            .filter { signal -> signal.status != LocalHealthStatus.HEALTHY }
            .joinToString(separator = "; ") { signal ->
                "${signal.name}=${signal.status.name.lowercase(Locale.ROOT)} cause=${signal.probableCause}"
            }
            .ifBlank { "all_local_sensors_healthy" }
        val type = when (status) {
            LocalHealthStatus.CRITICAL -> "nervous_system.health_critical"
            LocalHealthStatus.DEGRADED -> "nervous_system.health_degraded"
            LocalHealthStatus.HEALTHY -> "nervous_system.health_ok"
        }
        val body = "Sistema nervioso local: status=${status.name.lowercase(Locale.ROOT)} risk=$riskLevel; $findings"
        val evidence = JSONObject()
            .put("schema", "morimil.local_nervous_system.v2")
            .put("class", "operational_health")
            .put("source", source)
            .put("status", status.name.lowercase(Locale.ROOT))
            .put("risk_level", riskLevel)
            .put("generated_at_millis", generatedAtMillis)
            .put("memory_authority", false)
            .put("canonical_memory_write", false)
            .put("legacy_memory_event_write", false)
            .put("signals", JSONArray(signals.map { signal -> signal.toJson() }))
            .toString()
        return LocalHealthTelemetry(type = type, body = body, evidenceJson = evidence)
    }

    private fun LocalHealthSignal.toJson(): JSONObject {
        return JSONObject()
            .put("name", name)
            .put("status", status.name.lowercase(Locale.ROOT))
            .put("probable_cause", probableCause)
            .put("note", note)
    }
}

object LocalNervousSystemHealth {
    private const val CANONICAL_READ_DEGRADED_LATENCY_MILLIS = 1_500L
    private val CANONICAL_DIGEST = Regex("^sha256:[a-f0-9]{64}$")

    fun build(input: LocalNervousSystemInput, generatedAtMillis: Long): LocalNervousSystemReport {
        val signals = buildList {
            add(canonicalReadSignal(input.livingMemory))
            if (input.livingMemory.readStatus == LivingMemoryReadStatus.READY) {
                add(canonicalBindingSignal(input.livingMemory))
                add(birthRootSignal(input.livingMemory.birthRootPresent))
                add(canonicalIntegritySignal(input.livingMemory.canonicalMemoryVerified))
                add(canonicalEventShapeSignal(input.livingMemory))
                add(quarantineSignal(input.livingMemory.quarantineEventCount))
            }
            add(
                latencySignal(
                    name = "canonical_memory_read_latency",
                    latencyMillis = input.canonicalReadLatencyMillis,
                    thresholdMillis = CANONICAL_READ_DEGRADED_LATENCY_MILLIS
                )
            )
        }
        val status = signals.maxOf { signal -> signal.status }
        return LocalNervousSystemReport(
            status = status,
            riskLevel = status.toRiskLevel(),
            signals = signals,
            generatedAtMillis = generatedAtMillis
        )
    }

    private fun canonicalReadSignal(input: LocalLivingMemoryHealthInput): LocalHealthSignal {
        val failure = listOfNotNull(input.failureCode, input.diagnosticCode).joinToString(":")
        return when (input.readStatus) {
            LivingMemoryReadStatus.READY -> healthy("canonical_read", "verified_living_memory_snapshot_ready")
            LivingMemoryReadStatus.NOT_READY -> degraded(
                "canonical_read",
                "canonical_memory_not_ready",
                failure.ifBlank { "canonical living memory is not ready" }
            )
            LivingMemoryReadStatus.RETRYABLE -> degraded(
                "canonical_read",
                "canonical_snapshot_retryable",
                failure.ifBlank { "canonical living-memory snapshot changed during observation" }
            )
            LivingMemoryReadStatus.BLOCKED -> critical(
                "canonical_read",
                "canonical_evidence_blocked",
                failure.ifBlank { "canonical living-memory evidence failed verification" }
            )
        }
    }

    private fun canonicalBindingSignal(input: LocalLivingMemoryHealthInput): LocalHealthSignal {
        val instanceId = input.instanceId.orEmpty()
        val writerBodyId = input.writerBodyId.orEmpty()
        val writerEpochId = input.writerEpochId.orEmpty()
        val snapshotDigest = input.snapshotDigest.orEmpty()
        return if (
            instanceId.isNotBlank() &&
            writerBodyId.isNotBlank() &&
            writerEpochId.isNotBlank() &&
            instanceId != writerBodyId &&
            CANONICAL_DIGEST.matches(snapshotDigest)
        ) {
            healthy(
                "canonical_binding",
                "instance=$instanceId writer_body=$writerBodyId writer_epoch=$writerEpochId snapshot=$snapshotDigest"
            )
        } else {
            critical(
                "canonical_binding",
                "canonical_binding_invalid",
                "Verified Health input must retain Instance/Body/epoch separation and a canonical snapshot digest."
            )
        }
    }

    private fun birthRootSignal(present: Boolean): LocalHealthSignal {
        return if (present) {
            healthy("canonical_birth_root", "birth_root_present")
        } else {
            critical("canonical_birth_root", "birth_root_missing", "Canonical living memory has no verified birth root.")
        }
    }

    private fun canonicalIntegritySignal(verified: Boolean): LocalHealthSignal {
        return if (verified) {
            healthy("canonical_memory_integrity", "verified_snapshot_and_authority")
        } else {
            critical(
                "canonical_memory_integrity",
                "canonical_memory_unverified",
                "Health cannot claim a healthy state from unverified living-memory evidence."
            )
        }
    }

    private fun canonicalEventShapeSignal(input: LocalLivingMemoryHealthInput): LocalHealthSignal {
        val countsValid =
            input.totalCanonicalEventCount >= 1 &&
                input.postBirthEventCount >= 0 &&
                input.totalCanonicalEventCount == input.postBirthEventCount + 1 &&
                input.quarantineEventCount >= 0 &&
                input.quarantineEventCount <= input.postBirthEventCount
        return if (countsValid) {
            healthy(
                "canonical_memory_activity",
                "total=${input.totalCanonicalEventCount} post_birth=${input.postBirthEventCount}"
            )
        } else {
            critical(
                "canonical_memory_activity",
                "canonical_event_counts_inconsistent",
                "total=${input.totalCanonicalEventCount}, post_birth=${input.postBirthEventCount}, quarantine=${input.quarantineEventCount}"
            )
        }
    }

    private fun quarantineSignal(count: Int): LocalHealthSignal {
        return if (count == 0) {
            healthy("canonical_quarantine", "no_quarantine_events")
        } else {
            degraded(
                "canonical_quarantine",
                "quarantine_events_present",
                "quarantine_event_count=$count"
            )
        }
    }

    private fun latencySignal(name: String, latencyMillis: Long, thresholdMillis: Long): LocalHealthSignal {
        return if (latencyMillis in 0..thresholdMillis) {
            healthy(name, "latency_millis=$latencyMillis")
        } else if (latencyMillis < 0) {
            critical(name, "invalid_latency", "latency_millis=$latencyMillis")
        } else {
            degraded(name, "latency_above_threshold", "latency_millis=$latencyMillis threshold_millis=$thresholdMillis")
        }
    }

    private fun healthy(name: String, note: String): LocalHealthSignal {
        return LocalHealthSignal(name, LocalHealthStatus.HEALTHY, "none", note)
    }

    private fun degraded(name: String, probableCause: String, note: String): LocalHealthSignal {
        return LocalHealthSignal(name, LocalHealthStatus.DEGRADED, probableCause, note)
    }

    private fun critical(name: String, probableCause: String, note: String): LocalHealthSignal {
        return LocalHealthSignal(name, LocalHealthStatus.CRITICAL, probableCause, note)
    }

    private fun LocalHealthStatus.toRiskLevel(): String {
        return when (this) {
            LocalHealthStatus.HEALTHY -> "low"
            LocalHealthStatus.DEGRADED -> "medium"
            LocalHealthStatus.CRITICAL -> "critical"
        }
    }
}
