package com.morimil.app.architecture

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentStatusContractTest {
    @Test
    fun everyMutableMarkdownDocumentDeclaresAnAllowedStatus() {
        val root = repositoryRoot()
        val markdownFiles = markdownFiles(root)
        val mutableFiles = markdownFiles.filterKeys { path -> path !in SEALED_GENESIS_MARKDOWN }

        assertTrue("Expected mutable Markdown documents", mutableFiles.isNotEmpty())
        mutableFiles.forEach { (path, file) ->
            val firstLine = firstNonEmptyLine(file)
            assertNotNull("Missing document status in $path", firstLine)
            assertTrue(
                "Invalid or missing document status in $path: $firstLine",
                STATUS_PATTERN.matches(requireNotNull(firstLine))
            )
        }
    }

    @Test
    fun directoryAndSupersessionClassificationsStayFailClosed() {
        val statuses = markdownFiles(repositoryRoot())
            .filterKeys { path -> path !in SEALED_GENESIS_MARKDOWN }
            .mapValues { (_, file) -> statusOf(file) }

        statuses.filterKeys { path -> path.startsWith("docs/archive/") }
            .forEach { (path, status) ->
                assertEquals("$path must remain historical", "HISTORICAL", status)
            }
        statuses.filterKeys { path ->
            path.startsWith("docs/research/") ||
                path.startsWith("docs/model-artifacts/")
        }.forEach { (path, status) ->
            assertEquals("$path must remain research-only", "RESEARCH_ONLY", status)
        }
        SUPERSEDED_DOCUMENTS.forEach { path ->
            assertEquals("$path must remain superseded", "SUPERSEDED", statuses[path])
        }
        PROPOSAL_DOCUMENTS.forEach { path ->
            assertEquals("$path must remain a proposal", "PROPOSAL", statuses[path])
        }
        assertEquals("CURRENT", statuses["README.md"])
        assertEquals("CURRENT", statuses["docs/DOCUMENT_STATUS_POLICY.md"])
    }

    @Test
    fun sealedGenesisMarkdownRemainsByteExactAndUnclassified() {
        val root = repositoryRoot()
        SEALED_GENESIS_MARKDOWN.forEach { (path, expectedHash) ->
            val file = File(root, path)
            assertTrue("Missing sealed Genesis document: $path", file.isFile)
            assertEquals("Sealed Genesis document changed: $path", expectedHash, sha256(file))
            assertFalse(
                "Sealed Genesis document must not receive an app-doc status header: $path",
                STATUS_PATTERN.matches(firstNonEmptyLine(file).orEmpty())
            )
        }
    }

    private fun markdownFiles(root: File): Map<String, File> {
        return root.walkTopDown()
            .onEnter { directory ->
                directory == root || directory.name !in IGNORED_DIRECTORIES
            }
            .filter { file -> file.isFile && file.extension.equals("md", ignoreCase = true) }
            .associateBy { file -> file.relativeTo(root).invariantSeparatorsPath }
    }

    private fun statusOf(file: File): String {
        val firstLine = firstNonEmptyLine(file)
        val match = STATUS_PATTERN.matchEntire(firstLine.orEmpty())
        requireNotNull(match) { "Missing document status: ${file.path}" }
        return match.groupValues[1]
    }

    private fun firstNonEmptyLine(file: File): String? {
        return file.useLines { lines -> lines.firstOrNull(String::isNotBlank) }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
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
        val STATUS_PATTERN = Regex(
            "# Document status: (CURRENT|HISTORICAL|PROPOSAL|RESEARCH_ONLY|SUPERSEDED)"
        )
        val IGNORED_DIRECTORIES = setOf(".git", ".gradle", "build")
        val SEALED_GENESIS_MARKDOWN = mapOf(
            "app/src/main/assets/genesis/docs/GENESIS_MEMORY_CORE.md" to
                "cc426e7473a7f1d1b38fe98a217b61b1c48323566176d3b0c1b16622dee22c50",
            "app/src/main/assets/genesis/doctrine/doctrine.md" to
                "0020228d16e0d94f8e22df0966de9f67622534115e53e872e2a77feac0392ffb",
            "app/src/main/assets/genesis/doctrine/evolution_rules.md" to
                "1f81f9e4c8f4fc9af065b91810d5434253ee71945a5f17de834c79f758437572"
        )
        val SUPERSEDED_DOCUMENTS = setOf(
            "docs/ANDROID_STANDARDS_BASELINE.md",
            "docs/APP_ARCHITECTURE_V2.md",
            "docs/ARCHITECTURE.md",
            "docs/ARCHITECTURE_MAP.md",
            "docs/GENESIS_FORK_MODEL.md",
            "docs/MORIMIL_APP_V2_INTEGRATION.md",
            "docs/MORIMIL_REASONING_KERNEL.md",
            "docs/OBSIDIAN_ORGAN_MAP.md",
            "docs/ROADMAP.md"
        )
        val PROPOSAL_DOCUMENTS = setOf(
            "docs/PC_START.md",
            "tools/pc-agent/README.md"
        )
    }
}
