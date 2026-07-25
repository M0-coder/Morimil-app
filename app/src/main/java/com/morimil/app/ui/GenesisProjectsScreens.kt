package com.morimil.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.morimil.app.core.memory.MemoryBacklink
import com.morimil.app.core.memory.MemoryBacklinkGraphBuilder
import com.morimil.app.data.genesis.CurrentMobileAppCapabilities
import com.morimil.app.data.genesis.GenesisIdentity
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MigrationRecordEntity
import com.morimil.app.data.local.RecallScheduleEntity
import com.morimil.app.runtime.RestCycleScheduleStatus
import java.util.Locale

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
            null -> ProjectCard("Genesis Ultra", "Verificando nacimiento...", "loading")
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
            ProjectCard("Morimil_app", "Runtime Genesis Ultra: esperando estado de proyecto.", "loading")
        } else {
            projects.forEach { project ->
                ProjectCard(project.title, "Persisted project state in Room.", project.status)
            }
        }
        ProjectCard(
            "Genesis Ultra",
            "La identidad activa proviene del nacimiento local verificado.",
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
        "Mobile app capabilities",
        "memory=${capabilities.localMemory}, voice=${capabilities.voicePushToTalk}, " +
            "external_sync=${capabilities.externalReadOnlySync}, " +
            "external_write=${capabilities.externalWriteExecution}, " +
            "pc_execution=${capabilities.pcExecution}",
        capabilities.currentAppPhase
    )
}
