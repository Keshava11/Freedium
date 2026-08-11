package com.ravi.freedium.utils.notification

import android.app.Notification
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.ravi.freedium.utils.links.MediumLinks

/** A URL recovered from a notification, along with where it was found. */
data class ExtractedUrl(val url: String, val source: String)

/**
 * Digs an article link out of a posted notification, or out of an Intent recovered from
 * one.
 *
 * There is no contract that says where a link lives. `android.url` (which the first
 * version of this app relied on) is really meant for a content URI and Medium does not
 * appear to set it, so we probe likely keys, then brute-force every extra, then the same
 * again over each notification action and the public version of the notification.
 *
 * If all of that comes up empty the link simply is not in the notification, and the only
 * way to it is through the PendingIntent - see [PendingIntentProbe].
 */
object UrlExtractor {

    private val URL_REGEX = Regex("""https?://[^\s"'<>)\]}\\]+""")

    /** Probed in order; first hit wins, so keep the most link-like keys at the top. */
    private val PREFERRED_KEYS = listOf(
        "android.url",
        Notification.EXTRA_BIG_TEXT,      // android.bigText
        Notification.EXTRA_TEXT,          // android.text
        Notification.EXTRA_SUB_TEXT,      // android.subText
        Notification.EXTRA_SUMMARY_TEXT,  // android.summaryText
        Notification.EXTRA_INFO_TEXT,     // android.infoText
        Notification.EXTRA_TITLE,         // android.title
        Intent.EXTRA_TEXT                 // android.intent.extra.TEXT
    )

    /** Pulls the first http(s) URL out of arbitrary text. */
    fun firstUrlIn(text: String?): String? {
        if (text == null) return null
        return URL_REGEX.find(text)?.value?.trimEnd('.', ',', ')')
    }

    /** Scans a whole notification: its extras, its actions, and its public version. */
    fun extractFrom(notification: Notification?): ExtractedUrl? {
        if (notification == null) return null

        scanBundle(notification.extras, "extras")?.let { return it }

        notification.actions?.forEachIndexed { index, action ->
            val label = "action[$index] \"${action.title}\""
            scanBundle(action.extras, label)?.let { return it }
        }

        scanBundle(notification.publicVersion?.extras, "publicVersion")?.let { return it }

        firstUrlIn(notification.tickerText?.toString())?.let {
            return ExtractedUrl(it, "tickerText")
        }

        // No plain http link anywhere. Medium may still have left a deep link or a bare
        // post id behind, which is just as good - see MediumLinks.
        MediumLinks.resolveFromBundle(notification.extras, "extras")?.let {
            return ExtractedUrl(it.url, it.source)
        }

        return null
    }

    /**
     * Scans an Intent recovered from a PendingIntent. This is the highest-value source:
     * whatever Medium's notification would have opened is described here exactly.
     */
    fun extractFrom(intent: Intent?): ExtractedUrl? {
        val resolved = MediumLinks.resolve(intent) ?: return null
        return ExtractedUrl(resolved.url, resolved.source)
    }

    private fun scanBundle(extras: Bundle?, label: String): ExtractedUrl? {
        if (extras == null) return null

        for (key in PREFERRED_KEYS) {
            findUrl(extras, key, label)?.let { return it }
        }
        for (key in extras.keySet().orEmpty()) {
            if (key in PREFERRED_KEYS) continue
            findUrl(extras, key, label)?.let { return it }
        }
        return null
    }

    private fun findUrl(extras: Bundle, key: String, label: String): ExtractedUrl? {
        val url = firstUrlIn(valueOf(extras, key)) ?: return null
        return ExtractedUrl(url, "$label[$key]")
    }

    /**
     * Renders a bundle value as text so it can be regex-scanned and shown in the UI.
     * [Bundle.get] is deprecated but it is the only way to read a bundle whose value
     * types we do not know in advance - which is the whole point here.
     */
    @Suppress("DEPRECATION")
    private fun valueOf(extras: Bundle, key: String): String? =
        runCatching { extras.get(key)?.toString() }.getOrNull()

    /**
     * A human-readable dump of the notification: every extra, every action, and what
     * little the platform lets us learn about each PendingIntent without firing it.
     */
    fun dump(sbn: StatusBarNotification): String {
        val notification = sbn.notification

        return buildString {
            appendLine("package=${sbn.packageName}")
            appendLine("key=${sbn.key}")
            appendLine("postTime=${sbn.postTime}")
            appendLine("ongoing=${sbn.isOngoing} clearable=${sbn.isClearable}")
            appendLine("channel=${notification?.channelId}")
            appendLine("category=${notification?.category}")
            appendLine("tickerText=${notification?.tickerText}")

            appendLine("--- contentIntent ---")
            appendPendingIntent(notification?.contentIntent)

            val actions = notification?.actions
            appendLine("--- actions (${actions?.size ?: 0}) ---")
            actions?.forEachIndexed { index, action ->
                appendLine("[$index] title=${action.title}")
                appendPendingIntent(action.actionIntent)
                action.extras?.keySet()?.sorted()?.forEach { key ->
                    appendLine("    $key = ${valueOf(action.extras, key)?.take(300)}")
                }
            }

            val extras = notification?.extras
            appendLine("--- extras (${extras?.keySet()?.size ?: 0}) ---")
            extras?.keySet()?.sorted()?.forEach { key ->
                appendLine("$key = ${valueOf(extras, key)?.take(500)}")
            }

            notification?.publicVersion?.extras?.let { public ->
                appendLine("--- publicVersion extras (${public.keySet().size}) ---")
                public.keySet().sorted().forEach { key ->
                    appendLine("$key = ${valueOf(public, key)?.take(300)}")
                }
            }
        }
    }

    private fun StringBuilder.appendPendingIntent(pendingIntent: android.app.PendingIntent?) {
        if (pendingIntent == null) {
            appendLine("    (none)")
            return
        }
        appendLine("    creatorPackage=${pendingIntent.creatorPackage}")
        appendLine("    isActivity=${pendingIntent.isActivity}")
        appendLine("    toString=$pendingIntent")
    }

    /** True for medium.com and its publication subdomains. */
    fun isMediumUrl(url: String?): Boolean {
        if (url == null) return false
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return false
        return host == "medium.com" || host.endsWith(".medium.com")
    }
}
