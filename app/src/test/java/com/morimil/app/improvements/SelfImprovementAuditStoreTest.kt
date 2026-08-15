package com.morimil.app.improvements

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfImprovementAuditStoreTest {
    @Test
    fun appendBuildsDurableVerifiedHashChainAndSeparateAnchor() {
        val root = Files.createTempDirectory("self-audit-test").toFile()
        try {
            val file = File(root, SelfImprovementAuditStore.DEFAULT_RELATIVE_PATH)
            val store = SelfImprovementAuditStore(file)
            val detected = SelfImprovementProtocol.detect(observation())
            val diagnosed = SelfImprovementProtocol.diagnose(detected, SelfChangeActor.MORIMIL)

            val first = store.append(detected, SelfChangeActor.MORIMIL, 100L, occurrenceCount = 2)
            val second = store.append(diagnosed, SelfChangeActor.MORIMIL, 101L)
            val recovered = SelfImprovementAuditStore(file).readVerifiedRecords()

            assertEquals(listOf(first, second), recovered)
            assertEquals(1L, recovered[0].sequence)
            assertEquals(2, recovered[0].occurrenceCount)
            assertEquals(2L, recovered[1].sequence)
            assertEquals(recovered[0].recordDigest, recovered[1].previousRecordDigest)
            assertTrue(file.length() > 0L)
            assertTrue(store.anchorPathForDiagnostics().isFile)
            assertTrue(store.anchorPathForDiagnostics().length() > 0L)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun tamperedAuditRecordFailsClosed() {
        val root = Files.createTempDirectory("self-audit-tamper").toFile()
        try {
            val file = File(root, SelfImprovementAuditStore.DEFAULT_RELATIVE_PATH)
            val store = SelfImprovementAuditStore(file)
            store.append(
                SelfImprovementProtocol.detect(observation()),
                SelfChangeActor.MORIMIL,
                100L
            )

            val original = file.readText(StandardCharsets.UTF_8)
            file.writeText(
                original.replace("DETECTED", "DIAGNOSED"),
                StandardCharsets.UTF_8
            )

            assertThrows(IllegalArgumentException::class.java) {
                SelfImprovementAuditStore(file).readVerifiedRecords()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun truncatedAuditFailsClosed() {
        val root = Files.createTempDirectory("self-audit-truncated").toFile()
        try {
            val file = File(root, SelfImprovementAuditStore.DEFAULT_RELATIVE_PATH)
            val store = SelfImprovementAuditStore(file)
            store.append(
                SelfImprovementProtocol.detect(observation()),
                SelfChangeActor.MORIMIL,
                100L
            )
            val bytes = file.readBytes()
            file.writeBytes(bytes.copyOf(bytes.size - 1))

            assertThrows(IllegalArgumentException::class.java) {
                SelfImprovementAuditStore(file).readVerifiedRecords()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deletingAuditWhileAnchorSurvivesIsDetectedAsRollback() {
        val root = Files.createTempDirectory("self-audit-delete").toFile()
        try {
            val file = File(root, SelfImprovementAuditStore.DEFAULT_RELATIVE_PATH)
            val store = SelfImprovementAuditStore(file)
            store.append(
                SelfImprovementProtocol.detect(observation()),
                SelfChangeActor.MORIMIL,
                100L
            )
            assertTrue(store.anchorPathForDiagnostics().isFile)
            assertTrue(file.delete())

            val failure = assertThrows(IllegalArgumentException::class.java) {
                SelfImprovementAuditStore(file).readVerifiedRecords()
            }
            assertTrue(failure.message.orEmpty().contains("rollback_or_deletion"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rollingLogBackToEarlierValidPrefixIsRejectedByLaterAnchor() {
        val root = Files.createTempDirectory("self-audit-rollback").toFile()
        try {
            val file = File(root, SelfImprovementAuditStore.DEFAULT_RELATIVE_PATH)
            val store = SelfImprovementAuditStore(file)
            val detected = SelfImprovementProtocol.detect(observation())
            val diagnosed = SelfImprovementProtocol.diagnose(detected, SelfChangeActor.MORIMIL)
            store.append(detected, SelfChangeActor.MORIMIL, 100L)
            val firstRecordBytes = file.readBytes()
            store.append(diagnosed, SelfChangeActor.MORIMIL, 101L)
            file.writeBytes(firstRecordBytes)

            val failure = assertThrows(IllegalArgumentException::class.java) {
                SelfImprovementAuditStore(file).readVerifiedRecords()
            }
            assertTrue(failure.message.orEmpty().contains("anchor_sequence_ahead"))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun observation(): SelfChangeObservation {
        return SelfChangeObservation.create(
            changeId = "runtime_issue_memory",
            problem = "Canonical memory verification reported an internal issue.",
            proposal = "Investigate the exact failure and prepare a bounded patch candidate.",
            surfaces = setOf(SelfChangeSurface.CANONICAL_MEMORY)
        )
    }
}
