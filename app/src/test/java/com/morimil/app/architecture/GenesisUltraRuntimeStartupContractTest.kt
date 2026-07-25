package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraRuntimeStartupContractTest {
    @Test
    fun appCannotConstructRuntimeViewModelBeforeVerifiedHost() {
        val appSource = productionSource("com/morimil/app/ui/MorimilApp.kt").readText()
        val hostSource = productionSource(
            "com/morimil/app/ui/GenesisUltraRuntimeHost.kt"
        ).readText()

        assertTrue(appSource.contains("GenesisUltraRuntimeHost()"))
        assertFalse(appSource.contains("val runtimeViewModel: MorimilViewModel = viewModel()"))
        assertTrue(hostSource.contains("gate.requireReady()"))
        assertTrue(hostSource.contains("GenesisUltraRuntimeHostStatus.READY"))
        assertTrue(hostSource.contains("val runtimeViewModel: MorimilViewModel = viewModel()"))
    }

    @Test
    fun viewModelStartupCannotDependOnLegacyBirthTablesOrSeedLegacyRuntime() {
        val viewModelSource = productionSource(
            "com/morimil/app/ui/MorimilViewModel.kt"
        ).readText()

        assertTrue(
            viewModelSource.contains("genesisUltraRuntimeStartupGate.requireReady()")
        )
        assertFalse(viewModelSource.contains("readLocalBirthState"))
        assertFalse(viewModelSource.contains("LocalBirthState"))
        assertFalse(viewModelSource.contains("seedInitialStateIfNeeded"))
        assertFalse(viewModelSource.contains("rest_cycle.startup"))
        assertFalse(viewModelSource.contains("recall.startup"))
    }

    @Test
    fun transcriptIntroCannotClaimLegacyGenesisMobileV1() {
        val transcriptSource = productionSource(
            "com/morimil/app/data/repository/ReasoningTranscriptRepository.kt"
        ).readText()

        assertTrue(transcriptSource.contains("Genesis Ultra comprometido y verificado"))
        assertFalse(transcriptSource.contains("Genesis movil v1 activo"))
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
}
