package com.morimil.app.reasoning

import com.morimil.app.ai.IntrinsicSystemPromptBuilder
import com.morimil.app.ai.ReasoningProviderConfig
import com.morimil.app.data.genesis.GenesisIdentity
import com.morimil.app.data.genesis.ultra.CanonicalMemoryQuarantineStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningKernelCanonicalMemoryQuarantineTest {
    @After
    fun clearQuarantine() {
        CanonicalMemoryQuarantineStore.clearForTest()
    }

    @Test
    fun corruptedMemoryProducesAbstentionBeforePromptOrMotorExecution() = runBlocking {
        var capsuleReads = 0
        var intrinsicCalls = 0
        var externalCalls = 0
        val intrinsicMotor = object : IntrinsicReasoningMotor {
            override val role = ReasoningMotorRole.INTUITIVE
            override val capabilityVersion = "intuitive-test"

            override suspend fun compute(
                request: IntrinsicReasoningRequest
            ): Result<IntrinsicReasoningResponse> {
                intrinsicCalls += 1
                return Result.success(IntrinsicReasoningResponse("must not run"))
            }
        }
        val kernel = ReasoningKernel(
            contextReader = object : ReasoningContextReader {
                override suspend fun readLivingMemory(query: String): String {
                    return CanonicalMemoryQuarantineStore.verify(stage = "event_chain") {
                        throw IllegalArgumentException("canonical_memory_signature_invalid:7")
                    }
                }

                override suspend fun readKnowledgeCapsules(): String {
                    capsuleReads += 1
                    return "must not be read"
                }
            },
            intrinsicCoordinator = IntrinsicTriMotorCoordinator(listOf(intrinsicMotor)),
            temporaryExternalProvider = TemporaryExternalReasoningProvider {
                externalCalls += 1
                Result.success("must not run")
            }
        )

        val result = kernel.reason(
            ReasoningKernelRequest(
                input = "Recuerda este dato",
                alias = "Morimil",
                genesis = testGenesis(),
                doctrineText = null,
                policyText = null,
                priorHistory = emptyList(),
                runtimeConfig = ReasoningProviderConfig.default(),
                runtimeAccess = "",
                runtimeLabel = "local"
            )
        )

        assertNull(result.morimilReply)
        assertNull(result.auxiliaryAdvisory)
        assertTrue(result.errorMessage?.contains("memoria canónica está en cuarentena") == true)
        assertTrue(result.errorMessage?.contains("Ningún contenido de esa cadena entró al prompt") == true)
        assertEquals(0, capsuleReads)
        assertEquals(0, intrinsicCalls)
        assertEquals(0, externalCalls)
        assertTrue(result.state.trace.any { trace -> trace.stage == "error" })
        assertTrue(result.state.trace.none { trace -> trace.stage == "context_built" })
        assertTrue(result.state.trace.none { trace -> trace.stage == "intrinsic_context_boundary" })
        assertTrue(result.state.trace.none { trace -> trace.stage == "intrinsic_motor_plan" })
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
