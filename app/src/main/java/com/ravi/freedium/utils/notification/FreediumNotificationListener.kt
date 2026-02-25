package com.ravi.freedium.utils.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.ravi.freedium.store.AppDatabase
import com.ravi.freedium.store.NotificationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FreediumNotificationListener: NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        val notification = sbn?.notification
        val packageName = sbn?.packageName
        val extras = notification?.extras
        val title = extras?.getString("android.title")
        val text = extras?.getCharSequence("android.text")?.toString()

        Log.d("FreediumNotificationListener", "Notification posted: $title - $text")

        if (title != null || text != null) {
            val entity = NotificationEntity(
                packageName = packageName,
                title = title,
                text = text
            )
            
            serviceScope.launch {
                val db = AppDatabase.getDatabase(applicationContext)
                db.notificationDao().insert(entity)
                Log.d("FreediumNotificationListener", "Notification saved to database")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        Log.d("FreediumNotificationListener", "Notification removed. Nothing to be done here")
    }
}
