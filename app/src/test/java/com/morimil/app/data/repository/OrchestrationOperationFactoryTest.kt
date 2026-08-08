package com.morimil.app.data.repository

import com.morimil.app.core.orchestration.AgentCapabilityPolicy
import com.morimil.app.core.orchestration.DelegationPlan
import com.morimil.app.data.local.DelegatedTaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrchestrationOperationFactoryTest {
    @Test
    fun proposalIdentityIsDeterministicAndClockFree() {
        val identity = identity(epoch = "epoch_1")
        val plan = plan()

        val first = OrchestrationOperationFactory.propose(
            identity = identity,
            goal = "Revisar repositorio",
            plan = plan
        )
        val second = OrchestrationOperationFactory.propose(
            identity = identity,
            goal = "Revisar repositorio",
            plan = plan
        )

        assertEquals(first.operationId, second.operationId)
        assertEquals(first.subjectId, second.subjectId)
        assertEquals(first.eventId, second.eventId)
        assertEquals(first.payloadJson, second.payloadJson)
        assertEquals(first.evidenceJson, second.evidenceJson)
        assertTrue(first.subjectId.matches(Regex("^dtask_[a-f0-9]{64}$")))
        assertEquals(OrchestrationProtocolTypes.PROPOSE, first.operationType)
        assertEquals(OrchestrationProtocolTypes.PROPOSED_EVENT, first.eventType)
    }

    @Test
    fun proposalIdentityChangesWhenWriterEpochChanges() {
        val first = OrchestrationOperationFactory.propose(
            identity = identity(epoch = "epoch_1"),
            goal = "Revisar repositorio",
            plan = plan()
        )
        val second = OrchestrationOperationFactory.propose(
            identity = identity(epoch = "epoch_2"),
            goal = "Revisar repositorio",
            plan = plan()
        )

        assertNotEquals(first.operationId, second.operationId)
        assertNotEquals(first.subjectId, second.subjectId)
    }

    @Test
    fun approvalIdentityIgnoresMutableTaskTimestamps() {
        val task = task(updatedAtMillis = 10L)
        val sameTaskLater = task(updatedAtMillis = 99L)

        val first = OrchestrationOperationFactory.approve(identity(), task)
        val second = OrchestrationOperationFactory.approve(identity(), sameTaskLater)

        assertEquals(first.operationId, second.operationId)
        assertEquals(first.payloadDigest, second.payloadDigest)
        assertEquals(first.eventId, second.eventId)
        assertEquals(OrchestrationProtocolTypes.APPROVE, first.operationType)
    }

    @Test
    fun rejectionNormalizesUnicodeBeforeIdentityDerivation() {
        val composed = OrchestrationOperationFactory.reject(
            identity(),
            task(),
            "Razón técnica"
        )
        val decomposed = OrchestrationOperationFactory.reject(
            identity(),
            task(),
            "Razo\u0301n te\u0301cnica"
        )

        assertEquals(composed.operationId, decomposed.operationId)
        assertEquals(composed.payloadJson, decomposed.payloadJson)
        assertEquals(OrchestrationProtocolTypes.REJECT, composed.operationType)
    }

    @Test
    fun protocolRegistryContainsOnlyOrch002Through004() {
        assertEquals(
            mapOf(
                OrchestrationProtocolTypes.PROPOSE to OrchestrationProtocolTypes.PROPOSED_EVENT,
                OrchestrationProtocolTypes.APPROVE to OrchestrationProtocolTypes.APPROVED_EVENT,
                OrchestrationProtocolTypes.REJECT to OrchestrationProtocolTypes.REJECTED_EVENT
            ),
            OrchestrationProtocolTypes.CLOSED_REGISTRY
        )
        assertEquals(OrchestrationProtocolTypes.OWNER_TYPE, OrchestrationProtocolTypes.REGISTRY.ownerType)
        assertEquals(1, OrchestrationProtocolTypes.REGISTRY.version)
        assertTrue(OrchestrationProtocolTypes.REGISTRY.preRecoveryBlockedPayloadSchemas.isEmpty())
    }

    private fun identity(epoch: String = "epoch_1") = OrchestrationProtocolIdentity(
        instanceId = "instance_alpha",
        writerBodyId = "body_alpha",
        writerEpoch = epoch
    )

    private fun plan() = DelegationPlan(
        assignedAgentId = AgentCapabilityPolicy.AGENT_GITHUB,
        targetDeviceId = "android_body",
        allowedActions = listOf("read_repository"),
        allowedTransports = listOf(AgentCapabilityPolicy.TRANSPORT_WIFI),
        approvalRequired = true,
        riskLevel = "medium",
        contextSummary = "contexto estable",
        immuneDecision = "allow",
        immuneReasons = emptyList(),
        immuneMatchedSignals = emptyList()
    )

    private fun task(updatedAtMillis: Long = 10L) = DelegatedTaskEntity(
        taskId = "dtask_${"a".repeat(64)}",
        createdBy = "morimil_orchestrator",
        assignedAgentId = AgentCapabilityPolicy.AGENT_GITHUB,
        targetDeviceId = "android_body",
        goal = "Revisar repositorio",
        contextSummary = "contexto estable",
        inputRefsJson = "[]",
        allowedActionsJson = "[\"read_repository\"]",
        allowedTransportsJson = "[\"wifi_lan\"]",
        approvalRequired = true,
        approvalId = null,
        status = AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL,
        riskLevel = "medium",
        resultSummary = null,
        errorSummary = null,
        createdAtMillis = 1L,
        updatedAtMillis = updatedAtMillis,
        completedAtMillis = null
    )
}
