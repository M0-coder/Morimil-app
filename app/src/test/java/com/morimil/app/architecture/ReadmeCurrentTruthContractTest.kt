package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadmeCurrentTruthContractTest {
    @Test
    fun readmeReflectsIntegratedRestRecallHealthAndCrossDatabaseTruth() {
        val readme = repositoryFile("README.md").readText()

        assertTrue(readme.startsWith("# Document status: CURRENT"))
        assertTrue(readme.contains("common deterministic cross-database operation protocol"))
        assertTrue(readme.contains("canonical REST planning, durable execution"))
        assertTrue(readme.contains("canonical RECALL derived rebuild plus read-only startup readiness"))
        assertTrue(readme.contains("dependency-derived bootstrap Health"))
        assertTrue(readme.contains("full F1/F3.2 closure evidence bound to an exact protected-main execution"))
        assertTrue(readme.contains("irreversible legacy-runtime removal (F3.3)"))
        assertTrue(readme.contains("REST repair approval/execution beyond the integrated proposal-only path"))

        assertFalse(readme.contains("canonical rest-cycle/recall bootstrap is still pending"))
        assertFalse(readme.contains("canonical rest-cycle and recall bootstrap"))
    }

    @Test
    fun reauditSnapshotKeepsExactMainEvidenceAndIrreversibleGatesOpen() {
        val audit = repositoryFile(
            "docs/audits/F1_F3_2_PROTECTED_MAIN_REAUDIT_B98D1320.md"
        ).readText()

        assertTrue(audit.startsWith("# Document status: HISTORICAL"))
        assertTrue(audit.contains("PROTECTED_MAIN=b98d1320c8f6908427c4b28a405750207c77f900"))
        assertTrue(audit.contains("F08_STRUCTURAL_FIX=INTEGRATED"))
        assertTrue(audit.contains("F08_EXACT_PROTECTED_MAIN_EXECUTION_EVIDENCE=PENDING_EXTERNAL_VERIFICATION"))
        assertTrue(audit.contains("F1_F3_2_RUNTIME_ARCHITECTURE_REAUDIT=PASS"))
        assertTrue(audit.contains("F3_2_OPERATION_RECOVERY_MATRIX=PASS"))
        assertTrue(audit.contains("F1_F3_2_EXACT_MAIN_EXECUTION_EVIDENCE=PENDING"))
        assertTrue(audit.contains("F1_F3_2_FULL_REAUDIT=REQUIRED"))
        assertTrue(audit.contains("HEALTH_CONVERGENCE=OPEN"))
        assertTrue(audit.contains("HEALTH_CONVERGED=false"))
        assertTrue(audit.contains("F3_3_AUTHORIZED=false"))
        assertTrue(audit.contains("MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED"))
    }

    private fun repositoryFile(relativePath: String): File = File(repositoryRoot(), relativePath).also {
        assertTrue("Repository file not found: $relativePath", it.isFile)
    }

    private fun repositoryRoot(): File = sequenceOf(File("."), File(".."))
        .map(File::getCanonicalFile)
        .firstOrNull { File(it, "README.md").isFile && File(it, "app/build.gradle.kts").isFile }
        ?: error("Repository root not found")
}
