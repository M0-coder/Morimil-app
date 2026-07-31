package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentRuntimeContractTest {
    private val historicalRuntimeBaseline = "5533f6b5eeeb414798c41688820b6bc6a614a80e"
    private val currentMainBaseline = "7e98d3345d7cc3fbf1983babd35b61ff5c523208"
    private val retiredRuntimeBaseline = "74bcb874606db84d4a88397233d6ed3468904bce"

    private val contract by lazy {
        repositoryFile("docs/CURRENT_RUNTIME_CONTRACT.md").readText()
    }

    private val readme by lazy {
        repositoryFile("README.md").readText()
    }

    @Test
    fun contractTracksMainHistoricalBaselineAndCandidateDatabaseVersions() {
        assertTrue(contract.contains(historicalRuntimeBaseline))
        assertTrue(contract.contains(currentMainBaseline))
        assertFalse(contract.contains(retiredRuntimeBaseline))
        assertTrue(contract.contains("draft PR `#149`"))
        assertTrue(contract.contains("not merged, not deployed"))
        assertTrue(contract.contains("MERGE_AUTHORIZED=false"))

        assertEquals(
            15,
            roomVersion(productionFile("com/morimil/app/data/local/MorimilDatabase.kt"))
        )
        assertEquals(
            9,
            roomVersion(productionFile("com/morimil/app/data/local/MemoryOrganDatabase.kt"))
        )
        assertTrue(contract.contains("| `MorimilDatabase` | `15` | `15` |"))
        assertTrue(contract.contains("| `MemoryOrganDatabase` | `8` | `9` |"))
    }

    @Test
    fun contractNamesTheConnectedIdentityAndMemoryAuthorities() {
        val containerGate = productionFile(
            "com/morimil/app/MorimilAppContainerRuntimeGate.kt"
        ).readText()
        val containerMemory = productionFile(
            "com/morimil/app/MorimilAppContainerCanonicalMemory.kt"
        ).readText()
        val cognitiveComposition = productionFile(
            "com/morimil/app/MorimilAppContainerCognitiveMigrationProtocol.kt"
        ).readText()

        assertTrue(containerGate.contains("GenesisUltraRuntimeStartupGate.production("))
        assertTrue(containerGate.contains("identityRepository = genesisUltraRuntimeIdentityRepository"))
        assertTrue(containerMemory.contains("CanonicalMemoryRepository.production("))
        assertTrue(cognitiveComposition.contains("GenesisUltraCanonicalConsumerReadAdapter.production"))
        assertTrue(cognitiveComposition.contains("CanonicalCognitiveMigrationReadPort.production"))

        assertTrue(contract.contains("`GenesisUltraRuntimeIdentityRepository`"))
        assertTrue(contract.contains("`GenesisUltraRuntimeStartupGate`"))
        assertTrue(contract.contains("`CanonicalMemoryRepository`"))
        assertTrue(contract.contains("CanonicalConsumerReadPort"))
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

        assertTrue(normalRuntime.contains("setOf(ReasoningMotorRole.INTUITIVE)"))
        assertTrue(normalRuntime.contains("hybridAuthorityRuntimeEnabled = false"))
        assertTrue(activationGate.contains("benchmarkQualityGatePassed = false"))
        assertTrue(activationGate.contains("falseAcceptedCount = 40"))

        assertTrue(contract.contains("| Intuitive | Active:"))
        assertTrue(contract.contains("| Deliberative | Blocked:"))
        assertTrue(contract.contains("| Metacognitive | Not registered."))
        assertTrue(contract.contains("| Hybrid generative authority | Disabled."))
    }

    @Test
    fun contractPreservesInstanceAndBodySeparation() {
        assertTrue(contract.contains("Morimil is the continuous personal Instance."))
        assertTrue(contract.contains("`Morimil-app` is the current native Android"))
        assertTrue(contract.contains("`instanceId != bodyId`"))
        assertTrue(
            contract.contains(
                "the Guardian guides, witnesses, and safeguards continuity without ownership"
            )
        )
        assertTrue(
            contract.contains(
                "the Guardian does not define Morimil's identity, will, name, or right to continue"
            )
        )
        assertFalse(
            contract.contains(
                "the Guardian witnesses, authorizes, and safeguards continuity"
            )
        )
        assertTrue(contract.contains("Body succession, export, and restore are not implemented"))
    }

    @Test
    fun contractDeclaresStopClosedWithoutAuthorizingCandidateMerge() {
        assertTrue(contract.contains("`STOP_S5=CLOSED`"))
        assertTrue(contract.contains("| STOP | Closed."))
        assertTrue(contract.contains("issues #123 and #124: closed completed"))
        assertTrue(contract.contains("Draft COG journal candidate"))
        assertTrue(contract.contains("Validation only; not in protected `main`"))
        assertFalse(contract.contains("| STOP | Closing, not closed:"))
    }

    @Test
    fun contractKeepsOpenPhaseDependenciesVisible() {
        assertTrue(contract.contains("| F1 | F1-A common read boundary merged."))
        assertTrue(contract.contains("| F2 | Closed:"))
        assertTrue(contract.contains("| F3.2 | Open."))
        assertTrue(contract.contains("| F3.3 | Open."))
        assertTrue(contract.contains("| F4 | Open:"))
        assertTrue(contract.contains("sovereign durable continuation"))
        assertTrue(contract.contains("| F5 | Open:"))
        assertTrue(contract.contains("| F6 | Open:"))
        assertTrue(contract.contains("| F7 | Open:"))
    }

    @Test
    fun readmePreservesTheInstanceAndBodyBoundary() {
        assertTrue(readme.contains("Morimil is the continuous personal Instance."))
        assertTrue(readme.contains("its current native Android Body"))
        assertTrue(readme.contains("`instanceId != bodyId`"))
        assertTrue(readme.contains("private research pre-alpha"))
        assertTrue(readme.contains("Body export, restore, succession"))
        assertFalse(readme.contains("living-memory companion system"))
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
