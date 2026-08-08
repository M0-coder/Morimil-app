package com.morimil.app.data.genesis.ultra

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.morimil.app.data.repository.CrossDatabaseCanonicalCommand
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalAgentLifecycleCommitPortInstrumentedTest {
    @Test
    fun testingFactoryIsAvailableOnAndroidRuntime() {
        val port = CanonicalAgentLifecycleCommitPort.testing(
            appendText = { _: CanonicalMemoryAppendCommand -> error("append_not_expected") },
            readVerifiedSnapshot = { error("snapshot_not_expected") }
        )

        assertNotNull(port)
        @Suppress("UNUSED_VARIABLE")
        val compileBoundary: suspend (CrossDatabaseCanonicalCommand) -> Any = port::ensureCommitted
    }
}
