package com.ravi.freedium.utils.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.ravi.freedium.store.AppDatabase
import com.ravi.freedium.store.NotificationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FreediumNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val MEDIUM_PACKAGE_NAME = "com.medium.reader"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        val packageName = sbn?.packageName
//        if (packageName != MEDIUM_PACKAGE_NAME) {
//            return
//        }

        val notification = sbn?.notification
        val extras = notification?.extras
        val title = extras?.getString("android.title")
        val text = extras?.getCharSequence("android.text")?.toString()
        val url = extras?.getString("android.url")

        Log.d("FreediumNotificationListener", "Medium notification posted: $title - $text - $url")

        if (title != null || text != null) {
            val entity = NotificationEntity(
                packageName = packageName,
                title = title,
                text = text,
                url = url
            )

            serviceScope.launch {
                val db = AppDatabase.getDatabase(applicationContext)
                db.notificationDao().insert(entity)
                Log.d("FreediumNotificationListener", "Medium notification saved to database")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        Log.d("FreediumNotificationListener", "Notification removed.")
    }
}
