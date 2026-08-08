package com.morimil.app.data.repository

import com.morimil.app.core.orchestration.AgentCapabilityPolicy
import com.morimil.app.core.orchestration.DelegationPlan
import com.morimil.app.data.local.AgentInstanceEntity
import com.morimil.app.data.local.DelegatedTaskEntity
import com.morimil.app.data.local.ProjectVaultEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLifecycleOperationFactoryTest {
    @Test
    fun createIdentityIsDeterministicAndOrdinalSeparatesIntentionalDuplicates() {
        val first = AgentLifecycleOperationFactory.create(
            identity(), vault(), AgentCapabilityPolicy.AGENT_FILE_AUDIT, "Briefing estable", 1
        )
        val replay = AgentLifecycleOperationFactory.create(
            identity(), vault(), AgentCapabilityPolicy.AGENT_FILE_AUDIT, "Briefing estable", 1
        )
        val second = AgentLifecycleOperationFactory.create(
            identity(), vault(), AgentCapabilityPolicy.AGENT_FILE_AUDIT, "Briefing estable", 2
        )

        assertEquals(first, replay)
        assertNotEquals(first.subjectId, second.subjectId)
        assertTrue(first.subjectId.startsWith("agent_instance_"))
        assertTrue(first.payloadJson.contains("\"ordinal\":1"))
        assertTrue(first.evidenceJson.contains("\"ownership_conferred\":false"))
    }

    @Test
    fun assignmentChainsFromSemanticAgentStateWithoutClockIdentity() {
        val initial = agent(currentTaskId = null)
        val first = AgentLifecycleOperationFactory.assign(
            identity(), vault(), initial, "Revisar código", plan()
        )
        val replay = AgentLifecycleOperationFactory.assign(
            identity(), vault(), initial, "Revisar co\u0301digo", plan()
        )
        val nextAgent = initial.copy(currentTaskId = first.subjectId)
        val next = AgentLifecycleOperationFactory.assign(
            identity(), vault(), nextAgent, "Revisar código", plan()
        )

        assertEquals(first.operationId, replay.operationId)
        assertEquals(first.subjectId, replay.subjectId)
        assertNotEquals(first.subjectId, next.subjectId)
        assertTrue(first.subjectId.startsWith("ptask_"))
        assertTrue(first.payloadJson.contains("\"immune_blocked\":false"))
    }

    @Test
    fun submitResultBindsApprovedTaskAndNormalizedSummary() {
        val agent = agent(currentTaskId = "ptask_test")
        val task = task(status = AgentCapabilityPolicy.STATUS_APPROVED, approvalId = "xop_" + "a".repeat(64))
        val first = AgentLifecycleOperationFactory.submitResult(
            identity(), agent, task, "Resultado útil"
        )
        val replay = AgentLifecycleOperationFactory.submitResult(
            identity(), agent, task, "Resultado u\u0301til"
        )

        assertEquals(first.operationId, replay.operationId)
        assertEquals(AgentLifecycleProtocolTypes.SUBMIT_RESULT, first.operationType)
        assertTrue(first.payloadJson.contains("result_summary_digest"))
    }

    @Test
    fun terminalOperationsHaveDistinctTypesAndQuarantinePlansReplacement() {
        val agent = agent(currentTaskId = "ptask_test")
        val retire = AgentLifecycleOperationFactory.retire(identity(), agent, "cerrar")
        val promote = AgentLifecycleOperationFactory.promote(identity(), agent, "promover")
        val quarantine = AgentLifecycleOperationFactory.quarantine(
            identity(), vault(), agent, "fallo repetible"
        )

        assertEquals(AgentLifecycleProtocolTypes.RETIRE, retire.operationType)
        assertEquals(AgentLifecycleProtocolTypes.PROMOTE, promote.operationType)
        assertEquals(AgentLifecycleProtocolTypes.QUARANTINE, quarantine.operationType)
        assertNotEquals(retire.operationId, promote.operationId)
        assertTrue(quarantine.payloadJson.contains("replacement_agent_id"))
        assertTrue(quarantine.payloadJson.contains(AgentLifecycleProtocolSchemas.PLANNED_AGENT))
    }

    private fun identity() = AgentLifecycleProtocolIdentity(
        instanceId = "instance_test",
        writerBodyId = "body_test",
        writerEpoch = "epoch_test"
    )

    private fun vault() = ProjectVaultEntity(
        vaultId = "vault_test",
        displayName = "Vault Test",
        companyName = "Morimil",
        projectType = "software",
        mission = "Construir con evidencia",
        status = "active",
        roadmapSummary = "roadmap",
        progressPercent = 30,
        activeAgentCount = 1,
        healthStatus = "healthy",
        sourceContext = "test",
        createdAtMillis = 1,
        updatedAtMillis = 2,
        completedAtMillis = null
    )

    private fun agent(currentTaskId: String?) = AgentInstanceEntity(
        agentInstanceId = "agent_instance_test",
        projectVaultId = "vault_test",
        templateAgentId = AgentCapabilityPolicy.AGENT_FILE_AUDIT,
        displayName = "Vault Test file audit worker",
        briefing = "Briefing estable",
        constraintsJson = "{}",
        status = AgentInstanceLifecycleRepository.STATUS_THINKING,
        qualityScore = 50,
        errorCount = 0,
        currentTaskId = currentTaskId,
        lastHeartbeatAtMillis = 10,
        createdAtMillis = 10,
        updatedAtMillis = 10,
        retiredAtMillis = null,
        retireReason = null
    )

    private fun task(status: String, approvalId: String?) = DelegatedTaskEntity(
        taskId = "ptask_test",
        createdBy = "morimil_project_vault",
        assignedAgentId = "agent_instance_test",
        targetDeviceId = null,
        goal = "Revisar código",
        contextSummary = "contexto",
        inputRefsJson = "[]",
        allowedActionsJson = "[]",
        allowedTransportsJson = "[]",
        approvalRequired = true,
        approvalId = approvalId,
        status = status,
        riskLevel = "medium",
        resultSummary = null,
        errorSummary = null,
        createdAtMillis = 20,
        updatedAtMillis = 20,
        completedAtMillis = null
    )

    private fun plan() = DelegationPlan(
        assignedAgentId = AgentCapabilityPolicy.AGENT_FILE_AUDIT,
        targetDeviceId = null,
        allowedActions = listOf("read_allowed_files"),
        allowedTransports = listOf(AgentCapabilityPolicy.TRANSPORT_MANUAL),
        approvalRequired = true,
        riskLevel = "medium",
        contextSummary = "contexto estable",
        immuneDecision = "allow",
        immuneReasons = emptyList(),
        immuneMatchedSignals = emptyList()
    )
}
