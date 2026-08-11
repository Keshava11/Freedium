package com.ravi.freedium.ui

import com.ravi.freedium.utils.log.FreediumLog

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.ravi.freedium.ui.theme.FreediumTheme
import com.ravi.freedium.utils.links.CustomTabs
import com.ravi.freedium.utils.links.LinkHandling
import com.ravi.freedium.utils.links.OpenResult
import com.ravi.freedium.utils.notification.UrlExtractor

/**
 * The entry point that makes Freedium a viewer for Medium links.
 *
 * It is reachable three ways, and all of them matter:
 *  1. From our shadow notification, as an explicit intent - always works.
 *  2. From an external ACTION_VIEW on a medium.com URL - only once the user has approved
 *     Freedium under "Open by default" (see the manifest for why).
 *  3. From Medium's share sheet as an ACTION_SEND target.
 *
 * There is no in-app renderer any more: this is a trampoline that hands the link to a
 * Chrome Custom Tab and gets out of the way. It only draws anything when that is not
 * possible, in which case it explains why rather than silently doing something else.
 */
class ReaderActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ReaderActivity"

        /** Optional plain-string alternative to putting the link in the intent data. */
        const val EXTRA_URL = "com.ravi.freedium.extra.URL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    /**
     * launchMode is singleTop, so once this activity is showing an error it stays on top
     * and every further link arrives here rather than through onCreate. Without this the
     * error screen would swallow them silently.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        logIncoming(intent)

        val url = urlFrom(intent)
        if (url == null) {
            showError(null, "This screen expects an intent carrying a URL, but none was found.")
            return
        }

        when (val result = CustomTabs.open(this, url)) {
            OpenResult.Opened -> {
                // The Custom Tab owns the screen now; nothing left for us to show.
                finish()
            }

            else -> showError(url, CustomTabs.describe(result))
        }
    }

    private fun showError(url: String?, message: String) {
        enableEdgeToEdge()
        setContent {
            FreediumTheme {
                CannotOpenScreen(url = url, message = message, onDismiss = { finish() })
            }
        }
    }

    /**
     * Handles all three ways a link reaches this screen. The ACTION_SEND payload is free
     * text ("Title https://medium.com/..."), so the URL has to be picked out of it.
     */
    private fun urlFrom(intent: Intent?): String? {
        if (intent == null) return null

        intent.getStringExtra(EXTRA_URL)?.let { return it }

        if (intent.action == Intent.ACTION_SEND) {
            return UrlExtractor.firstUrlIn(intent.getStringExtra(Intent.EXTRA_TEXT))
                ?: UrlExtractor.extractFrom(intent)?.url
        }

        return intent.data?.toString()
    }

    private fun logIncoming(intent: Intent?) {
        FreediumLog.d(
            TAG,
            "incoming action=${intent?.action} data=${intent?.data} from=${referrer?.host}"
        )
    }
}

@Composable
private fun CannotOpenScreen(url: String?, message: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = "Can't open this link",
            style = MaterialTheme.typography.titleLarge
        )
        Text(text = message, style = MaterialTheme.typography.bodyMedium)

        if (url != null) {
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = { clipboard.setText(AnnotatedString(url)) }) {
                Text("Copy link")
            }
            TextButton(onClick = { LinkHandling.openExternally(context, url) }) {
                Text("Pick another app")
            }
        }

        Button(onClick = onDismiss) { Text("Close") }
    }
}
