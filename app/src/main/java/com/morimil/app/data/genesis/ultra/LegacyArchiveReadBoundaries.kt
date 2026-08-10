package com.morimil.app.data.genesis.ultra

import com.morimil.app.data.local.LegacyArchiveReadDao
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MorimilDatabase

internal data class LegacyBirthConflictSnapshot(
    val localIdentityCount: Int,
    val genesisCoreCount: Int
) {
    init {
        require(localIdentityCount >= 0) { "legacy_conflict_local_identity_count_invalid" }
        require(genesisCoreCount >= 0) { "legacy_conflict_genesis_core_count_invalid" }
    }

    val isEmpty: Boolean
        get() = localIdentityCount == 0 && genesisCoreCount == 0
}

/** Read-only anti-double-birth probe; it cannot return or mutate legacy identity rows. */
internal interface LegacyBirthConflictProbe {
    suspend fun inspect(): LegacyBirthConflictSnapshot

    companion object {
        fun production(database: MorimilDatabase): LegacyBirthConflictProbe {
            return RoomLegacyBirthConflictProbe(database.legacyArchiveReadDao())
        }
    }
}

private class RoomLegacyBirthConflictProbe(
    private val dao: LegacyArchiveReadDao
) : LegacyBirthConflictProbe {
    override suspend fun inspect(): LegacyBirthConflictSnapshot {
        return LegacyBirthConflictSnapshot(
            localIdentityCount = dao.countLocalIdentity(),
            genesisCoreCount = dao.countGenesisCore()
        )
    }
}

/**
 * Read-only access to the frozen `memory_events` lineage used only by one-way
 * convergence and verification. No normal runtime presentation may depend on it.
 */
internal interface LegacyMemoryArchiveReadPort {
    suspend fun loadAuditChain(): List<MemoryEventEntity>
    suspend fun countEvents(): Int

    companion object {
        fun production(database: MorimilDatabase): LegacyMemoryArchiveReadPort {
            return RoomLegacyMemoryArchiveReadPort(database.legacyArchiveReadDao())
        }
    }
}

private class RoomLegacyMemoryArchiveReadPort(
    private val dao: LegacyArchiveReadDao
) : LegacyMemoryArchiveReadPort {
    override suspend fun loadAuditChain(): List<MemoryEventEntity> =
        dao.loadMemoryEventAuditChain()

    override suspend fun countEvents(): Int = dao.countMemoryEvents()
}
