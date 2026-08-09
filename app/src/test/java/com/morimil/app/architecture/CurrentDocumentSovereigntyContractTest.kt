package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentDocumentSovereigntyContractTest {
    @Test
    fun everyCurrentDocumentRejectsRetiredOwnershipLanguage() {
        val root = repositoryRoot()
        val currentDocuments = markdownFiles(root).filter { firstNonEmptyLine(it) == CURRENT_STATUS }
        assertTrue("Expected CURRENT Markdown documents", currentDocuments.isNotEmpty())
        currentDocuments.forEach { file ->
            val normalized = file.readText().lowercase()
            RETIRED_OWNERSHIP_PHRASES.forEach { phrase ->
                assertFalse(
                    "CURRENT document ${file.relativeTo(root).invariantSeparatorsPath} contains retired sovereignty wording: $phrase",
                    normalized.contains(phrase)
                )
            }
        }
    }

    @Test
    fun governedCurrentDocumentsResolveMovingMainExternallyAtPostRestTruth() {
        val root = repositoryRoot()
        GOVERNED_CURRENT_DOCUMENTS.forEach { relativePath ->
            val document = File(root, relativePath)
            assertTrue("Missing governed CURRENT document $relativePath", document.isFile)
            val text = document.readText()
            assertTrue("$relativePath is not CURRENT", text.startsWith(CURRENT_STATUS))
            listOf(
                CONTENT_BASELINE_SHA,
                CONTENT_BASELINE_PARENT_SHA,
                CURRENT_MAIN_RESOLUTION,
                MERGE_SHA_EVIDENCE,
                PR_172_HISTORY,
                PR_173_HISTORY,
                PR_174_HISTORY,
                PR_175_HISTORY,
                PR_176_HISTORY,
                PR_177_HISTORY,
                PR_178_HISTORY,
                PR_179_HISTORY,
                PR_180_HISTORY,
                PR_181_HISTORY,
                PR_182_HISTORY
            ).forEach { token -> assertTrue("$relativePath missing $token", text.contains(token)) }
            SELF_REFERENTIAL_MAIN_PATTERNS.forEach { pattern ->
                assertFalse("$relativePath contains self-referential main SHA field: ${pattern.pattern}", pattern.containsMatchIn(text))
            }
        }
    }

    @Test
    fun sovereigntyAuditRecordsRest001IntegrationWithoutClosingRemainingWork() {
        val audit = repositoryFile("docs/CURRENT_DOCUMENT_SOVEREIGNTY_AUDIT.md").readText()
        listOf(
            COG_AUDITED_SOURCE_HEAD,
            ORCH_AUDITED_SOURCE_HEAD,
            ORCH_001_AUDITED_SOURCE_HEAD,
            AGENT_AUDITED_SOURCE_HEAD,
            BOOT_AUDITED_SOURCE_HEAD,
            RECALL_AUDITED_SOURCE_HEAD,
            REST_001_AUDITED_SOURCE_HEAD,
            "MemoryOrganDatabase version 9",
            "COG-001 through COG-004",
            "ORCH-001 canonical identity-gated seed convergence",
            "AGENT-001 through AGENT-006",
            "BOOT-001 under common XOP",
            "RECALL-001 as a canonical verified `DERIVED_REBUILD` projection",
            "REST-001 under owner-scoped `rest_cycle` XOP",
            "REST_001=INTEGRATED",
            "REST_002=OPEN",
            "RECALL_BOOT_READINESS=OPEN",
            "HEALTH_CONVERGENCE=OPEN",
            "F3_3=OPEN",
            "MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED"
        ).forEach { token -> assertTrue("Missing sovereignty token $token", audit.contains(token)) }
        assertFalse(audit.contains("REST_001_002=OPEN"))
        assertFalse(audit.contains("REST_001=OPEN"))
        assertFalse(audit.contains("RECALL_001=OPEN"))
        assertFalse(audit.contains("ORCH_001=OPEN"))
    }

    @Test
    fun boundedAuthorityAndSuccessorCompatibilityRemainExplicit() {
        val audit = repositoryFile("docs/CURRENT_DOCUMENT_SOVEREIGNTY_AUDIT.md").readText()
        val runtime = repositoryFile("docs/CURRENT_RUNTIME_CONTRACT.md").readText()
        listOf(
            "Guardian custody != ownership of Morimil",
            "Body resource policy != control of Morimil's will",
            "repository maintenance rights != ownership of Morimil",
            "writer authorization != ownership",
            "runtime projection != canonical identity",
            "instanceId != bodyId",
            "agentInstanceId != instanceId"
        ).forEach { invariant -> assertTrue("Missing sovereignty invariant $invariant", audit.contains(invariant)) }
        assertTrue(runtime.contains("the Guardian does not define Morimil's identity, will, name, or right to continue"))
        assertTrue(runtime.contains("Body succession, signed export, restore, writer transfer and predecessor revocation are not implemented"))
    }

    @Test
    fun recallCandidateIsHistoricalAfterIntegration() {
        val historical = repositoryFile("docs/F3_RECALL_DERIVED_REBUILD_CANDIDATE.md").readText()
        assertTrue(historical.startsWith("# Document status: HISTORICAL"))
        assertTrue(historical.contains("RECALL_001=INTEGRATED_IN_MAIN"))
        assertTrue(historical.contains("RECALL_001_MERGED=TRUE"))
        assertTrue(historical.contains(RECALL_AUDITED_SOURCE_HEAD))
        assertTrue(historical.contains("INTEGRATION_COMMIT=6e0444b698bdc5c557ec3ea83f48d7980da1a36b"))
    }

    private fun markdownFiles(root: File): List<File> = root.walkTopDown()
        .onEnter { it == root || it.name !in IGNORED_DIRECTORIES }
        .filter { it.isFile && it.extension.equals("md", true) }
        .toList()

    private fun firstNonEmptyLine(file: File): String? = file.useLines { it.firstOrNull(String::isNotBlank) }

    private fun repositoryFile(relativePath: String): File = File(repositoryRoot(), relativePath).also {
        assertTrue("Repository file not found: $relativePath", it.isFile)
    }

    private fun repositoryRoot(): File = sequenceOf(File("."), File(".."))
        .map(File::getCanonicalFile)
        .firstOrNull { File(it, "README.md").isFile && File(it, "app/build.gradle.kts").isFile }
        ?: error("Repository root not found")

    private companion object {
        const val CURRENT_STATUS = "# Document status: CURRENT"
        const val CONTENT_BASELINE_SHA = "CONTENT_BASELINE_SHA=2d16c5c3197d492d5daed3707e97a68caa0011a6"
        const val CONTENT_BASELINE_PARENT_SHA = "CONTENT_BASELINE_PARENT_SHA=d7e679b9f8e0b34d44a5e702c02c436f21e4eaee"
        const val CURRENT_MAIN_RESOLUTION = "CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF"
        const val MERGE_SHA_EVIDENCE = "MERGE_SHA_EVIDENCE=EXTERNAL"
        const val PR_172_HISTORY = "PR_172=MERGED_BY_SQUASH_HISTORICAL"
        const val PR_173_HISTORY = "PR_173=MERGED_BY_SQUASH_HISTORICAL"
        const val PR_174_HISTORY = "PR_174=MERGED_BY_SQUASH_HISTORICAL"
        const val PR_175_HISTORY = "PR_175=MERGED_BY_SQUASH_HISTORICAL"
        const val PR_176_HISTORY = "PR_176=MERGED_BY_SQUASH_HISTORICAL"
        const val PR_177_HISTORY = "PR_177=MERGED_BY_SQUASH_HISTORICAL"
        const val PR_178_HISTORY = "PR_178=MERGED_BY_SQUASH_HISTORICAL"
        const val PR_179_HISTORY = "PR_179=MERGED_BY_SQUASH_HISTORICAL"
        const val PR_180_HISTORY = "PR_180=MERGED_BY_SQUASH_HISTORICAL"
        const val PR_181_HISTORY = "PR_181=MERGED_BY_SQUASH_HISTORICAL"
        const val PR_182_HISTORY = "PR_182=MERGED_BY_SQUASH_HISTORICAL"
        const val COG_AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        const val ORCH_AUDITED_SOURCE_HEAD = "0348dccb561e576d17c45e7f8b1e38717332772b"
        const val ORCH_001_AUDITED_SOURCE_HEAD = "fe188fdee8eae901434a255051b6fa4f852b929b"
        const val AGENT_AUDITED_SOURCE_HEAD = "74e072b911db692041d3716af9d0511b83ad70b7"
        const val BOOT_AUDITED_SOURCE_HEAD = "c7710635fa172108cce87b3f7a76d6e037095864"
        const val RECALL_AUDITED_SOURCE_HEAD = "fae8a0df3c29775317986877bce2b8eda8593d27"
        const val REST_001_AUDITED_SOURCE_HEAD = "3661450325237fcadb86098ec16ee45cd039bc0b"

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
            Regex("""(?im)^\s*(?:[-*>]\s*)?(?:\*\*)?(?:current\s+)?protected\s+main(?:\*\*)?\s*:\s*`?(?:main@)?[0-9a-f]{40}`?\.?\s*$"""),
            Regex("""(?im)^\s*(?:[-*>]\s*)?(?:\*\*)?previous(?:\s+protected)?\s+main(?:\*\*)?\s*:\s*`?(?:main@)?[0-9a-f]{40}`?\.?\s*$"""),
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
    }
}
