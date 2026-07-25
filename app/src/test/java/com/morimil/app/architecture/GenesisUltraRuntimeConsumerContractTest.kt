package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraRuntimeConsumerContractTest {
    @Test
    fun normalChatCannotReadLegacyGenesisOrLegacyAlias() {
        val coordinator = productionSource(
            "com/morimil/app/ui/MorimilChatCoordinator.kt"
        ).readText()
        val executable = executableSource(coordinator)

        assertTrue(executable.contains("genesisUltraRuntimeIdentityRepository"))
        assertTrue(executable.contains("readCommittedIdentity("))
        assertFalse(executable.contains(".genesisReader"))
        assertFalse(executable.contains("readGenesisIdentity("))
        assertFalse(executable.contains("localIdentity.value"))
    }

    @Test
    fun intrinsicPromptRejectsAnyNonUltraIdentitySchema() {
        val promptBuilder = productionSource(
            "com/morimil/app/ai/IntrinsicSystemPromptBuilder.kt"
        ).readText()
        val executable = executableSource(promptBuilder)

        assertTrue(executable.contains("ULTRA_RUNTIME_CONTEXT_SCHEMA"))
        assertTrue(executable.contains("intrinsic_identity_not_genesis_ultra_runtime"))
        assertFalse(promptBuilder.contains("semilla local del Bloque Genesis empaquetada"))
    }

    private fun productionSource(relativePath: String): File {
        val root = sequenceOf(
            File("src/main/java"),
            File("app/src/main/java")
        ).firstOrNull(File::isDirectory)
            ?: error("Production source root not found")
        return File(root, relativePath).also { file ->
            require(file.isFile) { "Production source not found: $relativePath" }
        }
    }

    private fun executableSource(source: String): String {
        return source
            .replace(Regex("\"\"\"[\\s\\S]*?\"\"\""), "")
            .replace(Regex("\"(?:\\\\.|[^\"\\\\])*\""), "")
            .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
            .replace(Regex("//.*"), "")
    }
}
