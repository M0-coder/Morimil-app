package com.morimil.app.data.genesis.ultra

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraAtomicBirthWitnessArchiveBoundaryTest {
    @Test
    fun transportReaderCannotAuthorizeExecuteOrInjectTrust() {
        val source = sourceFile(
            "src/main/java/com/morimil/app/data/genesis/ultra/" +
                "GenesisUltraAtomicBirthWitnessArchiveReader.kt"
        ).readText()

        assertTrue(source.contains("GenesisUltraAtomicBirthWitnessPackage"))
        assertTrue(source.contains("expectedCandidateDigest"))
        assertTrue(source.contains("expectedConsentDigest"))
        assertTrue(source.contains("expectedEvaluatedAt"))
        assertFalse(source.contains("GenesisUltraAtomicBirthAuthorizationCoordinator"))
        assertFalse(source.contains("GenesisUltraAuthorizedAtomicBirth"))
        assertFalse(source.contains("GenesisUltraAtomicBirthExecutionCoordinator"))
        assertFalse(source.contains("GenesisUltraAndroidBodyIdentityRootStore"))
        assertFalse(source.contains("GenesisUltraAndroidGuardianTrustAnchorStore"))
        assertFalse(source.contains("GenesisUltraAndroidHostBirthConsentStore"))
        assertFalse(source.contains("MorimilDatabase"))
        assertFalse(source.contains("AndroidKeystore"))
        assertFalse(source.contains(".execute("))
        assertFalse(source.contains("withTransaction"))
    }

    private fun sourceFile(relativePath: String): File {
        return sequenceOf(
            File(relativePath),
            File("app/$relativePath")
        ).firstOrNull(File::isFile)
            ?: error("Source file not found: $relativePath")
    }
}
