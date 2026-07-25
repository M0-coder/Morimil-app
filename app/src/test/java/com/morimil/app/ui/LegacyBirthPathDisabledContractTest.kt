package com.morimil.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyBirthPathDisabledContractTest {
    @Test
    fun chatCoordinatorCannotInstallOrCommitLegacyBirth() {
        val source = sourceFile(
            "src/main/java/com/morimil/app/ui/MorimilChatCoordinator.kt"
        ).readText()

        assertTrue(source.contains("legacy_local_birth_path_disabled_use_genesis_ultra"))
        assertFalse(source.contains("installGenesisBundle("))
        assertFalse(source.contains("birthLocalIdentity("))
        assertFalse(source.contains("GenesisUltraIntegrationGate.requireBirthReady"))
    }

    @Test
    fun appRoutingDoesNotUseLegacyLocalIdentityAsBirthProof() {
        val source = sourceFile(
            "src/main/java/com/morimil/app/ui/MorimilApp.kt"
        ).readText()

        assertTrue(source.contains("GenesisUltraAppRoute.RUNTIME"))
        assertFalse(source.contains("localIdentity"))
    }

    private fun sourceFile(relativePath: String): File {
        return sequenceOf(
            File(relativePath),
            File("app/$relativePath")
        ).firstOrNull(File::isFile)
            ?: error("Source file not found: $relativePath")
    }
}
