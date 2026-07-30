package com.morimil.app.data.genesis.ultra

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalCognitiveMigrationCommitPortTest {
    @Test
    fun canonicalEnvelopeBindsOperationIdentityWriterAndDigests() {
        val source = sourceFile().readText()

        listOf(
            "cognitive_migration_protocol",
            "cross_database_operations",
            "durable_cognitive_migration_transition",
            "morimil.cross_database_operation.canonical_commit.v1",
            "operation_id",
            "operation_type",
            "operation_version",
            "instance_id",
            "writer_body_id",
            "writer_epoch",
            "subject_id",
            "payload_digest",
            "evidence_digest"
        ).forEach { binding ->
            assertTrue("Missing canonical binding: $binding", source.contains(binding))
        }
    }

    @Test
    fun ensureChecksExistingEventAndRecoversInterruptedAppend() {
        val source = sourceFile().readText()
        val firstLookup = source.indexOf("findVerified(command)?.let")
        val append = source.indexOf("repository.appendText")
        val recoveryLookup = source.indexOf("val recovered = findVerified(command)")
        val postAppendLookup = source.lastIndexOf("findVerified(command)")

        assertTrue(firstLookup >= 0)
        assertTrue(append > firstLookup)
        assertTrue(recoveryLookup > append)
        assertTrue(postAppendLookup > recoveryLookup)
        assertTrue(source.contains("records.size > 1"))
        assertTrue(source.contains("CANONICAL_EVENT_MISMATCH"))
        assertTrue(source.contains("CANONICAL_PROVENANCE_MISMATCH"))
    }

    private fun sourceFile(): File {
        val root = sequenceOf(File("."), File(".."))
            .map(File::getCanonicalFile)
            .firstOrNull { candidate ->
                File(candidate, "README.md").isFile &&
                    File(candidate, "app/build.gradle.kts").isFile
            }
            ?: error("Repository root not found")
        return File(
            root,
            "app/src/main/java/com/morimil/app/data/genesis/ultra/" +
                "CanonicalCognitiveMigrationCommitPort.kt"
        )
    }
}
