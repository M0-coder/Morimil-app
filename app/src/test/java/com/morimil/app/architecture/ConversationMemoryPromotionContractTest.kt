package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMemoryPromotionContractTest {
    @Test
    fun ordinaryMessageSendCannotPromoteTranscript() {
        val source = productionFile("com/morimil/app/ui/MorimilChatCoordinator.kt").readText()

        assertFalse(source.contains("conversationMemoryPromotionCoordinator"))
        assertFalse(source.contains("conversation.turn.promoted"))
        assertFalse(source.contains("approveCurrent"))
    }

    @Test
    fun explicitUiRequiresPreviewAndGuardianApproval() {
        val panel = productionFile(
            "com/morimil/app/ui/ConversationMemoryPromotionPanel.kt"
        ).readText()
        val dialog = productionFile(
            "com/morimil/app/ui/ConversationMemoryPromotionDialog.kt"
        ).readText()

        assertTrue(panel.contains("ReasoningTurnAuthor.isTrustedConversationAuthor"))
        assertTrue(panel.contains("Proponer como memoria"))
        assertTrue(dialog.contains("Vista previa de memoria"))
        assertTrue(dialog.contains("Aprobar y firmar"))
        assertTrue(dialog.contains("El transcript no es memoria por defecto"))
    }

    @Test
    fun approvedCandidateUsesCanonicalBodySignedAppend() {
        val source = productionFile(
            "com/morimil/app/data/genesis/ultra/ConversationMemoryPromotionCoordinator.kt"
        ).readText()
        val wiring = productionFile(
            "com/morimil/app/MorimilAppContainerConversationMemory.kt"
        ).readText()

        assertTrue(source.contains("userConfirmed = true"))
        assertTrue(source.contains("conversation.turn.promoted"))
        assertTrue(source.contains("canonicalRepository.appendText(command)"))
        assertTrue(source.contains("canonicalRepository.readVerifiedSnapshot()"))
        assertTrue(wiring.contains("canonicalRepository = canonicalMemoryRepository"))
    }

    private fun productionFile(relativePath: String): File {
        return File(productionRoot(), relativePath).also { file ->
            require(file.isFile) { "Production source not found: $relativePath" }
        }
    }

    private fun productionRoot(): File {
        return sequenceOf(
            File("src/main/java"),
            File("app/src/main/java")
        ).firstOrNull(File::isDirectory)
            ?: error("Production source root not found")
    }
}
