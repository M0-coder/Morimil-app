package com.morimil.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.morimil.app.core.health.LocalHealthStatus
import com.morimil.app.data.genesis.ultra.CanonicalConsumerReadPort
import com.morimil.app.data.genesis.ultra.CanonicalConsumerSnapshot
import com.morimil.app.data.genesis.ultra.CanonicalHealthInput
import com.morimil.app.data.genesis.ultra.CanonicalReadDisposition
import com.morimil.app.data.genesis.ultra.CanonicalReadFailure
import com.morimil.app.data.genesis.ultra.CanonicalReadFailureCode
import com.morimil.app.data.genesis.ultra.CanonicalReadResult
import com.morimil.app.data.genesis.ultra.CanonicalRecallCandidateBatch
import com.morimil.app.data.genesis.ultra.CanonicalRestCyclePlanningInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalNervousSystemCanonicalAndroidTest {
    @Test
    fun reconstructedObserverReReadsCanonicalLivingMemoryWithoutPersistedHealthState() = runBlocking {
        val port = FakeCanonicalHealthPort(CanonicalReadResult.Ready(readyHealthInput()))

        val firstObserver = LocalNervousSystemRepository(port, clockMillis = { 10_000L })
        val beforeRestart = firstObserver.observeHealth(
            source = "managed_device_restart",
            generatedAtMillis = 20_000L
        )

        val reconstructedObserver = LocalNervousSystemRepository(port, clockMillis = { 10_000L })
        val afterRestart = reconstructedObserver.observeHealth(
            source = "managed_device_restart",
            generatedAtMillis = 20_000L
        )

        assertEquals(2, port.healthReads)
        assertEquals(LocalHealthStatus.HEALTHY, beforeRestart.report.status)
        assertEquals(beforeRestart.report, afterRestart.report)
        assertEquals(beforeRestart.telemetry, afterRestart.telemetry)
        assertFalse(beforeRestart.telemetry.evidenceJson.contains("memory_authority\":true"))
    }

    @Test
    fun blockedCanonicalEvidenceNeverFallsBackToHealthyOnDevice() = runBlocking {
        val port = FakeCanonicalHealthPort(
            CanonicalReadResult.Blocked(
                CanonicalReadFailure(
                    code = CanonicalReadFailureCode.PAYLOAD_INTEGRITY_INVALID,
                    disposition = CanonicalReadDisposition.BLOCKED,
                    diagnosticCode = "canonical_read_payload_integrity_invalid"
                )
            )
        )
        val observer = LocalNervousSystemRepository(port, clockMillis = { 10_000L })

        val observation = observer.observeHealth(
            source = "managed_device_blocked",
            generatedAtMillis = 20_000L
        )

        assertEquals(LocalHealthStatus.CRITICAL, observation.report.status)
        assertEquals(1, port.healthReads)
    }

    private fun readyHealthInput(): CanonicalHealthInput {
        return CanonicalHealthInput(
            instanceId = "instance-health-android",
            writerBodyId = "body-health-android",
            writerEpochId = "epoch-health-android",
            snapshotDigest = "sha256:${"c".repeat(64)}",
            birthRootPresent = true,
            canonicalMemoryVerified = true,
            totalCanonicalEventCount = 3,
            postBirthEventCount = 2,
            recentVerifiedEventCount = 2,
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
                    diagnosticCode = "unused_android_test_path"
                )
            )
        }
    }
}
