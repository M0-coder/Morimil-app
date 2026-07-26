package com.morimil.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LegacyMemoryConvergenceDao {
    @Query(
        "SELECT * FROM genesis_ultra_legacy_memory_convergence " +
            "WHERE slotId = :slotId LIMIT 1"
    )
    suspend fun loadState(
        slotId: String = LegacyMemoryConvergenceEntity.PRIMARY_SLOT
    ): LegacyMemoryConvergenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: LegacyMemoryConvergenceEntity)

    @Query(
        "SELECT * FROM genesis_ultra_legacy_memory_imports " +
            "ORDER BY legacyEventId ASC"
    )
    suspend fun loadImports(): List<LegacyMemoryImportEntity>

    @Query("SELECT COUNT(*) FROM genesis_ultra_legacy_memory_imports")
    suspend fun countImports(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertImport(entry: LegacyMemoryImportEntity)
}
