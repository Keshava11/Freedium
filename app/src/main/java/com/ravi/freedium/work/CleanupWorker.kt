package com.ravi.freedium.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ravi.freedium.store.AppDatabase
import com.ravi.freedium.store.CleanupLogEntity
import com.ravi.freedium.store.CleanupStatus
import com.ravi.freedium.utils.log.FreediumLog

/**
 * The weekly retention sweep: deletes non-favourite captures older than three months.
 *
 * Every run writes an audit row, success or failure, because this deletes data unattended
 * in the middle of the night and there would otherwise be no way to tell a working sweep
 * from one that has been silently throwing for a month.
 */
class CleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CleanupWorker"

        /** Three months, the age at which an unfavourited capture is discarded. */
        const val RETENTION_DAYS = 90L
        private const val RETENTION_MS = RETENTION_DAYS * 24 * 60 * 60 * 1000

        /** How many audit rows to keep. */
        private const val LOG_HISTORY = 100
    }

    override suspend fun doWork(): Result {
        val startedAt = System.currentTimeMillis()
        val cutoff = startedAt - RETENTION_MS
        val database = AppDatabase.getDatabase(applicationContext)

        return try {
            val deleted = database.notificationDao().deleteExpired(cutoff)
            val duration = System.currentTimeMillis() - startedAt

            record(
                CleanupLogEntity(
                    runAt = startedAt,
                    status = CleanupStatus.SUCCESS,
                    deletedCount = deleted,
                    cutoffTimestamp = cutoff,
                    message = "Removed $deleted non-favourite captures older than $RETENTION_DAYS days",
                    durationMs = duration
                )
            )

            FreediumLog.d(TAG, "Sweep removed $deleted rows in ${duration}ms")
            Result.success()
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startedAt

            record(
                CleanupLogEntity(
                    runAt = startedAt,
                    status = CleanupStatus.FAILED,
                    deletedCount = 0,
                    cutoffTimestamp = cutoff,
                    message = "${e::class.java.simpleName}: ${e.message}",
                    durationMs = duration
                )
            )

            FreediumLog.e(TAG, "Retention sweep failed", e)
            // Retry rather than fail outright: a transient problem (database briefly
            // locked, say) should not mean waiting another whole week.
            Result.retry()
        }
    }

    /**
     * Writing the audit row can itself fail - if the database is what broke, this is where
     * we find out. Swallow it so a logging failure cannot mask the real result.
     */
    private suspend fun record(entry: CleanupLogEntity) {
        runCatching {
            val dao = AppDatabase.getDatabase(applicationContext).cleanupLogDao()
            dao.insert(entry)
            dao.trimTo(LOG_HISTORY)
        }.onFailure {
            FreediumLog.e(TAG, "Could not write cleanup audit row", it)
        }
    }
}
