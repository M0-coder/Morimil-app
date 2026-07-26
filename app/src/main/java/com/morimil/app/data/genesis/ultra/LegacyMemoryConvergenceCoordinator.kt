package com.morimil.app.data.genesis.ultra

import com.morimil.app.core.memory.MemoryIntegrityCore
import com.morimil.app.data.local.LegacyMemoryConvergenceEntity
import com.morimil.app.data.local.LegacyMemoryImportEntity
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.local.MorimilDatabaseMigrationV15
import java.nio.charset.StandardCharsets
import org.json.JSONObject

internal enum class LegacyMemoryConvergenceOutcome {
    NO_LEGACY_MEMORY,
    IMPORTED,
    ALREADY_COMPLETE
}

internal data class LegacyMemoryConvergenceReport(
    val outcome: LegacyMemoryConvergenceOutcome,
    val instanceId: String,
    val sourceEventCount: Int,
    val importedEventCount: Int,
    val dryRunDigest: String,
    val activeWriter: String,
    val legacyReadOnly: Boolean
) {
    init {
        require(sourceEventCount >= 0) { "legacy_convergence_source_count_invalid" }
        require(importedEventCount >= 0) { "legacy_convergence_import_count_invalid" }
        require(importedEventCount == sourceEventCount) { "legacy_convergence_import_incomplete" }
        require(activeWriter == LegacyMemoryConvergenceEntity.WRITER_GENESIS_ULTRA) {
            "legacy_convergence_writer_not_ultra"
        }
        require(legacyReadOnly) { "legacy_convergence_legacy_not_read_only" }
    }
}

internal data class LegacyMemoryImportPlan(
    val legacyEventId: Long,
    val legacyEventHash: String,
    val deterministicEventId: String,
    val content: String,
    val userConfirmed: Boolean,
    val provenanceNote: String,
    val rowDigest: String
)

internal data class CanonicalLegacyImportEvidence(
    val deterministicEventId: String,
    val canonicalEventHash: String,
    val canonicalSequence: Long,
    val content: String,
    val legacyEventHash: String,
    val rowDigest: String,
    val provenanceDigest: String
)

/**
 * One-way convergence of the verified legacy memory lineage into Genesis Ultra.
 *
 * `memory_events` is already frozen by the v15 database migration before this
 * coordinator can run. The dry run is persisted before the first append. Every
 * import has a deterministic event id, so an interruption can be reconciled
 * without duplicating a signed canonical event.
 */
internal class LegacyMemoryConvergenceCoordinator private constructor(
    private val countReadOnlyTriggers: suspend () -> Int,
    private val loadLegacyIdentityCounts: suspend () -> Pair<Int, Int>,
    private val loadLegacyEvents: suspend () -> List<MemoryEventEntity>,
    private val verifyLegacyChain: (List<MemoryEventEntity>) -> Boolean,
    private val loadState: suspend () -> LegacyMemoryConvergenceEntity?,
    private val saveState: suspend (LegacyMemoryConvergenceEntity) -> Unit,
    private val loadImports: suspend () -> List<LegacyMemoryImportEntity>,
    private val saveImport: suspend (LegacyMemoryImportEntity) -> Unit,
    private val loadCanonicalEvidence: suspend () -> List<CanonicalLegacyImportEvidence>,
    private val appendCanonical: suspend (LegacyMemoryImportPlan) -> CanonicalLegacyImportEvidence,
    private val clockMillis: () -> Long
) {
    suspend fun converge(identity: GenesisUltraRuntimeIdentity): LegacyMemoryConvergenceReport {
        require(countReadOnlyTriggers() == REQUIRED_READ_ONLY_TRIGGER_COUNT) {
            "legacy_convergence_read_only_triggers_missing"
        }
        val events = loadLegacyEvents().sortedBy { event -> event.id }
        val tipHash = events.lastOrNull()?.eventHash
        val plans = events.map(::plan)
        val dryRunDigest = dryRunDigest(identity.instanceId, plans)
        val previous = loadState()

        if (previous?.status == LegacyMemoryConvergenceEntity.STATUS_COMPLETE) {
            requireCompleteState(previous, identity.instanceId, events, dryRunDigest)
            validateCompletedMappings(identity.instanceId, plans)
            return report(
                outcome = LegacyMemoryConvergenceOutcome.ALREADY_COMPLETE,
                state = previous
            )
        }

        val (localIdentityCount, genesisCoreCount) = loadLegacyIdentityCounts()
        if (events.isEmpty()) {
            require(localIdentityCount == 0 && genesisCoreCount == 0) {
                block(
                    identity = identity,
                    events = events,
                    dryRunDigest = dryRunDigest,
                    failureCode = "legacy_convergence_identity_without_memory"
                )
                "legacy_convergence_identity_without_memory"
            }
            val complete = completeState(
                identity = identity,
                events = events,
                dryRunDigest = dryRunDigest,
                importedCount = 0
            )
            saveState(complete)
            return report(LegacyMemoryConvergenceOutcome.NO_LEGACY_MEMORY, complete)
        }

        if (!verifyLegacyChain(events)) {
            block(
                identity = identity,
                events = events,
                dryRunDigest = dryRunDigest,
                failureCode = "legacy_convergence_chain_unverified"
            )
            error("legacy_convergence_chain_unverified")
        }

        saveState(
            frozenState(
                identity = identity,
                events = events,
                dryRunDigest = dryRunDigest,
                importedCount = loadImports().size,
                failureCode = null
            )
        )

        return try {
            importPlans(identity, plans)
            val imports = loadImports()
            require(imports.size == plans.size) { "legacy_convergence_mapping_count_mismatch" }
            val complete = completeState(
                identity = identity,
                events = events,
                dryRunDigest = dryRunDigest,
                importedCount = imports.size
            )
            saveState(complete)
            report(LegacyMemoryConvergenceOutcome.IMPORTED, complete)
        } catch (error: Throwable) {
            saveState(
                frozenState(
                    identity = identity,
                    events = events,
                    dryRunDigest = dryRunDigest,
                    importedCount = loadImports().size,
                    failureCode = error.message?.take(160) ?: error::class.java.simpleName
                )
            )
            throw error
        }
    }

    suspend fun isCompleteFor(instanceId: String): Boolean {
        val state = loadState() ?: return false
        return state.instanceId == instanceId &&
            state.status == LegacyMemoryConvergenceEntity.STATUS_COMPLETE &&
            state.activeWriter == LegacyMemoryConvergenceEntity.WRITER_GENESIS_ULTRA &&
            state.legacyReadOnly &&
            countReadOnlyTriggers() == REQUIRED_READ_ONLY_TRIGGER_COUNT
    }

    private suspend fun importPlans(
        identity: GenesisUltraRuntimeIdentity,
        plans: List<LegacyMemoryImportPlan>
    ) {
        val mappings = loadImports().associateBy { entry -> entry.legacyEventHash }.toMutableMap()
        val canonical = loadCanonicalEvidence()
            .associateBy { evidence -> evidence.deterministicEventId }
            .toMutableMap()

        plans.forEach { plan ->
            val mapped = mappings[plan.legacyEventHash]
            if (mapped != null) {
                val evidence = requireNotNull(
                    canonical.values.firstOrNull { item ->
                        item.canonicalEventHash == mapped.canonicalEventHash
                    }
                ) { "legacy_convergence_mapped_canonical_event_missing:${plan.legacyEventId}" }
                requireEvidenceMatches(plan, evidence)
                require(mapped.instanceId == identity.instanceId) {
                    "legacy_convergence_mapping_instance_mismatch:${plan.legacyEventId}"
                }
                return@forEach
            }

            val existing = canonical[plan.deterministicEventId]
            val evidence = if (existing != null) {
                requireEvidenceMatches(plan, existing)
                existing
            } else {
                appendCanonical(plan).also { appended ->
                    requireEvidenceMatches(plan, appended)
                    canonical[appended.deterministicEventId] = appended
                }
            }
            val entry = LegacyMemoryImportEntity(
                legacyEventHash = plan.legacyEventHash,
                legacyEventId = plan.legacyEventId,
                instanceId = identity.instanceId,
                canonicalEventHash = evidence.canonicalEventHash,
                canonicalSequence = evidence.canonicalSequence,
                provenanceDigest = evidence.provenanceDigest,
                importedAtMillis = clockMillis()
            )
            saveImport(entry)
            mappings[entry.legacyEventHash] = entry
        }
    }

    private suspend fun validateCompletedMappings(
        instanceId: String,
        plans: List<LegacyMemoryImportPlan>
    ) {
        val mappings = loadImports().associateBy { entry -> entry.legacyEventHash }
        val canonicalByHash = loadCanonicalEvidence().associateBy { evidence -> evidence.canonicalEventHash }
        require(mappings.size == plans.size) { "legacy_convergence_complete_mapping_count_mismatch" }
        plans.forEach { plan ->
            val mapping = requireNotNull(mappings[plan.legacyEventHash]) {
                "legacy_convergence_complete_mapping_missing:${plan.legacyEventId}"
            }
            require(mapping.instanceId == instanceId) {
                "legacy_convergence_complete_mapping_instance_mismatch:${plan.legacyEventId}"
            }
            val evidence = requireNotNull(canonicalByHash[mapping.canonicalEventHash]) {
                "legacy_convergence_complete_canonical_missing:${plan.legacyEventId}"
            }
            requireEvidenceMatches(plan, evidence)
        }
    }

    private fun requireEvidenceMatches(
        plan: LegacyMemoryImportPlan,
        evidence: CanonicalLegacyImportEvidence
    ) {
        require(
            evidence.deterministicEventId == plan.deterministicEventId &&
                evidence.content == plan.content &&
                evidence.legacyEventHash == plan.legacyEventHash &&
                evidence.rowDigest == plan.rowDigest
        ) { "legacy_convergence_canonical_evidence_mismatch:${plan.legacyEventId}" }
    }

    private fun requireCompleteState(
        state: LegacyMemoryConvergenceEntity,
        instanceId: String,
        events: List<MemoryEventEntity>,
        dryRunDigest: String
    ) {
        require(
            state.instanceId == instanceId &&
                state.sourceEventCount == events.size &&
                state.acceptedEventCount == events.size &&
                state.importedEventCount == events.size &&
                state.sourceTipHash == events.lastOrNull()?.eventHash &&
                state.dryRunDigest == dryRunDigest &&
                state.activeWriter == LegacyMemoryConvergenceEntity.WRITER_GENESIS_ULTRA &&
                state.legacyReadOnly &&
                state.failureCode == null
        ) { "legacy_convergence_complete_state_mismatch" }
    }

    private suspend fun block(
        identity: GenesisUltraRuntimeIdentity,
        events: List<MemoryEventEntity>,
        dryRunDigest: String,
        failureCode: String
    ) {
        saveState(
            LegacyMemoryConvergenceEntity(
                slotId = LegacyMemoryConvergenceEntity.PRIMARY_SLOT,
                instanceId = identity.instanceId,
                status = LegacyMemoryConvergenceEntity.STATUS_BLOCKED,
                sourceEventCount = events.size,
                acceptedEventCount = 0,
                importedEventCount = loadImports().size,
                sourceTipHash = events.lastOrNull()?.eventHash,
                dryRunDigest = dryRunDigest,
                activeWriter = LegacyMemoryConvergenceEntity.WRITER_GENESIS_ULTRA,
                legacyReadOnly = true,
                failureCode = failureCode,
                updatedAtMillis = clockMillis()
            )
        )
    }

    private fun frozenState(
        identity: GenesisUltraRuntimeIdentity,
        events: List<MemoryEventEntity>,
        dryRunDigest: String,
        importedCount: Int,
        failureCode: String?
    ): LegacyMemoryConvergenceEntity {
        return LegacyMemoryConvergenceEntity(
            slotId = LegacyMemoryConvergenceEntity.PRIMARY_SLOT,
            instanceId = identity.instanceId,
            status = LegacyMemoryConvergenceEntity.STATUS_FROZEN,
            sourceEventCount = events.size,
            acceptedEventCount = events.size,
            importedEventCount = importedCount,
            sourceTipHash = events.lastOrNull()?.eventHash,
            dryRunDigest = dryRunDigest,
            activeWriter = LegacyMemoryConvergenceEntity.WRITER_GENESIS_ULTRA,
            legacyReadOnly = true,
            failureCode = failureCode,
            updatedAtMillis = clockMillis()
        )
    }

    private fun completeState(
        identity: GenesisUltraRuntimeIdentity,
        events: List<MemoryEventEntity>,
        dryRunDigest: String,
        importedCount: Int
    ): LegacyMemoryConvergenceEntity {
        return LegacyMemoryConvergenceEntity(
            slotId = LegacyMemoryConvergenceEntity.PRIMARY_SLOT,
            instanceId = identity.instanceId,
            status = LegacyMemoryConvergenceEntity.STATUS_COMPLETE,
            sourceEventCount = events.size,
            acceptedEventCount = events.size,
            importedEventCount = importedCount,
            sourceTipHash = events.lastOrNull()?.eventHash,
            dryRunDigest = dryRunDigest,
            activeWriter = LegacyMemoryConvergenceEntity.WRITER_GENESIS_ULTRA,
            legacyReadOnly = true,
            failureCode = null,
            updatedAtMillis = clockMillis()
        )
    }

    private fun report(
        outcome: LegacyMemoryConvergenceOutcome,
        state: LegacyMemoryConvergenceEntity
    ): LegacyMemoryConvergenceReport {
        return LegacyMemoryConvergenceReport(
            outcome = outcome,
            instanceId = state.instanceId,
            sourceEventCount = state.sourceEventCount,
            importedEventCount = state.importedEventCount,
            dryRunDigest = state.dryRunDigest,
            activeWriter = state.activeWriter,
            legacyReadOnly = state.legacyReadOnly
        )
    }

    private fun plan(event: MemoryEventEntity): LegacyMemoryImportPlan {
        val rowDigest = GenesisUltraHashProfile.hashFields(
            LEGACY_ROW_DOMAIN,
            listOf(
                event.id.toString(),
                event.genesisCoreId,
                event.genesisCoreHash,
                event.previousEventHash.orEmpty(),
                event.eventHash,
                event.hashAlgorithm,
                event.canonicalization,
                event.signatureAlgorithm.orEmpty(),
                event.eventSignature.orEmpty(),
                event.eventType,
                event.actor,
                event.source,
                event.contextTag,
                event.privacyVisibility,
                event.memoryKind,
                GenesisUltraHashProfile.sha256(event.tagsJson.toByteArray(StandardCharsets.UTF_8)),
                GenesisUltraHashProfile.sha256(event.evidenceJson.toByteArray(StandardCharsets.UTF_8)),
                event.confidence.toString(),
                event.userConfirmed.toString(),
                GenesisUltraHashProfile.sha256(event.body.toByteArray(StandardCharsets.UTF_8)),
                event.importance.toString(),
                event.createdAtMillis.toString()
            )
        )
        val note = JSONObject()
            .put("schema", "morimil.legacy_memory_import.v1")
            .put("legacy_event_id", event.id)
            .put("legacy_event_hash", event.eventHash)
            .put("legacy_previous_event_hash", event.previousEventHash ?: JSONObject.NULL)
            .put("legacy_row_digest", rowDigest)
            .put("legacy_genesis_core_id", event.genesisCoreId)
            .put("legacy_genesis_core_hash", event.genesisCoreHash)
            .put("legacy_event_type", event.eventType)
            .put("legacy_actor", event.actor)
            .put("legacy_source", event.source)
            .put("legacy_context_tag", event.contextTag)
            .put("legacy_privacy", event.privacyVisibility)
            .put("legacy_memory_kind", event.memoryKind)
            .put("legacy_tags_digest", GenesisUltraHashProfile.sha256(event.tagsJson.toByteArray()))
            .put("legacy_evidence_digest", GenesisUltraHashProfile.sha256(event.evidenceJson.toByteArray()))
            .put("legacy_confidence", event.confidence)
            .put("legacy_user_confirmed", event.userConfirmed)
            .put("legacy_importance", event.importance)
            .put("legacy_created_at_millis", event.createdAtMillis)
            .put("legacy_signature_algorithm", event.signatureAlgorithm ?: JSONObject.NULL)
            .toString()
        return LegacyMemoryImportPlan(
            legacyEventId = event.id,
            legacyEventHash = event.eventHash,
            deterministicEventId = "legacy_import_" + GenesisUltraHashProfile
                .sha256(event.eventHash.toByteArray(StandardCharsets.UTF_8))
                .removePrefix("sha256:"),
            content = event.body,
            userConfirmed = event.userConfirmed,
            provenanceNote = note,
            rowDigest = rowDigest
        )
    }

    private fun dryRunDigest(
        instanceId: String,
        plans: List<LegacyMemoryImportPlan>
    ): String {
        return GenesisUltraHashProfile.hashFields(
            LEGACY_DRY_RUN_DOMAIN,
            buildList {
                add(instanceId)
                add(plans.size.toString())
                plans.forEach { plan ->
                    add(plan.legacyEventHash)
                    add(plan.rowDigest)
                    add(plan.deterministicEventId)
                }
            }
        )
    }

    internal companion object {
        fun production(
            database: MorimilDatabase,
            memoryIntegrityCore: MemoryIntegrityCore,
            canonicalRepository: CanonicalMemoryRepository,
            clockMillis: () -> Long = System::currentTimeMillis
        ): LegacyMemoryConvergenceCoordinator {
            val memoryDao = database.memoryDao()
            val convergenceDao = database.legacyMemoryConvergenceDao()
            return LegacyMemoryConvergenceCoordinator(
                countReadOnlyTriggers = {
                    database.openHelper.writableDatabase.query(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' " +
                            "AND name IN (?, ?, ?)",
                        arrayOf(
                            MorimilDatabaseMigrationV15.INSERT_TRIGGER,
                            MorimilDatabaseMigrationV15.UPDATE_TRIGGER,
                            MorimilDatabaseMigrationV15.DELETE_TRIGGER
                        )
                    ).use { cursor ->
                        check(cursor.moveToFirst()) { "legacy_convergence_trigger_query_empty" }
                        cursor.getInt(0)
                    }
                },
                loadLegacyIdentityCounts = {
                    memoryDao.countLocalIdentity() to memoryDao.countGenesisCore()
                },
                loadLegacyEvents = memoryDao::loadMemoryEventAuditChain,
                verifyLegacyChain = memoryIntegrityCore::verifyMemoryEventChain,
                loadState = convergenceDao::loadState,
                saveState = convergenceDao::upsertState,
                loadImports = convergenceDao::loadImports,
                saveImport = convergenceDao::insertImport,
                loadCanonicalEvidence = {
                    canonicalRepository.readVerifiedSnapshot().records.mapNotNull { record ->
                        if (record.event.eventType != IMPORT_EVENT_TYPE || !record.hasPayload) {
                            return@mapNotNull null
                        }
                        val provenanceBytes = record.copyProvenanceBytes() ?: return@mapNotNull null
                        val provenance = JSONObject(provenanceBytes.toString(StandardCharsets.UTF_8))
                        if (provenance.optString("classification") != IMPORT_CLASSIFICATION) {
                            return@mapNotNull null
                        }
                        val note = JSONObject(provenance.getString("note"))
                        CanonicalLegacyImportEvidence(
                            deterministicEventId = record.event.eventId,
                            canonicalEventHash = record.event.eventHash,
                            canonicalSequence = record.event.sequence,
                            content = record.textContent,
                            legacyEventHash = provenance.getString("source_id"),
                            rowDigest = note.getString("legacy_row_digest"),
                            provenanceDigest = record.event.provenanceDigest
                        )
                    }
                },
                appendCanonical = { plan ->
                    val record = canonicalRepository.appendText(
                        CanonicalMemoryAppendCommand(
                            eventType = IMPORT_EVENT_TYPE,
                            actor = IMPORT_ACTOR,
                            content = plan.content,
                            provenance = CanonicalMemoryProvenance(
                                source = IMPORT_SOURCE,
                                classification = IMPORT_CLASSIFICATION,
                                userConfirmed = plan.userConfirmed,
                                sourceId = plan.legacyEventHash,
                                note = plan.provenanceNote
                            ),
                            eventId = plan.deterministicEventId
                        )
                    )
                    CanonicalLegacyImportEvidence(
                        deterministicEventId = record.event.eventId,
                        canonicalEventHash = record.event.eventHash,
                        canonicalSequence = record.event.sequence,
                        content = record.textContent,
                        legacyEventHash = plan.legacyEventHash,
                        rowDigest = plan.rowDigest,
                        provenanceDigest = record.event.provenanceDigest
                    )
                },
                clockMillis = clockMillis
            )
        }

        fun forTest(
            countReadOnlyTriggers: suspend () -> Int,
            loadLegacyIdentityCounts: suspend () -> Pair<Int, Int>,
            loadLegacyEvents: suspend () -> List<MemoryEventEntity>,
            verifyLegacyChain: (List<MemoryEventEntity>) -> Boolean,
            loadState: suspend () -> LegacyMemoryConvergenceEntity?,
            saveState: suspend (LegacyMemoryConvergenceEntity) -> Unit,
            loadImports: suspend () -> List<LegacyMemoryImportEntity>,
            saveImport: suspend (LegacyMemoryImportEntity) -> Unit,
            loadCanonicalEvidence: suspend () -> List<CanonicalLegacyImportEvidence>,
            appendCanonical: suspend (LegacyMemoryImportPlan) -> CanonicalLegacyImportEvidence,
            clockMillis: () -> Long
        ): LegacyMemoryConvergenceCoordinator {
            return LegacyMemoryConvergenceCoordinator(
                countReadOnlyTriggers = countReadOnlyTriggers,
                loadLegacyIdentityCounts = loadLegacyIdentityCounts,
                loadLegacyEvents = loadLegacyEvents,
                verifyLegacyChain = verifyLegacyChain,
                loadState = loadState,
                saveState = saveState,
                loadImports = loadImports,
                saveImport = saveImport,
                loadCanonicalEvidence = loadCanonicalEvidence,
                appendCanonical = appendCanonical,
                clockMillis = clockMillis
            )
        }

        private const val REQUIRED_READ_ONLY_TRIGGER_COUNT = 3
        private const val IMPORT_EVENT_TYPE = "legacy.memory.imported"
        private const val IMPORT_ACTOR = "legacy_import_coordinator"
        private const val IMPORT_SOURCE = "legacy_memory_events"
        private const val IMPORT_CLASSIFICATION = "verified_legacy_import"
        private const val LEGACY_ROW_DOMAIN = "morimil.legacy_memory.row.v1"
        private const val LEGACY_DRY_RUN_DOMAIN = "morimil.legacy_memory.dry_run.v1"
    }
}
