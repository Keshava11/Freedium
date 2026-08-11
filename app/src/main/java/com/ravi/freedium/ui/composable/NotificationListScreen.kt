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
import com.ravi.freedium.utils.links.LinkResolver
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

    // Probing fires someone else's PendingIntent, which can pull their app to the
    // foreground - so it is always confirmed first rather than done on a stray tap.
    var probeCandidate by remember { mutableStateOf<NotificationEntity?>(null) }
    var recovered by remember { mutableStateOf<Pair<String, String>?>(null) }
    var probeFailure by remember { mutableStateOf<String?>(null) }

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
                onItemClick = { url -> openAndMarkRead(url, item.id) },
                onInspect = { onNavigateToDetail(item.id) },
                onRecoverUrl = { probeCandidate = item },
                onToggleRead = { viewModel.setRead(item.id, !item.isRead) },
                onToggleFavorite = { viewModel.setFavorite(item.id, !item.isFavorite) }
            )
        }
    }

    probeCandidate?.let { candidate ->
        ProbeConfirmationDialog(
            onDismiss = { probeCandidate = null },
            onConfirm = { launchTarget ->
                probeCandidate = null
                PendingIntentProbe.probeByKey(
                    context,
                    candidate.notificationKey,
                    launchTarget
                ) { result ->
                    when (result) {
                        is ProbeResult.Found -> {
                            viewModel.setUrl(candidate.id, result.url, "probe/${result.source}")
                            viewModel.setProbeIntent(candidate.id, result.intent)
                            recovered = result.url to result.source
                        }

                        is ProbeResult.NoUrl -> {
                            // Keep the Intent even though we could not use it - it is the
                            // evidence for which key holds the post id.
                            viewModel.setProbeIntent(candidate.id, result.intent)
                            probeFailure = "The probe worked and returned Medium's Intent, " +
                                    "but no link or post id could be built from it.\n\n" +
                                    "${result.intent}\n\n" +
                                    "This is saved on the notification - open Inspect to " +
                                    "copy it."
                        }

                        is ProbeResult.NoIntent -> probeFailure = result.reason
                    }
                }
            }
        )
    }

    recovered?.let { (url, source) ->
        UrlActionSheet(
            url = url,
            source = source,
            onDismiss = { recovered = null },
            onOpen = openLink
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
private fun ProbeConfirmationDialog(onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recover the article link") },
        text = {
            Text(
                text = buildString {
                    append(
                        "Both routes fire the notification's own PendingIntent and read the " +
                                "Intent the system hands back.\n\n"
                    )
                    if (PendingIntentProbe.canProbeSilently) {
                        append(
                            "Recover here - Medium is stopped from opening. Fast, but only " +
                                    "works if the Intent carries a link or a post id.\n\n"
                        )
                    }
                    append(
                        "Open in Medium - lets Medium open the article normally. Use its " +
                                "share button and pick Freedium, or copy the link and come " +
                                "back; Freedium picks it up either way."
                    )
                },
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            if (PendingIntentProbe.canProbeSilently) {
                TextButton(onClick = { onConfirm(false) }) { Text("Recover here") }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { onConfirm(true) }) { Text("Open in Medium") }
            }
        }
    )
}

@Composable
fun NotificationItem(
    item: NotificationEntity,
    onItemClick: (String) -> Unit,
    onInspect: () -> Unit,
    onRecoverUrl: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // With a link we open the reader; without one the only useful thing to
                // show is the raw dump, so you can go find where the link is hiding.
                item.readyUrl?.let { onItemClick(it) } ?: onInspect()
            },
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
                if (item.resolvedUrl == null && LinkResolver.needsResolving(item.url)) {
                    Text(
                        text = "not resolved yet - still a /p/<id> stub",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
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
