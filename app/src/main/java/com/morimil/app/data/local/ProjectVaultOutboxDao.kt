package com.morimil.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProjectVaultOutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(operation: ProjectVaultOutboxEntity)

    @Query("SELECT * FROM project_vault_outbox WHERE operationId = :operationId LIMIT 1")
    suspend fun load(operationId: String): ProjectVaultOutboxEntity?

    @Query(
        """
        SELECT * FROM project_vault_outbox
        WHERE status = 'pending'
        ORDER BY createdAtMillis ASC, operationId ASC
        LIMIT :limit
        """
    )
    suspend fun loadPending(limit: Int): List<ProjectVaultOutboxEntity>

    @Query(
        """
        SELECT COUNT(*) FROM project_vault_outbox
        WHERE vaultId = :vaultId AND status = 'pending'
        """
    )
    suspend fun countPendingForVault(vaultId: String): Int

    @Query("SELECT COUNT(*) FROM project_vault_outbox WHERE status = 'pending'")
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM project_vault_outbox WHERE status = 'blocked'")
    suspend fun countBlocked(): Int

    @Query("SELECT COUNT(*) FROM project_vault_outbox WHERE status = 'committed'")
    suspend fun countCommitted(): Int

    @Query("SELECT * FROM project_vault_outbox ORDER BY createdAtMillis ASC, operationId ASC")
    suspend fun loadAll(): List<ProjectVaultOutboxEntity>

    @Query(
        """
        UPDATE project_vault_outbox
        SET attemptCount = attemptCount + 1,
            lastError = :error,
            updatedAtMillis = :updatedAtMillis
        WHERE operationId = :operationId AND status = 'pending'
        """
    )
    suspend fun recordRetryableFailure(
        operationId: String,
        error: String,
        updatedAtMillis: Long
    ): Int

    @Query(
        """
        UPDATE project_vault_outbox
        SET status = 'blocked',
            attemptCount = attemptCount + 1,
            lastError = :error,
            canonicalEventHash = :canonicalEventHash,
            canonicalSequence = :canonicalSequence,
            updatedAtMillis = :updatedAtMillis
        WHERE operationId = :operationId AND status = 'pending'
        """
    )
    suspend fun markBlocked(
        operationId: String,
        error: String,
        canonicalEventHash: String?,
        canonicalSequence: Long?,
        updatedAtMillis: Long
    ): Int

    @Query(
        """
        UPDATE project_vault_outbox
        SET status = 'committed',
            lastError = NULL,
            canonicalEventHash = :canonicalEventHash,
            canonicalSequence = :canonicalSequence,
            updatedAtMillis = :committedAtMillis,
            committedAtMillis = :committedAtMillis
        WHERE operationId = :operationId AND status = 'pending'
        """
    )
    suspend fun markCommitted(
        operationId: String,
        canonicalEventHash: String,
        canonicalSequence: Long,
        committedAtMillis: Long
    ): Int
}
