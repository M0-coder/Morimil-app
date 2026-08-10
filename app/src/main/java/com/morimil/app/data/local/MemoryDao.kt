package com.morimil.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Normal runtime DAO. Legacy birth, identity, memory-event and memory-snapshot
 * tables are intentionally absent from this capability surface.
 */
@Dao
interface MemoryDao {
    @Query("SELECT * FROM decision_log ORDER BY createdAtMillis DESC, id DESC")
    fun observeDecisions(): Flow<List<DecisionLogEntity>>

    @Query("SELECT COUNT(*) FROM decision_log")
    suspend fun countDecisions(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDecision(decision: DecisionLogEntity)

    @Query("SELECT * FROM project_state ORDER BY updatedAtMillis DESC")
    fun observeProjects(): Flow<List<ProjectStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectStateEntity)

    @Query("SELECT * FROM user_workspace ORDER BY updatedAtMillis DESC LIMIT 1")
    fun observeActiveWorkspace(): Flow<UserWorkspaceEntity?>

    @Query("SELECT COUNT(*) FROM user_workspace")
    suspend fun countWorkspaces(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkspace(workspace: UserWorkspaceEntity)

    @Query("UPDATE user_workspace SET displayName = :displayName, updatedAtMillis = :updatedAtMillis WHERE workspaceId = 'local_primary'")
    suspend fun renameWorkspace(displayName: String, updatedAtMillis: Long): Int

    @Query("SELECT * FROM improvement_decision_history ORDER BY decidedAtMillis DESC LIMIT :limit")
    suspend fun loadImprovementDecisionHistory(limit: Int): List<ImprovementDecisionHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertImprovementDecisionHistory(entry: ImprovementDecisionHistoryEntity)
}
