package com.morimil.app.data.genesis.ultra

import com.morimil.app.data.local.GenesisUltraBirthCommitEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraDurableBirthAuthorizationTest {
    @Test
    fun canonicalEntityRoundTripPreservesAuthorization() {
        val evidence = evidence()
        val entity = evidence.toEntity(GenesisUltraBirthCommitEntity.PRIMARY_SLOT)

        val parsed = GenesisUltraDurableBirthAuthorization.fromEntity(entity)

        assertEquals(evidence, parsed)
        assertTrue(entity.sourceBytes.contentEquals(parsed.copySourceBytes()))
        assertEquals(GenesisUltraHashProfile.sha256(entity.sourceBytes), entity.sourceDigest)
    }

    @Test
    fun sourceTamperingFailsClosedEvenWhenColumnsRemainUntouched() {
        val entity = evidence().toEntity(GenesisUltraBirthCommitEntity.PRIMARY_SLOT)
        val altered = entity.copy(
            sourceBytes = entity.sourceBytes.copyOf().also { bytes ->
                bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            }
        )

        val failure = runCatching {
            GenesisUltraDurableBirthAuthorization.fromEntity(altered)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(
            failure?.message.orEmpty().contains("durable_birth_authorization_source_digest_mismatch")
        )
    }

    @Test
    fun suppliedAuthorizationDigestMustMatchEveryBoundField() {
        val valid = evidence()

        val failure = runCatching {
            GenesisUltraDurableBirthAuthorization.create(
                candidateDigest = valid.candidateDigest,
                consentDigest = valid.consentDigest,
                birthStateDigest = valid.birthStateDigest,
                receiptDigest = valid.receiptDigest,
                bodyId = valid.bodyId,
                guardianId = valid.guardianId,
                guardianKeyEpochId = valid.guardianKeyEpochId,
                authorizedAt = valid.authorizedAt,
                expiresAt = valid.expiresAt,
                authorizationDigest = digest("wrong-authorization")
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(
            failure?.message.orEmpty().contains("durable_birth_authorization_digest_mismatch")
        )
    }

    private fun evidence(): GenesisUltraDurableBirthAuthorization {
        return GenesisUltraDurableBirthAuthorization.create(
            candidateDigest = digest("candidate"),
            consentDigest = digest("consent"),
            birthStateDigest = digest("birth-state"),
            receiptDigest = digest("receipt"),
            bodyId = "body_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            guardianId = "guardian_01HMORIMILCUSTODIAN0001",
            guardianKeyEpochId = "guardian_epoch_01HMORIMIL000001",
            authorizedAt = "2026-07-25T00:00:00Z",
            expiresAt = "2026-07-25T00:02:00Z"
        )
    }

    private fun digest(value: String): String {
        return GenesisUltraHashProfile.sha256(value.toByteArray(Charsets.UTF_8))
    }
}
