package com.morimil.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CrossDatabaseOperationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOperationAbort(operation: CrossDatabaseOperationEntity)

    @Query("SELECT * FROM cross_database_operations WHERE operationId = :operationId LIMIT 1")
    suspend fun loadOperation(operationId: String): CrossDatabaseOperationEntity?

    @Query("SELECT * FROM cross_database_operations WHERE eventId = :eventId LIMIT 1")
    suspend fun loadByEventId(eventId: String): CrossDatabaseOperationEntity?

    @Query(
        """
        SELECT * FROM cross_database_operations
        WHERE instanceId = :instanceId
          AND status IN (
              'STAGED',
              'PENDING_CANONICAL',
              'CANONICAL_COMMITTED',
              'PENDING_LOCAL_COMMIT',
              'BLOCKED'
          )
        ORDER BY createdAtMillis ASC, operationId ASC
        LIMIT :limit
        """
    )
    suspend fun loadRecoverableForInstance(
        instanceId: String,
        limit: Int
    ): List<CrossDatabaseOperationEntity>

    @Query(
        """
        SELECT * FROM cross_database_operations
        WHERE instanceId = :instanceId
          AND ownerType = :ownerType
          AND status IN (
              'STAGED',
              'PENDING_CANONICAL',
              'CANONICAL_COMMITTED',
              'PENDING_LOCAL_COMMIT',
              'BLOCKED'
          )
        ORDER BY createdAtMillis ASC, operationId ASC
        LIMIT :limit
        """
    )
    suspend fun loadRecoverableForOwner(
        instanceId: String,
        ownerType: String,
        limit: Int
    ): List<CrossDatabaseOperationEntity>

    @Query(
        """
        SELECT COUNT(*) FROM cross_database_operations
        WHERE instanceId = :instanceId
          AND status NOT IN ('COMMITTED', 'BLOCKED')
        """
    )
    suspend fun countRecoverableForInstance(instanceId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM cross_database_operations
        WHERE instanceId = :instanceId
          AND ownerType = :ownerType
          AND status NOT IN ('COMMITTED', 'BLOCKED')
        """
    )
    suspend fun countRecoverableForOwner(instanceId: String, ownerType: String): Int

    @Query(
        """
        SELECT * FROM cross_database_operations
        WHERE ownerType = :ownerType
          AND subjectId = :subjectId
          AND operationType = :operationType
        ORDER BY createdAtMillis ASC, operationId ASC
        """
    )
    suspend fun loadAnyForOwnerSubjectAndOperationType(
        ownerType: String,
        subjectId: String,
        operationType: String
    ): List<CrossDatabaseOperationEntity>

    @Query(
        """
        SELECT * FROM cross_database_operations
        WHERE ownerType = :ownerType
          AND subjectId = :subjectId
          AND status NOT IN ('COMMITTED', 'BLOCKED')
        ORDER BY createdAtMillis ASC, operationId ASC
        """
    )
    suspend fun loadActiveForOwnerSubject(
        ownerType: String,
        subjectId: String
    ): List<CrossDatabaseOperationEntity>

    @Query(
        """
        SELECT COUNT(*) FROM cross_database_operations
        WHERE instanceId = :instanceId AND status = :status
        """
    )
    suspend fun countByInstanceAndStatus(instanceId: String, status: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM cross_database_operations
        WHERE instanceId = :instanceId
          AND ownerType = :ownerType
          AND payloadSchema = :payloadSchema
          AND status != 'COMMITTED'
        """
    )
    suspend fun countNonTerminalByInstanceOwnerAndPayloadSchema(
        instanceId: String,
        ownerType: String,
        payloadSchema: String
    ): Int

    @Query(
        """
        UPDATE cross_database_operations
        SET status = 'PENDING_CANONICAL',
            lastErrorCode = NULL,
            updatedAtMillis = :updatedAtMillis
        WHERE operationId = :operationId AND status = 'STAGED'
        """
    )
    suspend fun transitionStagedToPendingCanonical(
        operationId: String,
        updatedAtMillis: Long
    ): Int

    @Query(
        """
        UPDATE cross_database_operations
        SET canonicalEventHash = :canonicalEventHash,
            canonicalSequence = :canonicalSequence,
            canonicalProvenanceDigest = :canonicalProvenanceDigest,
            status = 'CANONICAL_COMMITTED',
            lastErrorCode = NULL,
            updatedAtMillis = :updatedAtMillis
        WHERE operationId = :operationId AND status = 'PENDING_CANONICAL'
        """
    )
    suspend fun persistCanonicalReceipt(
        operationId: String,
        canonicalEventHash: String,
        canonicalSequence: Long,
        canonicalProvenanceDigest: String,
        updatedAtMillis: Long
    ): Int

    @Query(
        """
        UPDATE cross_database_operations
        SET status = 'PENDING_LOCAL_COMMIT',
            lastErrorCode = NULL,
            updatedAtMillis = :updatedAtMillis
        WHERE operationId = :operationId AND status = 'CANONICAL_COMMITTED'
        """
    )
    suspend fun transitionCanonicalCommittedToPendingLocalCommit(
        operationId: String,
        updatedAtMillis: Long
    ): Int

    @Query(
        """
        UPDATE cross_database_operations
        SET attemptCount = attemptCount + 1,
            lastErrorCode = :lastErrorCode,
            updatedAtMillis = :updatedAtMillis
        WHERE operationId = :operationId AND status = :expectedStatus
        """
    )
    suspend fun recordRetryableFailure(
        operationId: String,
        expectedStatus: String,
        lastErrorCode: String,
        updatedAtMillis: Long
    ): Int

    @Query(
        """
        UPDATE cross_database_operations
        SET status = 'BLOCKED',
            attemptCount = attemptCount + 1,
            lastErrorCode = :lastErrorCode,
            updatedAtMillis = :updatedAtMillis
        WHERE operationId = :operationId
          AND status NOT IN ('COMMITTED', 'BLOCKED')
        """
    )
    suspend fun markBlocked(
        operationId: String,
        lastErrorCode: String,
        updatedAtMillis: Long
    ): Int

    @Query(
        """
        UPDATE cross_database_operations
        SET localResultSchema = :localResultSchema,
            localResultJson = :localResultJson,
            localResultDigest = :localResultDigest,
            status = 'COMMITTED',
            lastErrorCode = NULL,
            updatedAtMillis = :updatedAtMillis,
            committedAtMillis = :committedAtMillis
        WHERE operationId = :operationId AND status = 'PENDING_LOCAL_COMMIT'
        """
    )
    suspend fun markCommittedWithLocalResult(
        operationId: String,
        localResultSchema: String,
        localResultJson: String,
        localResultDigest: String,
        updatedAtMillis: Long,
        committedAtMillis: Long
    ): Int
}
