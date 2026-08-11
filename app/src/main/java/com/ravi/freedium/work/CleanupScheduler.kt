package com.ravi.freedium.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ravi.freedium.utils.log.FreediumLog
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules the weekly retention sweep for roughly 2am.
 *
 * "Roughly" is the honest word. WorkManager is not an alarm clock: it batches work and
 * respects Doze, so the sweep runs in the first maintenance window at or after 2am rather
 * than on the stroke of it. Getting exact timing would mean AlarmManager with
 * SCHEDULE_EXACT_ALARM, which is a heavyweight permission to burn on deleting old rows -
 * nothing here cares whether it happens at 02:00 or 06:00.
 */
object CleanupScheduler {

    private const val TAG = "CleanupScheduler"
    private const val WORK_NAME = "freedium_weekly_cleanup"
    private const val TARGET_HOUR = 2

    fun schedule(context: Context) {
        val initialDelay = millisUntilNextRun()

        val request = PeriodicWorkRequestBuilder<CleanupWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    // Housekeeping should never be the reason a battery dies overnight.
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            // KEEP so re-opening the app does not endlessly push the next run back.
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )

        FreediumLog.d(TAG, "Weekly sweep scheduled, first run in ${initialDelay / 60_000} min")
    }

    /** Runs the sweep immediately, for checking it works without waiting a week. */
    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<CleanupWorker>().build()
        )
        FreediumLog.d(TAG, "Manual sweep enqueued")
    }

    /** Milliseconds from now until the next 2am. */
    private fun millisUntilNextRun(): Long {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, TARGET_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }
}
