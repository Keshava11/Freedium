package com.ravi.freedium.utils.links

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/** Per-host verdict for one of the hosts declared in our ACTION_VIEW intent filter. */
data class HostState(val host: String, val state: String, val handled: Boolean)

data class LinkStatus(
    /** False when the user has switched off "Open supported links" for Freedium entirely. */
    val linkHandlingAllowed: Boolean,
    val hosts: List<HostState>,
    val mediumAppInstalled: Boolean
) {
    /** True once at least one medium.com host actually routes here. */
    val handlesAnyMediumLink: Boolean
        get() = linkHandlingAllowed && hosts.any { it.handled }
}

/**
 * Reads and surfaces the "who owns medium.com links" state.
 *
 * We can never be *verified* for medium.com - Digital Asset Links verification requires
 * publishing a file on the domain, which only Medium can do. The only route to owning
 * these links is DOMAIN_STATE_SELECTED: the user approving Freedium by hand under
 * "Open by default". And because a verified app outranks a selected one, the Medium
 * app's own link handling has to be turned off for the override to actually bite.
 */
object LinkHandling {

    fun status(context: Context): LinkStatus {
        val manager = context.getSystemService(DomainVerificationManager::class.java)
        val userState = runCatching {
            manager?.getDomainVerificationUserState(context.packageName)
        }.getOrNull()

        val hosts = userState?.hostToStateMap.orEmpty()
            .toSortedMap()
            .map { (host, state) ->
                HostState(
                    host = host,
                    state = describe(state),
                    handled = state == DomainVerificationUserState.DOMAIN_STATE_SELECTED ||
                            state == DomainVerificationUserState.DOMAIN_STATE_VERIFIED
                )
            }

        return LinkStatus(
            linkHandlingAllowed = userState?.isLinkHandlingAllowed ?: false,
            hosts = hosts,
            mediumAppInstalled = isInstalled(context, "com.medium.reader")
        )
    }

    private fun describe(state: Int): String = when (state) {
        DomainVerificationUserState.DOMAIN_STATE_VERIFIED -> "verified (app owns the domain)"
        DomainVerificationUserState.DOMAIN_STATE_SELECTED -> "selected by you - links come here"
        DomainVerificationUserState.DOMAIN_STATE_NONE -> "not approved - links go to the browser"
        else -> "unknown ($state)"
    }

    private fun isInstalled(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    /** Settings > Apps > Freedium > Open by default. */
    fun openByDefaultSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            // Fall back to the app's detail page if the OEM lacks the dedicated screen.
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Standard share sheet for the URL. */
    fun share(context: Context, url: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(
            Intent.createChooser(send, "Share link").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Hands the URL to some *other* app. Once Freedium is the selected handler a plain
     * ACTION_VIEW would resolve straight back to us, so we enumerate the candidates and
     * drop ourselves from the list before building the chooser.
     */
    fun openExternally(context: Context, url: String) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)

        val targets = context.packageManager
            .queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { Intent(view).setPackage(it) }

        if (targets.isEmpty()) {
            Toast.makeText(context, "No other app can open this link", Toast.LENGTH_SHORT).show()
            return
        }

        val chooser = Intent.createChooser(targets.first(), "Open with").apply {
            if (targets.size > 1) {
                putExtra(
                    Intent.EXTRA_INITIAL_INTENTS,
                    targets.drop(1).toTypedArray()
                )
            }
        }
        context.startActivity(chooser)
    }
}
