package com.morimil.app.data.repository

import androidx.room.withTransaction
import com.morimil.app.core.identity.StableIdDigest
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.ProjectVaultEntity
import com.morimil.app.data.local.ProjectVaultOutboxEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

internal data class ProjectVaultOutboxRecoveryReport(
    val recoveredCount: Int,
    val failedCount: Int,
    val pendingCount: Int,
    val blockedCount: Int,
    val committedCount: Int
)

class ProjectVaultRepository(
    private val organDatabase: MemoryOrganDatabase,
    private val commitPort: ProjectVaultCommitPort
) {
    private val dao = organDatabase.memoryOrganDao()
    private val outboxDao = organDatabase.projectVaultOutboxDao()
    private val dispatchMutex = Mutex()

    val projectVaults: Flow<List<ProjectVaultEntity>> = dao.observeProjectVaults()

    suspend fun createProjectVaultFromIntent(
        displayName: String,
        mission: String,
        projectType: String = inferProjectType(displayName, mission),
        sourceContext: String = "user_intent",
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        requireRecoveryHasNoBlockedOperations()
        val cleanName = displayName.trim().ifBlank { "Nuevo proyecto" }
        val cleanMission = mission.trim().ifBlank { "Construir y coordinar un proyecto nuevo." }
        val vaultId = buildVaultId(cleanName, nowMillis)
        val vault = ProjectVaultEntity(
            vaultId = vaultId,
            displayName = cleanName,
            companyName = cleanName,
            projectType = projectType.trim().ifBlank { "company_project" },
            mission = cleanMission,
            status = STATUS_ACTIVE,
            roadmapSummary = buildInitialRoadmap(cleanName, cleanMission),
            progressPercent = 0,
            activeAgentCount = 0,
            healthStatus = HEALTH_PLANNING,
            sourceContext = sourceContext.trim().ifBlank { "user_intent" },
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            completedAtMillis = null
        )
        val operation = buildOperation(
            operationType = ProjectVaultOutboxEntity.OPERATION_CREATE,
            desiredVault = vault,
            note = cleanMission,
            nowMillis = nowMillis
        )
        stageCreate(operation)
        dispatchOperation(operation.operationId)
        return vaultId
    }

    suspend fun completeProjectVault(
        vaultId: String,
        finalSummary: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        requireRecoveryHasNoBlockedOperations()
        val cleanVaultId = vaultId.trim()
        if (cleanVaultId.isEmpty()) return false
        val operation = organDatabase.withTransaction {
            val current = dao.loadProjectVault(cleanVaultId) ?: return@withTransaction null
            requireNoPendingOperation(cleanVaultId)
            val desired = current.copy(
                status = STATUS_COMPLETED,
                healthStatus = HEALTH_COMPLETED,
                progressPercent = 100,
                roadmapSummary = finalSummary.trim().ifBlank { "Proyecto completado." },
                updatedAtMillis = nowMillis,
                completedAtMillis = nowMillis
            )
            val operation = buildOperation(
                operationType = ProjectVaultOutboxEntity.OPERATION_COMPLETE,
                desiredVault = desired,
                note = finalSummary,
                nowMillis = nowMillis
            )
            outboxDao.insert(operation)
            operation
        } ?: return false
        dispatchOperation(operation.operationId)
        return true
    }

    suspend fun archiveProjectVault(
        vaultId: String,
        reason: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        requireRecoveryHasNoBlockedOperations()
        val cleanVaultId = vaultId.trim()
        if (cleanVaultId.isEmpty()) return false
        val operation = organDatabase.withTransaction {
            val current = dao.loadProjectVault(cleanVaultId) ?: return@withTransaction null
            requireNoPendingOperation(cleanVaultId)
            val desired = current.copy(
                status = STATUS_ARCHIVED,
                healthStatus = HEALTH_ARCHIVED,
                updatedAtMillis = nowMillis
            )
            val operation = buildOperation(
                operationType = ProjectVaultOutboxEntity.OPERATION_ARCHIVE,
                desiredVault = desired,
                note = reason,
                nowMillis = nowMillis
            )
            outboxDao.insert(operation)
            operation
        } ?: return false
        dispatchOperation(operation.operationId)
        return true
    }

    internal suspend fun recoverPendingOperations(
        limit: Int = DEFAULT_RECOVERY_LIMIT
    ): ProjectVaultOutboxRecoveryReport {
        require(limit in 1..MAX_RECOVERY_LIMIT) { "project_vault_outbox_recovery_limit_invalid" }
        return dispatchMutex.withLock {
            var recovered = 0
            var failed = 0
            outboxDao.loadPending(limit).forEach { operation ->
                runCatching { dispatchUnlocked(operation.operationId) }
                    .onSuccess { recovered += 1 }
                    .onFailure { failed += 1 }
            }
            ProjectVaultOutboxRecoveryReport(
                recoveredCount = recovered,
                failedCount = failed,
                pendingCount = outboxDao.countPending(),
                blockedCount = outboxDao.countBlocked(),
                committedCount = outboxDao.countCommitted()
            )
        }
    }

    private suspend fun requireRecoveryHasNoBlockedOperations() {
        val recovery = recoverPendingOperations()
        check(recovery.blockedCount == 0) { "project_vault_outbox_blocked" }
    }

    private suspend fun stageCreate(operation: ProjectVaultOutboxEntity) {
        organDatabase.withTransaction {
            val existingOperation = outboxDao.load(operation.operationId)
            if (existingOperation != null) {
                requireSameOperation(existingOperation, operation)
                return@withTransaction
            }
            check(dao.loadProjectVault(operation.vaultId) == null) {
                "project_vault_create_existing_vault"
            }
            requireNoPendingOperation(operation.vaultId)
            outboxDao.insert(operation)
        }
    }

    private suspend fun requireNoPendingOperation(vaultId: String) {
        check(outboxDao.countPendingForVault(vaultId) == 0) {
            "project_vault_outbox_operation_already_pending"
        }
    }

    private suspend fun dispatchOperation(operationId: String): ProjectVaultCommitReceipt {
        return dispatchMutex.withLock { dispatchUnlocked(operationId) }
    }

    private suspend fun dispatchUnlocked(operationId: String): ProjectVaultCommitReceipt {
        val operation = requireNotNull(outboxDao.load(operationId)) {
            "project_vault_outbox_operation_missing"
        }
        if (operation.status == ProjectVaultOutboxEntity.STATUS_COMMITTED) {
            return committedReceipt(operation)
        }
        check(operation.status == ProjectVaultOutboxEntity.STATUS_PENDING) {
            "project_vault_outbox_operation_not_retryable"
        }

        val receipt = try {
            commitPort.ensureCommitted(operation.toCommitCommand())
        } catch (error: Throwable) {
            outboxDao.recordRetryableFailure(
                operationId = operation.operationId,
                error = failureCode(error),
                updatedAtMillis = System.currentTimeMillis()
            )
            throw error
        }

        try {
            organDatabase.withTransaction {
                val current = requireNotNull(outboxDao.load(operation.operationId)) {
                    "project_vault_outbox_operation_lost"
                }
                if (current.status == ProjectVaultOutboxEntity.STATUS_COMMITTED) {
                    return@withTransaction
                }
                check(current.status == ProjectVaultOutboxEntity.STATUS_PENDING) {
                    "project_vault_outbox_operation_not_pending"
                }
                requireSameOperation(current, operation)
                applyLocalTransition(current)
                check(
                    outboxDao.markCommitted(
                        operationId = current.operationId,
                        canonicalEventHash = receipt.eventHash,
                        canonicalSequence = receipt.sequence,
                        committedAtMillis = System.currentTimeMillis()
                    ) == 1
                ) { "project_vault_outbox_commit_state_not_updated" }
            }
        } catch (conflict: ProjectVaultOutboxConflict) {
            outboxDao.markBlocked(
                operationId = operation.operationId,
                error = failureCode(conflict),
                canonicalEventHash = receipt.eventHash,
                canonicalSequence = receipt.sequence,
                updatedAtMillis = System.currentTimeMillis()
            )
            throw conflict
        } catch (error: Throwable) {
            outboxDao.recordRetryableFailure(
                operationId = operation.operationId,
                error = failureCode(error),
                updatedAtMillis = System.currentTimeMillis()
            )
            throw error
        }
        return receipt
    }

    private suspend fun applyLocalTransition(operation: ProjectVaultOutboxEntity) {
        val payload = JSONObject(operation.payloadJson)
        val desired = decodeVault(payload.getJSONObject("vault"))
        if (desired.vaultId != operation.vaultId) {
            throw ProjectVaultOutboxConflict("project_vault_outbox_payload_vault_mismatch")
        }
        when (operation.operationType) {
            ProjectVaultOutboxEntity.OPERATION_CREATE -> {
                val existing = dao.loadProjectVault(operation.vaultId)
                if (existing == null) {
                    dao.insertProjectVault(desired)
                } else if (existing != desired) {
                    throw ProjectVaultOutboxConflict("project_vault_outbox_create_conflict")
                }
            }

            ProjectVaultOutboxEntity.OPERATION_COMPLETE -> {
                val existing = dao.loadProjectVault(operation.vaultId)
                    ?: throw ProjectVaultOutboxConflict("project_vault_outbox_complete_missing_vault")
                if (
                    existing.status == STATUS_COMPLETED &&
                    existing.healthStatus == HEALTH_COMPLETED &&
                    existing.progressPercent == 100 &&
                    existing.roadmapSummary == desired.roadmapSummary &&
                    existing.completedAtMillis == desired.completedAtMillis
                ) {
                    return
                }
                val updated = dao.completeProjectVault(
                    vaultId = operation.vaultId,
                    status = STATUS_COMPLETED,
                    healthStatus = HEALTH_COMPLETED,
                    progressPercent = 100,
                    roadmapSummary = desired.roadmapSummary,
                    updatedAtMillis = desired.updatedAtMillis,
                    completedAtMillis = requireNotNull(desired.completedAtMillis)
                )
                if (updated != 1) {
                    throw ProjectVaultOutboxConflict("project_vault_outbox_complete_conflict")
                }
            }

            ProjectVaultOutboxEntity.OPERATION_ARCHIVE -> {
                val existing = dao.loadProjectVault(operation.vaultId)
                    ?: throw ProjectVaultOutboxConflict("project_vault_outbox_archive_missing_vault")
                if (
                    existing.status == STATUS_ARCHIVED &&
                    existing.healthStatus == HEALTH_ARCHIVED &&
                    existing.updatedAtMillis == desired.updatedAtMillis
                ) {
                    return
                }
                val updated = dao.archiveProjectVault(
                    vaultId = operation.vaultId,
                    status = STATUS_ARCHIVED,
                    healthStatus = HEALTH_ARCHIVED,
                    updatedAtMillis = desired.updatedAtMillis
                )
                if (updated != 1) {
                    throw ProjectVaultOutboxConflict("project_vault_outbox_archive_conflict")
                }
            }

            else -> throw ProjectVaultOutboxConflict("project_vault_outbox_operation_unknown")
        }
    }

    private fun buildOperation(
        operationType: String,
        desiredVault: ProjectVaultEntity,
        note: String,
        nowMillis: Long
    ): ProjectVaultOutboxEntity {
        val payloadJson = JSONObject()
            .put("schema", PAYLOAD_SCHEMA)
            .put("operation_type", operationType)
            .put("note", note.trim())
            .put("vault", encodeVault(desiredVault))
            .toString()
        val payloadDigest = StableIdDigest.shortSha256Hex(
            namespace = "project_vault_outbox_payload",
            parts = listOf(payloadJson),
            hexLength = 64
        )
        val operationId = "project_vault_${operationType}_$payloadDigest"
        val eventId = "project_vault_event_$payloadDigest"
        val eventType = when (operationType) {
            ProjectVaultOutboxEntity.OPERATION_CREATE -> EVENT_VAULT_CREATED
            ProjectVaultOutboxEntity.OPERATION_COMPLETE -> EVENT_VAULT_COMPLETED
            ProjectVaultOutboxEntity.OPERATION_ARCHIVE -> EVENT_VAULT_ARCHIVED
            else -> error("project_vault_outbox_operation_type_invalid")
        }
        val evidenceJson = JSONObject()
            .put("schema", EVIDENCE_SCHEMA)
            .put("operation_id", operationId)
            .put("operation_type", operationType)
            .put("payload_digest", payloadDigest)
            .put("recorded_at_millis", nowMillis)
            .put("vault", encodeVault(desiredVault))
            .put("note", note.trim())
            .toString()
        val eventBody = buildString {
            append("Boveda de proyecto ")
            append(operationType)
            append(": ")
            append(desiredVault.displayName)
            append("; type=")
            append(desiredVault.projectType)
            append("; status=")
            append(desiredVault.status)
            append("; mission=")
            append(desiredVault.mission)
        }
        return ProjectVaultOutboxEntity(
            operationId = operationId,
            vaultId = desiredVault.vaultId,
            operationType = operationType,
            eventId = eventId,
            eventType = eventType,
            eventBody = eventBody,
            evidenceJson = evidenceJson,
            payloadJson = payloadJson,
            payloadDigest = payloadDigest,
            status = ProjectVaultOutboxEntity.STATUS_PENDING,
            attemptCount = 0,
            lastError = null,
            canonicalEventHash = null,
            canonicalSequence = null,
            occurredAtMillis = nowMillis,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            committedAtMillis = null
        )
    }

    private fun ProjectVaultOutboxEntity.toCommitCommand(): ProjectVaultCommitCommand {
        return ProjectVaultCommitCommand(
            operationId = operationId,
            vaultId = vaultId,
            operationType = operationType,
            eventId = eventId,
            eventType = eventType,
            eventBody = eventBody,
            evidenceJson = evidenceJson,
            payloadDigest = payloadDigest,
            occurredAtMillis = occurredAtMillis
        )
    }

    private fun committedReceipt(operation: ProjectVaultOutboxEntity): ProjectVaultCommitReceipt {
        return ProjectVaultCommitReceipt(
            eventId = operation.eventId,
            eventHash = requireNotNull(operation.canonicalEventHash),
            sequence = requireNotNull(operation.canonicalSequence),
            reusedExistingEvent = true
        )
    }

    private fun requireSameOperation(
        existing: ProjectVaultOutboxEntity,
        expected: ProjectVaultOutboxEntity
    ) {
        check(
            existing.operationId == expected.operationId &&
                existing.vaultId == expected.vaultId &&
                existing.operationType == expected.operationType &&
                existing.eventId == expected.eventId &&
                existing.eventType == expected.eventType &&
                existing.eventBody == expected.eventBody &&
                existing.evidenceJson == expected.evidenceJson &&
                existing.payloadJson == expected.payloadJson &&
                existing.payloadDigest == expected.payloadDigest
        ) { "project_vault_outbox_operation_collision" }
    }

    private fun encodeVault(vault: ProjectVaultEntity): JSONObject {
        return JSONObject()
            .put("vault_id", vault.vaultId)
            .put("display_name", vault.displayName)
            .put("company_name", vault.companyName)
            .put("project_type", vault.projectType)
            .put("mission", vault.mission)
            .put("status", vault.status)
            .put("roadmap_summary", vault.roadmapSummary)
            .put("progress_percent", vault.progressPercent)
            .put("active_agent_count", vault.activeAgentCount)
            .put("health_status", vault.healthStatus)
            .put("source_context", vault.sourceContext)
            .put("created_at_millis", vault.createdAtMillis)
            .put("updated_at_millis", vault.updatedAtMillis)
            .put("completed_at_millis", vault.completedAtMillis ?: JSONObject.NULL)
    }

    private fun decodeVault(json: JSONObject): ProjectVaultEntity {
        return ProjectVaultEntity(
            vaultId = json.getString("vault_id"),
            displayName = json.getString("display_name"),
            companyName = json.getString("company_name"),
            projectType = json.getString("project_type"),
            mission = json.getString("mission"),
            status = json.getString("status"),
            roadmapSummary = json.getString("roadmap_summary"),
            progressPercent = json.getInt("progress_percent"),
            activeAgentCount = json.getInt("active_agent_count"),
            healthStatus = json.getString("health_status"),
            sourceContext = json.getString("source_context"),
            createdAtMillis = json.getLong("created_at_millis"),
            updatedAtMillis = json.getLong("updated_at_millis"),
            completedAtMillis = if (json.isNull("completed_at_millis")) {
                null
            } else {
                json.getLong("completed_at_millis")
            }
        )
    }

    private fun failureCode(error: Throwable): String {
        return (error.message ?: error::class.java.simpleName).take(MAX_ERROR_LENGTH)
    }

    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_ARCHIVED = "archived"
        const val HEALTH_PLANNING = "planning"
        const val HEALTH_COMPLETED = "completed"
        const val HEALTH_ARCHIVED = "archived"

        private const val EVENT_VAULT_CREATED = "project.vault_created"
        private const val EVENT_VAULT_COMPLETED = "project.vault_completed"
        private const val EVENT_VAULT_ARCHIVED = "project.vault_archived"
        private const val PAYLOAD_SCHEMA = "morimil.project_vault_outbox.payload.v1"
        private const val EVIDENCE_SCHEMA = "morimil.project_vault_outbox.evidence.v1"
        private const val DEFAULT_RECOVERY_LIMIT = 50
        private const val MAX_RECOVERY_LIMIT = 500
        private const val MAX_ERROR_LENGTH = 240

        fun buildVaultId(displayName: String, nowMillis: Long): String {
            val suffix = StableIdDigest.shortSha256Hex(
                namespace = "project_vault",
                parts = listOf(displayName.lowercase(), nowMillis.toString())
            )
            return "vault_${nowMillis}_$suffix"
        }

        fun inferProjectType(displayName: String, mission: String): String {
            val text = "$displayName $mission".lowercase()
            return when {
                listOf("pago", "payment", "billetera", "wallet", "fintech", "exchange")
                    .any { it in text } -> "fintech"
                listOf("anime", "animacion", "animation", "studio", "dibujo")
                    .any { it in text } -> "creative_studio"
                listOf("app", "android", "software", "repo").any { it in text } -> "software"
                else -> "company_project"
            }
        }

        fun buildInitialRoadmap(displayName: String, mission: String): String {
            return "1. Definir vision de $displayName. 2. Capturar contexto base. " +
                "3. Crear enjambre inicial. 4. Proponer arquitectura. " +
                "5. Validar avances con aprobacion humana. Mision: $mission"
        }
    }
}

private class ProjectVaultOutboxConflict(message: String) : IllegalStateException(message)
