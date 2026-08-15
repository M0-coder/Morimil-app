package com.morimil.app.improvements

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfImprovementSignalCollectorTest {
    @Test
    fun internalGenesisSignalIsCapturedConservativelyAsCritical() {
        val root = Files.createTempDirectory("self-signal-genesis").toFile()
        try {
            val store = SelfImprovementAuditStore(File(root, "audit.log"))
            val collector = SelfImprovementSignalCollector(store)
            val observation = collector.captureInternalRuntimeIssue(
                component = "genesis_ultra_runtime_gate",
                message = "identity verification failed",
                failureCount = 1,
                occurredAtMillis = 100L
            )

            requireNotNull(observation)
            val candidate = SelfImprovementProtocol.detect(observation)
            assertEquals(SelfChangeRisk.CRITICAL, candidate.risk)
            assertTrue(SelfChangeSurface.GENESIS in observation.surfaces)
            assertTrue(SelfChangeSurface.INSTANCE_IDENTITY in observation.surfaces)
            assertEquals(1, store.readVerifiedRecords().size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun signingMemoryFailureRetainsBothCriticalAndSecuritySurfaces() {
        val root = Files.createTempDirectory("self-signal-signing").toFile()
        try {
            val store = SelfImprovementAuditStore(File(root, "audit.log"))
            val collector = SelfImprovementSignalCollector(store)
            val observation = requireNotNull(
                collector.captureInternalRuntimeIssue(
                    component = "memory_signature.keystore_failure",
                    message = "signing blocked",
                    failureCount = 1,
                    occurredAtMillis = 100L
                )
            )

            assertTrue(SelfChangeSurface.CANONICAL_MEMORY in observation.surfaces)
            assertTrue(SelfChangeSurface.SECURITY_BOUNDARY in observation.surfaces)
            assertEquals(
                SelfChangeRisk.CRITICAL,
                SelfImprovementProtocol.detect(observation).risk
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun repeatedIdenticalSignalWithinCooldownDoesNotSpamAudit() {
        val root = Files.createTempDirectory("self-signal-dedupe").toFile()
        try {
            val store = SelfImprovementAuditStore(File(root, "audit.log"))
            val collector = SelfImprovementSignalCollector(store, duplicateCooldownMillis = 1_000L)
            val first = collector.captureChatError("backend unavailable", 100L)
            val duplicate = collector.captureChatError("backend unavailable", 500L)
            val later = collector.captureChatError("backend unavailable", 1_100L)

            requireNotNull(first)
            assertNull(duplicate)
            requireNotNull(later)
            assertEquals(2, store.readVerifiedRecords().size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun memoryAttentionIsCriticalAndStoredOnlyAsDetectedEvidence() {
        val root = Files.createTempDirectory("self-signal-memory").toFile()
        try {
            val store = SelfImprovementAuditStore(File(root, "audit.log"))
            val collector = SelfImprovementSignalCollector(store)
            val observation = requireNotNull(collector.captureMemoryAttention(100L))
            val candidate = SelfImprovementProtocol.detect(observation)
            val audit = store.readVerifiedRecords().single()

            assertEquals(SelfChangeRisk.CRITICAL, candidate.risk)
            assertEquals(SelfChangeStage.DETECTED, audit.stage)
            assertEquals(SelfChangeActor.MORIMIL, audit.actor)
            assertNull(audit.candidateDigest)
            assertNull(audit.baseCommitSha)
        } finally {
            root.deleteRecursively()
        }
    }
}
