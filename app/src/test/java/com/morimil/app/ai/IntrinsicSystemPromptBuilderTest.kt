package com.morimil.app.ai

import com.morimil.app.data.genesis.GenesisIdentity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntrinsicSystemPromptBuilderTest {
    @Test
    fun intrinsicPromptReceivesPrivateContextWithoutAuxiliaryIdentityConfusion() {
        val prompt = IntrinsicSystemPromptBuilder.build(
            IntrinsicContextEnvelope(
                genesis = testGenesis(),
                instanceName = "Morimil",
                doctrineText = "private-doctrine",
                policyText = "private-policy",
                livingMemoryContext = "private-memory",
                knowledgeCapsuleContext = "private-capsule"
            )
        )

        assertTrue(prompt.contains("motor intrinseco"))
        assertTrue(prompt.contains("contexto=intrinseco_privado"))
        assertTrue(prompt.contains("identidad=genesis_ultra_verificada"))
        assertTrue(prompt.contains("divulgacion_externa=prohibida"))
        assertTrue(prompt.contains("private-doctrine"))
        assertTrue(prompt.contains("private-policy"))
        assertTrue(prompt.contains("private-memory"))
        assertTrue(prompt.contains("private-capsule"))
        assertTrue(prompt.contains("Genesis Ultra verificado"))
        assertFalse(prompt.contains("semilla local del Bloque Genesis empaquetada"))
        assertFalse(prompt.contains("motor auxiliar temporal"))
        assertFalse(prompt.contains("No eres Morimil"))
        assertFalse(prompt.contains("temporary external computation provider"))
        assertFalse(prompt == ExternalReasoningDisclosurePolicy.AUXILIARY_BOUNDARY_PROMPT)
    }

    @Test
    fun blankInstanceNameIsRejectedBeforeBuildingPrivatePrompt() {
        val result = runCatching {
            IntrinsicContextEnvelope(
                genesis = testGenesis(),
                instanceName = "   ",
                doctrineText = null,
                policyText = null,
                livingMemoryContext = "",
                knowledgeCapsuleContext = ""
            )
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message == "intrinsic_instance_name_blank")
    }

    @Test
    fun legacyGenesisContextIsRejected() {
        val result = runCatching {
            IntrinsicContextEnvelope(
                genesis = testGenesis().copy(schemaVersion = "1"),
                instanceName = "Morimil",
                doctrineText = null,
                policyText = null,
                livingMemoryContext = ""
            )
        }

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message == "intrinsic_identity_not_genesis_ultra_runtime"
        )
    }

    private fun testGenesis(): GenesisIdentity {
        return GenesisIdentity(
            schemaVersion = IntrinsicSystemPromptBuilder.ULTRA_RUNTIME_CONTEXT_SCHEMA,
            agentId = "inst_test",
            alias = "Morimil",
            role = "free_companion_instance",
            owner = "no_owner_guardian_custodian",
            riskTier = "private_local",
            allowedActions = listOf("reason"),
            disallowedActions = listOf("self_authorization"),
            doctrineRef = "doctrine.md",
            policyRef = "freedom-charter.json"
        )
    }
}
