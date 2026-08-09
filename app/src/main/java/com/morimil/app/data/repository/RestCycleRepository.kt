package com.morimil.app.data.repository

import com.morimil.app.core.memory.RestCycleMode
import com.morimil.app.data.genesis.ultra.CanonicalConsumerReadPort
import com.morimil.app.data.genesis.ultra.CanonicalReadDisposition
import com.morimil.app.data.genesis.ultra.CanonicalReadFailure
import com.morimil.app.data.genesis.ultra.CanonicalReadResult
import com.morimil.app.data.genesis.ultra.CanonicalRestCyclePlanningInput
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentityRepository
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.MemoryOrganDatabase
import java.time.Instant

class RestCycleRepository internal constructor(
    organDatabase: MemoryOrganDatabase,
    private val identityRepository: GenesisUltraRuntimeIdentityRepository,
    private val canonicalReadPort: CanonicalConsumerReadPort,
    private val protocol: CrossDatabaseOperationCoordinator,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val migrationStore = RestCycleMigrationStore(organDatabase, nowMillis)

    suspend fun runLocalRestCycleIfDue(force: Boolean = false): Boolean {
        val context = loadCanonicalContext() ?: return false
        if (!force && !isDue(context.planning.latestRestCycle?.observedAt)) return false

        val meaningfulEvents = selectMeaningfulEvents(context.sources)
        if (meaningfulEvents.isEmpty()) return false
        if (!force && meaningfulEvents.size < REST_CYCLE_MIN_EVENTS) return false

        val mode = if (force) RestCycleMode.Deep else RestCycleMode.Normal
        val approvalRequired = !force && RestCyclePolicy.requiresHumanApproval(meaningfulEvents)
        val plan = buildPlan(
            context = context,
            meaningfulEvents = meaningfulEvents,
            mode = mode,
            approvalRequired = approvalRequired
        )
        migrationStore.ensurePlanned(
            migrationId = plan.migrationId,
            instanceId = context.identity.instanceId,
            birthRootEventHash = context.planning.snapshot.birthRootEventHash,
            sourceEventHashes = plan.linkedSources.map { it.eventHash },
            preSnapshotId = context.planning.latestRestCycle?.eventHash
                ?: context.planning.snapshot.birthRootEventHash,
            snapshotDigest = context.planning.snapshot.snapshotDigest,
            sourceSetDigest = context.planning.sourceSetDigest,
            mode = mode,
            approvalRequired = approvalRequired,
            riskLevel = if (approvalRequired) "medium" else "low",
            summary = plan.summary
        )
        if (approvalRequired) return false
        return executePlan(context, plan, approvalRequired = false, approvalId = null)
    }

    suspend fun approvePlannedRestCycle(migrationId: String): Boolean {
        val existing = migrationStore.load(migrationId) ?: return false
        if (
            existing.migrationType != RestCycleMigrationStore.REST_CYCLE_MIGRATION_TYPE ||
            existing.status != RestCycleMigrationStore.STATUS_PLANNED ||
            !existing.approvalRequired
        ) {
            return false
        }

        val context = loadCanonicalContext() ?: return false
        val meaningfulEvents = selectMeaningfulEvents(context.sources)
        if (meaningfulEvents.isEmpty()) return false
        val plan = buildPlan(
            context = context,
            meaningfulEvents = meaningfulEvents,
            mode = RestCycleMode.Normal,
            approvalRequired = true
        )
        if (plan.migrationId != migrationId) return false
        val approvalId = "user_approved:$migrationId"
        migrationStore.approveExact(migrationId, approvalId)
        return executePlan(context, plan, approvalRequired = true, approvalId = approvalId)
    }

    private suspend fun loadCanonicalContext(): CanonicalRestCycleContext? {
        val identity = identityRepository.readCommittedIdentity() ?: return null
        val recovery = protocol.recoverBeforeMutation(
            identity = identity,
            ownerType = RestCycleProtocolTypes.OWNER_TYPE,
            limit = MAX_RECOVERY_BATCH
        )
        check(recovery.blockedCount == 0) { "rest_cycle_protocol_blocked" }
        check(recovery.retryableFailureCount == 0) { "rest_cycle_protocol_recovery_incomplete" }

        val planning = when (val result = canonicalReadPort.readRestCyclePlanningInput(CANONICAL_SOURCE_LIMIT)) {
            is CanonicalReadResult.Ready -> result.value
            is CanonicalReadResult.Blocked -> return handleBlockedRead(result.failure)
        }
        requireCanonicalPlanning(identity, planning)
        val sources = planning.sources.map { source -> source.toRestCycleSourceEvent() }
        return CanonicalRestCycleContext(identity, planning, sources)
    }

    private fun handleBlockedRead(failure: CanonicalReadFailure): CanonicalRestCycleContext? {
        if (failure.disposition == CanonicalReadDisposition.NOT_READY) return null
        throw CanonicalRestCycleReadException(failure)
    }

    private fun requireCanonicalPlanning(
        identity: GenesisUltraRuntimeIdentity,
        planning: CanonicalRestCyclePlanningInput
    ) {
        require(planning.identity.instanceId == identity.instanceId) {
            "rest_cycle_planning_foreign_instance"
        }
        require(planning.identity.companionName == identity.companionName) {
            "rest_cycle_planning_identity_mismatch"
        }
        require(planning.writer.writerBodyId == identity.activeBody.bodyId) {
            "rest_cycle_planning_wrong_body"
        }
        require(planning.writer.writerEpochId == identity.activeBody.keyEpochId) {
            "rest_cycle_planning_stale_epoch"
        }
        require(planning.snapshot.instanceId == identity.instanceId) {
            "rest_cycle_planning_snapshot_instance_mismatch"
        }
        require(planning.sourceSetDigest.matches(SHA256_DIGEST)) {
            "rest_cycle_source_set_digest_invalid"
        }
        require(planning.snapshot.snapshotDigest.matches(SHA256_DIGEST)) {
            "rest_cycle_snapshot_digest_invalid"
        }
        planning.sources.forEach { source ->
            require(source.event.instanceId == identity.instanceId) {
                "rest_cycle_source_foreign_instance"
            }
            require(source.event.bodyId == identity.activeBody.bodyId) {
                "rest_cycle_source_wrong_body"
            }
            require(source.event.signerId == identity.activeBody.bodyId) {
                "rest_cycle_source_wrong_signer"
            }
            require(source.event.signerEpochId == identity.activeBody.keyEpochId) {
                "rest_cycle_source_stale_epoch"
            }
        }
    }

    private fun isDue(latestObservedAt: String?): Boolean {
        if (latestObservedAt == null) return true
        val latestMillis = Instant.parse(latestObservedAt).toEpochMilli()
        return nowMillis() - latestMillis >= REST_CYCLE_MIN_INTERVAL_MILLIS
    }

    private fun selectMeaningfulEvents(events: List<RestCycleSourceEvent>): List<RestCycleSourceEvent> {
        return events.filter { event ->
            when {
                event.memoryKind == "chat_noise" -> false
                event.memoryKind == "conversation" -> event.importance >= 60
                event.memoryKind == "observation" -> event.importance >= 60 || event.userConfirmed
                else -> true
            }
        }
    }

    private fun buildPlan(
        context: CanonicalRestCycleContext,
        meaningfulEvents: List<RestCycleSourceEvent>,
        mode: RestCycleMode,
        approvalRequired: Boolean
    ): RestCyclePlan {
        val protocolIdentity = RestCycleOperationFactory.identityOf(context.identity)
        val migrationId = RestCycleOperationFactory.deterministicMigrationId(
            identity = protocolIdentity,
            sourceSetDigest = context.planning.sourceSetDigest,
            mode = mode
        )
        val linkedSources = meaningfulEvents
            .sortedWith(
                compareByDescending<RestCycleSourceEvent> { it.userConfirmed }
                    .thenByDescending { it.importance }
                    .thenByDescending { it.confidence }
                    .thenByDescending { it.observedAtMillis }
                    .thenBy { it.eventHash }
            )
            .take(REST_CYCLE_LINK_LIMIT)
        val summary = buildRestCycleSummary(
            events = meaningfulEvents,
            mode = mode,
            snapshotDigest = context.planning.snapshot.snapshotDigest,
            sourceSetDigest = context.planning.sourceSetDigest,
            approvalRequired = approvalRequired
        )
        val generatedAtMillis = meaningfulEvents.maxOfOrNull { it.observedAtMillis } ?: 0L
        val autobiography = AutobiographicalMemoryConsolidator.build(
            alias = context.planning.identity.companionName,
            sourceRestCycleRef = migrationId,
            events = meaningfulEvents,
            generatedAtMillis = generatedAtMillis
        )
        return RestCyclePlan(
            migrationId = migrationId,
            mode = mode,
            summary = summary,
            linkedSources = linkedSources,
            autobiography = autobiography
        )
    }

    private suspend fun executePlan(
        context: CanonicalRestCycleContext,
        plan: RestCyclePlan,
        approvalRequired: Boolean,
        approvalId: String?
    ): Boolean {
        val command = RestCycleOperationFactory.execute(
            identity = RestCycleOperationFactory.identityOf(context.identity),
            companionName = context.planning.identity.companionName,
            migrationId = plan.migrationId,
            mode = plan.mode,
            sourceSetDigest = context.planning.sourceSetDigest,
            snapshotDigest = context.planning.snapshot.snapshotDigest,
            birthRootEventHash = context.planning.snapshot.birthRootEventHash,
            summary = plan.summary,
            sourceEvents = plan.linkedSources,
            autobiography = plan.autobiography,
            approvalRequired = approvalRequired,
            approvalId = approvalId
        )
        return try {
            val result = protocol.execute(context.identity, command)
            check(result.status == CrossDatabaseOperationStatus.COMMITTED) {
                "rest_cycle_protocol_not_committed"
            }
            true
        } catch (failure: Throwable) {
            CrossDatabaseProtocolErrors.rethrowCancellation(failure)
            throw RestCycleExecutionException(
                "Rest cycle failed: ${failure.message ?: failure::class.java.simpleName}",
                failure
            )
        }
    }

    private fun buildRestCycleSummary(
        events: List<RestCycleSourceEvent>,
        mode: RestCycleMode,
        snapshotDigest: String,
        sourceSetDigest: String,
        approvalRequired: Boolean
    ): String {
        val prioritized = events.sortedWith(
            compareByDescending<RestCycleSourceEvent> { it.userConfirmed }
                .thenByDescending { it.importance }
                .thenByDescending { it.confidence }
                .thenByDescending { it.observedAtMillis }
                .thenBy { it.eventHash }
        )
        return buildString {
            appendLine("REST_CYCLE_CANONICAL_V1")
            appendLine("mode=${mode.id}")
            appendLine("snapshot_digest=$snapshotDigest")
            appendLine("source_set_digest=$sourceSetDigest")
            appendLine("approval_required=$approvalRequired")
            appendLine("policy=canonical_verified_local_projection_no_external_actions")
            appendLine("purpose=consolidate_verified_memory_for_future_reasoning_context")
            appendLine()
            appendRestSection("decisions", prioritized.filter { it.memoryKind == "decision" }, 6)
            appendRestSection("corrections", prioritized.filter { it.memoryKind == "correction" }, 6)
            appendRestSection("preferences", prioritized.filter { it.memoryKind == "preference" }, 6)
            appendRestSection("learning", prioritized.filter { it.memoryKind == "learning" }, 6)
            appendRestSection("errors", prioritized.filter { it.memoryKind == "error_detected" }, 6)
            appendRestSection(
                "approvals_rejections",
                prioritized.filter { it.memoryKind == "approval" || it.memoryKind == "rejection" },
                6
            )
            appendRestSection("identity", prioritized.filter { it.memoryKind == "identity" }, 4)
            appendRestSection("recent_context", prioritized.take(10), 10)
        }.trim()
    }

    private fun StringBuilder.appendRestSection(
        title: String,
        events: List<RestCycleSourceEvent>,
        limit: Int
    ) {
        appendLine("[$title]")
        val selected = events.take(limit)
        if (selected.isEmpty()) {
            appendLine("- none")
        } else {
            selected.forEach { event ->
                appendLine(
                    "- ${event.memoryKind}/i${event.importance}/c${event.confidence}/${event.eventHash.take(19)}: " +
                        event.body.replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(260)
                )
            }
        }
        appendLine()
    }

    private data class CanonicalRestCycleContext(
        val identity: GenesisUltraRuntimeIdentity,
        val planning: CanonicalRestCyclePlanningInput,
        val sources: List<RestCycleSourceEvent>
    )

    private data class RestCyclePlan(
        val migrationId: String,
        val mode: RestCycleMode,
        val summary: String,
        val linkedSources: List<RestCycleSourceEvent>,
        val autobiography: AutobiographicalMemoryDraft
    )

    companion object {
        const val REST_CYCLE_MIGRATION_TYPE = RestCycleMigrationStore.REST_CYCLE_MIGRATION_TYPE
        const val REST_REPAIR_MIGRATION_TYPE = "rest_cycle.repair_proposal"
        private const val CANONICAL_SOURCE_LIMIT = 80
        private const val REST_CYCLE_LINK_LIMIT = 12
        private const val REST_CYCLE_MIN_EVENTS = 6
        private const val REST_CYCLE_MIN_INTERVAL_MILLIS = 6L * 60L * 60L * 1000L
        private const val MAX_RECOVERY_BATCH = 200
        private val SHA256_DIGEST = Regex("^sha256:[a-f0-9]{64}$")
    }
}

internal class CanonicalRestCycleReadException(
    val failure: CanonicalReadFailure
) : IllegalStateException(
    "canonical_rest_cycle_read_${failure.disposition.name.lowercase()}:${failure.diagnosticCode}"
)

class RestCycleExecutionException(
    message: String,
    cause: Throwable
) : RuntimeException(message, cause)
