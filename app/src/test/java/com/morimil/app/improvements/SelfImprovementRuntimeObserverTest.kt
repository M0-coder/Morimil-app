package com.morimil.app.improvements

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfImprovementRuntimeObserverTest {
    @After
    fun tearDown() {
        SelfImprovementRuntimeObserver.resetForTest()
    }

    @Test
    fun observerCapturesConnectedSignalsWithoutAndroidOrRoom() {
        val root = Files.createTempDirectory("self-runtime-observer").toFile()
        try {
            val auditFile = File(root, "audit.log")
            SelfImprovementRuntimeObserver.initializeForTest(auditFile)

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
    fun observerDoesNothingBeforeInitialization() {
        SelfImprovementRuntimeObserver.resetForTest()
        SelfImprovementRuntimeObserver.reportChatError("ignored", 100L)
        assertTrue(SelfImprovementRuntimeObserver.readVerifiedAuditForDiagnostics().isEmpty())
    }
}
