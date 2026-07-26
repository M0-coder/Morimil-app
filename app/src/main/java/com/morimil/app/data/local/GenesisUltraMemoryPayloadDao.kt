package com.morimil.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GenesisUltraMemoryPayloadDao {
    @Query("SELECT COUNT(*) FROM genesis_ultra_memory_payloads")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM genesis_ultra_memory_payloads WHERE instanceId = :instanceId")
    suspend fun countForInstance(instanceId: String): Int

    @Query(
        "SELECT * FROM genesis_ultra_memory_payloads " +
            "WHERE instanceId = :instanceId ORDER BY sequence ASC"
    )
    suspend fun loadAscending(instanceId: String): List<GenesisUltraMemoryPayloadEntity>

    @Query("SELECT * FROM genesis_ultra_memory_payloads WHERE eventHash = :eventHash LIMIT 1")
    suspend fun loadByEventHash(eventHash: String): GenesisUltraMemoryPayloadEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payload: GenesisUltraMemoryPayloadEntity)
}
