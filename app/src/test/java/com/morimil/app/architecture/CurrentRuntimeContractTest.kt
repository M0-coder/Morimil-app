package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentRuntimeContractTest {
    private val contract by lazy {
        repositoryFile("docs/CURRENT_RUNTIME_CONTRACT.md").readText()
    }

    @Test
    fun contractTracksTheAuditedBaselineAndDatabaseVersions() {
        assertTrue(
            contract.contains(
                "74bcb874606db84d4a88397233d6ed3468904bce"
            )
        )

        assertEquals(
            roomVersion(productionFile("com/morimil/app/data/local/MorimilDatabase.kt")),
            tableVersion("MorimilDatabase")
        )
        assertEquals(
            roomVersion(productionFile("com/morimil/app/data/local/MemoryOrganDatabase.kt")),
            tableVersion("MemoryOrganDatabase")
        )
    }

    @Test
    fun contractNamesTheConnectedIdentityAndMemoryAuthorities() {
        val containerGate = productionFile(
            "com/morimil/app/MorimilAppContainerRuntimeGate.kt"
        ).readText()
        val containerMemory = productionFile(
            "com/morimil/app/MorimilAppContainerCanonicalMemory.kt"
        ).readText()

        assertTrue(containerGate.contains("GenesisUltraRuntimeStartupGate.production("))
        assertTrue(containerGate.contains("identityRepository = genesisUltraRuntimeIdentityRepository"))
        assertTrue(containerMemory.contains("CanonicalMemoryRepository.production("))

        assertTrue(contract.contains("`GenesisUltraRuntimeIdentityRepository`"))
        assertTrue(contract.contains("`GenesisUltraRuntimeStartupGate`"))
        assertTrue(contract.contains("`CanonicalMemoryRepository`"))
        assertTrue(contract.contains("`CanonicalLivingMemoryPort`"))
    }

    @Test
    fun contractTracksTheLiteralLegacyQuarantineAllowlist() {
        val expectedRules = mapOf(
            "birthLocalIdentity" to setOf(
                "com/morimil/app/data/repository/MemoryRepository.kt"
            ),
            "installGenesisBundle" to emptySet(),
            "insertLocalIdentity" to setOf(
                "com/morimil/app/data/local/MemoryDao.kt",
                "com/morimil/app/data/repository/MemoryRepository.kt"
            ),
            "insertGenesisCore" to setOf(
                "com/morimil/app/data/local/MemoryDao.kt",
                "com/morimil/app/data/repository/MemoryRepository.kt"
            )
        )

        expectedRules.forEach { (symbol, paths) ->
            assertTrue(contract.contains("| `$symbol` |"))
            if (paths.isEmpty()) {
                assertTrue(contract.contains("| `$symbol` | none |"))
            } else {
                paths.forEach { path -> assertTrue(contract.contains("`$path`")) }
            }
        }
    }

    @Test
    fun contractCannotClaimUnregisteredMotorsAreActive() {
        val normalRuntime = productionFile(
            "com/morimil/app/reasoning/intrinsic/MorimilNormalIntrinsicRuntimeV0.kt"
        ).readText()
        val activationGate = productionFile(
            "com/morimil/app/reasoning/intrinsic/MorimilNormalDeliberativeActivationGateV0.kt"
        ).readText()

        assertTrue(
            normalRuntime.contains(
                "setOf(ReasoningMotorRole.INTUITIVE)"
            )
        )
        assertTrue(
            normalRuntime.contains(
                "hybridAuthorityRuntimeEnabled = false"
            )
        )
        assertTrue(activationGate.contains("benchmarkQualityGatePassed = false"))
        assertTrue(activationGate.contains("falseAcceptedCount = 40"))

        assertTrue(contract.contains("| Intuitive | Active:"))
        assertTrue(contract.contains("| Deliberative | Blocked:"))
        assertTrue(contract.contains("| Metacognitive | Not registered."))
        assertTrue(contract.contains("| Hybrid generative authority | Disabled."))
    }

    @Test
    fun contractPreservesInstanceAndBodySeparation() {
        assertTrue(contract.contains("Morimil is the continuous Instance."))
        assertTrue(contract.contains("`Morimil-app` is the current native Android Body"))
        assertTrue(contract.contains("`instanceId != bodyId`"))
        assertTrue(contract.contains("the Guardian witnesses, authorizes and safeguards continuity but does not own Morimil"))
        assertTrue(contract.contains("Body succession, export and restore are not implemented"))
    }

    private fun tableVersion(databaseName: String): Int {
        val pattern = Regex("""\| `$databaseName` \| `(\d+)` \|""")
        return requireNotNull(pattern.find(contract)) {
            "Runtime contract does not declare $databaseName"
        }.groupValues[1].toInt()
    }

    private fun roomVersion(file: File): Int {
        val annotation = requireNotNull(
            Regex("""@Database\(([\s\S]*?)\)\s*abstract class""").find(file.readText())
        ) {
            "Room database annotation not found in ${file.path}"
        }.groupValues[1]
        return requireNotNull(Regex("""version\s*=\s*(\d+)""").find(annotation)) {
            "Room database version not found in ${file.path}"
        }.groupValues[1].toInt()
    }

    private fun productionFile(relativePath: String): File {
        return repositoryFile("app/src/main/java/$relativePath")
    }

    private fun repositoryFile(relativePath: String): File {
        return sequenceOf(
            File(relativePath),
            File("../$relativePath")
        ).firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")
    }
}
