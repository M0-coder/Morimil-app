package com.morimil.app.data.repository

import com.morimil.app.core.health.LocalHealthStatus
import com.morimil.app.data.genesis.ultra.CanonicalConsumerReadPort
import com.morimil.app.data.genesis.ultra.CanonicalHealthInput
import com.morimil.app.data.genesis.ultra.CanonicalReadDisposition
import com.morimil.app.data.genesis.ultra.CanonicalReadFailure
import com.morimil.app.data.genesis.ultra.CanonicalReadFailureCode
import com.morimil.app.data.genesis.ultra.CanonicalReadResult
import com.morimil.app.data.genesis.ultra.CanonicalRecallCandidateBatch
import com.morimil.app.data.genesis.ultra.CanonicalRestCyclePlanningInput
import com.morimil.app.data.genesis.ultra.CanonicalConsumerSnapshot
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNervousSystemRepositoryCanonicalTest {
    @Test
    fun readyCanonicalInputProducesHealthyReadOnlyObservation() = runBlocking {
        val port = FakeCanonicalHealthPort(CanonicalReadResult.Ready(readyHealthInput()))
        val repository = LocalNervousSystemRepository(
            canonicalReadPort = port,
            clockMillis = { 1_000L }
        )

        val observation = repository.observeHealth(
            source = "unit_test",
            generatedAtMillis = 2_000L
        )

        assertEquals(1, port.healthReads)
        assertEquals(LocalHealthStatus.HEALTHY, observation.report.status)
        val evidence = JSONObject(observation.telemetry.evidenceJson)
        assertFalse(evidence.getBoolean("memory_authority"))
        assertFalse(evidence.getBoolean("canonical_memory_write"))
        assertFalse(evidence.getBoolean("legacy_memory_event_write"))
    }

    @Test
    fun blockedCanonicalEvidenceFailsClosedWithoutFallback() = runBlocking {
        val port = FakeCanonicalHealthPort(
            CanonicalReadResult.Blocked(
                CanonicalReadFailure(
                    code = CanonicalReadFailureCode.CHAIN_CORRUPT,
                    disposition = CanonicalReadDisposition.BLOCKED,
                    diagnosticCode = "canonical_read_previous_hash_mismatch"
                )
            )
        )
        val repository = LocalNervousSystemRepository(port, clockMillis = { 1_000L })

        val observation = repository.observeHealth(
            source = "unit_test",
            generatedAtMillis = 2_000L
        )

        assertEquals(1, port.healthReads)
        assertEquals(LocalHealthStatus.CRITICAL, observation.report.status)
        assertTrue(observation.report.signals.any { signal ->
            signal.probableCause == "canonical_evidence_blocked"
        })
    }

    @Test
    fun notReadyCanonicalMemoryCannotReportHealthy() = runBlocking {
        val port = FakeCanonicalHealthPort(
            CanonicalReadResult.Blocked(
                CanonicalReadFailure(
                    code = CanonicalReadFailureCode.BIRTH_NOT_COMMITTED,
                    disposition = CanonicalReadDisposition.NOT_READY,
                    diagnosticCode = "canonical_read_birth_not_committed"
                )
            )
        )
        val repository = LocalNervousSystemRepository(port, clockMillis = { 1_000L })

        val observation = repository.observeHealth(
            source = "unit_test",
            generatedAtMillis = 2_000L
        )

        assertEquals(LocalHealthStatus.DEGRADED, observation.report.status)
        assertFalse(observation.report.status == LocalHealthStatus.HEALTHY)
    }

    @Test
    fun repositoryReinstantiationReReadsCanonicalEvidenceAndReconstructsSameHealth() = runBlocking {
        val port = FakeCanonicalHealthPort(CanonicalReadResult.Ready(readyHealthInput()))
        val firstProcessRepository = LocalNervousSystemRepository(port, clockMillis = { 1_000L })
        val secondProcessRepository = LocalNervousSystemRepository(port, clockMillis = { 1_000L })

        val first = firstProcessRepository.observeHealth(
            source = "restart_test",
            generatedAtMillis = 3_000L
        )
        val second = secondProcessRepository.observeHealth(
            source = "restart_test",
            generatedAtMillis = 3_000L
        )

        assertEquals(2, port.healthReads)
        assertEquals(first.report, second.report)
        assertEquals(first.telemetry, second.telemetry)
    }

    @Test
    fun blankTelemetrySourceFailsBeforeCanonicalRead() {
        val port = FakeCanonicalHealthPort(CanonicalReadResult.Ready(readyHealthInput()))
        val repository = LocalNervousSystemRepository(port, clockMillis = { 1_000L })

        val failure = runCatching {
            runBlocking { repository.observeHealth(source = " ") }
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(0, port.healthReads)
    }

    private fun readyHealthInput(): CanonicalHealthInput {
        return CanonicalHealthInput(
            instanceId = "instance-health-001",
            writerBodyId = "body-health-001",
            writerEpochId = "epoch-health-001",
            snapshotDigest = "sha256:${"b".repeat(64)}",
            birthRootPresent = true,
            canonicalMemoryVerified = true,
            totalCanonicalEventCount = 5,
            postBirthEventCount = 4,
            recentVerifiedEventCount = 4,
            latestRestCycle = null,
            quarantineEventCount = 0
        )
    }

    private class FakeCanonicalHealthPort(
        private val healthResult: CanonicalReadResult<CanonicalHealthInput>
    ) : CanonicalConsumerReadPort {
        var healthReads: Int = 0
            private set

        override suspend fun readHealthInput(recentLimit: Int): CanonicalReadResult<CanonicalHealthInput> {
            healthReads += 1
            return healthResult
        }

        override suspend fun readVerifiedSnapshot(): CanonicalReadResult<CanonicalConsumerSnapshot> = unused()

        override suspend fun readRecallCandidates(limit: Int): CanonicalReadResult<CanonicalRecallCandidateBatch> = unused()

        override suspend fun readRestCyclePlanningInput(limit: Int): CanonicalReadResult<CanonicalRestCyclePlanningInput> = unused()

        private fun unused(): CanonicalReadResult.Blocked {
            return CanonicalReadResult.Blocked(
                CanonicalReadFailure(
                    code = CanonicalReadFailureCode.UNCLASSIFIED_VERIFICATION_FAILURE,
                    disposition = CanonicalReadDisposition.BLOCKED,
                    diagnosticCode = "unused_test_path"
                )
            )
        }
    }
}
