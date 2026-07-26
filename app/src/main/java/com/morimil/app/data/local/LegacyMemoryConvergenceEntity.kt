package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "genesis_ultra_legacy_memory_convergence")
data class LegacyMemoryConvergenceEntity(
    @PrimaryKey
    val slotId: String,
    val instanceId: String,
    val status: String,
    val sourceEventCount: Int,
    val acceptedEventCount: Int,
    val importedEventCount: Int,
    val sourceTipHash: String?,
    val dryRunDigest: String,
    val activeWriter: String,
    val legacyReadOnly: Boolean,
    val failureCode: String?,
    val updatedAtMillis: Long
) {
    companion object {
        const val PRIMARY_SLOT = "legacy_memory_v1"
        const val STATUS_FROZEN = "frozen"
        const val STATUS_BLOCKED = "blocked_unverified"
        const val STATUS_COMPLETE = "complete"
        const val WRITER_GENESIS_ULTRA = "genesis_ultra"
    }
}

@Entity(
    tableName = "genesis_ultra_legacy_memory_imports",
    indices = [
        Index(value = ["canonicalEventHash"], unique = true),
        Index(value = ["instanceId", "canonicalSequence"], unique = true)
    ]
)
data class LegacyMemoryImportEntity(
    @PrimaryKey
    val legacyEventHash: String,
    val legacyEventId: Long,
    val instanceId: String,
    val canonicalEventHash: String,
    val canonicalSequence: Long,
    val provenanceDigest: String,
    val importedAtMillis: Long
)
