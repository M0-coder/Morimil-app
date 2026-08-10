package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class F33ALegacyRuntimeSurfaceContractTest {
    @Test
    fun normalRuntimeCannotExposeLegacyIdentityOrMemoryPresentation() {
        val repository = productionSource(
            "com/morimil/app/data/repository/MemoryRepository.kt"
        ).readText()
        val viewModel = productionSource(
            "com/morimil/app/ui/MorimilViewModel.kt"
        ).readText()
        val memoryScreen = productionSource(
            "com/morimil/app/ui/MemoryScreen.kt"
        ).readText()
        val graphCanvas = productionSource(
            "com/morimil/app/ui/MemoryGraphCanvasPanel.kt"
        ).readText()
        val graphExplorer = productionSource(
            "com/morimil/app/core/memory/MemoryGraphExplorer.kt"
        ).readText()
        val workspace = productionSource(
            "com/morimil/app/ui/UserWorkspaceScreen.kt"
        ).readText()
        val container = productionSource(
            "com/morimil/app/MorimilAppContainer.kt"
        ).readText()

        listOf(
            "LocalBirthState",
            "LocalInstanceIdentityEntity",
            "GenesisCoreEntity",
            "MemoryEventEntity",
            "MemorySnapshotEntity",
            "readLocalBirthState",
            "hasCompleteBirth",
            "birthLocalIdentity",
            "buildLivingMemoryContext",
            "auditLivingMemoryChain"
        ).forEach { token ->
            assertFalse(
                "Legacy runtime token remains in MemoryRepository: $token",
                repository.contains(token)
            )
        }

        listOf(
            "LocalInstanceIdentityEntity",
            "GenesisCoreEntity",
            "MemoryEventEntity",
            "MemorySnapshotEntity",
            "memoryDatabase.memoryDao()",
            "countMemoryEvents()",
            "loadLatestRestCycleEvent()"
        ).forEach { token ->
            assertFalse(
                "Legacy runtime token remains in MorimilViewModel: $token",
                viewModel.contains(token)
            )
        }

        assertTrue(viewModel.contains("CanonicalMemoryPresentationRepository"))
        assertTrue(memoryScreen.contains("CanonicalMemoryPresentationEvent"))
        assertTrue(memoryScreen.contains("eventos canónicos"))
        assertTrue(memoryScreen.contains("MemoryGraphCanvasPanel("))
        assertTrue(memoryScreen.contains("RestCycleHistoryPanel("))
        assertTrue(memoryScreen.contains("CognitiveMigrationPanel("))
        assertTrue(memoryScreen.contains("MemoryOrgansPanel("))
        assertFalse(memoryScreen.contains("MemoryEventEntity"))
        assertFalse(memoryScreen.contains("Genesis Core inmutable"))

        assertTrue(graphCanvas.contains("CanonicalMemoryPresentationEvent"))
        assertTrue(graphCanvas.contains("MemoryGraphEventView"))
        assertFalse(graphCanvas.contains("MemoryEventEntity"))
        assertTrue(graphExplorer.contains("MemoryGraphEventView"))
        assertTrue(graphExplorer.contains("canonical_memory_event"))
        assertFalse(graphExplorer.contains("MemoryEventEntity"))

        assertFalse(workspace.contains("localIdentity"))
        assertTrue(workspace.contains("Android es el Body actual"))
        assertFalse(container.contains("GenesisReader"))
        assertFalse(container.contains("genesisReader"))
    }

    @Test
    fun legacyArchiveRemainsFrozenForMigrationWithoutBecomingRuntimeAuthority() {
        val database = productionSource(
            "com/morimil/app/data/local/MorimilDatabase.kt"
        ).readText()
        val migration = productionSource(
            "com/morimil/app/data/local/MorimilDatabaseMigrationV15.kt"
        ).readText()
        val runtimeGate = productionSource(
            "com/morimil/app/MorimilAppContainerRuntimeGate.kt"
        ).readText()

        assertTrue(database.contains("LocalInstanceIdentityEntity::class"))
        assertTrue(database.contains("GenesisCoreEntity::class"))
        assertTrue(database.contains("MemoryEventEntity::class"))
        assertTrue(database.contains("MemorySnapshotEntity::class"))
        assertTrue(migration.contains("BEFORE INSERT ON memory_events"))
        assertTrue(migration.contains("BEFORE UPDATE ON memory_events"))
        assertTrue(migration.contains("BEFORE DELETE ON memory_events"))
        assertTrue(migration.contains("legacy_memory_events_read_only"))

        val convergence = runtimeGate.indexOf("convergence.converge(identity)")
        val bootstrap = runtimeGate.indexOf("bootstrap.bootstrap(identity)")
        assertTrue(convergence >= 0)
        assertTrue(bootstrap > convergence)
    }

    @Test
    fun compiledLegacyGenesisReaderStaysRemoved() {
        assertFalse(
            File(productionRoot(), "com/morimil/app/data/genesis/GenesisReader.kt").isFile
        )
    }

    private fun productionSource(relativePath: String): File {
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
}
