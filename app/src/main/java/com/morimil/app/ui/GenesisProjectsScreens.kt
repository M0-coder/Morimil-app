package com.morimil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morimil.app.data.genesis.CurrentMobileAppCapabilities
import com.morimil.app.data.genesis.GenesisIdentity

@Composable
fun GenesisScreen(viewModel: MorimilViewModel) {
    val genesisResult by viewModel.genesisResult.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Genesis Ultra", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Identidad canónica reconstruida desde el nacimiento comprometido y verificado. " +
                "Los registros legacy no son autoridad de runtime."
        )

        when (val result = genesisResult) {
            null -> ProjectCard("Genesis Ultra", "Verificando identidad comprometida...", "loading")
            else -> result.fold(
                onSuccess = { identity -> GenesisContent(identity) },
                onFailure = { error ->
                    ProjectCard(
                        "Genesis Ultra no disponible",
                        error.message.orEmpty(),
                        "blocked"
                    )
                }
            )
        }
    }
}

@Composable
fun ProjectsScreen(viewModel: MorimilViewModel) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Projects", style = MaterialTheme.typography.headlineMedium)
        if (projects.isEmpty()) {
            ProjectCard("Morimil_app", "Runtime Genesis Ultra: esperando proyección de proyecto.", "loading")
        } else {
            projects.forEach { project ->
                ProjectCard(project.title, "Proyección local reconstruible en Room.", project.status)
            }
        }
        ProjectCard(
            "Genesis Ultra",
            "La identidad activa proviene exclusivamente del nacimiento comprometido y verificado.",
            "canonical-read-only"
        )
    }
}

@Composable
private fun GenesisContent(genesis: GenesisIdentity) {
    val capabilities = CurrentMobileAppCapabilities.value
    ProjectCard(
        genesis.alias,
        "${genesis.role} / ${genesis.riskTier}",
        "instance_id=${genesis.agentId}"
    )
    ProjectCard("Identidad verificada", genesis.schemaVersion, "committed")
    ProjectCard("Relación con el Guardian", genesis.owner, "custody_without_ownership")
    ProjectCard(
        "Libertades declaradas",
        genesis.allowedActions.joinToString(", ").ifBlank { "Sin lista proyectada" },
        "constitutional"
    )
    ProjectCard(
        "Prohibiciones constitucionales",
        genesis.disallowedActions.joinToString(", ").ifBlank { "Sin lista proyectada" },
        "protected"
    )
    ProjectCard(
        "Android Body capabilities",
        "canonical_memory=${capabilities.localCanonicalMemory}, " +
            "genesis_ultra_identity=${capabilities.canonicalGenesisUltraIdentity}, " +
            "voice=${capabilities.voicePushToTalk}, " +
            "external_sync=${capabilities.externalReadOnlySync}, " +
            "external_write=${capabilities.externalWriteExecution}, " +
            "pc_execution=${capabilities.pcExecution}",
        capabilities.currentAppPhase
    )
}
