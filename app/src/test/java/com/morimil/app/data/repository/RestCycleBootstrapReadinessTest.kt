package com.morimil.app.data.repository

import com.morimil.app.data.genesis.ultra.CanonicalInstanceRef
import com.morimil.app.data.genesis.ultra.CanonicalReadDisposition
import com.morimil.app.data.genesis.ultra.CanonicalReadFailure
import com.morimil.app.data.genesis.ultra.CanonicalReadFailureCode
import com.morimil.app.data.genesis.ultra.CanonicalReadResult
import com.morimil.app.data.genesis.ultra.CanonicalRestCyclePlanningInput
import com.morimil.app.data.genesis.ultra.CanonicalSnapshotRef
import com.morimil.app.data.genesis.ultra.CanonicalWriterRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RestCycleBootstrapReadinessTest {
    @Test
    fun readyReturnsTheVerifiedPlanningInput() {
        val planning = planningInput()

        val resolved = RestCycleBootstrapReadiness.resolve(CanonicalReadResult.Ready(planning))

        assertSame(planning, resolved)
    }

    @Test
    fun notReadyReturnsNullWithoutThrowing() {
        val resolved = RestCycleBootstrapReadiness.resolve(
            CanonicalReadResult.Blocked(
                failure(CanonicalReadDisposition.NOT_READY, "canonical_memory_not_ready")
            )
        )

        assertNull(resolved)
    }

    @Test
    fun retryableFailsClosedWithOriginalFailure() {
        val failure = failure(
            CanonicalReadDisposition.RETRYABLE,
            "canonical_snapshot_changed_during_read"
        )

        val thrown = runCatching {
            RestCycleBootstrapReadiness.resolve(CanonicalReadResult.Blocked(failure))
        }.exceptionOrNull()

        require(thrown is CanonicalRestCycleReadException)
        assertSame(failure, thrown.failure)
        assertEquals(
            "canonical_rest_cycle_read_retryable:canonical_snapshot_changed_during_read",
            thrown.message
        )
    }

    @Test
    fun blockedFailsClosedWithOriginalFailure() {
        val failure = failure(CanonicalReadDisposition.BLOCKED, "canonical_chain_corrupt")

        val thrown = runCatching {
            RestCycleBootstrapReadiness.resolve(CanonicalReadResult.Blocked(failure))
        }.exceptionOrNull()

        require(thrown is CanonicalRestCycleReadException)
        assertSame(failure, thrown.failure)
        assertEquals("canonical_rest_cycle_read_blocked:canonical_chain_corrupt", thrown.message)
    }

    private fun failure(
        disposition: CanonicalReadDisposition,
        diagnosticCode: String
    ): CanonicalReadFailure {
        return CanonicalReadFailure(
            code = when (disposition) {
                CanonicalReadDisposition.NOT_READY -> CanonicalReadFailureCode.CANONICAL_MEMORY_ABSENT
                CanonicalReadDisposition.RETRYABLE -> CanonicalReadFailureCode.SNAPSHOT_CHANGED_DURING_READ
                CanonicalReadDisposition.BLOCKED -> CanonicalReadFailureCode.CHAIN_CORRUPT
            },
            disposition = disposition,
            diagnosticCode = diagnosticCode
        )
    }

    private fun planningInput(): CanonicalRestCyclePlanningInput {
        return CanonicalRestCyclePlanningInput(
            identity = CanonicalInstanceRef(
                instanceId = "instance_test",
                companionName = "Morimil",
                identityDigest = digest('1')
            ),
            writer = CanonicalWriterRef(
                writerBodyId = "body_test",
                writerEpochId = "epoch_test",
                writerEpochDigest = digest('2'),
                writerPublicKeyRef = digest('3'),
                registryEpoch = 1L,
                registryDigest = digest('4')
            ),
            snapshot = CanonicalSnapshotRef(
                instanceId = "instance_test",
                birthRootEventHash = digest('5'),
                birthRootSequence = 1L,
                lastEventHash = digest('6'),
                lastSequence = 1L,
                postBirthEventCount = 0,
                snapshotDigest = digest('7')
            ),
            sources = emptyList(),
            latestRestCycle = null,
            sourceSetDigest = digest('8')
        )
    }

    private fun digest(character: Char): String = "sha256:" + character.toString().repeat(64)
}
