package com.morimil.app.data.repository

import com.morimil.app.core.orchestration.AgentCapabilityPolicy
import com.morimil.app.core.orchestration.DelegationPlan
import com.morimil.app.data.local.CrossDatabaseOperationEntity
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.DelegatedTaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrchestrationProtocolFinalizerTest {
    @Test
    fun proposalCreatesOwnerStateOnlyDuringFinalizationAndReplayIsIdempotent() = runBlocking {
        val store = FakeStore()
        val finalizer = OrchestrationProtocolFinalizer.testing(store)
        val command = proposalCommand()
        val operation = operation(command)
        val receipt = receipt(command, 41)

        assertEquals(null, store.task)
        val first = finalizer.finalizeInsideTransaction(operation, receipt)
        val inserted = requireNotNull(store.task)

        assertEquals(command.subjectId, inserted.taskId)
        assertEquals(AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL, inserted.status)
        assertEquals(1, store.insertCalls)
        assertEquals(OrchestrationProtocolSchemas.ORCH_002_LOCAL_RESULT, first.schema)
        assertTrue(first.json.contains("\"record_inserted\":true"))
        assertTrue(first.json.contains(receipt.eventHash))

        val replay = finalizer.finalizeInsideTransaction(operation, receipt)
        assertEquals(1, store.insertCalls)
        assertTrue(replay.json.contains("\"record_inserted\":false"))
        assertEquals(first.ownerStatus, replay.ownerStatus)
    }

    @Test
    fun conflictingExistingProposalFailsClosed() = runBlocking {
        val store = FakeStore()
        val finalizer = OrchestrationProtocolFinalizer.testing(store)
        val command = proposalCommand()
        val operation = operation(command)
        finalizer.finalizeInsideTransaction(operation, receipt(command, 42))
        store.task = requireNotNull(store.task).copy(goal = "conflicting goal")

        assertFailure(
            CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT,
            runCatching {
                finalizer.finalizeInsideTransaction(operation, receipt(command, 42))
            }.exceptionOrNull()
        )
    }

    @Test
    fun approvalUsesConditionalTransitionAndSameOperationIsIdempotent() = runBlocking {
        val store = FakeStore()
        val finalizer = OrchestrationProtocolFinalizer.testing(store)
        val proposal = proposalCommand()
        finalizer.finalizeInsideTransaction(operation(proposal), receipt(proposal, 50))
        val pending = requireNotNull(store.task)
        val approval = OrchestrationOperationFactory.approve(identity(), pending)
        val approvalOperation = operation(approval)

        val first = finalizer.finalizeInsideTransaction(
            approvalOperation,
            receipt(approval, 51)
        )
        val approved = requireNotNull(store.task)

        assertEquals(1, store.approveCalls)
        assertEquals(AgentCapabilityPolicy.STATUS_APPROVED, approved.status)
        assertEquals(approval.operationId, approved.approvalId)
        assertTrue(first.json.contains("\"record_updated\":true"))

        val replay = finalizer.finalizeInsideTransaction(
            approvalOperation,
            receipt(approval, 51)
        )
        assertEquals(1, store.approveCalls)
        assertTrue(replay.json.contains("\"record_updated\":false"))
        assertEquals(AgentCapabilityPolicy.STATUS_APPROVED, replay.ownerStatus)
    }

    @Test
    fun rejectionUsesConditionalTransitionAndPreservesExactReason() = runBlocking {
        val store = FakeStore()
        val finalizer = OrchestrationProtocolFinalizer.testing(store)
        val proposal = proposalCommand()
        finalizer.finalizeInsideTransaction(operation(proposal), receipt(proposal, 60))
        val pending = requireNotNull(store.task)
        val rejection = OrchestrationOperationFactory.reject(
            identity = identity(),
            task = pending,
            reason = "Requiere revisión manual"
        )
        val rejectionOperation = operation(rejection)

        val first = finalizer.finalizeInsideTransaction(
            rejectionOperation,
            receipt(rejection, 61)
        )
        val rejected = requireNotNull(store.task)

        assertEquals(1, store.rejectCalls)
        assertEquals(AgentCapabilityPolicy.STATUS_REJECTED, rejected.status)
        assertEquals("Requiere revisión manual", rejected.errorSummary)
        assertNotNull(rejected.completedAtMillis)
        assertTrue(first.json.contains("\"record_updated\":true"))

        val replay = finalizer.finalizeInsideTransaction(
            rejectionOperation,
            receipt(rejection, 61)
        )
        assertEquals(1, store.rejectCalls)
        assertTrue(replay.json.contains("\"record_updated\":false"))
    }

    @Test
    fun lostCasAcceptsOnlyExactConcurrentTerminalState() = runBlocking {
        val store = FakeStore()
        val finalizer = OrchestrationProtocolFinalizer.testing(store)
        val proposal = proposalCommand()
        finalizer.finalizeInsideTransaction(operation(proposal), receipt(proposal, 70))
        val pending = requireNotNull(store.task)
        val approval = OrchestrationOperationFactory.approve(identity(), pending)
        store.approveBehavior = { taskId, approvalId, updatedAt ->
            store.task = requireNotNull(store.task).copy(
                status = AgentCapabilityPolicy.STATUS_APPROVED,
                approvalId = approvalId,
                updatedAtMillis = updatedAt
            )
            0
        }

        val result = finalizer.finalizeInsideTransaction(
            operation(approval),
            receipt(approval, 71)
        )
        assertTrue(result.json.contains("\"record_updated\":false"))

        store.task = pending
        val rejection = OrchestrationOperationFactory.reject(identity(), pending, "No")
        store.rejectBehavior = { _, _, _, _ ->
            store.task = pending.copy(
                status = AgentCapabilityPolicy.STATUS_APPROVED,
                approvalId = "xop_" + "9".repeat(64)
            )
            0
        }
        assertFailure(
            CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT,
            runCatching {
                finalizer.finalizeInsideTransaction(
                    operation(rejection),
                    receipt(rejection, 72)
                )
            }.exceptionOrNull()
        )
    }

    @Test
    fun malformedStateAndPayloadFailClosedWithTypedErrors() = runBlocking {
        val finalizer = OrchestrationProtocolFinalizer.testing(FakeStore())
        val command = proposalCommand()
        val receipt = receipt(command, 80)

        assertFailure(
            CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT,
            runCatching {
                finalizer.finalizeInsideTransaction(
                    operation(command).copy(status = CrossDatabaseOperationStatus.STAGED),
                    receipt
                )
            }.exceptionOrNull()
        )
        assertFailure(
            CrossDatabaseProtocolErrors.UNSUPPORTED_OPERATION_VERSION,
            runCatching {
                finalizer.finalizeInsideTransaction(
                    operation(command).copy(ownerType = "foreign_owner"),
                    receipt
                )
            }.exceptionOrNull()
        )
        assertFailure(
            CrossDatabaseProtocolErrors.UNSUPPORTED_PAYLOAD_SCHEMA,
            runCatching {
                finalizer.finalizeInsideTransaction(
                    operation(command).copy(payloadSchema = "wrong.schema"),
                    receipt
                )
            }.exceptionOrNull()
        )
    }

    private fun proposalCommand(): CrossDatabaseStageCommand =
        OrchestrationOperationFactory.propose(
            identity = identity(),
            goal = "Revisar repositorio",
            plan = DelegationPlan(
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
        )

    private fun identity() = OrchestrationProtocolIdentity(
        instanceId = "instance_test",
        writerBodyId = "body_test",
        writerEpoch = "epoch_test"
    )

    private fun operation(command: CrossDatabaseStageCommand): CrossDatabaseOperationEntity {
        val receipt = receipt(command, 1)
        return CrossDatabaseOperationEntity(
            operationId = command.operationId,
            ownerType = command.ownerType,
            operationType = command.operationType,
            operationVersion = command.operationVersion,
            instanceId = command.instanceId,
            writerBodyId = command.writerBodyId,
            writerEpoch = command.writerEpoch,
            subjectId = command.subjectId,
            parentOperationId = command.parentOperationId,
            childPhase = command.childPhase,
            payloadSchema = command.payloadSchema,
            payloadJson = command.payloadJson,
            payloadDigest = command.payloadDigest,
            eventId = command.eventId,
            eventType = command.eventType,
            eventBody = command.eventBody,
            evidenceSchema = command.evidenceSchema,
            evidenceJson = command.evidenceJson,
            evidenceDigest = command.evidenceDigest,
            status = CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT,
            attemptCount = 0,
            lastErrorCode = null,
            canonicalEventHash = receipt.eventHash,
            canonicalSequence = receipt.sequence,
            canonicalProvenanceDigest = receipt.provenanceDigest,
            localResultSchema = null,
            localResultJson = null,
            localResultDigest = null,
            occurredAtMillis = 1_000,
            createdAtMillis = 1_000,
            updatedAtMillis = 1_000,
            committedAtMillis = null
        )
    }

    private fun receipt(
        command: CrossDatabaseStageCommand,
        sequence: Long
    ) = CrossDatabaseCanonicalReceipt(
        eventId = command.eventId,
        eventHash = "evsha256:" + "1".repeat(64),
        sequence = sequence,
        provenanceDigest = "sha256:" + "2".repeat(64),
        reusedExistingEvent = true
    )

    private fun assertFailure(expectedCode: String, error: Throwable?) {
        val failure = error as CrossDatabaseProtocolFailure
        assertEquals(expectedCode, failure.stableCode)
        assertTrue(failure.permanent)
    }

    private class FakeStore : OrchestrationFinalizerStore {
        var task: DelegatedTaskEntity? = null
        var insertCalls = 0
        var approveCalls = 0
        var rejectCalls = 0
        var approveBehavior: suspend (String, String, Long) -> Int =
            { _, approvalId, updatedAt ->
                val current = task
                if (
                    current != null &&
                    current.status == AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL &&
                    current.approvalId == null
                ) {
                    task = current.copy(
                        status = AgentCapabilityPolicy.STATUS_APPROVED,
                        approvalId = approvalId,
                        updatedAtMillis = updatedAt
                    )
                    1
                } else {
                    0
                }
            }
        var rejectBehavior: suspend (String, String, Long, Long) -> Int =
            { _, reason, updatedAt, completedAt ->
                val current = task
                if (
                    current != null &&
                    current.status == AgentCapabilityPolicy.STATUS_AWAITING_APPROVAL &&
                    current.approvalId == null
                ) {
                    task = current.copy(
                        status = AgentCapabilityPolicy.STATUS_REJECTED,
                        errorSummary = reason,
                        updatedAtMillis = updatedAt,
                        completedAtMillis = completedAt
                    )
                    1
                } else {
                    0
                }
            }

        override suspend fun loadDelegatedTask(taskId: String): DelegatedTaskEntity? =
            task?.takeIf { it.taskId == taskId }

        override suspend fun insertDelegatedTask(task: DelegatedTaskEntity) {
            insertCalls += 1
            this.task = task
        }

        override suspend fun approveDelegatedTaskIfAwaitingApproval(
            taskId: String,
            approvalId: String,
            updatedAtMillis: Long
        ): Int {
            approveCalls += 1
            return approveBehavior(taskId, approvalId, updatedAtMillis)
        }

        override suspend fun rejectDelegatedTaskIfAwaitingApproval(
            taskId: String,
            errorSummary: String,
            updatedAtMillis: Long,
            completedAtMillis: Long
        ): Int {
            rejectCalls += 1
            return rejectBehavior(taskId, errorSummary, updatedAtMillis, completedAtMillis)
        }
    }
}
