package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseZeroLegacyResidueContractTest {
    @Test
    fun deadGenesisAndDatabaseResiduesStayRemoved() {
        val integrationGate = sourceFile(
            "src/main/java/com/morimil/app/data/genesis/GenesisUltraIntegrationGate.kt"
        ).readText()
        val memoryOrganDatabase = sourceFile(
            "src/main/java/com/morimil/app/data/local/MemoryOrganDatabase.kt"
        ).readText()
        val legacyGenesisReader = optionalSourceFile(
            "src/main/java/com/morimil/app/data/genesis/GenesisReader.kt"
        )

        assertFalse(
            Regex("fun\\s+requireBirthReady\\s*\\(\\s*\\)\\s*:\\s*Nothing")
                .containsMatchIn(integrationGate)
        )
        assertFalse("Legacy GenesisReader must not remain in production sources", legacyGenesisReader.isFile)
        assertFalse(memoryOrganDatabase.contains("Room.databaseBuilder("))
        assertTrue(memoryOrganDatabase.contains("MemoryOrganDatabaseEncryption.open(context)"))
    }

    @Test
    fun reasoningUsesExplicitUnverifiedFinalization() {
        val productionRoot = productionSourceRoot()
        val productionText = productionRoot
            .walkTopDown()
            .filter { file -> file.isFile && file.extension in setOf("kt", "java") }
            .joinToString("\n") { file -> file.readText() }

        assertFalse(productionText.contains("LEGACY_UNROUTED"))
        assertTrue(productionText.contains("UNVERIFIED_DIRECT"))
        assertTrue(productionText.contains("HybridAuthorityPresentationStatus.UNVERIFIED"))
    }

    private fun sourceFile(relativePath: String): File {
        return sequenceOf(
            File(relativePath),
            File("app/$relativePath")
        ).firstOrNull(File::isFile)
            ?: error("Source file not found: $relativePath")
    }

    private fun optionalSourceFile(relativePath: String): File {
        return sequenceOf(
            File(relativePath),
            File("app/$relativePath")
        ).first { candidate -> candidate.parentFile?.exists() == true }
    }

    private fun productionSourceRoot(): File {
        return sequenceOf(
            File("src/main/java"),
            File("app/src/main/java")
        ).firstOrNull(File::isDirectory)
            ?: error("Production source root not found")
    }
}
