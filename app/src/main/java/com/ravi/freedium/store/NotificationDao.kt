package com.ravi.freedium.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    /** Returns the new row id so a URL recovered afterwards can be backfilled onto it. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity): Long

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE id = :id")
    fun getById(id: Long): Flow<NotificationEntity?>

    /** Backfills a URL recovered after capture, e.g. by probing the PendingIntent. */
    @Query("UPDATE notifications SET url = :url, urlSource = :source WHERE id = :id")
    suspend fun setUrl(id: Long, url: String, source: String)

    /** Records the flattened Intent a probe returned, whether or not a URL came out of it. */
    @Query("UPDATE notifications SET probeIntent = :intent WHERE id = :id")
    suspend fun setProbeIntent(id: Long, intent: String)

    /** Stores the canonical article URL once the redirects have been walked. */
    @Query("UPDATE notifications SET resolvedUrl = :resolvedUrl WHERE id = :id")
    suspend fun setResolvedUrl(id: Long, resolvedUrl: String)

    @Query("UPDATE notifications SET isRead = :isRead WHERE id = :id")
    suspend fun setRead(id: Long, isRead: Boolean)

    @Query("UPDATE notifications SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    /**
     * The retention sweep. Favourites are exempt no matter how old they are - that is the
     * whole point of marking one.
     */
    @Query("DELETE FROM notifications WHERE isFavorite = 0 AND timestamp < :cutoff")
    suspend fun deleteExpired(cutoff: Long): Int

    /** Used to report what a sweep *would* remove without touching anything. */
    @Query("SELECT COUNT(*) FROM notifications WHERE isFavorite = 0 AND timestamp < :cutoff")
    suspend fun countExpired(cutoff: Long): Int

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}
