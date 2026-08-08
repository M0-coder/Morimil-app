package com.morimil.app.data.repository

import com.morimil.app.data.genesis.ultra.CanonicalConsumerReadPort
import com.morimil.app.data.genesis.ultra.CanonicalReadDisposition
import com.morimil.app.data.genesis.ultra.CanonicalReadFailure
import com.morimil.app.data.genesis.ultra.CanonicalReadResult
import com.morimil.app.data.genesis.ultra.CanonicalRecallCandidate
import com.morimil.app.data.genesis.ultra.CanonicalRecallCandidateBatch
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.RecallScheduleEntity
import kotlinx.coroutines.flow.Flow

class RecallScheduleRepository internal constructor(
    organDatabase: MemoryOrganDatabase,
    private val canonicalReadPort: CanonicalConsumerReadPort,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val organDao = organDatabase.memoryOrganDao()
    private val memoryLinkRepository = MemoryLinkRepository(organDatabase)

    val activeRecallSchedules: Flow<List<RecallScheduleEntity>> = organDao.observeActiveRecallSchedules()

    suspend fun seedFromRecentMemoryIfNeeded(limit: Int = 10): Int {
        require(limit in 1..CANONICAL_CANDIDATE_LIMIT) { "canonical_recall_seed_limit_invalid" }
        val batch = when (
            val result = canonicalReadPort.readRecallCandidates(CANONICAL_CANDIDATE_LIMIT)
        ) {
            is CanonicalReadResult.Ready -> result.value
            is CanonicalReadResult.Blocked -> return handleBlockedRead(result.failure)
        }
        requireCanonicalBatch(batch)

        val now = nowMillis()
        val candidates = selectCandidates(batch.candidates, limit)
        var created = 0
        candidates.forEach { candidate ->
            val priority = RecallSchedulePolicy.priority(
                memoryKind = candidate.memoryKind,
                importance = candidate.importance,
                confidence = candidate.confidence,
                userConfirmed = candidate.userConfirmed
            )
            val intervalDays = RecallSchedulePolicy.initialIntervalDays(priority)
            val priorityBand = RecallSchedulePolicy.priorityBand(priority)
            val insertedId = organDao.insertRecallSchedule(
                RecallScheduleEntity(
                    // Legacy-named projection column retained until F3.3; this value is the
                    // canonical Genesis Ultra birth-root event hash, never a genesis_core row.
                    genesisCoreId = batch.snapshot.birthRootEventHash,
                    targetEventHash = candidate.event.eventHash,
                    targetMemoryKind = candidate.memoryKind,
                    prompt = buildPrompt(candidate),
                    reason = "canonical_recall_schedule_v1:${candidate.memoryKind}/band=$priorityBand/i${candidate.importance}/c${candidate.confidence}/seq=${candidate.event.sequence}",
                    priority = priority,
                    intervalDays = intervalDays,
                    dueAtMillis = now + RecallSchedulePolicy.delayMillis(intervalDays),
                    status = "active",
                    lastAction = "created",
                    source = CANONICAL_MEMORY_SOURCE,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                    lastReviewedAtMillis = null
                )
            )
            if (insertedId > 0) {
                created += 1
                memoryLinkRepository.createMemoryLink(
                    instanceId = batch.instanceId,
                    // Same legacy-named projection field semantics as RecallScheduleEntity.
                    genesisCoreHash = batch.snapshot.birthRootEventHash,
                    sourceId = canonicalRecallNodeId(candidate.event.eventHash),
                    sourceType = RECALL_NODE_TYPE,
                    targetId = candidate.event.eventHash,
                    targetType = CANONICAL_MEMORY_EVENT_NODE_TYPE,
                    relation = RELATION_SCHEDULES_REVIEW_FOR,
                    strength = priority / 100.0,
                    reason = "canonical_recall_schedule:${candidate.memoryKind}/priority=$priority/band=$priorityBand",
                    createdAtMillis = now
                )
            }
        }
        return created
    }

    suspend fun reinforceRecall(recallId: Long) {
        val schedule = requireNotNull(organDao.loadRecallSchedule(recallId)) {
            "Recall schedule not found."
        }
        val now = nowMillis()
        val nextInterval = RecallSchedulePolicy.nextIntervalDays(
            currentIntervalDays = schedule.intervalDays,
            priority = schedule.priority
        )
        val rows = organDao.updateRecallSchedule(
            recallId = recallId,
            dueAtMillis = now + RecallSchedulePolicy.delayMillis(nextInterval),
            intervalDays = nextInterval,
            status = "active",
            lastAction = "reinforced",
            lastReviewedAtMillis = now,
            updatedAtMillis = now
        )
        require(rows > 0) { "Recall schedule update failed." }
    }

    suspend fun postponeRecall(recallId: Long) {
        val schedule = requireNotNull(organDao.loadRecallSchedule(recallId)) {
            "Recall schedule not found."
        }
        val now = nowMillis()
        val intervalDays = RecallSchedulePolicy.postponedIntervalDays(schedule.priority)
        val rows = organDao.updateRecallSchedule(
            recallId = recallId,
            dueAtMillis = now + RecallSchedulePolicy.delayMillis(intervalDays),
            intervalDays = schedule.intervalDays.coerceAtLeast(intervalDays),
            status = "active",
            lastAction = "postponed",
            lastReviewedAtMillis = schedule.lastReviewedAtMillis,
            updatedAtMillis = now
        )
        require(rows > 0) { "Recall schedule postpone failed." }
    }

    suspend fun degradeRecall(recallId: Long) {
        val now = nowMillis()
        val rows = organDao.markRecallScheduleDegraded(
            recallId = recallId,
            updatedAtMillis = now
        )
        require(rows > 0) { "Recall schedule degrade failed." }
    }

    private fun handleBlockedRead(failure: CanonicalReadFailure): Int {
        if (failure.disposition == CanonicalReadDisposition.NOT_READY) return 0
        throw CanonicalRecallReadException(failure)
    }

    private fun requireCanonicalBatch(batch: CanonicalRecallCandidateBatch) {
        require(batch.instanceId.isNotBlank()) { "canonical_recall_instance_missing" }
        require(batch.snapshot.instanceId == batch.instanceId) {
            "canonical_recall_snapshot_instance_mismatch"
        }
        require(batch.writerBodyId.isNotBlank() && batch.writerBodyId != batch.instanceId) {
            "canonical_recall_writer_binding_invalid"
        }
        require(batch.writerEpochId.isNotBlank()) { "canonical_recall_writer_epoch_missing" }
        batch.candidates.forEach { candidate ->
            require(candidate.event.instanceId == batch.instanceId) {
                "canonical_recall_candidate_foreign_instance"
            }
            require(candidate.event.bodyId == batch.writerBodyId) {
                "canonical_recall_candidate_wrong_body"
            }
            require(candidate.event.signerId == batch.writerBodyId) {
                "canonical_recall_candidate_wrong_signer"
            }
            require(candidate.event.signerEpochId == batch.writerEpochId) {
                "canonical_recall_candidate_stale_epoch"
            }
            require(candidate.event.eventHash.isNotBlank()) {
                "canonical_recall_candidate_hash_missing"
            }
        }
    }

    private fun selectCandidates(
        candidates: List<CanonicalRecallCandidate>,
        limit: Int
    ): List<CanonicalRecallCandidate> {
        return candidates
            .asSequence()
            .filter { candidate ->
                RecallSchedulePolicy.shouldSchedule(
                    memoryKind = candidate.memoryKind,
                    importance = candidate.importance,
                    confidence = candidate.confidence,
                    userConfirmed = candidate.userConfirmed
                )
            }
            .sortedWith(
                compareByDescending<CanonicalRecallCandidate> { candidate ->
                    RecallSchedulePolicy.priority(
                        memoryKind = candidate.memoryKind,
                        importance = candidate.importance,
                        confidence = candidate.confidence,
                        userConfirmed = candidate.userConfirmed
                    )
                }
                    .thenByDescending { it.userConfirmed }
                    .thenByDescending { it.importance }
                    .thenByDescending { it.confidence }
                    .thenByDescending { it.event.sequence }
                    .thenBy { it.event.eventHash }
            )
            .take(limit)
            .toList()
    }

    private fun buildPrompt(candidate: CanonicalRecallCandidate): String {
        return "Repasar ${candidate.memoryKind}: " + candidate.content
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(360)
    }

    internal companion object {
        const val RECALL_NODE_TYPE = "recall_schedule"
        const val CANONICAL_MEMORY_EVENT_NODE_TYPE = "canonical_memory_event"
        const val CANONICAL_MEMORY_SOURCE = "canonical_memory_event"
        const val RELATION_SCHEDULES_REVIEW_FOR = "schedules_review_for"
        private const val CANONICAL_CANDIDATE_LIMIT = 60

        fun canonicalRecallNodeId(eventHash: String): String {
            require(eventHash.isNotBlank()) { "canonical_recall_event_hash_missing" }
            return "recall:$eventHash"
        }
    }
}

internal class CanonicalRecallReadException(
    val failure: CanonicalReadFailure
) : IllegalStateException(
    "canonical_recall_read_${failure.disposition.name.lowercase()}:${failure.diagnosticCode}"
)
