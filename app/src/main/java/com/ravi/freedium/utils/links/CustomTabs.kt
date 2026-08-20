package com.ravi.freedium.utils.links

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

/** Why a link could not be opened, so callers can say something useful. */
sealed interface OpenResult {
    data object Opened : OpenResult
    data object NoProvider : OpenResult
    data class Failed(val reason: String) : OpenResult
}

/**
 * Opens links in a Chrome Custom Tab - the only renderer this app uses.
 *
 * Custom Tabs rather than an embedded WebView because medium.com sits behind Cloudflare:
 * an embedded WebView gets served "verify you are human" challenges, while the real
 * browser carries the user's profile, cookies and Medium session and simply renders the
 * article.
 *
 * The wrinkle: a CustomTabsIntent is just an ACTION_VIEW with extras. Once Freedium is the
 * selected handler for medium.com, launching one without naming a package resolves
 * straight back into Freedium - an infinite round trip. So a custom-tabs-capable browser
 * is always resolved first and targeted explicitly.
 */
object CustomTabs {

    /** The browser that will host our custom tabs, or null if none supports them. */
    fun providerPackage(context: Context): String? {
        // Never let this pick us, even if Freedium matches the query.
        val candidates = browserPackages(context).filter { it != context.packageName }
        return CustomTabsClient.getPackageName(context, candidates, false)
            ?: CustomTabsClient.getPackageName(context, null)
    }

    fun isAvailable(context: Context): Boolean = providerPackage(context) != null

    /**
     * Deliberately does not fall back to some other app. Custom Tabs is the whole reading
     * experience now, so when it is unavailable the caller must say so rather than quietly
     * handing the user off somewhere else.
     */
    fun open(context: Context, url: String): OpenResult {
        val provider = providerPackage(context) ?: return OpenResult.NoProvider

        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .addMenuItem(CustomTabActionReceiver.RELOAD_LABEL, reloadPendingIntent(context))
            .build()

        customTabsIntent.intent.setPackage(provider)
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return runCatching {
            customTabsIntent.launchUrl(context, Uri.parse(url))
            OpenResult.Opened
        }.getOrElse { error ->
            OpenResult.Failed(error.message ?: error::class.java.simpleName)
        }
    }

    /** Human-readable explanation for an [OpenResult] that is not [OpenResult.Opened]. */
    fun describe(result: OpenResult): String = when (result) {
        OpenResult.Opened -> ""
        OpenResult.NoProvider ->
            "No browser on this device supports Chrome Custom Tabs, which is the only " +
                    "renderer Freedium uses. Install Chrome (or another browser that " +
                    "implements Custom Tabs) and try again."

        is OpenResult.Failed ->
            "The Custom Tab could not be launched: ${result.reason}"
    }

    /**
     * The PendingIntent behind our overflow menu item.
     *
     * FLAG_MUTABLE is required, not optional: Chrome fills in the current page URL as the
     * Intent's data before sending it, and an immutable PendingIntent would arrive empty,
     * leaving the handler with nothing to reload.
     */
    private fun reloadPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, CustomTabActionReceiver::class.java)
            .setAction(CustomTabActionReceiver.ACTION_RELOAD)

        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private fun browserPackages(context: Context): List<String> {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return runCatching {
            context.packageManager.queryIntentActivities(probe, 0)
                .map { it.activityInfo.packageName }
                .distinct()
        }.getOrDefault(emptyList())
    }
}
