package com.morimil.app.core.health

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNervousSystemHealthTest {
    @Test
    fun verifiedCanonicalLivingMemoryReportsHealthy() {
        val report = LocalNervousSystemHealth.build(
            input = baseInput(),
            generatedAtMillis = 1000L
        )

        assertEquals(LocalHealthStatus.HEALTHY, report.status)
        assertEquals("low", report.riskLevel)
        assertFalse(report.hasAlert)
        assertTrue(report.signals.any { signal ->
            signal.name == "canonical_memory_integrity" && signal.status == LocalHealthStatus.HEALTHY
        })
    }

    @Test
    fun blockedCanonicalEvidenceFailsClosedAsCritical() {
        val report = LocalNervousSystemHealth.build(
            input = baseInput(
                livingMemory = blockedInput()
            ),
            generatedAtMillis = 1000L
        )

        assertEquals(LocalHealthStatus.CRITICAL, report.status)
        assertTrue(report.hasAlert)
        assertTrue(report.signals.any { signal -> signal.probableCause == "canonical_evidence_blocked" })
    }

    @Test
    fun retryableCanonicalReadCannotReportHealthy() {
        val report = LocalNervousSystemHealth.build(
            input = baseInput(
                livingMemory = LocalLivingMemoryHealthInput(
                    readStatus = LivingMemoryReadStatus.RETRYABLE,
                    failureCode = "SNAPSHOT_CHANGED_DURING_READ",
                    diagnosticCode = "canonical_read_snapshot_changed_during_read"
                )
            ),
            generatedAtMillis = 1000L
        )

        assertEquals(LocalHealthStatus.DEGRADED, report.status)
        assertTrue(report.signals.any { signal -> signal.probableCause == "canonical_snapshot_retryable" })
    }

    @Test
    fun quarantineEvidenceReportsDegraded() {
        val report = LocalNervousSystemHealth.build(
            input = baseInput(
                livingMemory = readyInput(postBirthEventCount = 4, recentVerifiedEventCount = 3, quarantineEventCount = 1)
            ),
            generatedAtMillis = 1000L
        )

        assertEquals(LocalHealthStatus.DEGRADED, report.status)
        assertTrue(report.signals.any { signal -> signal.probableCause == "quarantine_events_present" })
    }

    @Test
    fun inconsistentCanonicalCountsReportCritical() {
        val report = LocalNervousSystemHealth.build(
            input = baseInput(
                livingMemory = readyInput(
                    totalCanonicalEventCount = 2,
                    postBirthEventCount = 4,
                    recentVerifiedEventCount = 4
                )
            ),
            generatedAtMillis = 1000L
        )

        assertEquals(LocalHealthStatus.CRITICAL, report.status)
        assertTrue(report.signals.any { signal -> signal.probableCause == "canonical_event_counts_inconsistent" })
    }

    @Test
    fun operationalTelemetryExplicitlyHasNoMemoryAuthority() {
        val report = LocalNervousSystemHealth.build(
            input = baseInput(),
            generatedAtMillis = 1000L
        )

        val telemetry = report.operationalTelemetry("unit_test")
        val evidence = JSONObject(telemetry.evidenceJson)

        assertEquals("nervous_system.health_ok", telemetry.type)
        assertEquals("morimil.local_nervous_system.v2", evidence.getString("schema"))
        assertEquals("operational_health", evidence.getString("class"))
        assertFalse(evidence.getBoolean("memory_authority"))
        assertFalse(evidence.getBoolean("canonical_memory_write"))
        assertFalse(evidence.getBoolean("legacy_memory_event_write"))
    }

    @Test
    fun slowCanonicalReadReportsDegraded() {
        val report = LocalNervousSystemHealth.build(
            input = baseInput(canonicalReadLatencyMillis = 2_000L),
            generatedAtMillis = 1000L
        )

        assertEquals(LocalHealthStatus.DEGRADED, report.status)
        assertTrue(report.signals.any { signal ->
            signal.name == "canonical_memory_read_latency" && signal.probableCause == "latency_above_threshold"
        })
    }

    private fun baseInput(
        livingMemory: LocalLivingMemoryHealthInput = readyInput(),
        canonicalReadLatencyMillis: Long = 20L
    ): LocalNervousSystemInput {
        return LocalNervousSystemInput(
            livingMemory = livingMemory,
            canonicalReadLatencyMillis = canonicalReadLatencyMillis
        )
    }

    private fun readyInput(
        totalCanonicalEventCount: Int = 5,
        postBirthEventCount: Int = 4,
        recentVerifiedEventCount: Int = 4,
        quarantineEventCount: Int = 0
    ): LocalLivingMemoryHealthInput {
        return LocalLivingMemoryHealthInput(
            readStatus = LivingMemoryReadStatus.READY,
            instanceId = "instance-health-001",
            writerBodyId = "body-health-001",
            writerEpochId = "epoch-health-001",
            snapshotDigest = "sha256:${"a".repeat(64)}",
            birthRootPresent = true,
            canonicalMemoryVerified = true,
            totalCanonicalEventCount = totalCanonicalEventCount,
            postBirthEventCount = postBirthEventCount,
            recentVerifiedEventCount = recentVerifiedEventCount,
            quarantineEventCount = quarantineEventCount
        )
    }

    private fun blockedInput(): LocalLivingMemoryHealthInput {
        return LocalLivingMemoryHealthInput(
            readStatus = LivingMemoryReadStatus.BLOCKED,
            failureCode = "CHAIN_CORRUPT",
            diagnosticCode = "canonical_read_previous_hash_mismatch"
        )
    }
}
