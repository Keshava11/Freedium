package com.ravi.freedium

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.ravi.freedium.store.AppDatabase
import com.ravi.freedium.store.CleanupStatus
import com.ravi.freedium.store.NotificationEntity
import com.ravi.freedium.work.CleanupWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Covers the weekly retention sweep. This is the one feature that deletes data on its own
 * with nobody watching, so the rules it follows are worth pinning down: old and
 * unfavourited goes, old and favourited stays, recent stays, and every run leaves an
 * audit row behind.
 */
@RunWith(AndroidJUnit4::class)
class RetentionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database by lazy { AppDatabase.getDatabase(context) }

    private val dayMs = TimeUnit.DAYS.toMillis(1)
    private val now = System.currentTimeMillis()

    private fun capture(title: String, ageDays: Long, favorite: Boolean = false) =
        NotificationEntity(
            packageName = "com.medium.reader",
            title = title,
            text = null,
            url = "https://medium.com/p/abc123def456",
            timestamp = now - ageDays * dayMs,
            isFavorite = favorite
        )

    @Before
    fun clearDatabase() = runBlocking {
        database.notificationDao().clearAll()
    }

    @Test
    fun sweepDeletesOnlyOldNonFavourites() = runBlocking {
        val dao = database.notificationDao()
        dao.insert(capture("old and ordinary", ageDays = 120))
        dao.insert(capture("old but favourited", ageDays = 120, favorite = true))
        dao.insert(capture("recent", ageDays = 10))

        val worker = TestListenableWorkerBuilder<CleanupWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        val remaining = dao.getAllNotifications().first()
        val titles = remaining.mapNotNull { it.title }.toSet()
        assertEquals(setOf("old but favourited", "recent"), titles)
    }

    @Test
    fun sweepWritesASuccessAuditRow() = runBlocking {
        val dao = database.notificationDao()
        dao.insert(capture("expired", ageDays = 200))

        TestListenableWorkerBuilder<CleanupWorker>(context).build().doWork()

        val latest = database.cleanupLogDao().recent(1).first().firstOrNull()
        requireNotNull(latest) { "sweep did not write an audit row" }
        assertEquals(CleanupStatus.SUCCESS, latest.status)
        assertTrue("expected at least one deletion, got ${latest.deletedCount}", latest.deletedCount >= 1)
        assertTrue("cutoff should be in the past", latest.cutoffTimestamp < System.currentTimeMillis())
    }

    @Test
    fun sweepWithNothingToDeleteIsStillASuccess() = runBlocking {
        database.notificationDao().insert(capture("fresh", ageDays = 1))

        val result = TestListenableWorkerBuilder<CleanupWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val latest = database.cleanupLogDao().recent(1).first().first()
        assertEquals(CleanupStatus.SUCCESS, latest.status)
        assertEquals(0, latest.deletedCount)
    }

    @Test
    fun cutoffSitsAtThreeMonths() {
        assertEquals(90L, CleanupWorker.RETENTION_DAYS)
    }

    @Test
    fun readAndFavouriteFlagsRoundTrip() = runBlocking {
        val dao = database.notificationDao()
        val id = dao.insert(capture("toggle me", ageDays = 1))

        dao.setRead(id, true)
        dao.setFavorite(id, true)
        var row = dao.getById(id).first()
        assertTrue("should be read", row!!.isRead)
        assertTrue("should be favourite", row.isFavorite)

        dao.setRead(id, false)
        dao.setFavorite(id, false)
        row = dao.getById(id).first()
        assertTrue("should be unread", !row!!.isRead)
        assertTrue("should not be favourite", !row.isFavorite)
    }
}
