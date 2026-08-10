package com.morimil.app.data.local

import androidx.room.Dao
import androidx.room.Query

/**
 * Read-only Room surface for the frozen pre-Genesis-Ultra archive.
 *
 * This DAO deliberately exposes no INSERT/UPDATE/DELETE capability. Legacy rows
 * exist only as conflict evidence and one-way convergence input until F3.3-C
 * removes the physical schema through an explicit Room migration.
 */
@Dao
interface LegacyArchiveReadDao {
    @Query("SELECT COUNT(*) FROM local_instance_identity")
    suspend fun countLocalIdentity(): Int

    @Query("SELECT COUNT(*) FROM genesis_core")
    suspend fun countGenesisCore(): Int

    @Query("SELECT * FROM memory_events ORDER BY id ASC")
    suspend fun loadMemoryEventAuditChain(): List<MemoryEventEntity>

    @Query("SELECT COUNT(*) FROM memory_events")
    suspend fun countMemoryEvents(): Int
}
