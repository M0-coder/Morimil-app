package com.morimil.app.data.repository

import com.morimil.app.core.health.LivingMemoryReadStatus
import com.morimil.app.core.health.LocalLivingMemoryHealthInput
import com.morimil.app.core.health.LocalNervousSystemHealth
import com.morimil.app.core.health.LocalNervousSystemInput
import com.morimil.app.core.health.LocalNervousSystemObservation
import com.morimil.app.data.genesis.ultra.CanonicalConsumerReadPort
import com.morimil.app.data.genesis.ultra.CanonicalHealthInput
import com.morimil.app.data.genesis.ultra.CanonicalReadDisposition
import com.morimil.app.data.genesis.ultra.CanonicalReadFailure
import com.morimil.app.data.genesis.ultra.CanonicalReadResult

class LocalNervousSystemRepository internal constructor(
    private val canonicalReadPort: CanonicalConsumerReadPort,
    private val clockMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun observeHealth(
        source: String,
        recentLimit: Int = DEFAULT_RECENT_LIMIT,
        generatedAtMillis: Long = clockMillis()
    ): LocalNervousSystemObservation {
        require(source.isNotBlank()) { "local_health_source_blank" }
        val startedAtMillis = clockMillis()
        val livingMemory = when (val result = canonicalReadPort.readHealthInput(recentLimit)) {
            is CanonicalReadResult.Ready -> result.value.toLivingMemoryHealthInput()
            is CanonicalReadResult.Blocked -> result.failure.toLivingMemoryHealthInput()
        }
        val canonicalReadLatencyMillis = (clockMillis() - startedAtMillis).coerceAtLeast(0L)
        val report = LocalNervousSystemHealth.build(
            input = LocalNervousSystemInput(
                livingMemory = livingMemory,
                canonicalReadLatencyMillis = canonicalReadLatencyMillis
            ),
            generatedAtMillis = generatedAtMillis
        )
        return LocalNervousSystemObservation(
            report = report,
            telemetry = report.operationalTelemetry(source)
        )
    }

    private fun CanonicalHealthInput.toLivingMemoryHealthInput(): LocalLivingMemoryHealthInput {
        return LocalLivingMemoryHealthInput(
            readStatus = LivingMemoryReadStatus.READY,
            instanceId = instanceId,
            writerBodyId = writerBodyId,
            writerEpochId = writerEpochId,
            snapshotDigest = snapshotDigest,
            birthRootPresent = birthRootPresent,
            canonicalMemoryVerified = canonicalMemoryVerified,
            totalCanonicalEventCount = totalCanonicalEventCount,
            postBirthEventCount = postBirthEventCount,
            quarantineEventCount = quarantineEventCount
        )
    }

    private fun CanonicalReadFailure.toLivingMemoryHealthInput(): LocalLivingMemoryHealthInput {
        return LocalLivingMemoryHealthInput(
            readStatus = when (disposition) {
                CanonicalReadDisposition.NOT_READY -> LivingMemoryReadStatus.NOT_READY
                CanonicalReadDisposition.RETRYABLE -> LivingMemoryReadStatus.RETRYABLE
                CanonicalReadDisposition.BLOCKED -> LivingMemoryReadStatus.BLOCKED
            },
            failureCode = code.name,
            diagnosticCode = diagnosticCode
        )
    }

    private companion object {
        const val DEFAULT_RECENT_LIMIT = 20
    }
}
