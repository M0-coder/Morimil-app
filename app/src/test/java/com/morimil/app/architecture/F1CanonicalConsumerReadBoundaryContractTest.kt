package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class F1CanonicalConsumerReadBoundaryContractTest {
    @Test
    fun boundaryContainsExactlyTheFourReservedFiles() {
        val root = repositoryRoot()
        val expected = EXPECTED_FILES.toSortedSet()
        val sourceRoots = listOf(
            File(root, "app/src/main/java"),
            File(root, "app/src/test/java")
        )
        val discovered = sourceRoots.asSequence()
            .filter(File::isDirectory)
            .flatMap { sourceRoot -> sourceRoot.walkTopDown().asSequence() }
            .filter { file -> file.isFile && file.extension == "kt" }
            .map { file -> file.relativeTo(root).invariantSeparatorsPath }
            .filter { path ->
                val name = path.substringAfterLast('/')
                name.contains("CanonicalConsumerRead") ||
                    name.contains("GenesisUltraCanonicalConsumerRead")
            }
            .toSortedSet()

        assertEquals(expected, discovered)
        expected.forEach { path ->
            assertTrue("Missing reserved F1-A file: $path", File(root, path).isFile)
        }
    }

    @Test
    fun commonApiRemainsInternalReadOnlyAndComplete() {
        val port = productionSource(PORT_PATH).readText()
        val executable = executableSource(port)

        assertTrue(executable.contains("internal interface CanonicalConsumerReadPort"))
        assertTrue(executable.contains("suspend fun readVerifiedSnapshot"))
        assertTrue(executable.contains("suspend fun readRecallCandidates"))
        assertTrue(executable.contains("suspend fun readRestCyclePlanningInput"))
        assertTrue(executable.contains("suspend fun readHealthInput"))
        assertTrue(executable.contains("sealed interface CanonicalReadResult"))
        assertTrue(executable.contains("data class Ready"))
        assertTrue(executable.contains("data class Blocked"))
        assertTrue(executable.contains("CanonicalConsumerSnapshot"))
        assertTrue(executable.contains("CanonicalRecallCandidateBatch"))
        assertTrue(executable.contains("CanonicalRestCyclePlanningInput"))
        assertTrue(executable.contains("CanonicalHealthInput"))
        assertFalse(Regex("\\bsuspend\\s+fun\\s+(append|write|persist|repair|migrate)\\b").containsMatchIn(executable))
    }

    @Test
    fun adapterUsesOnlyTheTwoCanonicalReadAuthorities() {
        val source = productionSource(ADAPTER_PATH).readText()
        val executable = executableSource(source)

        assertTrue(Regex("\\bGenesisUltraRuntimeIdentityRepository\\b").containsMatchIn(executable))
        assertTrue(Regex("\\bCanonicalMemoryRepository\\b").containsMatchIn(executable))
        assertTrue(Regex("\\.readCommittedIdentity\\b").containsMatchIn(executable))
        assertTrue(Regex("\\.readVerifiedSnapshot\\b").containsMatchIn(executable))
        assertTrue(executable.contains("readIdentity"))
        assertTrue(executable.contains("readMemorySnapshot"))
        assertTrue(executable.contains("GenesisUltraHashProfile.hashFields"))
        assertTrue(executable.contains("GenesisUltraHashProfile.sha256"))
    }

    @Test
    fun adapterPreservesBeforeSnapshotAfterAndFailClosedTaxonomy() {
        val adapter = productionSource(ADAPTER_PATH).readText()
        val port = productionSource(PORT_PATH).readText()
        val executable = executableSource(adapter)

        val firstIdentity = executable.indexOf("readIdentity()")
        val snapshot = executable.indexOf("readMemorySnapshot()", startIndex = firstIdentity + 1)
        val secondIdentity = executable.indexOf("readIdentity()", startIndex = snapshot + 1)
        assertTrue(firstIdentity >= 0)
        assertTrue(snapshot > firstIdentity)
        assertTrue(secondIdentity > snapshot)
        assertTrue(executable.contains("validateStableIdentity"))
        assertFalse(executable.contains("getOrNull"))

        REQUIRED_FAILURE_CODES.forEach { code ->
            assertTrue("Missing failure code $code", port.contains(code))
        }
        assertTrue(port.contains("NOT_READY"))
        assertTrue(port.contains("RETRYABLE"))
        assertTrue(port.contains("BLOCKED"))
    }

    @Test
    fun productionBoundaryCannotImportLegacyStorageF3OrCompositionRoot() {
        val executable = productionExecutable()

        FORBIDDEN_TYPE_TOKENS.forEach { token ->
            assertFalse(
                "Forbidden executable token in F1-A production boundary: $token",
                Regex("\\b${Regex.escape(token)}\\b").containsMatchIn(executable)
            )
        }
        FORBIDDEN_PACKAGE_TOKENS.forEach { token ->
            assertFalse("Forbidden package token in F1-A production boundary: $token", executable.contains(token))
        }
        assertFalse(Regex("\\bCognitiveMigrationCanonicalReadPort\\b").containsMatchIn(executable))
        assertFalse(Regex("\\b(operationId|proposalId|migrationId|approvalId)\\b").containsMatchIn(executable))
    }

    @Test
    fun productionBoundaryCannotWriteOrOpenTransactions() {
        val executable = productionExecutable()

        assertFalse(Regex("\\.(insert|update|delete|upsert|appendText)\\s*\\(").containsMatchIn(executable))
        assertFalse(Regex("\\bwithTransaction\\s*\\{").containsMatchIn(executable))
        assertFalse(Regex("@(?:Dao|Entity|Database)\\b").containsMatchIn(executable))
        assertFalse(Regex("\\bRoomDatabase\\b").containsMatchIn(executable))
        assertFalse(Regex("\\bMessageDigest\\b").containsMatchIn(executable))
    }

    @Test
    fun legacyCompatibilityFallbacksRemainForbidden() {
        val executable = productionExecutable()

        LEGACY_FALLBACKS.forEach { token ->
            assertFalse("Forbidden legacy fallback in F1-A: $token", executable.contains(token))
        }
    }

    @Test
    fun byteOwnershipAndActivationExceptionRemainExplicit() {
        val port = productionSource(PORT_PATH).readText()
        val adapter = productionSource(ADAPTER_PATH).readText()
        val combined = port + "\n" + adapter

        assertTrue(combined.contains("copyContentBytes"))
        assertTrue(combined.contains("copyProvenanceBytes"))
        assertTrue(combined.contains("copyOf()"))
        assertTrue(combined.contains("ACTIVATION_METADATA_ONLY"))
        assertTrue(combined.contains("instance.activation.confirmed"))
        assertTrue(combined.contains("application/vnd.genesis.atomic-birth-authorization+json"))
        assertTrue(combined.contains("metadata-only"))
    }

    @Test
    fun digestRepresentationRemainsCanonicalAndStableIdsStayOutsideF1() {
        val source = productionSource(ADAPTER_PATH).readText()
        val executable = executableSource(source)

        assertTrue(source.contains("^sha256:[a-f0-9]{64}$"))
        assertFalse(executable.contains("removePrefix"))
        assertFalse(executable.contains("substringAfter"))
        assertFalse(Regex("\\bStableIdDigest\\b").containsMatchIn(executable))
        assertFalse(Regex("\\btoHex\\s*\\(").containsMatchIn(executable))
    }

    private fun productionExecutable(): String {
        return listOf(PORT_PATH, ADAPTER_PATH)
            .joinToString("\n") { path -> executableSource(productionSource(path).readText()) }
    }

    private fun productionSource(relativePath: String): File {
        return File(repositoryRoot(), "app/src/main/java/$relativePath").also { file ->
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

    private fun repositoryRoot(): File {
        return sequenceOf(File("."), File(".."))
            .map(File::getCanonicalFile)
            .firstOrNull { candidate ->
                File(candidate, "README.md").isFile &&
                    File(candidate, "app/build.gradle.kts").isFile
            }
            ?: error("Repository root not found")
    }

    private companion object {
        const val PORT_PATH =
            "com/morimil/app/data/genesis/ultra/CanonicalConsumerReadPort.kt"
        const val ADAPTER_PATH =
            "com/morimil/app/data/genesis/ultra/GenesisUltraCanonicalConsumerReadAdapter.kt"

        val EXPECTED_FILES = setOf(
            "app/src/main/java/$PORT_PATH",
            "app/src/main/java/$ADAPTER_PATH",
            "app/src/test/java/com/morimil/app/data/genesis/ultra/GenesisUltraCanonicalConsumerReadAdapterTest.kt",
            "app/src/test/java/com/morimil/app/architecture/F1CanonicalConsumerReadBoundaryContractTest.kt"
        )

        val REQUIRED_FAILURE_CODES = setOf(
            "BIRTH_NOT_COMMITTED",
            "IDENTITY_INCONSISTENT",
            "CANONICAL_MEMORY_ABSENT",
            "CHAIN_CORRUPT",
            "FOREIGN_INSTANCE",
            "WRITER_BINDING_MISMATCH",
            "SNAPSHOT_CHANGED_DURING_READ",
            "PAYLOAD_MISSING",
            "PAYLOAD_INTEGRITY_INVALID",
            "PROVENANCE_UNVERIFIABLE",
            "TRANSIENT_STORE_UNAVAILABLE",
            "UNCLASSIFIED_VERIFICATION_FAILURE"
        )

        val FORBIDDEN_TYPE_TOKENS = setOf(
            "MemoryDao",
            "MemoryRepository",
            "MorimilDatabase",
            "MemoryOrganDatabase",
            "CanonicalLivingMemoryPort",
            "LivingMemoryPort",
            "GenesisReader",
            "LocalBirthState",
            "MorimilAppContainer",
            "RecallScheduleRepository",
            "RestCycleRepository",
            "LocalNervousSystemRepository",
            "AgentOrchestrationRepository",
            "MorimilViewModel"
        )

        val FORBIDDEN_PACKAGE_TOKENS = setOf(
            "com.morimil.app.data.local.",
            "com.morimil.app.data.repository.",
            "com.morimil.app.runtime.",
            "com.morimil.app.ui.",
            "androidx.room"
        )

        val LEGACY_FALLBACKS = setOf(
            "genesis_core",
            "local_instance_identity",
            "memory_events",
            "memory_snapshots",
            "local_instance_pending",
            "legacy_instance_read_only",
            "loadGenesisCore",
            "loadLocalIdentity",
            "loadMemoryContext",
            "getLivingMemorySnapshot",
            "hasCompleteBirth"
        )
    }
}
