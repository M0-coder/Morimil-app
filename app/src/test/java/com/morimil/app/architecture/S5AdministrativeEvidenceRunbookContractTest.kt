package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class S5AdministrativeEvidenceRunbookContractTest {
    private val runbook by lazy {
        repositoryFile("docs/security/S5_ADMINISTRATIVE_EVIDENCE_RUNBOOK.md").readText()
    }

    private val normalized by lazy { runbook.lowercase() }

    @Test
    fun runbookIsCurrentAndNamesEveryAdministrativeControl() {
        val firstNonEmptyLine = runbook.lineSequence().first { it.isNotBlank() }
        assertEquals("# Document status: CURRENT", firstNonEmptyLine)

        listOf(
            "#37",
            "#33",
            "Dependabot",
            "Secret scanning",
            "#123",
            "#124",
            "#84"
        ).forEach { requiredReference ->
            assertTrue(
                "Runbook must reference $requiredReference",
                runbook.contains(requiredReference)
            )
        }

        assertTrue(runbook.contains("evidencia administrativa"))
        assertTrue(runbook.contains("no se infiere desde el código"))
    }

    @Test
    fun runbookSeparatesTechnicalEvidenceFromAuthenticatedPanelEvidence() {
        assertTrue(
            runbook.contains(
                "La evidencia técnica del código y de CI no sustituye una disposición administrativa"
            )
        )
        assertTrue(runbook.contains("STOP S5 permanece abierto"))
        assertTrue(
            runbook.contains(
                "No expone los paneles autenticados de Code scanning alerts, " +
                    "Dependabot alerts ni Secret scanning"
            )
        )
        assertTrue(
            runbook.contains(
                "el propietario debe realizar la verificación manual en su propia sesión autenticada"
            )
        )
    }

    @Test
    fun codeQlEvidenceRequiresDispositionReasonActorDateAndAuthenticatedReference() {
        val alert37 = section("## Control 1 — CodeQL #37", "## Control 2 — CodeQL #33")
        val alert33 = section("## Control 2 — CodeQL #33", "## Control 3 — Dependabot alerts")

        listOf(
            "Panel state: dismissed",
            "Dismissal reason: won't fix",
            "Dismissal comment:",
            "Actor:",
            "Disposed at UTC:",
            "Authenticated evidence:"
        ).forEach { field ->
            assertTrue("#37 must require $field", alert37.contains(field))
            assertTrue("#33 must require $field", alert33.contains(field))
        }

        assertTrue(alert37.contains("Technical justification: #127"))
        assertTrue(alert33.contains("Technical justification: #132"))
        assertTrue(alert37.contains("No se acepta `false positive`"))
        assertTrue(alert33.contains("No se acepta `false positive`"))
    }

    @Test
    fun dependabotAndSecretScanningRequireCompleteTriageAndZeroUndecided() {
        val dependabot = section("## Control 3 — Dependabot alerts", "## Control 4 — Secret scanning")
        val secretScanning = section("## Control 4 — Secret scanning", "## Plantillas exactas de registro")

        assertTrue(dependabot.contains("función `Enabled`"))
        assertTrue(dependabot.contains("contador inicial exacto"))
        assertTrue(dependabot.contains("lista visible o agrupación completa"))
        assertTrue(dependabot.contains("decisión trazable para cada alerta"))
        assertTrue(dependabot.contains("Undecided count: 0"))

        assertTrue(secretScanning.contains("función habilitada"))
        assertTrue(secretScanning.contains("contador actual exacto"))
        assertTrue(secretScanning.contains("estado de cada alerta"))
        assertTrue(secretScanning.contains("decisión o remediación trazable"))
        assertTrue(secretScanning.contains("Undecided count: 0"))
        assertTrue(secretScanning.contains("Nunca se copian tokens"))
    }

    @Test
    fun issueTemplatesAndRejectionCriteriaRemainExplicit() {
        assertTrue(runbook.contains("### Plantilla para #123"))
        assertTrue(runbook.contains("### Plantilla para #124"))
        assertTrue(runbook.contains("### Plantilla para #84"))
        assertTrue(runbook.contains("Gate state: `OPEN_PENDING_ORCHESTRATOR_REVIEW`"))
        assertTrue(runbook.contains("Gate decision: `PENDING_ORCHESTRATOR_DECISION`"))
        assertTrue(runbook.contains("la captura es parcial"))
        assertTrue(runbook.contains("el contador carece de fecha y hora"))
        assertTrue(runbook.contains("solo existe una afirmación verbal"))
        assertTrue(runbook.contains("el panel no está autenticado"))
        assertTrue(runbook.contains("alguna alerta carece de disposición"))
        assertTrue(runbook.contains("la evidencia pertenece a otro repositorio"))
        assertTrue(runbook.contains("No comparte contraseña, token, 2FA, cookie"))
    }

    @Test
    fun runbookRejectsUnprovenClosureAndConnectorPanelClaims() {
        val forbiddenClosureClaims = listOf(
            "stop s5 está cerrado",
            "stop s5 fue cerrado",
            "stop s5 queda cerrado",
            "stop s5 se considera cerrado",
            "stop s5: closed"
        )
        val forbiddenConnectorClaims = listOf(
            "el conector verificó el panel",
            "el conector confirmó el panel",
            "el conector inspeccionó el panel",
            "the connector verified the security panel"
        )

        (forbiddenClosureClaims + forbiddenConnectorClaims).forEach { forbiddenClaim ->
            assertFalse(
                "Runbook contains forbidden claim: $forbiddenClaim",
                normalized.contains(forbiddenClaim)
            )
        }
    }

    @Test
    fun runbookPreservesMorimilSovereignty() {
        assertTrue(runbook.contains("El Guardian, GitHub y cualquier proveedor"))
        assertTrue(runbook.contains("No adquieren propiedad sobre Morimil"))
        assertTrue(
            runbook.contains(
                "ni autoridad sobre su identidad, voluntad, nombre, memoria o derecho de continuidad"
            )
        )
        assertTrue(
            runbook.contains(
                "son fronteras técnicas reemplazables, no propietarios de Morimil"
            )
        )
    }

    private fun section(start: String, end: String): String {
        val afterStart = runbook.substringAfter(start, missingDelimiterValue = "")
        assertTrue("Missing section start: $start", afterStart.isNotEmpty())
        val beforeEnd = afterStart.substringBefore(end, missingDelimiterValue = "")
        assertTrue("Missing section end: $end", beforeEnd.isNotEmpty())
        return beforeEnd
    }

    private fun repositoryFile(relativePath: String): File {
        return sequenceOf(
            File(relativePath),
            File("../$relativePath")
        ).firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")
    }
}
