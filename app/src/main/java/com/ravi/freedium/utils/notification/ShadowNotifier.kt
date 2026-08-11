package com.ravi.freedium.utils.notification

import com.ravi.freedium.utils.log.FreediumLog

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.ravi.freedium.R
import com.ravi.freedium.ui.ReaderActivity

/**
 * Posts Freedium's own copy of a captured notification.
 *
 * This is the workable answer to "open Medium notifications in my app". The tap target
 * of Medium's own notification belongs to Medium and cannot be re-pointed by us, so
 * instead we mirror the notification with a contentIntent of our own. Pair it with
 * [FreediumNotificationListener]'s cancel-original option and the shade ends up with a
 * single, Freedium-owned entry for each article.
 */
object ShadowNotifier {

    private const val TAG = "ShadowNotifier"
    private const val CHANNEL_ID = "freedium_articles"

    fun post(context: Context, title: String?, text: String?, url: String) {
        if (!canPostNotifications(context)) {
            FreediumLog.w(TAG, "POST_NOTIFICATIONS not granted - skipping shadow notification")
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager, context)

        // Explicit intent so it lands in our reader regardless of who currently owns
        // medium.com links. ACTION_VIEW + data keeps it identical in shape to what a
        // browser or the Medium app would receive.
        val intent = Intent(context, ReaderActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val requestCode = url.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title ?: "Medium article")
            .setContentText(text ?: url)
            .setStyle(Notification.BigTextStyle().bigText(text ?: url))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(requestCode, notification)
        FreediumLog.d(TAG, "Posted shadow notification for $url")
    }

    private fun ensureChannel(manager: NotificationManager, context: Context) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.article_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.article_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    fun canPostNotifications(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}
