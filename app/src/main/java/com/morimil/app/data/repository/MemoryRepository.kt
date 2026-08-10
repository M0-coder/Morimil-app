package com.morimil.app.data.repository

import com.morimil.app.data.local.DecisionLogEntity
import com.morimil.app.data.local.MemoryDao
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.local.ProjectStateEntity
import com.morimil.app.data.local.UserWorkspaceEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

/**
 * Product facade for ordinary workspace metadata plus canonical living-memory writes.
 *
 * Legacy identity and legacy living-memory reads are intentionally absent from this
 * runtime API. Historical tables remain in Room only for bounded migration/convergence.
 */
class MemoryRepository(
    database: MorimilDatabase,
    private val livingMemoryPort: LivingMemoryPort
) {
    private val memoryDao: MemoryDao = database.memoryDao()

    val decisions: Flow<List<DecisionLogEntity>> = memoryDao.observeDecisions()
    val projects: Flow<List<ProjectStateEntity>> = memoryDao.observeProjects()
    val activeWorkspace: Flow<UserWorkspaceEntity?> = memoryDao.observeActiveWorkspace()

    suspend fun renameWorkspace(displayName: String): List<String> {
        return listOf("El nombre solo se define una vez.")
    }

    suspend fun recordMemoryReview(
        targetEvent: CanonicalMemoryPresentationEvent,
        action: String,
        note: String
    ) {
        val cleanAction = action.trim()
            .ifBlank { "reviewed" }
            .replace(Regex("[^a-zA-Z0-9_.-]+"), "_")
        val cleanNote = note.trim().ifBlank { "Revision local de memoria." }
        val importance = when (cleanAction) {
            "aprobado" -> 80
            "correccion_requerida" -> 90
            "ruido_degradado" -> 30
            else -> 60
        }
        livingMemoryPort.append(
            LivingMemoryAppendRequest(
                eventType = "memory_review.$cleanAction",
                actor = "user",
                body = "Revision de memoria canónica: action=$cleanAction; " +
                    "target_event_hash=${targetEvent.eventHash}; " +
                    "target_kind=${targetEvent.memoryKind}; note=$cleanNote; " +
                    "excerpt=${targetEvent.body.take(220)}",
                importance = importance,
                evidenceJson = JSONObject()
                    .put("schema", "morimil.canonical_memory_review.v1")
                    .put("canonical_event_hash", targetEvent.eventHash)
                    .put("canonical_sequence", targetEvent.sequence)
                    .put("action", cleanAction)
                    .put("note", cleanNote)
                    .toString(),
                source = "canonical_memory_review",
                userConfirmed = true
            )
        )
    }

    suspend fun recordSystemMemoryEvent(
        eventType: String,
        body: String,
        importance: Int,
        evidenceJson: String? = null
    ): String? {
        if (body.isBlank()) return null
        return livingMemoryPort.append(
            LivingMemoryAppendRequest(
                eventType = eventType,
                actor = "system",
                body = body,
                importance = importance,
                evidenceJson = evidenceJson,
                source = "system"
            )
        ).eventHash
    }

    suspend fun loadLatestLivingMemoryEventByType(eventType: String): LivingMemoryEventView? {
        return livingMemoryPort.loadLatestByType(eventType)
    }
}
