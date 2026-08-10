package com.morimil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morimil.app.core.memory.MemoryBacklink
import com.morimil.app.core.memory.MemoryBacklinkGraphBuilder
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MigrationRecordEntity
import com.morimil.app.data.local.RecallScheduleEntity
import com.morimil.app.data.repository.CanonicalMemoryPresentationEvent
import com.morimil.app.runtime.RestCycleScheduleStatus

@Composable
fun MemoryScreen(viewModel: MemoryViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val decisions = uiState.decisions
    val messages = uiState.messages
    val projects = uiState.projects
    val snapshot = uiState.snapshot
    val events = uiState.events
    val recalls = uiState.recalls
    val migrations = uiState.migrations
    val selfSnapshot = uiState.selfSnapshot
    val knowledgeCapsules = uiState.knowledgeCapsules
    val recentLinks = uiState.recentLinks
    val integrityAudit = uiState.integrityAudit
    val restCycleScheduleStatus = uiState.restCycleScheduleStatus
    val organismHealth = uiState.organismHealth
    val selectedMemoryEventHash by viewModel.selectedMemoryEventHash.collectAsStateWithLifecycle()
    val selectedMemoryLinks by viewModel.selectedMemoryLinks.collectAsStateWithLifecycle()
    val selectedGraphEvents by viewModel.selectedGraphEvents.collectAsStateWithLifecycle()
    val eventsByHash = events.associateBy { event -> event.eventHash }
    val selectedMemoryEvent = selectedMemoryEventHash?.let { hash ->
        eventsByHash[hash] ?: selectedGraphEvents.firstOrNull { event -> event.eventHash == hash }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshCanonicalMemory()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Living Memory", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Genesis Ultra verificado + eventos canónicos append-only + órganos locales derivados. " +
                "El archivo legacy permanece sólo para migración."
        )
        HealthStatusCard(
            health = organismHealth,
            onRefresh = viewModel::refreshOrganismHealth,
            onRunIntegrityAudit = viewModel::runMemoryIntegrityAudit
        )
        ProjectCard(
            "Historial de conversación",
            "${messages.size} turnos operativos; no forman parte de la memoria canónica por defecto.",
            "separated"
        )
        snapshot?.let { liveSnapshot ->
            ProjectCard(
                "Living snapshot",
                "${liveSnapshot.totalEventCount} eventos canónicos verificados; " +
                    "post_birth=${liveSnapshot.postBirthEventCount}; " +
                    "last_seq=${liveSnapshot.lastSequence}; " +
                    "last_hash=${liveSnapshot.lastEventHash.take(24)}",
                "verified-read-only"
            )
        } ?: ProjectCard(
            "Living snapshot",
            "La proyección canónica todavía no está disponible para presentación.",
            "pending"
        )
        ProjectCard("Project state", "${projects.size} proyectos persistidos.", "connected")
        if (decisions.isEmpty()) {
            ProjectCard("Decision log", "Sin decisiones registradas todavía.", "empty")
        } else {
            decisions.take(3).forEach { decision ->
                ProjectCard(decision.title, "Decision persisted locally.", decision.status)
            }
        }

        MemoryOrgansPanel(
            selfSnapshot = selfSnapshot,
            knowledgeCapsules = knowledgeCapsules,
            links = recentLinks,
            migrations = migrations,
            audit = integrityAudit,
            onRunIntegrityAudit = viewModel::runMemoryIntegrityAudit,
            onOpenMemory = viewModel::selectMemoryEvent
        )

        RecallSchedulePanel(
            recalls = recalls,
            eventsByHash = eventsByHash,
            onSeedRecalls = viewModel::seedRecallScheduleIfNeeded,
            onReinforceRecall = viewModel::reinforceRecall,
            onPostponeRecall = viewModel::postponeRecall,
            onDegradeRecall = viewModel::degradeRecall,
            onOpenMemory = viewModel::selectMemoryEvent
        )

        Text("Memory review queue", style = MaterialTheme.typography.titleMedium)
        Text(
            "Los recuerdos canónicos son append-only. Aprobar, corregir o degradar crea una " +
                "revisión canónica nueva; no modifica el evento original."
        )
        if (events.isEmpty()) {
            ProjectCard("Memory events", "Sin eventos canónicos visibles todavía.", "empty")
        } else {
            events.asReversed().take(12).forEach { event ->
                MemoryEventReviewCard(
                    event = event,
                    selected = selectedMemoryEventHash == event.eventHash,
                    onSelect = viewModel::selectMemoryEvent,
                    onApprove = viewModel::approveMemoryEvent,
                    onDegrade = viewModel::degradeMemoryEvent,
                    onRequestCorrection = viewModel::requestMemoryCorrection
                )
            }
        }

        RestCycleHistoryPanel(
            migrations = migrations,
            scheduleStatus = restCycleScheduleStatus,
            onApproveRestCycle = viewModel::approveRestCycleConsolidation,
            onRunRestCycleNow = viewModel::runRestCycleNow,
            onEnableSchedule = viewModel::enableRestCycleSchedule,
            onCancelSchedule = viewModel::cancelRestCycleSchedule,
            onRefreshSchedule = viewModel::refreshRestCycleScheduleStatus
        )
        CognitiveMigrationPanel(
            migrations = migrations,
            onProposeMigration = viewModel::proposeCognitiveMigration,
            onApproveMigration = viewModel::approveCognitiveMigration,
            onExecuteMigration = viewModel::executeCognitiveMigration,
            onRollbackMigration = viewModel::rollbackCognitiveMigration
        )
        MemoryGraphCanvasPanel(
            selectedEventHash = selectedMemoryEventHash,
            selectedEvent = selectedMemoryEvent,
            graphEvents = selectedGraphEvents,
            allEvents = events,
            links = if (selectedMemoryEventHash == null) {
                recentLinks
            } else {
                selectedMemoryLinks + recentLinks
            },
            knowledgeCapsules = knowledgeCapsules,
            recalls = recalls,
            migrations = migrations,
            projects = projects,
            decisions = decisions,
            onSelectEventHash = viewModel::selectMemoryEvent,
            onClearSelection = viewModel::clearSelectedMemoryEvent
        )
        MemoryBacklinksPanel(
            selectedEventHash = selectedMemoryEventHash,
            selectedEvent = selectedMemoryEvent,
            selectedLinks = selectedMemoryLinks,
            eventsByHash = eventsByHash,
            onSelectEventHash = viewModel::selectMemoryEvent,
            onClearSelection = viewModel::clearSelectedMemoryEvent
        )
        ProjectCard(
            "Scope guardian",
            "Guardian = custodio/testigo sin propiedad; Instance ≠ Body; memoria canónica ≠ archivo legacy.",
            "protected"
        )
    }
}

@Composable
private fun MemoryEventReviewCard(
    event: CanonicalMemoryPresentationEvent,
    selected: Boolean,
    onSelect: (String) -> Unit,
    onApprove: (CanonicalMemoryPresentationEvent) -> Unit,
    onDegrade: (CanonicalMemoryPresentationEvent) -> Unit,
    onRequestCorrection: (CanonicalMemoryPresentationEvent) -> Unit
) {
    val isQuarantine = event.memoryKind == "integrity_quarantine" ||
        event.eventType == "memory_integrity.quarantine"
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isQuarantine) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MemoryKindChip(event.memoryKind)
                if (isQuarantine) StatusChip("cuarentena", attention = true)
            }
            Text(event.eventType, style = MaterialTheme.typography.titleMedium)
            Text(event.body.take(340))
            Text(
                "seq=${event.sequence} actor=${event.actor} source=${event.source} " +
                    "importance=${event.importance} confidence=${event.confidence} " +
                    "hash=${event.eventHash.take(24)}"
            )
            if (selected) {
                AssistChip(onClick = {}, label = { Text("Backlinks abiertos") })
            } else {
                Button(onClick = { onSelect(event.eventHash) }) { Text("Ver backlinks") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onApprove(event) }) { Text("Aprobar") }
                Button(onClick = { onDegrade(event) }) { Text("Degradar ruido") }
            }
            Button(onClick = { onRequestCorrection(event) }) { Text("Pedir corrección") }
        }
    }
}

@Composable
private fun MemoryKindChip(memoryKind: String) {
    val container = memoryKindContainerColor(memoryKind)
    val labelColor = if (memoryKind == "chat_noise") {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        Color.White
    }
    AssistChip(
        onClick = {},
        label = { Text(memoryKind.ifBlank { "memory" }) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = container,
            labelColor = labelColor
        )
    )
}

@Composable
private fun memoryKindContainerColor(memoryKind: String): Color {
    return when (memoryKind) {
        "identity" -> Color(0xFF166534)
        "decision" -> Color(0xFF0F766E)
        "preference" -> Color(0xFF15803D)
        "correction", "error_detected" -> Color(0xFFB45309)
        "approval" -> Color(0xFF2563EB)
        "rejection" -> Color(0xFF991B1B)
        "learning" -> Color(0xFF6D4C41)
        "integrity_quarantine" -> MaterialTheme.colorScheme.tertiary
        "chat_noise" -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.secondary
    }
}

@Composable
private fun RecallSchedulePanel(
    recalls: List<RecallScheduleEntity>,
    eventsByHash: Map<String, CanonicalMemoryPresentationEvent>,
    onSeedRecalls: () -> Unit,
    onReinforceRecall: (Long) -> Unit,
    onPostponeRecall: (Long) -> Unit,
    onDegradeRecall: (Long) -> Unit,
    onOpenMemory: (String) -> Unit
) {
    val now = System.currentTimeMillis()
    val overdue = recalls
        .filter { recall -> recall.dueAtMillis <= now }
        .sortedWith(
            compareByDescending<RecallScheduleEntity> { it.priority }
                .thenBy { it.dueAtMillis }
        )
    val future = recalls
        .filter { recall -> recall.dueAtMillis > now }
        .sortedWith(
            compareBy<RecallScheduleEntity> { it.dueAtMillis }
                .thenByDescending { it.priority }
        )

    Text("Recalls pendientes", style = MaterialTheme.typography.titleMedium)
    Text("Recalls derivados de eventos canónicos verificados.")
    Button(onClick = onSeedRecalls) {
        Text(if (recalls.isEmpty()) "Crear recalls" else "Actualizar recalls")
    }

    if (recalls.isEmpty()) {
        ProjectCard("Recall schedule", "No hay recalls activos todavía.", "empty")
        return
    }

    RecallScheduleSection(
        "Vencidos ahora",
        "No hay recalls vencidos.",
        overdue.take(8),
        eventsByHash,
        onReinforceRecall,
        onPostponeRecall,
        onDegradeRecall,
        onOpenMemory
    )
    RecallScheduleSection(
        "Futuros",
        "No hay recalls futuros.",
        future.take(8),
        eventsByHash,
        onReinforceRecall,
        onPostponeRecall,
        onDegradeRecall,
        onOpenMemory
    )
}

@Composable
private fun RecallScheduleSection(
    title: String,
    emptyText: String,
    recalls: List<RecallScheduleEntity>,
    eventsByHash: Map<String, CanonicalMemoryPresentationEvent>,
    onReinforceRecall: (Long) -> Unit,
    onPostponeRecall: (Long) -> Unit,
    onDegradeRecall: (Long) -> Unit,
    onOpenMemory: (String) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (recalls.isEmpty()) {
        Text(emptyText)
        return
    }

    recalls.forEach { recall ->
        val targetEvent = eventsByHash[recall.targetEventHash]
        ElevatedCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${recall.targetMemoryKind} / priority=${recall.priority}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(recall.prompt.take(360))
                Text(
                    "due=${recall.dueAtMillis} interval=${recall.intervalDays}d " +
                        "action=${recall.lastAction}"
                )
                Text("organ=${recall.source} link=${recall.targetEventHash.take(24)}")
                targetEvent?.let { event ->
                    Text("recuerdo=${event.memoryKind}: ${event.body.take(180)}")
                } ?: Text("Evento canónico fuera de la ventana visual; el hash sigue siendo resoluble.")
                Text("reason=${recall.reason.take(180)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onReinforceRecall(recall.recallId) }) { Text("Reforzar") }
                    Button(onClick = { onPostponeRecall(recall.recallId) }) { Text("Posponer") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onOpenMemory(recall.targetEventHash) }) {
                        Text("Abrir recuerdo")
                    }
                    Button(onClick = { onDegradeRecall(recall.recallId) }) { Text("Degradar") }
                }
            }
        }
    }
}

@Composable
private fun CognitiveMigrationPanel(
    migrations: List<MigrationRecordEntity>,
    onProposeMigration: () -> Unit,
    onApproveMigration: (String) -> Unit,
    onExecuteMigration: (String) -> Unit,
    onRollbackMigration: (String) -> Unit
) {
    val cognitiveMigrations = migrations
        .filter { migration -> migration.migrationType == COGNITIVE_MIGRATION_TYPE }
        .take(8)
    Text("Migraciones cognitivas", style = MaterialTheme.typography.titleMedium)
    Text("Propuestas auditables para refinar memoria sin reescribir eventos originales.")
    Button(onClick = onProposeMigration) { Text("Proponer migración") }

    if (cognitiveMigrations.isEmpty()) {
        ProjectCard(
            "Auditoría cognitiva",
            "Todavía no hay migraciones cognitivas propuestas.",
            "empty"
        )
        return
    }

    cognitiveMigrations.forEach { migration ->
        ElevatedCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${migration.status} / risk=${migration.riskLevel}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "approved=${migration.approvedByUser} chain=${migration.chainVerified} " +
                        "backup=${migration.backupRequired} rollback=${migration.rollbackAvailable}"
                )
                Text(
                    "pre=${migration.preSnapshotId} " +
                        "post=${migration.postSnapshotId?.take(32) ?: "pending"}"
                )
                Text("Diff lógico", style = MaterialTheme.typography.titleMedium)
                Text(
                    migrationPlanSection(migration.expectedEffect, "diff_logico")
                        .ifBlank { "Sin diff visible." }
                        .take(900)
                )
                Text("Cápsulas propuestas", style = MaterialTheme.typography.titleMedium)
                Text(
                    migrationPlanSection(migration.expectedEffect, "capsulas_propuestas")
                        .ifBlank { "Sin cápsulas propuestas." }
                        .take(900)
                )
                Text("Backlinks propuestos", style = MaterialTheme.typography.titleMedium)
                Text(
                    migrationPlanSection(migration.expectedEffect, "backlinks_propuestos")
                        .ifBlank { "Sin backlinks propuestos." }
                        .take(900)
                )
                Text("Auditoría y resultado", style = MaterialTheme.typography.titleMedium)
                Text("steps=${migration.stepsJson.take(360)}")
                Text("result=${migration.errorsJson.take(360)}")
                Text("strategy=${migration.rollbackStrategy.take(360)}")
                Text("affected=${migration.affectedArtifactsJson.take(360)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (migration.status == "planned" && !migration.approvedByUser) {
                        Button(onClick = { onApproveMigration(migration.migrationId) }) {
                            Text("Aprobar")
                        }
                    }
                    if (migration.status == "approved") {
                        Button(onClick = { onExecuteMigration(migration.migrationId) }) {
                            Text("Ejecutar")
                        }
                    }
                }
                if (
                    migration.rollbackAvailable &&
                    migration.status in setOf("approved", "completed", "failed")
                ) {
                    Button(onClick = { onRollbackMigration(migration.migrationId) }) {
                        Text("Rollback")
                    }
                }
            }
        }
    }
}

private fun migrationPlanSection(plan: String, sectionName: String): String {
    val header = "$sectionName:"
    val lines = plan.lines()
    val startIndex = lines.indexOfFirst { line -> line.trim() == header }
    if (startIndex == -1) return ""

    return lines
        .drop(startIndex + 1)
        .takeWhile { line -> !line.trim().isMigrationPlanHeader() }
        .joinToString("\n")
        .trim()
}

private fun String.isMigrationPlanHeader(): Boolean {
    return this in setOf(
        "diff_logico:",
        "capsulas_propuestas:",
        "backlinks_propuestos:",
        "eventos_seleccionados:"
    ) || startsWith("policy=")
}

@Composable
private fun RestCycleHistoryPanel(
    migrations: List<MigrationRecordEntity>,
    scheduleStatus: RestCycleScheduleStatus,
    onApproveRestCycle: (String) -> Unit,
    onRunRestCycleNow: () -> Unit,
    onEnableSchedule: () -> Unit,
    onCancelSchedule: () -> Unit,
    onRefreshSchedule: () -> Unit
) {
    val restCycles = migrations
        .filter { migration -> migration.migrationType == REST_CYCLE_MIGRATION_TYPE }
        .take(8)
    Text("Rest cycle", style = MaterialTheme.typography.titleMedium)
    Text("Consolidaciones locales programadas, con aprobación para cambios importantes.")
    RestCycleSchedulePanel(
        status = scheduleStatus,
        onRunRestCycleNow = onRunRestCycleNow,
        onEnableSchedule = onEnableSchedule,
        onCancelSchedule = onCancelSchedule,
        onRefreshSchedule = onRefreshSchedule
    )

    if (restCycles.isEmpty()) {
        ProjectCard(
            "Historial de descanso",
            "Todavía no hay consolidaciones registradas.",
            "empty"
        )
        return
    }

    restCycles.forEach { migration ->
        val report = RestCycleReportUiStateBuilder.build(migration)
        ElevatedCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${migration.status} / risk=${migration.riskLevel}",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip("modo=${report.mode}")
                    StatusChip("riesgo=${report.risk}", attention = report.risk != "low")
                    StatusChip(
                        "cadena=${report.fullChainVerified}",
                        attention = report.fullChainVerified != "true"
                    )
                }
                Text(
                    "organos_con_alerta=${report.organReconciliation} " +
                        "capsulas_ok=${report.capsuleChainVerified} " +
                        "eventos=${report.sourceEvents} utiles=${report.meaningfulEvents}"
                )
                Text("policy=${report.policyReason}")
                report.tasks.take(6).forEach { task -> RestCycleTaskLine(task) }
                if (report.resultNotes.isNotEmpty()) {
                    Text("Resultado", style = MaterialTheme.typography.titleMedium)
                    report.resultNotes.take(6).forEach { note -> Text(note.take(180)) }
                }
                Text(
                    "approval_required=${report.approvalRequired} " +
                        "approved=${migration.approvedByUser} " +
                        "rollback=${migration.rollbackAvailable}"
                )
                Text("strategy=${migration.rollbackStrategy.take(220)}")
                if (
                    migration.status == "planned" &&
                    migration.approvalRequired &&
                    !migration.approvedByUser
                ) {
                    Button(onClick = { onApproveRestCycle(migration.migrationId) }) {
                        Text("Aprobar consolidación")
                    }
                }
            }
        }
    }
}

@Composable
private fun RestCycleSchedulePanel(
    status: RestCycleScheduleStatus,
    onRunRestCycleNow: () -> Unit,
    onEnableSchedule: () -> Unit,
    onCancelSchedule: () -> Unit,
    onRefreshSchedule: () -> Unit
) {
    ElevatedCard {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Programación del descanso", style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip("estado=${status.stateLabel}", attention = status.needsAttention)
                StatusChip("cada=${status.repeatIntervalHours}h")
                StatusChip("flex=${status.flexIntervalHours}h")
            }
            Text(
                "inicio=${status.initialDelayMinutes}min " +
                    "bateria_ok=${status.requiresBatteryNotLow} " +
                    "storage_ok=${status.requiresStorageNotLow}"
            )
            status.errorMessage?.let { error -> Text("scheduler_error=${error.take(160)}") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRunRestCycleNow) { Text("Ejecutar ahora") }
                Button(onClick = onEnableSchedule) {
                    Text(if (status.isScheduled) "Asegurar agenda" else "Activar")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefreshSchedule) { Text("Actualizar estado") }
                Button(enabled = status.isScheduled, onClick = onCancelSchedule) {
                    Text("Pausar automático")
                }
            }
        }
    }
}

@Composable
private fun RestCycleTaskLine(task: RestCycleTaskUiState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusChip(task.status, attention = task.risk != "low")
        Text("${task.name}: ${task.note}".take(160))
    }
}

@Composable
private fun MemoryBacklinksPanel(
    selectedEventHash: String?,
    selectedEvent: CanonicalMemoryPresentationEvent?,
    selectedLinks: List<MemoryLinkEntity>,
    eventsByHash: Map<String, CanonicalMemoryPresentationEvent>,
    onSelectEventHash: (String) -> Unit,
    onClearSelection: () -> Unit
) {
    Text("Backlinks de memoria", style = MaterialTheme.typography.titleMedium)
    if (selectedEventHash == null) {
        ProjectCard(
            "Grafo de recuerdos",
            "Selecciona un recuerdo canónico para ver qué apunta a él y a qué apunta.",
            "empty"
        )
        return
    }

    val graph = MemoryBacklinkGraphBuilder.buildForNode(
        nodeId = selectedEventHash,
        nodeType = CANONICAL_MEMORY_EVENT_NODE_TYPE,
        links = selectedLinks
    )

    ElevatedCard {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Recuerdo seleccionado", style = MaterialTheme.typography.titleMedium)
            Text(selectedEvent?.body?.take(260) ?: selectedEventHash.take(32))
            Text("hash=${selectedEventHash.take(32)} links=${selectedLinks.size}")
            Button(onClick = onClearSelection) { Text("Cerrar") }
            MemoryBacklinkSection(
                "Este recuerdo apunta a...",
                "Este recuerdo todavía no apunta a otros nodos.",
                graph.outgoing,
                eventsByHash,
                onSelectEventHash
            )
            MemoryBacklinkSection(
                "Este recuerdo es mencionado por...",
                "Ningún nodo reciente apunta todavía a este recuerdo.",
                graph.incoming,
                eventsByHash,
                onSelectEventHash
            )
        }
    }
}

@Composable
private fun MemoryBacklinkSection(
    title: String,
    emptyText: String,
    backlinks: List<MemoryBacklink>,
    eventsByHash: Map<String, CanonicalMemoryPresentationEvent>,
    onSelectEventHash: (String) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (backlinks.isEmpty()) {
        Text(emptyText)
        return
    }

    backlinks.take(8).forEach { backlink ->
        val linkedEvent = eventsByHash[backlink.linkedNodeId]
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${backlink.link.relation} / strength=${backlink.link.strength} / " +
                    backlink.link.reason.take(120)
            )
            if (backlink.linkedNodeType == CANONICAL_MEMORY_EVENT_NODE_TYPE) {
                Button(onClick = { onSelectEventHash(backlink.linkedNodeId) }) {
                    Text(
                        memoryNodeLabel(
                            linkedEvent,
                            backlink.linkedNodeId,
                            backlink.linkedNodeType
                        ).take(90)
                    )
                }
            } else {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            memoryNodeLabel(
                                linkedEvent,
                                backlink.linkedNodeId,
                                backlink.linkedNodeType
                            ).take(90)
                        )
                    }
                )
            }
        }
    }
}

private fun memoryNodeLabel(
    event: CanonicalMemoryPresentationEvent?,
    nodeId: String,
    nodeType: String
): String {
    if (event == null) return "$nodeType ${nodeId.take(24)}"
    return "${event.memoryKind} / ${event.eventType}: ${event.body.take(90)}"
}

private const val CANONICAL_MEMORY_EVENT_NODE_TYPE = "canonical_memory_event"
