package com.ravi.freedium.utils.notification

import com.ravi.freedium.utils.log.FreediumLog

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ravi.freedium.store.AppDatabase
import com.ravi.freedium.store.NotificationEntity
import com.ravi.freedium.utils.links.LinkResolver
import com.ravi.freedium.utils.prefs.FreediumPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FreediumNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "FreediumListener"
        const val MEDIUM_PACKAGE_NAME = "com.medium.reader"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        FreediumPrefs.init(applicationContext)
        FreediumLog.d(TAG, "Listener connected - now receiving every posted notification")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // Our own shadow notification comes straight back through this callback.
        // Without this guard we would mirror our mirror, forever.
        if (sbn.packageName == packageName) return

        FreediumPrefs.init(applicationContext)

        // Package filter. Only Medium is of interest; capturing everything was useful
        // while working out where links hide, but it buries the list in noise.
        if (sbn.packageName != MEDIUM_PACKAGE_NAME && !FreediumPrefs.captureAllPackages.value) {
            return
        }

        // Hold on to the contentIntent so its URL can be recovered on demand later.
        // This is the only handle we ever get on it - it cannot be persisted.
        PendingIntentRegistry.remember(sbn)

        val extras = sbn.notification?.extras
        val title = extras?.getString("android.title")
        val text = extras?.getCharSequence("android.text")?.toString()
        val extracted = UrlExtractor.extractFrom(sbn.notification)
        val dump = UrlExtractor.dump(sbn)

        FreediumLog.d(TAG, "posted: ${sbn.packageName} | $title | url=${extracted?.url} (${extracted?.source})")

        // Keep anything worth showing OR worth probing. Notifications built from custom
        // RemoteViews carry no android.title/android.text at all, yet still hold a
        // contentIntent - dropping those would discard exactly the cases where recovering
        // the link from the PendingIntent is the only option left.
        val probeable = sbn.notification?.contentIntent != null
        if (title == null && text == null && extracted == null && !probeable) return

        val entity = NotificationEntity(
            packageName = sbn.packageName,
            title = title,
            text = text,
            url = extracted?.url,
            notificationKey = sbn.key,
            urlSource = extracted?.source,
            rawExtras = dump
        )

        serviceScope.launch {
            val dao = AppDatabase.getDatabase(applicationContext).notificationDao()
            val id = dao.insert(entity)
            FreediumLog.d(TAG, "Saved notification from ${sbn.packageName} as row $id")

            // The link usually is not in the notification. When allowed, go get it from
            // the contentIntent before deciding whether we can mirror this one.
            var url = extracted?.url
            if (url == null && shouldAutoProbe(sbn)) {
                when (val result = autoProbe(sbn)) {
                    is ProbeResult.Found -> {
                        url = result.url
                        dao.setUrl(id, result.url, "autoProbe/${result.source}")
                        dao.setProbeIntent(id, result.intent)
                        FreediumLog.d(TAG, "Auto-probe recovered ${result.url}")
                    }

                    is ProbeResult.NoUrl -> {
                        // Keep the Intent regardless. In a release build there is no
                        // logging, so this row is the only evidence of what Medium sent
                        // and the only way to teach MediumLinks the right key.
                        dao.setProbeIntent(id, result.intent)
                        FreediumLog.w(TAG, "Auto-probe got an Intent but no link: ${result.intent}")
                    }

                    is ProbeResult.NoIntent -> {
                        dao.setProbeIntent(id, "no Intent recovered: ${result.reason}")
                        FreediumLog.w(TAG, "Auto-probe failed: ${result.reason}")
                    }
                }
            }

            // Walk the redirects now so the row holds the real article link by the time
            // the notification is tapped, rather than a /p/<postId> stub.
            var readyUrl = url
            if (LinkResolver.needsResolving(url)) {
                val canonical = LinkResolver.resolve(url!!)
                if (canonical != url) {
                    dao.setResolvedUrl(id, canonical)
                    readyUrl = canonical
                    FreediumLog.d(TAG, "Resolved $url -> $canonical")
                }
            }

            maybeIntercept(sbn, title, text, readyUrl)
        }
    }

    /**
     * Only ever true where the probe's activity launch can be suppressed. Without that
     * guarantee an automatic probe would drag the Medium app to the foreground every time
     * a notification landed, which is precisely what this app exists to avoid.
     */
    private fun shouldAutoProbe(sbn: StatusBarNotification): Boolean =
        sbn.packageName == MEDIUM_PACKAGE_NAME &&
                FreediumPrefs.autoProbeEnabled.value &&
                PendingIntentProbe.canProbeSilently

    private suspend fun autoProbe(sbn: StatusBarNotification): ProbeResult =
        suspendCancellableCoroutine { continuation ->
            PendingIntentProbe.probeByKey(applicationContext, sbn.key) { result ->
                if (continuation.isActive) continuation.resume(result)
            }
        }

    /**
     * The interception step. We cannot re-point the tap target of Medium's notification -
     * its contentIntent is a PendingIntent owned by Medium and the system fires it
     * directly. What we can do is post an equivalent notification whose contentIntent
     * belongs to us, and optionally dismiss Medium's so only ours is left in the shade.
     */
    private fun maybeIntercept(
        sbn: StatusBarNotification,
        title: String?,
        text: String?,
        url: String?
    ) {
        if (sbn.packageName != MEDIUM_PACKAGE_NAME) return
        if (!FreediumPrefs.shadowEnabled.value) return

        if (url == null) {
            // The link is not in the notification at all. It is still reachable by
            // probing the contentIntent - the user does that from the list screen,
            // because probing can bring the Medium app to the foreground.
            FreediumLog.w(TAG, "Medium notification had no URL in it - probe the contentIntent to recover")
            return
        }

        ShadowNotifier.post(applicationContext, title, text, url)

        if (FreediumPrefs.cancelOriginalEnabled.value) {
            cancelNotification(sbn.key)
            FreediumLog.d(TAG, "Cancelled Medium's original notification ${sbn.key}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        FreediumLog.d(TAG, "removed: ${sbn?.packageName}")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
