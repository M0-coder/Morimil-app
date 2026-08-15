package com.morimil.app.improvements

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfImprovementRuntimeObserverTest {
    @After
    fun tearDown() {
        SelfImprovementRuntimeObserver.resetForTest()
    }

    @Test
    fun observerCapturesConnectedSignalsWithoutAndroidOrRoomAndReportsReady() {
        val root = Files.createTempDirectory("self-runtime-observer").toFile()
        try {
            val auditFile = File(root, "audit.log")
            SelfImprovementRuntimeObserver.initializeForTest(auditFile)
            assertEquals(
                SelfImprovementRuntimeStatus.READY,
                SelfImprovementRuntimeObserver.runtimeStatus()
            )

            SelfImprovementRuntimeObserver.reportChatError(
                error = "backend unavailable",
                occurredAtMillis = 100L
            )
            SelfImprovementRuntimeObserver.reportInternalRuntimeIssue(
                component = "memory_signature.keystore_failure",
                message = "signing blocked",
                failureCount = 1,
                occurredAtMillis = 200L
            )

            val audit = SelfImprovementRuntimeObserver.readVerifiedAuditForDiagnostics()
            assertEquals(2, audit.size)
            assertTrue(audit.all { record -> record.stage == SelfChangeStage.DETECTED })
            assertTrue(audit.all { record -> record.actor == SelfChangeActor.MORIMIL })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun observerDoesNothingBeforeInitializationAndReportsNotInitialized() {
        SelfImprovementRuntimeObserver.resetForTest()
        SelfImprovementRuntimeObserver.reportChatError("ignored", 100L)
        assertTrue(SelfImprovementRuntimeObserver.readVerifiedAuditForDiagnostics().isEmpty())
        assertEquals(
            SelfImprovementRuntimeStatus.NOT_INITIALIZED,
            SelfImprovementRuntimeObserver.runtimeStatus()
        )
    }

    @Test
    fun auditRollbackBeforeReinitializationReportsDegradedInsteadOfReady() {
        val root = Files.createTempDirectory("self-runtime-degraded").toFile()
        try {
            val auditFile = File(root, "audit.log")
            SelfImprovementRuntimeObserver.initializeForTest(auditFile)
            SelfImprovementRuntimeObserver.reportChatError("backend unavailable", 100L)
            SelfImprovementRuntimeObserver.resetForTest()
            assertTrue(auditFile.delete())

            assertThrows(IllegalArgumentException::class.java) {
                SelfImprovementRuntimeObserver.initializeForTest(auditFile)
            }
            assertEquals(
                SelfImprovementRuntimeStatus.DEGRADED_AUDIT_UNAVAILABLE,
                SelfImprovementRuntimeObserver.runtimeStatus()
            )
            SelfImprovementRuntimeObserver.reportChatError("must not append", 200L)
            assertTrue(SelfImprovementRuntimeObserver.readVerifiedAuditForDiagnostics().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun postStartAuditFailureImmediatelyRevokesReadyStatusAndFurtherCapture() {
        val root = Files.createTempDirectory("self-runtime-post-start-failure").toFile()
        try {
            val auditFile = File(root, "audit.log")
            SelfImprovementRuntimeObserver.initializeForTest(auditFile)
            SelfImprovementRuntimeObserver.reportChatError("first", 100L)
            assertEquals(
                SelfImprovementRuntimeStatus.READY,
                SelfImprovementRuntimeObserver.runtimeStatus()
            )

            val anchor = File(root, SelfImprovementAuditStore.DEFAULT_ANCHOR_FILENAME)
            assertTrue(anchor.delete())

            assertThrows(IllegalArgumentException::class.java) {
                SelfImprovementRuntimeObserver.reportChatError("second", 200L)
            }
            assertEquals(
                SelfImprovementRuntimeStatus.DEGRADED_AUDIT_UNAVAILABLE,
                SelfImprovementRuntimeObserver.runtimeStatus()
            )
            SelfImprovementRuntimeObserver.reportChatError("ignored-after-degrade", 300L)
            assertTrue(SelfImprovementRuntimeObserver.readVerifiedAuditForDiagnostics().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
