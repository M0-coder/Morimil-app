package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class F33BLegacyArchiveIsolationContractTest {
    @Test
    fun normalMemoryDaoCannotExpressLegacyIdentityOrMemoryCapabilities() {
        val source = productionFile("com/morimil/app/data/local/MemoryDao.kt").readText()

        listOf(
            "LocalInstanceIdentityEntity",
            "GenesisCoreEntity",
            "MemoryEventEntity",
            "MemorySnapshotEntity",
            "local_instance_identity",
            "genesis_core",
            "memory_events",
            "memory_snapshots",
            "countLocalIdentity",
            "countGenesisCore",
            "loadMemoryEventAuditChain",
            "countMemoryEvents",
            "insertLocalIdentity",
            "insertGenesisCore",
            "insertMemoryEvent",
            "upsertMemorySnapshot"
        ).forEach { token ->
            assertFalse("Normal MemoryDao exposes legacy capability: $token", source.contains(token))
        }
    }

    @Test
    fun legacyArchiveDaoIsSelectOnlyAndBoundedToConflictAndConvergenceEvidence() {
        val source = productionFile("com/morimil/app/data/local/LegacyArchiveReadDao.kt").readText()
        val executable = executableSource(source)

        assertTrue(source.contains("interface LegacyArchiveReadDao"))
        assertTrue(source.contains("countLocalIdentity"))
        assertTrue(source.contains("countGenesisCore"))
        assertTrue(source.contains("loadMemoryEventAuditChain"))
        assertTrue(source.contains("countMemoryEvents"))

        assertFalse(executable.contains("@Insert"))
        assertFalse(executable.contains("@Update"))
        assertFalse(executable.contains("@Delete"))
        assertFalse(executable.contains("INSERT ", ignoreCase = true))
        assertFalse(executable.contains("UPDATE ", ignoreCase = true))
        assertFalse(executable.contains("DELETE ", ignoreCase = true))
    }

    @Test
    fun productionGuardsUseExplicitReadOnlyLegacyBoundaries() {
        val boundaries = productionFile(
            "com/morimil/app/data/genesis/ultra/LegacyArchiveReadBoundaries.kt"
        ).readText()
        val convergence = productionFile(
            "com/morimil/app/data/genesis/ultra/LegacyMemoryConvergenceCoordinator.kt"
        ).readText()
        val preparation = productionFile(
            "com/morimil/app/data/genesis/ultra/GenesisUltraBirthPreparationCoordinator.kt"
        ).readText()
        val atomicBirth = productionFile(
            "com/morimil/app/data/genesis/ultra/GenesisUltraAtomicBirthPersistence.kt"
        ).readText()
        val bootstrap = productionFile(
            "com/morimil/app/runtime/GenesisUltraRuntimeBootstrapCoordinator.kt"
        ).readText()
        val database = productionFile("com/morimil/app/data/local/MorimilDatabase.kt").readText()

        assertTrue(boundaries.contains("interface LegacyBirthConflictProbe"))
        assertTrue(boundaries.contains("interface LegacyMemoryArchiveReadPort"))
        assertTrue(boundaries.contains("legacyArchiveReadDao()"))
        assertTrue(convergence.contains("LegacyBirthConflictProbe.production(database)"))
        assertTrue(convergence.contains("LegacyMemoryArchiveReadPort.production(database)"))
        assertTrue(preparation.contains("LegacyBirthConflictProbe.production(database)"))
        assertTrue(atomicBirth.contains("LegacyBirthConflictProbe.production(database)"))
        assertTrue(bootstrap.contains("LegacyBirthConflictProbe.production(memoryDatabase)"))
        assertTrue(database.contains("abstract fun legacyArchiveReadDao(): LegacyArchiveReadDao"))
        assertTrue(database.contains("version = 15"))
    }

    @Test
    fun normalProductRuntimeCannotBypassLegacyReadBoundaries() {
        val productionRoot = productionRoot()
        val allowedBoundaryUsers = setOf(
            "com/morimil/app/data/genesis/ultra/LegacyArchiveReadBoundaries.kt",
            "com/morimil/app/data/genesis/ultra/LegacyMemoryConvergenceCoordinator.kt",
            "com/morimil/app/data/genesis/ultra/GenesisUltraBirthPreparationCoordinator.kt",
            "com/morimil/app/data/genesis/ultra/GenesisUltraAtomicBirthPersistence.kt",
            "com/morimil/app/runtime/GenesisUltraRuntimeBootstrapCoordinator.kt"
        )
        val allowedDaoAccessorUsers = setOf(
            "com/morimil/app/data/genesis/ultra/LegacyArchiveReadBoundaries.kt",
            "com/morimil/app/data/local/MorimilDatabase.kt"
        )
        val violations = productionRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { file ->
                file.relativeTo(productionRoot).invariantSeparatorsPath to file.readText()
            }
            .filter { (path, source) ->
                val boundaryEscape = path !in allowedBoundaryUsers &&
                    (source.contains("LegacyBirthConflictProbe") ||
                        source.contains("LegacyMemoryArchiveReadPort"))
                val daoBypass = path !in allowedDaoAccessorUsers &&
                    source.contains("legacyArchiveReadDao()")
                boundaryEscape || daoBypass
            }
            .map { (path, _) -> path }
            .toList()

        assertTrue(
            "Legacy archive read access escaped migration/birth-safety quarantine: ${violations.joinToString()}",
            violations.isEmpty()
        )
    }

    private fun productionFile(relativePath: String): File = File(productionRoot(), relativePath).also {
        require(it.isFile) { "Production source not found: $relativePath" }
    }

    private fun productionRoot(): File = sequenceOf(
        File("src/main/java"),
        File("app/src/main/java")
    ).firstOrNull(File::isDirectory)
        ?: error("Production source root not found")

    private fun executableSource(source: String): String = source
        .replace(Regex("\"\"\"[\\s\\S]*?\"\"\""), "")
        .replace(Regex("\"(?:\\\\.|[^\"\\\\])*\""), "")
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        .replace(Regex("//.*"), "")
}
