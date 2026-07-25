package com.morimil.app.data.genesis.ultra

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraSignedSeedPreviewBoundaryTest {
    @Test
    fun previewCoordinatorCannotRecordConsentAuthorizeOrExecuteBirth() {
        val source = sourceFile(
            "src/main/java/com/morimil/app/data/genesis/ultra/" +
                "GenesisUltraSignedSeedPreviewCoordinator.kt"
        ).readText()

        assertTrue(source.contains("GenesisUltraReleaseArchiveReader"))
        assertTrue(source.contains("guardianTrustAnchorStore.verifyRelease"))
        assertTrue(source.contains("candidateConstructionCoordinator.construct"))
        assertFalse(source.contains("GenesisUltraAndroidHostBirthConsentStore"))
        assertFalse(source.contains("GenesisUltraAtomicBirthAuthorizationCoordinator"))
        assertFalse(source.contains("GenesisUltraAtomicBirthExecutionCoordinator"))
        assertFalse(source.contains("withTransaction"))
        assertFalse(source.contains(".execute("))
    }

    @Test
    fun onboardingKeepsBirthActionDisabledAfterPreview() {
        val source = sourceFile(
            "src/main/java/com/morimil/app/ui/OnboardingScreen.kt"
        ).readText()

        assertTrue(source.contains("Candidato verificado; consentimiento aún bloqueado"))
        assertTrue(source.contains("Vista previa efímera: no es consentimiento, testimonio ni nacimiento."))
        assertTrue(source.contains("Button(\n                enabled = false"))
    }

    private fun sourceFile(relativePath: String): File {
        return sequenceOf(
            File(relativePath),
            File("app/$relativePath")
        ).firstOrNull(File::isFile)
            ?: error("Source file not found: $relativePath")
    }
}
