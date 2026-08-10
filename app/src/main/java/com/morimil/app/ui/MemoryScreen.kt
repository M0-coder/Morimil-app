package com.morimil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MigrationRecordEntity
import com.morimil.app.data.local.RecallScheduleEntity
import com.morimil.app.data.repository.CanonicalMemoryPresentationEvent

@Composable
fun MemoryScreen(viewModel: MemoryViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedMemoryEventHash by viewModel.selectedMemoryEventHash.collectAsStateWithLifecycle()
    val selectedMemoryLinks by viewModel.selectedMemoryLinks.collectAsStateWithLifecycle()
    val selectedGraphEvents by viewModel.selectedGraphEvents.collectAsStateWithLifecycle()
    val eventsByHash = uiState.events.associateBy { event -> event.eventHash }
    val selectedEvent = selectedMemoryEventHash?.let { hash ->
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
            "Memoria viva verificada de Genesis Ultra. El archivo legacy no se usa como identidad ni como memoria de runtime."
        )

        CanonicalSnapshotCard(uiState)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::refreshCanonicalMemory) { Text("Actualizar memoria") }
            Button(onClick = viewModel::runMemoryIntegrityAudit) { Text("Auditar integridad") }
        }

        val audit = uiState.integrityAudit
        ProjectCard(
            "Integridad",
            when {
                audit.errorMessage != null -> audit.errorMessage
                audit.memoryChainVerified == true && audit.capsuleChainVerified == true ->
                    "Cadena canónica y cápsulas verificadas."
                audit.memoryChainVerified == false || audit.capsuleChainVerified == false ->
                    "La verificación requiere atención."
                else -> "Aún no se ha ejecutado una auditoría manual."
            }.orEmpty(),
            when {
                audit.errorMessage != null -> "error"
                audit.memoryChainVerified == true && audit.capsuleChainVerified == true -> "verified"
                audit.memoryChainVerified == false || audit.capsuleChainVerified == false -> "attention"
                else -> "pending"
            }
        )

        ProjectCard(
            "Historial de conversación",
            "${uiState.messages.size} turnos operativos; el transcript no es memoria canónica por defecto.",
            "separated"
        )
        ProjectCard(
            "Órganos derivados",
            "${uiState.knowledgeCapsules.size} cápsulas, ${uiState.recentLinks.size} enlaces y " +
                "${uiState.migrations.size} registros de migración.",
            "rebuildable"
        )

        CanonicalRecallPanel(
            recalls = uiState.recalls,
            eventsByHash = eventsByHash,
            onSeedRecalls = viewModel::seedRecallScheduleIfNeeded,
            onReinforceRecall = viewModel::reinforceRecall,
            onPostponeRecall = viewModel::postponeRecall,
            onDegradeRecall = viewModel::degradeRecall,
            onOpenMemory = viewModel::selectMemoryEvent
        )

        Text("Memoria canónica", style = MaterialTheme.typography.titleMedium)
        if (uiState.events.isEmpty()) {
            ProjectCard(
                "Eventos verificados",
                "No hay eventos post-birth disponibles para presentación.",
                "empty"
            )
        } else {
            uiState.events.asReversed().take(16).forEach { event ->
                CanonicalMemoryEventCard(
                    event = event,
                    selected = selectedMemoryEventHash == event.eventHash,
                    onSelect = viewModel::selectMemoryEvent,
                    onApprove = viewModel::approveMemoryEvent,
                    onDegrade = viewModel::degradeMemoryEvent,
                    onRequestCorrection = viewModel::requestMemoryCorrection
                )
            }
        }

        CanonicalMemoryGraphPanel(
            selectedEvent = selectedEvent,
            selectedLinks = selectedMemoryLinks,
            graphEvents = selectedGraphEvents,
            onSelect = viewModel::selectMemoryEvent,
            onClear = viewModel::clearSelectedMemoryEvent
        )

        RestCyclePanel(
            migrations = uiState.migrations,
            scheduleState = uiState.restCycleScheduleStatus.stateLabel,
            scheduleAttention = uiState.restCycleScheduleStatus.needsAttention,
            onApprove = viewModel::approveRestCycleConsolidation,
            onRunNow = viewModel::runRestCycleNow,
            onEnable = viewModel::enableRestCycleSchedule,
            onCancel = viewModel::cancelRestCycleSchedule,
            onRefresh = viewModel::refreshRestCycleScheduleStatus
        )

        CognitiveMigrationPanelCanonical(
            migrations = uiState.migrations,
            onPropose = viewModel::proposeCognitiveMigration,
            onApprove = viewModel::approveCognitiveMigration,
            onExecute = viewModel::executeCognitiveMigration,
            onRollback = viewModel::rollbackCognitiveMigration
        )

        ProjectCard(
            "Boundary",
            "Instance ≠ Body. Guardian = custodio/testigo sin propiedad. Modelos y proveedores no son autoridad de identidad ni memoria.",
            "protected"
        )
    }
}

@Composable
private fun CanonicalSnapshotCard(uiState: MemoryUiState) {
    val snapshot = uiState.snapshot
    if (snapshot == null) {
        ProjectCard(
            "Genesis Ultra memory",
            "La proyección canónica todavía no está disponible para presentación.",
            "waiting"
        )
        return
    }
    ProjectCard(
        "Genesis Ultra memory",
        "instance=${snapshot.instanceId.take(24)} · events=${snapshot.totalEventCount} · " +
            "post_birth=${snapshot.postBirthEventCount} · last_seq=${snapshot.lastSequence} · " +
            "last_hash=${snapshot.lastEventHash.take(24)}",
        "verified-read-only"
    )
}

@Composable
private fun CanonicalMemoryEventCard(
    event: CanonicalMemoryPresentationEvent,
    selected: Boolean,
    onSelect: (String) -> Unit,
    onApprove: (CanonicalMemoryPresentationEvent) -> Unit,
    onDegrade: (CanonicalMemoryPresentationEvent) -> Unit,
    onRequestCorrection: (CanonicalMemoryPresentationEvent) -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "${event.memoryKind} · seq=${event.sequence}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(event.eventType)
            Text(event.body.take(360))
            Text(
                "actor=${event.actor} source=${event.source} i=${event.importance} " +
                    "c=${event.confidence} hash=${event.eventHash.take(24)}"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSelect(event.eventHash) }) {
                    Text(if (selected) "Backlinks abiertos" else "Ver backlinks")
                }
                Button(onClick = { onApprove(event) }) { Text("Aprobar") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onDegrade(event) }) { Text("Degradar ruido") }
                Button(onClick = { onRequestCorrection(event) }) { Text("Pedir corrección") }
            }
        }
    }
}

@Composable
private fun CanonicalRecallPanel(
    recalls: List<RecallScheduleEntity>,
    eventsByHash: Map<String, CanonicalMemoryPresentationEvent>,
    onSeedRecalls: () -> Unit,
    onReinforceRecall: (Long) -> Unit,
    onPostponeRecall: (Long) -> Unit,
    onDegradeRecall: (Long) -> Unit,
    onOpenMemory: (String) -> Unit
) {
    Text("Recalls", style = MaterialTheme.typography.titleMedium)
    Text("Los recalls se resuelven contra hashes de eventos canónicos verificados.")
    Button(onClick = onSeedRecalls) {
        Text(if (recalls.isEmpty()) "Crear recalls" else "Actualizar recalls")
    }

    if (recalls.isEmpty()) {
        ProjectCard("Recall schedule", "No hay recalls activos.", "empty")
        return
    }

    recalls.take(12).forEach { recall ->
        val target = eventsByHash[recall.targetEventHash]
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${recall.targetMemoryKind} · priority=${recall.priority}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(recall.prompt.take(360))
                if (target != null) {
                    Text("canonical_seq=${target.sequence}: ${target.body.take(180)}")
                } else {
                    Text("Evento canónico no cargado en la ventana actual: ${recall.targetEventHash.take(24)}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onReinforceRecall(recall.recallId) }) { Text("Reforzar") }
                    Button(onClick = { onPostponeRecall(recall.recallId) }) { Text("Posponer") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onOpenMemory(recall.targetEventHash) }) { Text("Abrir recuerdo") }
                    Button(onClick = { onDegradeRecall(recall.recallId) }) { Text("Degradar") }
                }
            }
        }
    }
}

@Composable
private fun CanonicalMemoryGraphPanel(
    selectedEvent: CanonicalMemoryPresentationEvent?,
    selectedLinks: List<MemoryLinkEntity>,
    graphEvents: List<CanonicalMemoryPresentationEvent>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit
) {
    Text("Backlinks canónicos", style = MaterialTheme.typography.titleMedium)
    if (selectedEvent == null) {
        Text("Selecciona un evento canónico para inspeccionar enlaces derivados.")
        return
    }

    ProjectCard(
        "Evento seleccionado",
        "seq=${selectedEvent.sequence} · ${selectedEvent.eventType} · ${selectedEvent.eventHash.take(28)}",
        "canonical"
    )
    Text("links=${selectedLinks.size} · canonical_events=${graphEvents.size}")
    graphEvents
        .filterNot { event -> event.eventHash == selectedEvent.eventHash }
        .take(8)
        .forEach { event ->
            Button(onClick = { onSelect(event.eventHash) }) {
                Text("seq=${event.sequence} · ${event.memoryKind} · ${event.eventHash.take(14)}")
            }
        }
    Button(onClick = onClear) { Text("Cerrar backlinks") }
}

@Composable
private fun RestCyclePanel(
    migrations: List<MigrationRecordEntity>,
    scheduleState: String,
    scheduleAttention: Boolean,
    onApprove: (String) -> Unit,
    onRunNow: () -> Unit,
    onEnable: () -> Unit,
    onCancel: () -> Unit,
    onRefresh: () -> Unit
) {
    Text("REST", style = MaterialTheme.typography.titleMedium)
    ProjectCard(
        "Scheduler",
        "state=$scheduleState",
        if (scheduleAttention) "attention" else "ready"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onRunNow) { Text("Ejecutar REST") }
        Button(onClick = onRefresh) { Text("Actualizar") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onEnable) { Text("Activar agenda") }
        Button(onClick = onCancel) { Text("Cancelar agenda") }
    }

    migrations
        .filter { migration -> migration.migrationType == REST_CYCLE_MIGRATION_TYPE }
        .take(6)
        .forEach { migration ->
            ProjectCard(
                "REST ${migration.status}",
                migration.expectedEffect.take(260),
                migration.migrationId.take(28)
            )
            if (migration.status == "planned") {
                Button(onClick = { onApprove(migration.migrationId) }) { Text("Aprobar REST") }
            }
        }
}

@Composable
private fun CognitiveMigrationPanelCanonical(
    migrations: List<MigrationRecordEntity>,
    onPropose: () -> Unit,
    onApprove: (String) -> Unit,
    onExecute: (String) -> Unit,
    onRollback: (String) -> Unit
) {
    Text("Migraciones cognitivas", style = MaterialTheme.typography.titleMedium)
    Text("Propuestas auditables; ningún refinamiento reescribe la memoria canónica existente.")
    Button(onClick = onPropose) { Text("Proponer migración") }

    migrations
        .filter { migration -> migration.migrationType == COGNITIVE_MIGRATION_TYPE }
        .take(8)
        .forEach { migration ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${migration.status} · ${migration.riskLevel}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(migration.expectedEffect.take(300))
                    when (migration.status) {
                        "planned" -> Button(onClick = { onApprove(migration.migrationId) }) {
                            Text("Aprobar")
                        }
                        "approved" -> Button(onClick = { onExecute(migration.migrationId) }) {
                            Text("Ejecutar")
                        }
                        "completed" -> if (migration.rollbackAvailable) {
                            Button(onClick = { onRollback(migration.migrationId) }) { Text("Rollback") }
                        }
                    }
                }
            }
        }
}
