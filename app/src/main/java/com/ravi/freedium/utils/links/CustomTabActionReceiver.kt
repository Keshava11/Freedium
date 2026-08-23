package com.ravi.freedium.utils.links

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.ravi.freedium.utils.log.FreediumLog

/**
 * Handles the custom items Freedium adds to the Chrome Custom Tab overflow menu.
 *
 * Chrome sends our PendingIntent when the item is tapped, filling in the **current page
 * URL as the Intent data** - which is the only way to learn what the tab is showing. That
 * fill-in is also why the PendingIntent must be created FLAG_MUTABLE; an immutable one
 * would arrive with no URL at all.
 *
 * Note there is no reload command in the Custom Tabs protocol. Re-launching the same URL
 * is the practical equivalent, and is what "Reload" does here.
 */
class CustomTabActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CustomTabAction"

        const val ACTION_RELOAD = "com.ravi.freedium.action.RELOAD_TAB"

        /** Menu label, kept here so the builder and the handler cannot drift apart. */
        const val RELOAD_LABEL = "Free It!"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RELOAD) return

        // Chrome puts the currently displayed URL in the data field.
        val url = intent.dataString
        FreediumLog.d(TAG, "reload requested for $url")

        if (url.isNullOrBlank()) {
            Toast.makeText(context, "Nothing to reload", Toast.LENGTH_SHORT).show()
            return
        }

        // Shown before the relaunch, and deliberately not deferred: once onReceive
        // returns, the app is a cached background process and Android 14's freezer can
        // drop anything posted for later.
        //
        // Note this toast is invisible unless notifications are enabled for Freedium.
        // Android gates toasts on areNotificationsEnabled() and drops them silently with
        // only a log line - "Suppressing toast from package ... by user request" - which
        // is exactly what happens on a fresh install before POST_NOTIFICATIONS is granted.
        Toast.makeText(context, "Page was freed", Toast.LENGTH_SHORT).show()

        // updating the url and freeing it
        val freeUrl = "https://freedium-mirror.cfd/$url"
        val opened = CustomTabs.open(context, freeUrl)
        if (opened != OpenResult.Opened) {
            Toast.makeText(context, CustomTabs.describe(opened), Toast.LENGTH_LONG).show()
        }
    }
}
