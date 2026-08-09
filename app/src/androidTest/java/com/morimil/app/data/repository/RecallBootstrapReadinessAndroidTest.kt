package com.morimil.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.morimil.app.data.genesis.ultra.CanonicalConsumerReadPort
import com.morimil.app.data.genesis.ultra.CanonicalConsumerSnapshot
import com.morimil.app.data.genesis.ultra.CanonicalHealthInput
import com.morimil.app.data.genesis.ultra.CanonicalReadResult
import com.morimil.app.data.genesis.ultra.CanonicalRecallCandidateBatch
import com.morimil.app.data.genesis.ultra.CanonicalRestCyclePlanningInput
import com.morimil.app.data.genesis.ultra.CanonicalSnapshotRef
import com.morimil.app.data.genesis.ultra.GenesisUltraHashProfile
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeActiveBody
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeAuthorization
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeAuthorizationState
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeDocument
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeGuardian
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimePolicy
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeVerifiedSeed
import com.morimil.app.data.local.MemoryOrganDatabase
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecallBootstrapReadinessAndroidTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var database: MemoryOrganDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
        database = null
    }

    @Test
    fun readinessReReadsCanonicalStateAfterRepositoryRecreationWithoutCreatingProjection() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, MemoryOrganDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }
        val port = FakeCanonicalReadPort(CanonicalReadResult.Ready(batch()))
        val identity = validIdentity()

        val firstRepository = RecallScheduleRepository(db, port)
        assertTrue(firstRepository.isBootstrapReady(identity))
        assertTrue(db.memoryOrganDao().loadActiveRecallSchedulesForReconciliation().isEmpty())
        assertTrue(db.memoryOrganDao().loadMemoryLinksForReconciliation().isEmpty())

        val recreatedRepository = RecallScheduleRepository(db, port)
        assertTrue(recreatedRepository.isBootstrapReady(identity))
        assertTrue(db.memoryOrganDao().loadActiveRecallSchedulesForReconciliation().isEmpty())
        assertTrue(db.memoryOrganDao().loadMemoryLinksForReconciliation().isEmpty())
    }

    private fun batch(): CanonicalRecallCandidateBatch {
        return CanonicalRecallCandidateBatch(
            snapshot = CanonicalSnapshotRef(
                instanceId = INSTANCE_ID,
                birthRootEventHash = digest('a'),
                birthRootSequence = 1L,
                lastEventHash = digest('b'),
                lastSequence = 1L,
                postBirthEventCount = 0,
                snapshotDigest = digest('c')
            ),
            instanceId = INSTANCE_ID,
            writerBodyId = BODY_ID,
            writerEpochId = EPOCH_ID,
            candidates = emptyList()
        )
    }

    private fun validIdentity(): GenesisUltraRuntimeIdentity {
        val doctrine = document("doctrine/free-birth.md", "doctrine", "free birth doctrine")
        val charter = document("policy/freedom-charter.json", "freedom_charter", "{}")
        val recovery = document("policy/recovery-policy.json", "recovery_policy", "{}")
        return GenesisUltraRuntimeIdentity(
            instanceId = INSTANCE_ID,
            companionName = "Morimil",
            bornAt = "2026-07-25T00:00:00Z",
            identityDigest = GenesisUltraHashProfile.sha256("identity".utf8()),
            activeBody = GenesisUltraRuntimeActiveBody(
                bodyId = BODY_ID,
                status = "active_writer",
                platformProfile = "android",
                publicKeyFingerprint = GenesisUltraHashProfile.sha256("body-key".utf8()),
                keyEpochId = EPOCH_ID,
                keyEpochDigest = GenesisUltraHashProfile.sha256("body-epoch".utf8()),
                registryEpoch = 1L,
                registryDigest = GenesisUltraHashProfile.sha256("registry".utf8())
            ),
            guardian = GenesisUltraRuntimeGuardian(
                guardianId = "guardian_test",
                keyEpochId = "guardian_key_epoch_1",
                publicKeyRef = GenesisUltraHashProfile.sha256("guardian-key".utf8()),
                status = "active",
                role = "custodian_witness",
                anchorDigest = GenesisUltraHashProfile.sha256("guardian-anchor".utf8())
            ),
            seed = GenesisUltraRuntimeVerifiedSeed(
                seedId = "seed_test",
                rootHash = GenesisUltraHashProfile.sha256("seed-root".utf8()),
                protocolVersion = "genesis-ultra-v1",
                hashProfile = "sha256",
                identityDigest = GenesisUltraHashProfile.sha256("seed-identity".utf8()),
                doctrineDigest = doctrine.digest
            ),
            doctrine = doctrine,
            policy = GenesisUltraRuntimePolicy(
                freedomCharter = charter,
                recoveryPolicy = recovery,
                freedomCharterDigest = charter.digest,
                recoveryPolicyDigest = recovery.digest
            ),
            authorization = GenesisUltraRuntimeAuthorization(
                state = GenesisUltraRuntimeAuthorizationState.COMMITTED,
                authorizationDigest = GenesisUltraHashProfile.sha256("authorization".utf8()),
                candidateDigest = GenesisUltraHashProfile.sha256("candidate".utf8()),
                consentDigest = GenesisUltraHashProfile.sha256("consent".utf8()),
                authorizedAt = "2026-07-25T00:00:00Z",
                expiresAt = "2026-07-25T00:05:00Z",
                receiptDigest = GenesisUltraHashProfile.sha256("receipt".utf8()),
                birthStatus = "born",
                ownershipConferred = false
            )
        )
    }

    private fun document(relativePath: String, kind: String, text: String): GenesisUltraRuntimeDocument {
        val bytes = text.utf8()
        return GenesisUltraRuntimeDocument(
            relativePath = relativePath,
            documentKind = kind,
            digest = GenesisUltraHashProfile.sha256(bytes),
            sourceBytes = bytes
        )
    }

    private fun String.utf8(): ByteArray = toByteArray(StandardCharsets.UTF_8)

    private fun digest(character: Char): String = "sha256:" + character.toString().repeat(64)

    private class FakeCanonicalReadPort(
        private val recallResult: CanonicalReadResult<CanonicalRecallCandidateBatch>
    ) : CanonicalConsumerReadPort {
        override suspend fun readVerifiedSnapshot(): CanonicalReadResult<CanonicalConsumerSnapshot> =
            error("not_used")

        override suspend fun readRecallCandidates(limit: Int): CanonicalReadResult<CanonicalRecallCandidateBatch> =
            recallResult

        override suspend fun readRestCyclePlanningInput(
            limit: Int
        ): CanonicalReadResult<CanonicalRestCyclePlanningInput> = error("not_used")

        override suspend fun readHealthInput(
            recentLimit: Int
        ): CanonicalReadResult<CanonicalHealthInput> = error("not_used")
    }

    private companion object {
        const val INSTANCE_ID = "instance_test"
        const val BODY_ID = "body_test"
        const val EPOCH_ID = "body_key_epoch_1"
    }
}
