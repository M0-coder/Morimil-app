package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectVaultOutboxContractTest {
    @Test
    fun projectVaultRepositoryCannotPerformSequentialLegacyWrite() {
        val source = productionFile(
            "com/morimil/app/data/repository/ProjectVaultRepository.kt"
        ).readText()

        assertTrue(source.contains("ProjectVaultCommitPort"))
        assertTrue(source.contains("projectVaultOutboxDao"))
        assertTrue(source.contains("recoverPendingOperations"))
        assertFalse(source.contains("MemoryRepository"))
        assertFalse(source.contains("recordSystemMemoryEvent"))
    }

    @Test
    fun createStagesOutboxBeforeLocalVisibility() {
        val source = productionFile(
            "com/morimil/app/data/repository/ProjectVaultRepository.kt"
        ).readText()
        val stage = source.indexOf("stageCreate(operation)")
        val canonical = source.indexOf("dispatchOperation(operation.operationId)", stage)
        val localApply = source.indexOf("applyLocalTransition(current)")

        assertTrue(stage >= 0)
        assertTrue(canonical > stage)
        assertTrue(localApply > canonical)
    }

    @Test
    fun runtimeRecoversOutboxBeforeBootstrap() {
        val source = productionFile(
            "com/morimil/app/MorimilAppContainerRuntimeGate.kt"
        ).readText()
        val recovery = source.indexOf("recoverPendingOperations()")
        val bootstrap = source.indexOf("bootstrap.bootstrap(identity)")

        assertTrue(recovery >= 0)
        assertTrue(bootstrap > recovery)
    }

    @Test
    fun memoryOrganDatabaseRegistersAndExportsVersionEightOutbox() {
        val database = productionFile(
            "com/morimil/app/data/local/MemoryOrganDatabase.kt"
        ).readText()
        val encryption = productionFile(
            "com/morimil/app/data/local/MemoryOrganDatabaseEncryption.kt"
        ).readText()
        val schema = schemaFile(
            "com.morimil.app.data.local.MemoryOrganDatabase/8.json"
        ).readText()

        assertTrue(database.contains("ProjectVaultOutboxEntity::class"))
        assertTrue(database.contains("version = 8"))
        assertTrue(database.contains("projectVaultOutboxDao"))
        assertTrue(encryption.contains("MemoryOrganDatabaseMigrationV8.MIGRATION_7_8"))
        assertTrue(schema.contains("\"version\": 8"))
        assertTrue(schema.contains("\"tableName\": \"project_vault_outbox\""))
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

    private fun schemaFile(relativePath: String): File {
        return sequenceOf(
            File("schemas", relativePath),
            File("app/schemas", relativePath)
        ).firstOrNull(File::isFile)
            ?: error("Exported schema not found: $relativePath")
    }
}
