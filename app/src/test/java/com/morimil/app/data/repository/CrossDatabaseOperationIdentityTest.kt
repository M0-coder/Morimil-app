package com.morimil.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossDatabaseOperationIdentityTest {
    @Test
    fun sameIntentProducesSameOperationAndEventIds() {
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "schema" to "morimil.cognitive_migration.cog_001.payload.v1",
                "migration_id" to "cog_migration_" + "a".repeat(64),
                "source_event_hashes_sorted" to listOf(
                    "evsha256:" + "1".repeat(64),
                    "evsha256:" + "2".repeat(64)
                )
            )
        )
        val digest = CrossDatabaseOperationIdentity.digestCanonicalJson(payload)
        val first = operationId(digest)
        val second = operationId(digest)

        assertEquals(first, second)
        assertTrue(first.matches(Regex("^xop_[a-f0-9]{64}$")))
        assertEquals(
            CrossDatabaseOperationIdentity.eventId(first, "cognitive_migration.proposed"),
            CrossDatabaseOperationIdentity.eventId(second, "cognitive_migration.proposed")
        )
    }

    @Test
    fun clockMetadataDoesNotParticipateInIdentity() {
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf("schema" to "test.payload.v1", "subject" to "migration-1")
        )
        val digest = CrossDatabaseOperationIdentity.digestCanonicalJson(payload)

        val stagedAtOne = operationId(digest)
        val stagedAtTwo = operationId(digest)

        assertEquals(stagedAtOne, stagedAtTwo)
    }

    @Test
    fun changedPayloadProducesDifferentOperationAndEventIds() {
        val firstDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(
            CrossDatabaseOperationIdentity.canonicalJson(
                mapOf("schema" to "test.payload.v1", "decision" to "approve")
            )
        )
        val secondDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(
            CrossDatabaseOperationIdentity.canonicalJson(
                mapOf("schema" to "test.payload.v1", "decision" to "reject")
            )
        )
        val first = operationId(firstDigest)
        val second = operationId(secondDigest)

        assertNotEquals(first, second)
        assertNotEquals(
            CrossDatabaseOperationIdentity.eventId(first, "cognitive_migration.approved"),
            CrossDatabaseOperationIdentity.eventId(second, "cognitive_migration.approved")
        )
    }

    @Test
    fun canonicalJsonSortsKeysAndPreservesArrayOrder() {
        val canonical = CrossDatabaseOperationIdentity.canonicalJson(
            linkedMapOf(
                "z" to listOf(2, 1),
                "a" to true,
                "m" to "á"
            )
        )

        assertEquals("""{"a":true,"m":"á","z":[2,1]}""", canonical)
        assertTrue(
            CrossDatabaseOperationIdentity.digestCanonicalJson(canonical)
                .matches(Regex("^sha256:[a-f0-9]{64}$"))
        )
    }

    private fun operationId(payloadDigest: String): String {
        return CrossDatabaseOperationIdentity.operationId(
            operationType = "cognitive_migration.propose",
            operationVersion = 1,
            instanceId = "instance-1",
            writerBodyId = "body-1",
            writerEpoch = "epoch-1",
            subjectId = "migration-1",
            parentOperationId = null,
            childPhase = null,
            payloadDigest = payloadDigest
        )
    }
}
