package com.morimil.app.core.memory

import com.morimil.app.data.local.DecisionLogEntity
import com.morimil.app.data.local.KnowledgeCapsuleEntity
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MigrationRecordEntity
import com.morimil.app.data.local.ProjectStateEntity
import com.morimil.app.data.local.RecallScheduleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGraphExplorerTest {
    @Test
    fun globalGraphBuildsTypedNodesAndDetectsGaps() {
        val validHash = "evsha256:valid"
        val missingHash = "evsha256:missing"
        val snapshot = MemoryGraphExplorer.build(
            mode = MemoryGraphExplorer.MODE_GLOBAL,
            selectedEventHash = null,
            events = listOf(event(validHash, "decision", "Fundador decide grafo global")),
            links = listOf(link("l1", validHash, missingHash, verificationState = "orphaned")),
            capsules = listOf(capsule("c1", "Capsula con hueco", missingHash)),
            recalls = listOf(recall(1L, missingHash)),
            migrations = listOf(migration("m1", """["$missingHash"]""")),
            projects = listOf(ProjectStateEntity("p1", "Morimil", "active", 1L)),
            decisions = listOf(DecisionLogEntity(1L, "Crear grafo v2", "accepted", 1L)),
            nowMillis = 10L
        )

        assertTrue(snapshot.nodes.any { node -> node.nodeType == "knowledge_capsule" })
        assertTrue(snapshot.nodes.any { node -> node.nodeType == "project" })
        assertTrue(snapshot.nodes.any { node -> node.nodeType == "decision" })
        assertTrue(snapshot.gaps.any { gap -> gap.title.contains("Capsula") })
        assertTrue(snapshot.issueCount > 0)
        assertTrue(snapshot.narrativePath.first() == "Morimil")
    }

    @Test
    fun focusGraphKeepsSelectedMemoryEvent() {
        val selectedHash = "evsha256:selected"
        val snapshot = MemoryGraphExplorer.build(
            mode = MemoryGraphExplorer.MODE_FOCUS,
            selectedEventHash = selectedHash,
            events = listOf(event(selectedHash, "identity", "Recuerdo central")),
            links = emptyList(),
            capsules = emptyList(),
            recalls = emptyList(),
            migrations = emptyList(),
            projects = emptyList(),
            decisions = emptyList()
        )

        assertEquals(selectedHash, snapshot.selectedNodeId)
        assertTrue(snapshot.nodes.any { node -> node.nodeId == selectedHash && node.selected })
    }

    @Test
    fun emptyGraphTreatsOnlyIdentityRootAsEmpty() {
        val snapshot = MemoryGraphExplorer.build(
            mode = MemoryGraphExplorer.MODE_GLOBAL,
            selectedEventHash = null,
            events = emptyList(),
            links = emptyList(),
            capsules = emptyList(),
            recalls = emptyList(),
            migrations = emptyList(),
            projects = emptyList(),
            decisions = emptyList()
        )

        assertTrue(snapshot.isEmpty)
    }

    @Test
    fun unloadedReferencesAreWatchNotCritical() {
        val missingHash = "evsha256:older-event-not-loaded"
        val snapshot = MemoryGraphExplorer.build(
            mode = MemoryGraphExplorer.MODE_GLOBAL,
            selectedEventHash = null,
            events = emptyList(),
            links = emptyList(),
            capsules = listOf(capsule("c2", "Capsula con fuente antigua", missingHash)),
            recalls = listOf(recall(2L, missingHash)),
            migrations = listOf(migration("m2", """["$missingHash"]""")),
            projects = emptyList(),
            decisions = emptyList(),
            nowMillis = 10L
        )

        assertTrue(snapshot.gaps.any { gap -> gap.severity == "watch" })
        assertTrue(snapshot.nodes.none { node -> node.health == "critical" })
        assertTrue(snapshot.edges.none { edge -> edge.health == "critical" })
    }

    private fun event(hash: String, memoryKind: String, body: String): MemoryGraphEventView {
        return MemoryGraphEventView(
            eventHash = hash,
            sequence = 1L,
            eventType = "test.event",
            memoryKind = memoryKind,
            importance = 90,
            confidence = 80,
            userConfirmed = true,
            body = body
        )
    }

    private fun link(
        linkId: String,
        sourceHash: String,
        targetHash: String,
        verificationState: String = "valid"
    ): MemoryLinkEntity {
        return MemoryLinkEntity(
            linkId = linkId,
            instanceId = "instance-test",
            genesisCoreHash = "evsha256:birth-root",
            sourceId = sourceHash,
            sourceType = MemoryGraphExplorer.MEMORY_EVENT_NODE_TYPE,
            targetId = targetHash,
            targetType = MemoryGraphExplorer.MEMORY_EVENT_NODE_TYPE,
            relation = "supports",
            strength = 0.8,
            reason = "test",
            createdBy = "test",
            privacyVisibility = "private_local",
            cloudSyncAllowed = false,
            exportAllowed = false,
            verificationState = verificationState,
            createdAtMillis = 1L
        )
    }

    private fun capsule(
        capsuleId: String,
        title: String,
        sourceEventHash: String
    ): KnowledgeCapsuleEntity {
        return KnowledgeCapsuleEntity(
            capsuleId = capsuleId,
            genesisCoreId = "primary_genesis",
            capsuleVersion = 1,
            capsuleCategory = "test",
            capsuleType = "knowledge_capsule",
            status = "active",
            title = title,
            source = "test",
            privacyVisibility = "private_local",
            summary = "summary",
            claimsJson = "[]",
            tags = "[]",
            evidenceJson = "{}",
            confidence = 80,
            sourceEventHash = sourceEventHash,
            previousCapsuleHash = null,
            capsuleHash = "sha256:$capsuleId",
            hashAlgorithm = "sha256",
            canonicalization = "morimil.knowledge_capsule_hash.v2",
            createdAtMillis = 1L,
            updatedAtMillis = 1L
        )
    }

    private fun recall(recallId: Long, targetEventHash: String): RecallScheduleEntity {
        return RecallScheduleEntity(
            recallId = recallId,
            genesisCoreId = "evsha256:birth-root",
            targetEventHash = targetEventHash,
            targetMemoryKind = "decision",
            prompt = "recordar",
            reason = "test",
            priority = 90,
            intervalDays = 1,
            dueAtMillis = 1L,
            status = "active",
            lastAction = "created",
            source = "canonical_memory_event",
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
            lastReviewedAtMillis = null
        )
    }

    private fun migration(migrationId: String, affectedArtifactsJson: String): MigrationRecordEntity {
        return MigrationRecordEntity(
            migrationId = migrationId,
            instanceId = "instance-test",
            genesisCoreHash = "evsha256:birth-root",
            proposalId = null,
            migrationType = "test",
            fromVersion = "from",
            toVersion = "to",
            affectedArtifactsJson = affectedArtifactsJson,
            preSnapshotId = "none",
            chainVerified = true,
            backupRequired = false,
            stepsJson = "[]",
            expectedEffect = "test",
            riskLevel = "low",
            approvalRequired = false,
            approvedByUser = false,
            approvalId = null,
            status = "planned",
            postSnapshotId = null,
            errorsJson = "[]",
            rollbackAvailable = true,
            rollbackStrategy = "append_only",
            createdBy = "test",
            createdAtMillis = 1L,
            updatedAtMillis = 1L
        )
    }
}
