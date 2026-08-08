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
    fun governedCurrentDocumentsResolveMovingMainExternally() {
        val root = repositoryRoot()

        GOVERNED_CURRENT_DOCUMENTS.forEach { relativePath ->
            val document = File(root, relativePath)
            assertTrue("Missing governed CURRENT document $relativePath", document.isFile)
            val text = document.readText()

            assertTrue("$relativePath is not CURRENT", text.startsWith(CURRENT_STATUS))
            assertTrue("$relativePath missing content baseline", text.contains(CONTENT_BASELINE_SHA))
            assertTrue("$relativePath missing baseline parent", text.contains(CONTENT_BASELINE_PARENT_SHA))
            assertTrue("$relativePath missing external main resolution", text.contains(CURRENT_MAIN_RESOLUTION))
            assertTrue("$relativePath missing external merge evidence", text.contains(MERGE_SHA_EVIDENCE))
            assertTrue("$relativePath missing PR #153 history", text.contains(PR_153_HISTORY))
            assertTrue("$relativePath missing PR #172 history", text.contains(PR_172_HISTORY))

            SELF_REFERENTIAL_MAIN_PATTERNS.forEach { pattern ->
                assertFalse(
                    "$relativePath contains a self-referential main SHA field: ${pattern.pattern}",
                    pattern.containsMatchIn(text)
                )
            }
        }
    }

    @Test
    fun sovereigntyAuditRecordsBaselineResolutionAndHistoricalProvenance() {
        val audit = repositoryFile("docs/CURRENT_DOCUMENT_SOVEREIGNTY_AUDIT.md").readText()

        assertTrue(audit.startsWith(CURRENT_STATUS))
        assertTrue(audit.contains(CONTENT_BASELINE_SHA))
        assertTrue(audit.contains(CONTENT_BASELINE_PARENT_SHA))
        assertTrue(audit.contains(CURRENT_MAIN_RESOLUTION))
        assertTrue(audit.contains(MERGE_SHA_EVIDENCE))
        assertTrue(audit.contains(COG_AUDITED_SOURCE_HEAD))
        assertTrue(audit.contains(ORCH_AUDITED_SOURCE_HEAD))
        assertTrue(audit.contains("PR `#149`: closed and merged by squash"))
        assertTrue(audit.contains("PR `#150`: closed and merged by squash"))
        assertTrue(audit.contains("PR `#153`: closed and merged by squash"))
        assertTrue(audit.contains("PR `#172`: closed and merged by squash"))
        assertTrue(audit.contains("PR #149 and PR #172 are historical integration evidence"))
        assertTrue(audit.contains("PR #150 and PR #153 are historical CURRENT reconciliation evidence"))
        assertTrue(audit.contains("MemoryOrganDatabase version 9"))
        assertTrue(audit.contains("COG-001 through COG-004"))
        assertTrue(audit.contains("ORCH-002 through ORCH-004"))
        assertTrue(audit.contains("`CanonicalConsumerReadPort`"))
        assertTrue(audit.contains("ProjectVault remains a separate protected protocol"))
        assertTrue(audit.contains("F1_ORCH_001=OPEN"))
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
        assertTrue(runtime.contains("MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED") || audit.contains("MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED"))
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
        const val CONTENT_BASELINE_SHA =
            "CONTENT_BASELINE_SHA=c6a6b0ca998d053c31c75977c5b6d4d9ae166e96"
        const val CONTENT_BASELINE_PARENT_SHA =
            "CONTENT_BASELINE_PARENT_SHA=c22920f68f8820bbec676a6cbc74b60548e43d29"
        const val CURRENT_MAIN_RESOLUTION = "CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF"
        const val MERGE_SHA_EVIDENCE = "MERGE_SHA_EVIDENCE=EXTERNAL"
        const val PR_153_HISTORY = "PR_153=MERGED_BY_SQUASH_HISTORICAL"
        const val PR_172_HISTORY = "PR_172=MERGED_BY_SQUASH_HISTORICAL"
        const val COG_AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        const val ORCH_AUDITED_SOURCE_HEAD = "0348dccb561e576d17c45e7f8b1e38717332772b"

        val GOVERNED_CURRENT_DOCUMENTS = setOf(
            "docs/CURRENT_DOCUMENT_SOVEREIGNTY_AUDIT.md",
            "docs/CURRENT_RUNTIME_CONTRACT.md",
            "docs/F1_CANONICAL_CONSUMER_CONVERGENCE.md",
            "docs/F3_COGNITIVE_MIGRATION_IMPLEMENTATION_BLUEPRINT.md",
            "docs/F3_CROSS_DATABASE_OPERATION_INVENTORY.md",
            "docs/adr/ADR-0002-cross-database-operation-protocol.md"
        )
        val SELF_REFERENTIAL_MAIN_PATTERNS = listOf(
            Regex("""(?im)^\s*CURRENT_MAIN=[0-9a-f]{40}\s*$"""),
            Regex("""(?im)^\s*PREVIOUS_MAIN=[0-9a-f]{40}\s*$"""),
            Regex(
                """(?im)^\s*(?:[-*>]\s*)?(?:\*\*)?(?:current\s+)?protected\s+main""" +
                    """(?:\*\*)?\s*:\s*`?(?:main@)?[0-9a-f]{40}`?\.?\s*$"""
            ),
            Regex(
                """(?im)^\s*(?:[-*>]\s*)?(?:\*\*)?previous(?:\s+protected)?\s+main""" +
                    """(?:\*\*)?\s*:\s*`?(?:main@)?[0-9a-f]{40}`?\.?\s*$"""
            ),
            Regex("""(?im)^\s*-\s*Current squash commit:\s*`?[0-9a-f]{40}`?\.?\s*$""")
        )
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
            "memoryorgan 8 en main",
            "orch, agent, boot, recall, and rest remain separately open"
        )
    }
}
