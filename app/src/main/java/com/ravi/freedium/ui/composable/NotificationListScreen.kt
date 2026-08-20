package com.ravi.freedium.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ravi.freedium.store.NotificationEntity
import com.ravi.freedium.utils.datetime.formatTimestamp
import com.ravi.freedium.utils.links.CustomTabs
import com.ravi.freedium.utils.links.OpenResult
import com.ravi.freedium.utils.notification.PendingIntentProbe
import com.ravi.freedium.utils.notification.PendingIntentRegistry
import com.ravi.freedium.utils.notification.ProbeResult
import com.ravi.freedium.viewmodel.NotificationViewModel


@Composable
fun NotificationListScreen(
    viewModel: NotificationViewModel,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToCleanupLog: () -> Unit,
    modifier: Modifier = Modifier
) {

    val items by viewModel.notificationsState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Every link in this screen goes to a Chrome Custom Tab; there is no in-app renderer.
    var openFailure by remember { mutableStateOf<String?>(null) }
    val openLink: (String) -> Unit = { url ->
        val result = CustomTabs.open(context, url)
        if (result != OpenResult.Opened) openFailure = CustomTabs.describe(result)
    }

    // Opening an article is the clearest possible signal that it has been read.
    val openAndMarkRead: (String, Long) -> Unit = { url, id ->
        if (id > 0) viewModel.setRead(id, true)
        openLink(url)
    }

    var probeCandidate by remember { mutableStateOf<NotificationEntity?>(null) }
    var probeFailure by remember { mutableStateOf<String?>(null) }

    // Recover the link and go straight to the Custom Tab. Runs from the foreground, where
    // firing someone else's PendingIntent is permitted without restriction.
    val recoverAndOpen: (NotificationEntity) -> Unit = { item ->
        PendingIntentProbe.probeByKey(context, item.notificationKey) { result ->
            when (result) {
                is ProbeResult.Found -> {
                    viewModel.setUrl(item.id, result.url, "probe/${result.source}")
                    viewModel.setProbeIntent(item.id, result.intent)
                    viewModel.setRead(item.id, true)
                    openLink(result.url)
                }

                is ProbeResult.NoUrl -> {
                    viewModel.setProbeIntent(item.id, result.intent)
                    probeFailure = "Medium's Intent came back, but it carries no link and " +
                            "no post id we could recognise.\n\n" + result.intent
                }

                is ProbeResult.NoIntent -> probeFailure = result.reason
            }
        }
    }

    /**
     * Tapping a capture must always try to end up in a Custom Tab. Previously a row with
     * no URL just opened the raw Intent dump, which is why tapping appeared to do nothing
     * useful - it never attempted recovery at all.
     */
    val onRowTap: (NotificationEntity) -> Unit = { item ->
        val ready = item.readyUrl
        when {
            ready != null -> {
                viewModel.setRead(item.id, true)
                openLink(ready)
            }

            !PendingIntentRegistry.has(item.notificationKey) -> {
                probeFailure = "This capture has no link, and Freedium is no longer holding " +
                        "its PendingIntent - those live in memory only and die with the app " +
                        "process. Nothing can be recovered from it now. Trigger a fresh " +
                        "Medium notification and tap that one."
            }

            // Silent probe available: just do it, no confirmation friction.
            PendingIntentProbe.canProbeSilently -> recoverAndOpen(item)

            // Otherwise Medium may flash open, so ask first.
            else -> probeCandidate = item
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { ClipboardLinkCard(onOpen = openLink) }
        item { LinkHandlingCard() }
        item { InterceptionCard() }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Captured (${items.size}) - ${items.count { !it.isRead }} unread",
                    style = MaterialTheme.typography.titleMedium
                )
                Row {
                    TextButton(onClick = onNavigateToCleanupLog) { Text("Sweep log") }
                    TextButton(onClick = { viewModel.clearAll() }) { Text("Clear") }
                }
            }
        }

        // show items from the database
        items(items = items, key = { it.id }) { item ->
            NotificationItem(
                item = item,
                onOpen = { onRowTap(item) },
                onInspect = { onNavigateToDetail(item.id) },
                onRecoverUrl = { onRowTap(item) },
                onToggleRead = { viewModel.setRead(item.id, !item.isRead) },
                onToggleFavorite = { viewModel.setFavorite(item.id, !item.isFavorite) }
            )
        }
    }

    probeCandidate?.let { candidate ->
        ProbeConfirmationDialog(
            onDismiss = { probeCandidate = null },
            onConfirm = {
                probeCandidate = null
                recoverAndOpen(candidate)
            }
        )
    }

    openFailure?.let { message ->
        AlertDialog(
            onDismissRequest = { openFailure = null },
            title = { Text("Can't open this link") },
            text = { Text(text = message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { openFailure = null }) { Text("OK") }
            }
        )
    }

    probeFailure?.let { message ->
        AlertDialog(
            onDismissRequest = { probeFailure = null },
            title = { Text("No link recovered") },
            text = {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { probeFailure = null }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun ProbeConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recover the article link") },
        text = {
            Text(
                text = buildString {
                    append(
                        "Freedium fires the notification's own PendingIntent and reads the " +
                                "Intent the system hands back, which is where the article id " +
                                "lives. The link then opens in a Chrome Custom Tab.\n\n"
                    )
                    append(
                        if (PendingIntentProbe.canProbeSilently) {
                            "Medium is stopped from opening."
                        } else {
                            "This Android version cannot suppress the launch, so Medium may " +
                                    "flash open for a moment."
                        }
                    )
                },
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Recover") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun NotificationItem(
    item: NotificationEntity,
    onOpen: () -> Unit,
    onInspect: () -> Unit,
    onRecoverUrl: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // Always aims at a Custom Tab: opens the link if we have one, otherwise
            // recovers it first. It never silently dead-ends in the raw dump.
            .clickable(onClick = onOpen),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title ?: "No Title",
                    style = MaterialTheme.typography.titleMedium,
                    // Unread stands out; read fades back.
                    fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.Bold,
                    color = if (item.isRead) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (item.isFavorite) "Remove favourite" else "Mark favourite",
                        tint = if (item.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            Text(text = item.text ?: "", style = MaterialTheme.typography.bodyMedium)

            Text(
                text = item.packageName ?: "unknown package",
                style = MaterialTheme.typography.labelSmall
            )

            if (item.url != null) {
                Text(
                    text = "${item.readyUrl}  (${item.urlSource})",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = if (PendingIntentRegistry.has(item.notificationKey)) {
                        "No URL in the notification - its PendingIntent can still be probed"
                    } else {
                        "No URL, and the PendingIntent is gone (lost on process restart)"
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    TextButton(onClick = onToggleRead) {
                        Text(if (item.isRead) "Mark unread" else "Mark read")
                    }
                    TextButton(onClick = onInspect) { Text("Inspect") }
                    if (item.url == null && PendingIntentRegistry.has(item.notificationKey)) {
                        TextButton(onClick = onRecoverUrl) { Text("Recover link") }
                    }
                }
                Text(
                    text = formatTimestamp(item.timestamp),
                    textAlign = TextAlign.End,          // android:gravity="end"
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
