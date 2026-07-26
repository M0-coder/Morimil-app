package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalMemoryQuarantineConsumerContractTest {
    @Test
    fun productionReasoningReaderQuarantinesBeforeReturningVerifiedContext() {
        val source = productionSource(
            "com/morimil/app/reasoning/ReasoningCapabilities.kt"
        ).readText()
        val executable = executableSource(source)

        assertTrue(executable.contains("CanonicalMemoryQuarantineStore.verify("))
        assertTrue(executable.contains("canonicalMemoryRepository.buildVerifiedContext("))
        assertTrue(
            executable.indexOf("CanonicalMemoryQuarantineStore.verify(") <
                executable.indexOf("canonicalMemoryRepository.buildVerifiedContext(")
        )
    }

    @Test
    fun chatKeepsReasoningErrorsVisibleToTheGuardian() {
        val coordinator = productionSource(
            "com/morimil/app/ui/MorimilChatCoordinator.kt"
        ).readText()
        val screen = productionSource(
            "com/morimil/app/ui/ChatScreenPolished.kt"
        ).readText()

        assertTrue(coordinator.contains("_chatError.value = result.errorMessage"))
        assertTrue(screen.contains("uiState.error?.let"))
        assertFalse(screen.contains("canonical_memory_signature_invalid"))
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
