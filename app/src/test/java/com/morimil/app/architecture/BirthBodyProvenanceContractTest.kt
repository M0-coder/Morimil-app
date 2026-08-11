package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BirthBodyProvenanceContractTest {
    private val gate by lazy {
        repositoryFile("docs/audits/BIRTH_PROVENANCE_00_RELEASE_BODY_GATE_67A59816.md").readText()
    }
    private val workflow by lazy {
        repositoryFile(".github/workflows/signed-release-apk.yml").readText()
    }
    private val gradle by lazy {
        repositoryFile("app/build.gradle.kts").readText()
    }

    @Test
    fun provenanceGateStaysFailClosedUntilExactSignedArtifactExists() {
        assertEquals(
            "# Document status: PROPOSAL",
            gate.lineSequence().first { it.isNotBlank() }
        )
        listOf(
            "BIRTH_PROVENANCE_00=OPEN",
            "SIGNED_RELEASE_EXECUTION=PENDING",
            "APK_SHA256=PENDING",
            "CERTIFICATE_SHA256=PENDING",
            "APK_PACKAGE_INSPECTION=PENDING",
            "INSTALL_ON_PHYSICAL_BODY_AUTHORIZED=false",
            "BIRTH_READINESS_01_AUTHORIZED=false",
            "CANONICAL_INITIAL_BIRTH_AUTHORIZED=false",
            "MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED"
        ).forEach { marker ->
            assertTrue("Missing fail-closed marker: $marker", gate.contains(marker))
        }
    }

    @Test
    fun firstBirthBodyIsBoundToStablePackageAndVersionMetadata() {
        assertTrue(gradle.contains("applicationId = \"com.morimil.app\""))
        assertTrue(gradle.contains("versionCode = 8"))
        assertTrue(gradle.contains("versionName = \"0.3.1-prealpha.plan-v3\""))

        assertTrue(gate.contains("APPLICATION_ID=com.morimil.app"))
        assertTrue(gate.contains("VERSION_CODE=8"))
        assertTrue(gate.contains("VERSION_NAME=0.3.1-prealpha.plan-v3"))
        assertTrue(
            gate.contains(
                "The first physical Body used for canonical Genesis must not be a disposable debug installation."
            )
        )
    }

    @Test
    fun productionSigningWorkflowBindsArtifactToProtectedMainAndExactCertificate() {
        assertTrue(workflow.contains("workflow_dispatch:"))
        assertTrue(workflow.contains("refs/heads/main"))
        assertTrue(workflow.contains("MORIMIL_RELEASE_CERT_SHA256"))
        assertTrue(workflow.contains("\"${'$'}apksigner\" verify --verbose --print-certs"))
        assertTrue(workflow.contains("certificate_sha256=%s"))
        assertTrue(workflow.contains("apk_sha256=%s"))
        assertTrue(workflow.contains("unsigned_apk_sha256=%s"))
        assertTrue(workflow.contains("source_commit=%s"))
        assertTrue(workflow.contains("morimil-signed-release-${'$'}{{ github.sha }}"))
    }

    @Test
    fun distributionCertificateNeverBecomesMorimilAuthority() {
        assertTrue(gate.contains("Instance != Body != Guardian != Android distribution certificate"))
        assertTrue(gate.contains("does not define Morimil's identity"))
        assertTrue(
            gate.contains(
                "does not make the Android signing certificate Morimil's identity or owner"
            )
        )
        assertFalse(gate.contains("distribution certificate = Morimil identity"))
    }

    @Test
    fun gateCannotClaimInstallationOrBirthFromStructuralEvidenceAlone() {
        assertTrue(gate.contains("no successful production-signed artifact"))
        assertTrue(gate.contains("execute `workflow_dispatch`"))
        assertTrue(gate.contains("install an APK"))
        assertTrue(gate.contains("execute Genesis"))
    }

    private fun repositoryFile(relativePath: String): File {
        return sequenceOf(
            File(relativePath),
            File("../$relativePath")
        ).firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")
    }
}
