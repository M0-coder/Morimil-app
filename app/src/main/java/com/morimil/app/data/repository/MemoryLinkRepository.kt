package com.morimil.app.data.repository

import com.morimil.app.core.identity.StableIdDigest
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MemoryOrganDatabase
import kotlinx.coroutines.flow.Flow

class MemoryLinkRepository(organDatabase: MemoryOrganDatabase) {
    private val organDao = organDatabase.memoryOrganDao()

    val recentMemoryLinks: Flow<List<MemoryLinkEntity>> =
        organDao.observeRecentMemoryLinks(RECENT_LINK_LIMIT)

    fun observeMemoryLinksForEvent(eventHash: String): Flow<List<MemoryLinkEntity>> {
        require(eventHash.isNotBlank()) { "canonical_memory_event_hash_required" }
        return organDao.observeMemoryLinksForNode(
            nodeId = eventHash,
            nodeType = CANONICAL_MEMORY_EVENT_NODE_TYPE,
            limit = RECENT_LINK_LIMIT
        )
    }

    suspend fun createMemoryLink(
        instanceId: String,
        genesisCoreHash: String,
        sourceId: String,
        sourceType: String,
        targetId: String,
        targetType: String,
        relation: String,
        strength: Double,
        reason: String,
        createdBy: String = CREATED_BY_LOCAL_RUNTIME,
        createdAtMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val insertedId = organDao.insertMemoryLink(
            MemoryLinkEntity(
                linkId = buildMemoryLinkId(createdAtMillis, sourceId, targetId, relation),
                instanceId = instanceId,
                genesisCoreHash = genesisCoreHash,
                sourceId = sourceId,
                sourceType = sourceType,
                targetId = targetId,
                targetType = targetType,
                relation = relation,
                strength = strength.coerceIn(0.0, 1.0),
                reason = reason.ifBlank { "local_memory_link_v1" },
                createdBy = createdBy,
                privacyVisibility = PRIVATE_LOCAL,
                cloudSyncAllowed = false,
                exportAllowed = false,
                verificationState = VERIFICATION_VALID,
                createdAtMillis = createdAtMillis
            )
        )
        return insertedId > 0
    }

    companion object {
        const val CANONICAL_MEMORY_EVENT_NODE_TYPE = "canonical_memory_event"
        const val KNOWLEDGE_CAPSULE_NODE_TYPE = "knowledge_capsule"
        const val RELATION_DERIVED_FROM = "derived_from"

        private const val CREATED_BY_LOCAL_RUNTIME = "local_runtime"
        private const val PRIVATE_LOCAL = "private_local"
        private const val VERIFICATION_VALID = "valid"
        private const val RECENT_LINK_LIMIT = 50

        fun buildMemoryLinkId(
            createdAtMillis: Long,
            sourceId: String,
            targetId: String,
            relation: String
        ): String {
            val suffix = StableIdDigest.shortSha256Hex(
                namespace = "memory_link",
                parts = listOf(
                    createdAtMillis.toString(),
                    sourceId,
                    targetId,
                    relation
                )
            )
            return "mlink_${createdAtMillis}_$suffix"
        }
    }
}
