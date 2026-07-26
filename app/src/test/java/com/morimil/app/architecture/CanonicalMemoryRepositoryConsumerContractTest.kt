package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalMemoryRepositoryConsumerContractTest {
    @Test
    fun reasoningContextCannotReturnToLegacyLivingMemory() {
        val source = productionSource(
            "com/morimil/app/reasoning/ReasoningCapabilities.kt"
        ).readText()
        val executable = executableSource(source)

        assertTrue(executable.contains("CanonicalMemoryRepository"))
        assertTrue(executable.contains("buildVerifiedContext("))
        assertFalse(executable.contains("MemoryRepository"))
        assertFalse(executable.contains("buildLivingMemoryContext("))
    }

    @Test
    fun applicationContainerWiresCanonicalMemoryIntoKernel() {
        val source = productionSource(
            "com/morimil/app/MorimilAppContainer.kt"
        ).readText()
        val executable = executableSource(source)

        assertTrue(executable.contains("canonicalMemoryRepository = canonicalMemoryRepository"))
        assertFalse(executable.contains("memoryRepository = memoryRepository"))
    }

    @Test
    fun canonicalPayloadMigrationIsRegisteredForEncryptedProductionDatabase() {
        val source = productionSource(
            "com/morimil/app/data/local/MorimilDatabaseEncryption.kt"
        ).readText()
        val executable = executableSource(source)

        assertTrue(executable.contains("MorimilDatabaseMigrationPlan.ALL"))
        assertFalse(executable.contains("MorimilDatabaseMigrations.ALL"))
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
