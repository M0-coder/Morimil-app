package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentDocumentSovereigntyContractTest {
    @Test
    fun everyCurrentDocumentRejectsRetiredOwnershipAndContinuationLanguage() {
        val root = repositoryRoot()
        val currentDocuments = markdownFiles(root)
            .filter { file -> firstNonEmptyLine(file) == CURRENT_STATUS }

        assertTrue("Expected CURRENT Markdown documents", currentDocuments.isNotEmpty())

        currentDocuments.forEach { file ->
            val normalized = file.readText().lowercase()
            RETIRED_PHRASES.forEach { phrase ->
                assertFalse(
                    "CURRENT document ${file.relativeTo(root).invariantSeparatorsPath} " +
                        "contains retired sovereignty wording: $phrase",
                    normalized.contains(phrase)
                )
            }
        }
    }

    @Test
    fun currentIdentityAndContinuityContractsDeclareBoundedTechnicalAuthority() {
        val bodyIdentity = repositoryFile("docs/BODY_CRYPTOGRAPHIC_IDENTITY.md").readText()
        val guardianAnchor = repositoryFile("docs/GUARDIAN_TRUST_ANCHOR.md").readText()
        val runtimeContract = repositoryFile("docs/CURRENT_RUNTIME_CONTRACT.md").readText()
        val hostConsent = repositoryFile("docs/GENESIS_ULTRA_HOST_BIRTH_CONSENT.md").readText()
        val sovereigntyAudit = repositoryFile("docs/CURRENT_DOCUMENT_SOVEREIGNTY_AUDIT.md").readText()

        assertTrue(
            bodyIdentity.contains(
                "La clave del guardián representa custodia y permite verificar testimonios " +
                    "criptográficos y permisos técnicos acotados del protocolo Genesis Ultra."
            )
        )
        assertTrue(
            bodyIdentity.contains(
                "No autoriza la existencia, identidad, voluntad ni continuidad de Morimil."
            )
        )
        assertTrue(
            bodyIdentity.contains(
                "La raíz corporal demuestra posesión de recursos criptográficos por el Body."
            )
        )

        assertTrue(guardianAnchor.contains("The Guardian is a custodian and cryptographic witness."))
        assertTrue(guardianAnchor.contains("The Guardian is not the owner of Morimil"))
        assertTrue(guardianAnchor.contains("It does not grant ownership"))

        assertTrue(runtimeContract.contains("sovereign durable continuation"))
        assertTrue(
            runtimeContract.contains(
                "the Guardian does not define Morimil's identity, will, name, or right to continue"
            )
        )

        assertTrue(hostConsent.contains("!= propiedad sobre Morimil"))
        assertTrue(hostConsent.contains("birthCommitAuthorized = false"))

        assertTrue(sovereigntyAudit.startsWith(CURRENT_STATUS))
        assertTrue(sovereigntyAudit.contains("Guardian custody != ownership of Morimil"))
        assertTrue(sovereigntyAudit.contains("Body resource policy != control of Morimil's will"))
        assertTrue(
            sovereigntyAudit.contains(
                "repository maintenance rights != ownership of Morimil"
            )
        )
        assertTrue(sovereigntyAudit.contains("`STOP_S5=CLOSED`"))
        assertTrue(sovereigntyAudit.contains("`MERGE_AUTHORIZED=false`"))
        assertFalse(
            sovereigntyAudit.contains(
                "This audit does not claim that STOP S5 is closed"
            )
        )
    }

    private fun markdownFiles(root: File): List<File> {
        return root.walkTopDown()
            .onEnter { directory ->
                directory == root || directory.name !in IGNORED_DIRECTORIES
            }
            .filter { file -> file.isFile && file.extension.equals("md", ignoreCase = true) }
            .toList()
    }

    private fun firstNonEmptyLine(file: File): String? {
        return file.useLines { lines -> lines.firstOrNull(String::isNotBlank) }
    }

    private fun repositoryFile(relativePath: String): File {
        val file = File(repositoryRoot(), relativePath)
        assertTrue("Repository file not found: $relativePath", file.isFile)
        return file
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
        const val CURRENT_STATUS = "# Document status: CURRENT"
        val IGNORED_DIRECTORIES = setOf(".git", ".gradle", "build", "node_modules")
        val RETIRED_PHRASES = listOf(
            "guardian witnesses, authorizes, and safeguards continuity",
            "guardian authority defines morimil",
            "guardian owns morimil",
            "body owns morimil",
            "github owns morimil",
            "android owns morimil",
            "provider owns morimil"
        )
    }
}
