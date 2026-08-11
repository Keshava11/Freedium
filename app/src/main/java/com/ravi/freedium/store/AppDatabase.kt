package com.ravi.freedium.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ravi.freedium.utils.log.FreediumLog
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [NotificationEntity::class, CleanupLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
    abstract fun cleanupLogDao(): CleanupLogDao

    companion object {

        private const val TAG = "AppDatabase"
        private const val DB_NAME = "freedium_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }
        }

        private fun build(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")

            // Captured notifications are other people's content, so the file is encrypted
            // at rest with a key sealed by the Android Keystore. See DatabaseKey.
            val factory = SupportOpenHelperFactory(DatabaseKey.getOrCreate(context))

            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                // Captured notifications are disposable test data - a schema change just
                // wipes and re-captures rather than needing a Migration.
                .fallbackToDestructiveMigration()
                .build()
                .also { FreediumLog.d(TAG, "Opened encrypted database") }
        }
    }
}
