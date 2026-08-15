package com.morimil.app.core.runtime

import com.morimil.app.improvements.SelfImprovementRuntimeObserver
import com.morimil.app.improvements.SelfImprovementRuntimeStatus
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRuntimeCapabilitiesTest {
    @After
    fun tearDown() {
        SelfImprovementRuntimeObserver.resetForTest()
    }

    @Test
    fun compiledCapabilityDoesNotPretendRuntimeObserverIsReadyBeforeInitialization() {
        SelfImprovementRuntimeObserver.resetForTest()

        val capabilities = CurrentRuntimeCapabilities.value

        assertTrue(capabilities.selfImprovementGovernance)
        assertTrue(capabilities.selfImprovementSignedAuthorityAttestations)
        assertEquals(
            SelfImprovementRuntimeStatus.NOT_INITIALIZED,
            capabilities.selfImprovementRuntimeStatus
        )
        assertFalse(capabilities.selfImprovementRuntimeSignalAutonomy)
        assertFalse(capabilities.selfImprovementDurableAuditStore)
        assertFalse(capabilities.selfPatchExecutorConnected)
        assertFalse(capabilities.selfIndependentVerifierConnected)
        assertFalse(capabilities.selfHumanAuthorizerTrustConnected)
        assertFalse(capabilities.selfImprovementExternalAuditWitnessConnected)
        assertFalse(capabilities.selfMergeAuthority)
    }

    @Test
    fun liveCapabilityBecomesReadyOnlyAfterObserverAndAuditInitialize() {
        val root = Files.createTempDirectory("runtime-capability-ready").toFile()
        try {
            SelfImprovementRuntimeObserver.initializeForTest(File(root, "audit.log"))

            val capabilities = CurrentRuntimeCapabilities.value

            assertEquals(SelfImprovementRuntimeStatus.READY, capabilities.selfImprovementRuntimeStatus)
            assertTrue(capabilities.selfImprovementRuntimeSignalAutonomy)
            assertTrue(capabilities.selfImprovementDurableAuditStore)
        } finally {
            root.deleteRecursively()
        }
    }
}
