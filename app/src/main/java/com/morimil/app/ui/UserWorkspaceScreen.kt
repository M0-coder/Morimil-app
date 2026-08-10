package com.morimil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun UserWorkspaceScreen(viewModel: MorimilViewModel) {
    val workspace by viewModel.activeWorkspace.collectAsStateWithLifecycle()
    val genesisResult by viewModel.genesisResult.collectAsStateWithLifecycle()
    val identity = genesisResult?.getOrNull()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Workspace", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Este Android es el Body actual. Aloja proyecciones y recursos locales, pero no define ni posee la identidad de Morimil."
        )

        identity?.let { genesis ->
            WorkspaceCard(
                "Instance",
                "${genesis.alias} · instance_id=${genesis.agentId}",
                "genesis-ultra-committed"
            )
            WorkspaceCard(
                "Relación Body / Instance",
                "El Body ejecuta y custodia recursos locales; instanceId y continuidad no dependen de este dispositivo.",
                "instance-not-body"
            )
            WorkspaceCard(
                "Guardian",
                genesis.owner,
                "custody-without-ownership"
            )
        } ?: WorkspaceCard(
            "Instance",
            "La identidad Genesis Ultra todavía no está disponible para esta vista.",
            "waiting"
        )

        workspace?.let {
            WorkspaceCard(
                "Workspace activo",
                it.displayName,
                "local-rebuildable-projection"
            )
        } ?: WorkspaceCard(
            "Workspace",
            "La proyección local todavía no está inicializada.",
            "waiting"
        )

        WorkspaceCard(
            "Persistencia local",
            "Room/SQLCipher guarda evidencia, memoria canónica y proyecciones del Body. Las tablas legacy permanecen sólo para convergencia/migración y no son autoridad de runtime.",
            "bounded"
        )
    }
}

@Composable
private fun WorkspaceCard(title: String, description: String, status: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description)
            AssistChip(onClick = {}, label = { Text(status) })
        }
    }
}
