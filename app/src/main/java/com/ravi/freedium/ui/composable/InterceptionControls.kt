package com.ravi.freedium.ui.composable

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ravi.freedium.utils.links.CustomTabs
import com.ravi.freedium.utils.links.LinkHandling
import com.ravi.freedium.utils.notification.PendingIntentProbe
import com.ravi.freedium.utils.notification.ShadowNotifier
import com.ravi.freedium.utils.notification.UrlExtractor
import com.ravi.freedium.utils.prefs.FreediumPrefs

/**
 * Shows who currently owns medium.com links and lets the two interception behaviours be
 * flipped at runtime. Re-reads the system state every time the screen resumes, so you
 * can change something in Settings and come straight back to see the effect.
 */
@Composable
fun LinkHandlingCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }

    LifecycleResumeEffect(Unit) {
        refreshKey++
        onPauseOrDispose { }
    }

    val status = remember(refreshKey) { LinkHandling.status(context) }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Link handling", style = MaterialTheme.typography.titleMedium)

            Text(
                text = if (status.handlesAnyMediumLink) {
                    "Freedium is currently opening Medium links."
                } else {
                    "Medium links are not coming here yet."
                },
                style = MaterialTheme.typography.bodyMedium
            )

            if (!status.linkHandlingAllowed) {
                Text(
                    text = "\"Open supported links\" is off for Freedium.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            status.hosts.forEach { host ->
                Text(
                    text = "${host.host}: ${host.state}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (status.mediumAppInstalled) {
                Text(
                    text = "The Medium app is installed and is domain-verified for " +
                            "medium.com. A verified app outranks one you picked by hand, so " +
                            "turn off its link handling too.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            TextButton(onClick = { LinkHandling.openByDefaultSettings(context) }) {
                Text("Open \"Open by default\" settings")
            }
        }
    }
}

/**
 * Catches the other half of the manual flow: if you used Medium's "copy link" instead of
 * sharing to Freedium, the URL is sitting on the clipboard when you come back here.
 *
 * Clipboard reads are restricted to the foreground app since Android 10, which is exactly
 * what this is - and why the system will flash its "Freedium pasted from your clipboard"
 * toast when it fires.
 */
@Composable
fun ClipboardLinkCard(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var dismissed by remember { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        refreshKey++
        onPauseOrDispose { }
    }

    val url = remember(refreshKey) {
        UrlExtractor.firstUrlIn(clipboard.getText()?.text)
    }

    if (url == null || url == dismissed) return

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Link on your clipboard", style = MaterialTheme.typography.titleMedium)
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onOpen(url) }) { Text("Read it here") }
                TextButton(onClick = { dismissed = url }) { Text("Dismiss") }
            }
        }
    }
}

@Composable
fun InterceptionCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shadowEnabled by FreediumPrefs.shadowEnabled.collectAsStateWithLifecycle()
    val cancelOriginal by FreediumPrefs.cancelOriginalEnabled.collectAsStateWithLifecycle()
    val autoProbe by FreediumPrefs.autoProbeEnabled.collectAsStateWithLifecycle()
    val captureAll by FreediumPrefs.captureAllPackages.collectAsStateWithLifecycle()
    val secureScreen by FreediumPrefs.secureScreen.collectAsStateWithLifecycle()
    val customTabsAvailable = remember { CustomTabs.isAvailable(context) }

    var canPost by remember { mutableStateOf(ShadowNotifier.canPostNotifications(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> canPost = granted }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Notification interception", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Android ${android.os.Build.VERSION.RELEASE} - automatic recovery " +
                        if (PendingIntentProbe.canProbeSilently) {
                            "runs the moment a Medium notification arrives."
                        } else {
                            "is UNAVAILABLE below Android 14. Tap a capture to recover it " +
                                    "by hand instead; Medium may flash open for a moment."
                        },
                style = MaterialTheme.typography.bodySmall,
                color = if (PendingIntentProbe.canProbeSilently) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            Text(
                text = "Medium's own notification cannot be re-pointed at this app - its " +
                        "tap target is a PendingIntent owned by Medium. Freedium mirrors it " +
                        "instead.",
                style = MaterialTheme.typography.bodySmall
            )

            ToggleRow(
                label = "Block screenshots and recents preview",
                checked = secureScreen,
                onCheckedChange = { FreediumPrefs.setSecureScreen(it) }
            )
            ToggleRow(
                label = "Capture every app (not just Medium)",
                checked = captureAll,
                onCheckedChange = { FreediumPrefs.setCaptureAllPackages(it) }
            )
            ToggleRow(
                label = "Mirror Medium notifications",
                checked = shadowEnabled,
                onCheckedChange = { FreediumPrefs.setShadowEnabled(it) }
            )
            ToggleRow(
                label = "Dismiss Medium's original",
                checked = cancelOriginal,
                enabled = shadowEnabled,
                onCheckedChange = { FreediumPrefs.setCancelOriginalEnabled(it) }
            )
            ToggleRow(
                label = "Recover links automatically",
                checked = autoProbe,
                enabled = PendingIntentProbe.canProbeSilently,
                onCheckedChange = { FreediumPrefs.setAutoProbeEnabled(it) }
            )
            Text(
                text = if (PendingIntentProbe.canProbeSilently) {
                    "Fires the notification's PendingIntent as it arrives to read the " +
                            "article URL out of it, without letting Medium open."
                } else {
                    "Automatic recovery needs Android 14+, where the probe's activity " +
                            "launch can be suppressed. Use \"Recover link\" per notification " +
                            "instead."
                },
                style = MaterialTheme.typography.bodySmall
            )

            if (!customTabsAvailable) {
                Text(
                    text = "No browser here supports Chrome Custom Tabs, which is the only " +
                            "renderer Freedium uses. Links will not open until one is " +
                            "installed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!canPost) {
                Text(
                    text = "Notification permission is required to post the mirror.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                ) {
                    Text("Grant notification permission")
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
