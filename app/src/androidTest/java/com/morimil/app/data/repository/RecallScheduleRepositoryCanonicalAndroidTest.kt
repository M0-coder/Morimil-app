package com.morimil.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.morimil.app.data.genesis.ultra.CanonicalConsumerReadPort
import com.morimil.app.data.genesis.ultra.CanonicalConsumerSnapshot
import com.morimil.app.data.genesis.ultra.CanonicalEventProvenance
import com.morimil.app.data.genesis.ultra.CanonicalEventRef
import com.morimil.app.data.genesis.ultra.CanonicalHealthInput
import com.morimil.app.data.genesis.ultra.CanonicalReadDisposition
import com.morimil.app.data.genesis.ultra.CanonicalReadFailure
import com.morimil.app.data.genesis.ultra.CanonicalReadFailureCode
import com.morimil.app.data.genesis.ultra.CanonicalReadResult
import com.morimil.app.data.genesis.ultra.CanonicalRecallCandidate
import com.morimil.app.data.genesis.ultra.CanonicalRecallCandidateBatch
import com.morimil.app.data.genesis.ultra.CanonicalRestCyclePlanningInput
import com.morimil.app.data.genesis.ultra.CanonicalSnapshotRef
import com.morimil.app.data.local.MemoryOrganDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecallScheduleRepositoryCanonicalAndroidTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var database: MemoryOrganDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
        database = null
    }

    @Test
    fun canonicalCandidateCreatesDerivedScheduleOnceAndReplayAfterRepositoryRestartIsIdempotent() = runBlocking {
        val db = openDatabase()
        val now = 1_800_000_000_000L
        val batch = batch(candidate("event-canonical-1", sequence = 7L))
        val canonicalReadPort = FakeCanonicalReadPort(CanonicalReadResult.Ready(batch))
        val firstProcess = RecallScheduleRepository(
            organDatabase = db,
            canonicalReadPort = canonicalReadPort,
            nowMillis = { now }
        )

        assertEquals(1, firstProcess.seedFromRecentMemoryIfNeeded())

        val restartedProcess = RecallScheduleRepository(
            organDatabase = db,
            canonicalReadPort = canonicalReadPort,
            nowMillis = { now + 5_000L }
        )
        assertEquals(0, restartedProcess.seedFromRecentMemoryIfNeeded())

        val recalls = db.memoryOrganDao().loadActiveRecallSchedulesForReconciliation()
        assertEquals(1, recalls.size)
        val recall = recalls.single()
        assertEquals(BIRTH_ROOT_HASH, recall.genesisCoreId)
        assertEquals("event-canonical-1", recall.targetEventHash)
        assertEquals(RecallScheduleRepository.CANONICAL_MEMORY_SOURCE, recall.source)
        assertTrue(recall.reason.startsWith("canonical_recall_schedule_v1:"))

        val links = db.memoryOrganDao().loadMemoryLinksForReconciliation()
        assertEquals(1, links.size)
        val link = links.single()
        assertEquals(INSTANCE_ID, link.instanceId)
        assertEquals(BIRTH_ROOT_HASH, link.genesisCoreHash)
        assertEquals("recall:${recall.recallId}", link.sourceId)
        assertEquals(RecallScheduleRepository.RECALL_NODE_TYPE, link.sourceType)
        assertEquals("event-canonical-1", link.targetId)
        assertEquals(RecallScheduleRepository.CANONICAL_MEMORY_EVENT_NODE_TYPE, link.targetType)
    }

    @Test
    fun canonicalNotReadyReturnsZeroWithoutCreatingProjection() = runBlocking {
        val db = openDatabase()
        val repository = RecallScheduleRepository(
            organDatabase = db,
            canonicalReadPort = FakeCanonicalReadPort(
                CanonicalReadResult.Blocked(
                    CanonicalReadFailure(
                        code = CanonicalReadFailureCode.BIRTH_NOT_COMMITTED,
                        disposition = CanonicalReadDisposition.NOT_READY,
                        diagnosticCode = "canonical_read_birth_not_committed"
                    )
                )
            ),
            nowMillis = { 1_800_000_000_000L }
        )

        assertEquals(0, repository.seedFromRecentMemoryIfNeeded())
        assertTrue(db.memoryOrganDao().loadActiveRecallSchedulesForReconciliation().isEmpty())
        assertTrue(db.memoryOrganDao().loadMemoryLinksForReconciliation().isEmpty())
    }

    @Test
    fun canonicalVerificationFailureFailsClosedWithoutCreatingProjection() = runBlocking {
        val db = openDatabase()
        val failure = CanonicalReadFailure(
            code = CanonicalReadFailureCode.CHAIN_CORRUPT,
            disposition = CanonicalReadDisposition.BLOCKED,
            diagnosticCode = "canonical_read_previous_hash_mismatch"
        )
        val repository = RecallScheduleRepository(
            organDatabase = db,
            canonicalReadPort = FakeCanonicalReadPort(CanonicalReadResult.Blocked(failure)),
            nowMillis = { 1_800_000_000_000L }
        )

        val thrown = runCatching { repository.seedFromRecentMemoryIfNeeded() }.exceptionOrNull()
        assertTrue(thrown is CanonicalRecallReadException)
        assertEquals(failure, (thrown as CanonicalRecallReadException).failure)
        assertTrue(db.memoryOrganDao().loadActiveRecallSchedulesForReconciliation().isEmpty())
        assertTrue(db.memoryOrganDao().loadMemoryLinksForReconciliation().isEmpty())
    }

    private fun openDatabase(): MemoryOrganDatabase {
        return Room.inMemoryDatabaseBuilder(context, MemoryOrganDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }
    }

    private fun batch(candidate: CanonicalRecallCandidate): CanonicalRecallCandidateBatch {
        return CanonicalRecallCandidateBatch(
            snapshot = CanonicalSnapshotRef(
                instanceId = INSTANCE_ID,
                birthRootEventHash = BIRTH_ROOT_HASH,
                birthRootSequence = 0L,
                lastEventHash = candidate.event.eventHash,
                lastSequence = candidate.event.sequence,
                postBirthEventCount = 1,
                snapshotDigest = "snapshot-digest"
            ),
            instanceId = INSTANCE_ID,
            writerBodyId = BODY_ID,
            writerEpochId = EPOCH_ID,
            candidates = listOf(candidate)
        )
    }

    private fun candidate(eventHash: String, sequence: Long): CanonicalRecallCandidate {
        return CanonicalRecallCandidate(
            event = CanonicalEventRef(
                eventId = "event-id-$sequence",
                eventHash = eventHash,
                sequence = sequence,
                previousEventHash = BIRTH_ROOT_HASH,
                instanceId = INSTANCE_ID,
                bodyId = BODY_ID,
                signerId = BODY_ID,
                signerEpochId = EPOCH_ID,
                signerPublicKeyRef = "writer-key",
                eventType = "living_memory.append",
                actor = "morimil",
                observedAt = "2026-08-08T16:00:00Z",
                contentDigest = "content-digest",
                contentType = "text/plain",
                provenanceDigest = "provenance-digest",
                privacy = "private"
            ),
            content = "Preferencia canónica verificada para recordar.",
            provenance = CanonicalEventProvenance(
                schema = "morimil.memory_provenance.v1",
                instanceId = INSTANCE_ID,
                bodyId = BODY_ID,
                source = "living_memory",
                classification = "preference",
                userConfirmed = true,
                sourceId = null,
                noteSchema = "morimil.living_memory_write.v1",
                noteJson = null
            ),
            memoryKind = "preference",
            importance = 92,
            confidence = 96,
            userConfirmed = true
        )
    }

    private class FakeCanonicalReadPort(
        private val recallResult: CanonicalReadResult<CanonicalRecallCandidateBatch>
    ) : CanonicalConsumerReadPort {
        override suspend fun readVerifiedSnapshot(): CanonicalReadResult<CanonicalConsumerSnapshot> =
            error("not_used")

        override suspend fun readRecallCandidates(
            limit: Int
        ): CanonicalReadResult<CanonicalRecallCandidateBatch> = recallResult

        override suspend fun readRestCyclePlanningInput(
            limit: Int
        ): CanonicalReadResult<CanonicalRestCyclePlanningInput> = error("not_used")

        override suspend fun readHealthInput(
            recentLimit: Int
        ): CanonicalReadResult<CanonicalHealthInput> = error("not_used")
    }

    private companion object {
        const val INSTANCE_ID = "instance-morimil"
        const val BODY_ID = "body-android-current"
        const val EPOCH_ID = "writer-epoch-1"
        const val BIRTH_ROOT_HASH = "canonical-birth-root-hash"
    }
}
