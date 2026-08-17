package com.ravi.freedium.utils.notification

import com.ravi.freedium.utils.log.FreediumLog

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.service.notification.StatusBarNotification
import com.ravi.freedium.utils.links.MediumLinks

/**
 * Keeps the PendingIntents of captured notifications so they can be probed later.
 *
 * In-memory only and deliberately so - a PendingIntent is a live token, not something we
 * can serialise into Room. It survives only as long as the listener process does, so a
 * notification captured before the last process death cannot be probed.
 */
object PendingIntentRegistry {

    private val cache = LruCache<String, PendingIntent>(100)

    fun remember(sbn: StatusBarNotification) {
        val contentIntent = sbn.notification?.contentIntent ?: return
        synchronized(cache) { cache.put(sbn.key, contentIntent) }
    }

    fun get(key: String?): PendingIntent? {
        if (key == null) return null
        return synchronized(cache) { cache.get(key) }
    }

    /** Whether a notification can still be probed - drives whether the UI offers it. */
    fun has(key: String?): Boolean = get(key) != null
}

/** Outcome of a probe, so the UI can explain what happened rather than just failing. */
sealed interface ProbeResult {
    data class Found(val url: String, val source: String, val intent: String) : ProbeResult
    data class NoUrl(val intent: String) : ProbeResult
    data class NoIntent(val reason: String) : ProbeResult
}

/**
 * Recovers the article URL by firing Medium's own PendingIntent and reading the Intent
 * that comes back.
 *
 * The trick: [PendingIntent.send] takes an [PendingIntent.OnFinished] callback, and the
 * system hands that callback the Intent it actually dispatched - component, data URI and
 * extras included. So the PendingIntent is opaque to inspection but not to observation.
 * This is the automated equivalent of opening the post in Medium and hitting Share.
 *
 * The obvious cost is that sending it normally launches Medium. On Android 14+ we can
 * suppress that with MODE_BACKGROUND_ACTIVITY_START_DENIED and still get the callback,
 * which makes the recovery invisible. Below 14 there is no such control and the Medium
 * app will come to the foreground - the caller is expected to warn about this first.
 *
 * Whether the callback still fires when the launch is denied is exactly the sort of thing
 * that varies by OEM and version, so [ProbeResult] distinguishes "no Intent came back"
 * from "an Intent came back but had no URL in it".
 */
object PendingIntentProbe {

    private const val TAG = "PendingIntentProbe"
    private const val TIMEOUT_MS = 3_000L

    /** True when this device can probe without Medium visibly taking over the screen. */
    val canProbeSilently: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /**
     * [launchTarget] = false reads the Intent without letting the owning app open (needs
     * Android 14+). Setting it true deliberately lets Medium come to the foreground - the
     * "open it in Medium, then share back" route, which still captures the Intent on the
     * way past.
     */
    fun probe(
        context: Context,
        pendingIntent: PendingIntent,
        launchTarget: Boolean = false,
        onResult: (ProbeResult) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        var settled = false

        fun settle(result: ProbeResult) {
            if (settled) return
            settled = true
            handler.post { onResult(result) }
        }

        val options = if (canProbeSilently && !launchTarget) {
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_DENIED
                )
                .toBundle()
        } else {
            null
        }

        val onFinished = PendingIntent.OnFinished { _, intent, _, _, _ ->
            // FIRST, before anything reads the extras: grab the raw parcel strings.
            // Touching keySet() on a Bundle full of another app's Parcelable classes makes
            // Android empty it, and the payload is gone for good.
            val rawStrings = MediumLinks.rawStringsFrom(intent)

            val flattened = runCatching { intent?.toUri(0) }.getOrNull() ?: "$intent"
            val diagnostic = buildString {
                appendLine(flattened)
                if (rawStrings.isNotEmpty()) {
                    appendLine()
                    appendLine("--- strings recovered from raw extras (${rawStrings.size}) ---")
                    rawStrings.forEach { appendLine(it) }
                }
            }
            FreediumLog.d(TAG, "probe returned: $diagnostic")

            // Normal extraction first; fall back to the raw strings when the Bundle was
            // emptied by a failed unparcel.
            // Guarded: reading another app's extras can throw BadParcelableException
            // outright on some versions instead of quietly emptying the Bundle. Either
            // way the raw-string fallback still has to run.
            val extracted = runCatching { UrlExtractor.extractFrom(intent) }.getOrNull()
                ?: MediumLinks.resolveFromStrings(rawStrings, "rawExtras")
                    ?.let { ExtractedUrl(it.url, it.source) }

            settle(
                if (extracted != null) {
                    ProbeResult.Found(extracted.url, extracted.source, diagnostic)
                } else {
                    ProbeResult.NoUrl(diagnostic)
                }
            )
        }

        try {
            pendingIntent.send(context, 0, null, onFinished, handler, null, options)
        } catch (e: PendingIntent.CanceledException) {
            settle(ProbeResult.NoIntent("PendingIntent was cancelled by ${pendingIntent.creatorPackage}"))
            return
        }

        // The callback is not guaranteed - if the system drops the send (background
        // activity launch blocked outright, for instance) nothing ever comes back.
        handler.postDelayed({
            settle(ProbeResult.NoIntent("No callback within ${TIMEOUT_MS}ms"))
        }, TIMEOUT_MS)
    }

    /** Convenience: probe whatever we stored for this notification key. */
    fun probeByKey(
        context: Context,
        notificationKey: String?,
        launchTarget: Boolean = false,
        onResult: (ProbeResult) -> Unit
    ) {
        val pendingIntent = PendingIntentRegistry.get(notificationKey)
        if (pendingIntent == null) {
            onResult(
                ProbeResult.NoIntent(
                    "Freedium is no longer holding this notification's PendingIntent, so " +
                            "there is nothing to probe. These live in memory only, so they " +
                            "are lost whenever the app's process restarts - including on " +
                            "reinstall. Trigger a fresh Medium notification and probe that one."
                )
            )
            return
        }
        probe(context, pendingIntent, launchTarget, onResult)
    }
}
