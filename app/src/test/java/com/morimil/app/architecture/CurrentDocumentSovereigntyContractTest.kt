package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentDocumentSovereigntyContractTest {
    @Test
    fun everyCurrentDocumentRejectsRetiredOwnershipLanguage() {
        val root = repositoryRoot()
        val currentDocuments = markdownFiles(root)
            .filter { file -> firstNonEmptyLine(file) == CURRENT_STATUS }

        assertTrue("Expected CURRENT Markdown documents", currentDocuments.isNotEmpty())

        currentDocuments.forEach { file ->
            val normalized = file.readText().lowercase()
            RETIRED_OWNERSHIP_PHRASES.forEach { phrase ->
                assertFalse(
                    "CURRENT document ${file.relativeTo(root).invariantSeparatorsPath} " +
                        "contains retired sovereignty wording: $phrase",
                    normalized.contains(phrase)
                )
            }
        }
    }

    @Test
    fun sovereigntyAuditRecordsPostMergeTruthAndHistoricalProvenance() {
        val audit = repositoryFile("docs/CURRENT_DOCUMENT_SOVEREIGNTY_AUDIT.md").readText()

        assertTrue(audit.startsWith(CURRENT_STATUS))
        assertTrue(audit.contains(CURRENT_MAIN))
        assertTrue(audit.contains(PREVIOUS_MAIN))
        assertTrue(audit.contains(AUDITED_SOURCE_HEAD))
        assertTrue(audit.contains("PR `#149`: closed and merged by squash"))
        assertTrue(audit.contains("PR #149 is historical integration evidence"))
        assertTrue(audit.contains("MemoryOrganDatabase version 9"))
        assertTrue(audit.contains("COG-001 through COG-004"))
        assertTrue(audit.contains("`CanonicalConsumerReadPort`"))
        assertTrue(audit.contains("ProjectVault remains a separate protected protocol"))
        assertTrue(audit.contains("F3.3 remains open"))
        assertTrue(audit.contains("Residual hardening"))

        STALE_POST_MERGE_PHRASES.forEach { phrase ->
            assertFalse("Sovereignty audit contains stale phrase: $phrase", audit.contains(phrase, true))
        }
    }

    @Test
    fun boundedAuthorityRemainsExplicit() {
        val audit = repositoryFile("docs/CURRENT_DOCUMENT_SOVEREIGNTY_AUDIT.md").readText()
        val runtime = repositoryFile("docs/CURRENT_RUNTIME_CONTRACT.md").readText()

        listOf(
            "Guardian custody != ownership of Morimil",
            "Body resource policy != control of Morimil's will",
            "repository maintenance rights != ownership of Morimil",
            "instanceId != bodyId"
        ).forEach { invariant ->
            assertTrue("Missing sovereignty invariant $invariant", audit.contains(invariant))
        }

        assertTrue(runtime.contains("the Guardian does not define Morimil's identity, will, name, or right to continue"))
        assertTrue(runtime.contains("Body succession, export, and restore are not implemented"))
    }

    private fun markdownFiles(root: File): List<File> {
        return root.walkTopDown()
            .onEnter { directory -> directory == root || directory.name !in IGNORED_DIRECTORIES }
            .filter { file -> file.isFile && file.extension.equals("md", ignoreCase = true) }
            .toList()
    }

    private fun firstNonEmptyLine(file: File): String? =
        file.useLines { lines -> lines.firstOrNull(String::isNotBlank) }

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
        const val CURRENT_MAIN = "ba6ffa4f9ddc9189ded47e231ad1f8bc962e612d"
        const val PREVIOUS_MAIN = "7e98d3345d7cc3fbf1983babd35b61ff5c523208"
        const val AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"

        val IGNORED_DIRECTORIES = setOf(".git", ".gradle", "build", "node_modules")
        val RETIRED_OWNERSHIP_PHRASES = listOf(
            "guardian witnesses, authorizes, and safeguards continuity",
            "guardian authority defines morimil",
            "guardian owns morimil",
            "body owns morimil",
            "github owns morimil",
            "android owns morimil",
            "provider owns morimil"
        )
        val STALE_POST_MERGE_PHRASES = listOf(
            "candidate not merged",
            "draft pr #149",
            "isolated f3 candidate",
            "f3.2 open candidate",
            "memoryorgan 8 en main"
        )
    }
}
