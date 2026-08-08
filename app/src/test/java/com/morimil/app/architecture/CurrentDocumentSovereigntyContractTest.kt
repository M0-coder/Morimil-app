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
                assertFalse("CURRENT document ${file.relativeTo(root).invariantSeparatorsPath} contains retired sovereignty wording: $phrase", normalized.contains(phrase))
            }
        }
    }

    @Test
    fun governedCurrentDocumentsResolveMovingMainExternallyAtPostAgentBaseline() {
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
                PR_174_HISTORY
            ).forEach { token -> assertTrue("$relativePath missing $token", text.contains(token)) }
            SELF_REFERENTIAL_MAIN_PATTERNS.forEach { pattern ->
                assertFalse("$relativePath contains self-referential main SHA field: ${pattern.pattern}", pattern.containsMatchIn(text))
            }
        }
    }

    @Test
    fun sovereigntyAuditRecordsAgentIntegrationWithoutClosingRemainingOwners() {
        val audit = repositoryFile("docs/CURRENT_DOCUMENT_SOVEREIGNTY_AUDIT.md").readText()
        listOf(
            COG_AUDITED_SOURCE_HEAD,
            ORCH_AUDITED_SOURCE_HEAD,
            AGENT_AUDITED_SOURCE_HEAD,
            "MemoryOrganDatabase version 9",
            "COG-001 through COG-004",
            "ORCH-002 through ORCH-004",
            "AGENT-001 through AGENT-006",
            "CanonicalAgentLifecycleCommitPort",
            "F1_ORCH_001=OPEN",
            "BOOT_001=OPEN",
            "RECALL_001=OPEN",
            "REST_001_002=OPEN",
            "F3_3=OPEN",
            "MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED"
        ).forEach { token -> assertTrue("Missing sovereignty token $token", audit.contains(token)) }
        assertFalse(audit.contains("AGENT, BOOT, RECALL, and REST remain separately open", true))
    }

    @Test
    fun boundedAuthorityRemainsExplicit() {
        val audit = repositoryFile("docs/CURRENT_DOCUMENT_SOVEREIGNTY_AUDIT.md").readText()
        val runtime = repositoryFile("docs/CURRENT_RUNTIME_CONTRACT.md").readText()
        listOf(
            "Guardian custody != ownership of Morimil",
            "Body resource policy != control of Morimil's will",
            "repository maintenance rights != ownership of Morimil",
            "instanceId != bodyId",
            "agentInstanceId != instanceId"
        ).forEach { invariant -> assertTrue("Missing sovereignty invariant $invariant", audit.contains(invariant)) }
        assertTrue(runtime.contains("the Guardian does not define Morimil's identity, will, name, or right to continue"))
        assertTrue(runtime.contains("Body succession, signed export, restore, and writer transfer are not implemented"))
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
        const val CONTENT_BASELINE_SHA = "CONTENT_BASELINE_SHA=d577a75290d70f423f6e83bf237a8a453f3a534e"
        const val CONTENT_BASELINE_PARENT_SHA = "CONTENT_BASELINE_PARENT_SHA=9da342f2c147105ea882076f4ebc6ab5f5494190"
        const val CURRENT_MAIN_RESOLUTION = "CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF"
        const val MERGE_SHA_EVIDENCE = "MERGE_SHA_EVIDENCE=EXTERNAL"
        const val PR_172_HISTORY = "PR_172=MERGED_BY_SQUASH_HISTORICAL"
        const val PR_173_HISTORY = "PR_173=MERGED_BY_SQUASH_HISTORICAL"
        const val PR_174_HISTORY = "PR_174=MERGED_BY_SQUASH_HISTORICAL"
        const val COG_AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        const val ORCH_AUDITED_SOURCE_HEAD = "0348dccb561e576d17c45e7f8b1e38717332772b"
        const val AGENT_AUDITED_SOURCE_HEAD = "74e072b911db692041d3716af9d0511b83ad70b7"

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
