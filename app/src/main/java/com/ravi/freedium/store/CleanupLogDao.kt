package com.ravi.freedium.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CleanupLogDao {

    @Insert
    suspend fun insert(entry: CleanupLogEntity)

    @Query("SELECT * FROM cleanup_log ORDER BY runAt DESC LIMIT :limit")
    fun recent(limit: Int = 50): Flow<List<CleanupLogEntity>>

    /** Keeps the audit trail from growing without bound. */
    @Query("DELETE FROM cleanup_log WHERE id NOT IN (SELECT id FROM cleanup_log ORDER BY runAt DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)
}
