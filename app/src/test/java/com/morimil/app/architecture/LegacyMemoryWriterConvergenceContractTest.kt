package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyMemoryWriterConvergenceContractTest {
    @Test
    fun productionCodeCannotInsertLegacyMemoryEvents() {
        val violations = productionKotlinFiles()
            .filterNot { file -> file.name == "MemoryDao.kt" }
            .filter { file ->
                val executable = executableSource(file.readText())
                executable.contains("insertMemoryEvent(") ||
                    executable.contains("INSERT INTO memory_events")
            }
            .map { file -> file.relativeTo(productionRoot()).invariantSeparatorsPath }
            .toList()

        assertTrue("Legacy memory writers remain: $violations", violations.isEmpty())
    }

    @Test
    fun databaseMigrationFreezesInsertUpdateAndDelete() {
        val source = productionFile(
            "com/morimil/app/data/local/MorimilDatabaseMigrationV15.kt"
        ).readText()

        assertTrue(source.contains("BEFORE INSERT ON memory_events"))
        assertTrue(source.contains("BEFORE UPDATE ON memory_events"))
        assertTrue(source.contains("BEFORE DELETE ON memory_events"))
        assertTrue(source.contains("legacy_memory_events_read_only"))
    }

    @Test
    fun runtimeConvergesLegacyLineageBeforeCanonicalBootstrap() {
        val source = productionFile(
            "com/morimil/app/MorimilAppContainerRuntimeGate.kt"
        ).readText()
        val convergence = source.indexOf("convergence.converge(identity)")
        val bootstrap = source.indexOf("bootstrap.bootstrap(identity)")

        assertTrue(convergence >= 0)
        assertTrue(bootstrap > convergence)
    }

    @Test
    fun applicationContainerInjectsTheCanonicalWriter() {
        val source = productionFile("com/morimil/app/MorimilAppContainer.kt").readText()

        assertTrue(source.contains("livingMemoryPort = canonicalLivingMemoryPort"))
        assertFalse(source.contains("memoryEventSigner = memoryEventSigner"))
    }

    private fun productionKotlinFiles(): Sequence<File> {
        return productionRoot().walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
    }

    private fun productionFile(relativePath: String): File {
        return File(productionRoot(), relativePath).also { file ->
            require(file.isFile) { "Production source not found: $relativePath" }
        }
    }

    private fun productionRoot(): File {
        return sequenceOf(
            File("src/main/java"),
            File("app/src/main/java")
        ).firstOrNull(File::isDirectory)
            ?: error("Production source root not found")
    }

    private fun executableSource(source: String): String {
        return source
            .replace(Regex("\"\"\"[\\s\\S]*?\"\"\""), "")
            .replace(Regex("\"(?:\\\\.|[^\"\\\\])*\""), "")
            .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
            .replace(Regex("//.*"), "")
    }
}
