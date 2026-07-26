package com.morimil.app.data.genesis.ultra

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalMemoryQuarantineStoreTest {
    @After
    fun clearStore() {
        CanonicalMemoryQuarantineStore.clearForTest()
    }

    @Test
    fun integrityFailureCreatesTypedVisibleQuarantine() = runBlocking {
        val failure = runCatching {
            CanonicalMemoryQuarantineStore.verify(
                stage = "event_chain",
                clockMillis = { 1234L }
            ) {
                throw IllegalArgumentException("canonical_memory_signature_invalid:2")
            }
        }.exceptionOrNull()

        assertTrue(failure is CanonicalMemoryQuarantinedException)
        val diagnostic = CanonicalMemoryQuarantineStore.diagnostic.value
        assertEquals("canonical_memory_signature_invalid", diagnostic?.code)
        assertEquals("event_chain", diagnostic?.stage)
        assertEquals(2L, diagnostic?.sequence)
        assertEquals(1234L, diagnostic?.detectedAtMillis)
        assertTrue(diagnostic?.visibleMessage()?.contains("Ningún contenido de esa cadena entró al prompt") == true)
    }

    @Test
    fun successfulFullVerificationClearsDerivedQuarantine() = runBlocking {
        runCatching {
            CanonicalMemoryQuarantineStore.verify(stage = "event_chain") {
                throw IllegalArgumentException("canonical_memory_chain_mismatch:4")
            }
        }
        assertTrue(CanonicalMemoryQuarantineStore.diagnostic.value != null)

        val result = CanonicalMemoryQuarantineStore.verify(stage = "event_chain") { "verified" }

        assertEquals("verified", result)
        assertNull(CanonicalMemoryQuarantineStore.diagnostic.value)
    }

    @Test
    fun cancellationPropagatesWithoutCreatingQuarantine() = runBlocking {
        val failure = runCatching {
            CanonicalMemoryQuarantineStore.verify(stage = "event_chain") {
                throw CancellationException("cancelled")
            }
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertNull(CanonicalMemoryQuarantineStore.diagnostic.value)
    }

    @Test
    fun nonIntegrityAppendFailureIsNotConvertedIntoQuarantine() {
        val original = IllegalStateException("body_memory_signing_failed")

        val returned = CanonicalMemoryQuarantineStore.quarantineIfIntegrityFailure(
            stage = "append",
            error = original,
            detectedAtMillis = 2000L
        )

        assertTrue(returned === original)
        assertNull(CanonicalMemoryQuarantineStore.diagnostic.value)
    }
}
