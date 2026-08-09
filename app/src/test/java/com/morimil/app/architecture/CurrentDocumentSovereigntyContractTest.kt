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
    fun governedCurrentDocumentsResolveMovingMainExternallyAtPostBootstrapHealthRestReadinessTruth() {
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
                PR_182_HISTORY,
                PR_183_HISTORY,
                PR_184_HISTORY,
                PR_186_HISTORY,
                PR_187_HISTORY,
                PR_188_HISTORY,
                BOOTSTRAP_HEALTH_AUDITED_SOURCE_HEAD,
                REST_BOOT_001_AUDITED_SOURCE_HEAD
            ).forEach { token -> assertTrue("$relativePath missing $token", text.contains(token)) }
            SELF_REFERENTIAL_MAIN_PATTERNS.forEach { pattern ->
                assertFalse("$relativePath contains self-referential main SHA field: ${pattern.pattern}", pattern.containsMatchIn(text))
            }
        }
    }

    @Test
    fun sovereigntyAuditRecordsRestReadinessAndBootstrapHealthWithoutClosingF1HealthOrRecall() {
        val audit = repositoryFile("docs/CURRENT_DOCUMENT_SOVEREIGNTY_AUDIT.md").readText()
        listOf(
            COG_AUDITED_SOURCE_HEAD,
            ORCH_AUDITED_SOURCE_HEAD,
            ORCH_001_AUDITED_SOURCE_HEAD,
            AGENT_AUDITED_SOURCE_HEAD,
            BOOT_AUDITED_SOURCE_HEAD,
            RECALL_AUDITED_SOURCE_HEAD,
            REST_001_AUDITED_SOURCE_HEAD,
            REST_002_AUDITED_SOURCE_HEAD,
            BOOTSTRAP_HEALTH_AUDITED_SOURCE_HEAD,
            REST_BOOT_001_AUDITED_SOURCE_HEAD,
            "MemoryOrganDatabase version 9",
            "COG-001 through COG-004",
            "ORCH-001 canonical identity-gated seed convergence",
            "AGENT-001 through AGENT-006",
            "BOOT-001 under common XOP",
            "RECALL-001 as a canonical verified `DERIVED_REBUILD` projection",
            "REST-001 canonical local-consolidation execution under owner-scoped `rest_cycle` XOP",
            "REST-002 canonical repair-proposal convergence under the same closed `rest_cycle` owner registry",
            "dependency-derived bootstrap health from PR #187",
            "REST-BOOT-001 read-only startup readiness",
            "REST_BOOT_READINESS=INTEGRATED",
            "RECALL_BOOT_READINESS=OPEN",
            "BOOTSTRAP_HEALTH_DERIVATION=INTEGRATED",
            "HEALTH_CONVERGENCE=OPEN",
            "HEALTH_CONVERGED=false",
            "HEALTH_STATE=WAITING_FOR_DEPENDENCIES",
            "F3_3=OPEN",
            "MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED"
        ).forEach { token -> assertTrue("Missing sovereignty token $token", audit.contains(token)) }
        assertTrue(audit.contains("LocalNervousSystemRepository.recordHealthCheckIfDegraded"))
        assertFalse(audit.contains("REST_BOOT_READINESS=OPEN"))
        assertFalse(audit.contains("HEALTH_CONVERGENCE=INTEGRATED"))
        assertFalse(audit.contains("REST_001_002=OPEN"))
        assertFalse(audit.contains("REST_001=OPEN"))
        assertFalse(audit.contains("REST_002=OPEN"))
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
        const val CONTENT_BASELINE_SHA = "CONTENT_BASELINE_SHA=32a183e7821de49a4958c52d75693c43ee99b2e1"
        const val CONTENT_BASELINE_PARENT_SHA = "CONTENT_BASELINE_PARENT_SHA=0e06cd99c72db66a72d6f36345a2dae6d63c4c1f"
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
        const val PR_183_HISTORY = "PR_183=MERGED_BY_SQUASH_HISTORICAL"
        const val PR_184_HISTORY = "PR_184=MERGED_BY_SQUASH_HISTORICAL"
        const val PR_186_HISTORY = "PR_186=MERGED_BY_SQUASH_HISTORICAL"
        const val PR_187_HISTORY = "PR_187=MERGED_BY_SQUASH_HISTORICAL"
        const val PR_188_HISTORY = "PR_188=MERGED_BY_SQUASH_HISTORICAL"
        const val COG_AUDITED_SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        const val ORCH_AUDITED_SOURCE_HEAD = "0348dccb561e576d17c45e7f8b1e38717332772b"
        const val ORCH_001_AUDITED_SOURCE_HEAD = "fe188fdee8eae901434a255051b6fa4f852b929b"
        const val AGENT_AUDITED_SOURCE_HEAD = "74e072b911db692041d3716af9d0511b83ad70b7"
        const val BOOT_AUDITED_SOURCE_HEAD = "c7710635fa172108cce87b3f7a76d6e037095864"
        const val RECALL_AUDITED_SOURCE_HEAD = "fae8a0df3c29775317986877bce2b8eda8593d27"
        const val REST_001_AUDITED_SOURCE_HEAD = "3661450325237fcadb86098ec16ee45cd039bc0b"
        const val REST_002_AUDITED_SOURCE_HEAD = "2ecca3f48d5e0ef27bd927da3986292daf7f7e2c"
        const val BOOTSTRAP_HEALTH_AUDITED_SOURCE_HEAD = "f1697227241459f316bd562756e15ae3ce02c90d"
        const val REST_BOOT_001_AUDITED_SOURCE_HEAD = "dd7a92a011fd4c453775df6ec307638b05313ec9"

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