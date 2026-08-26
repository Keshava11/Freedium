package com.ravi.freedium.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ravi.freedium.store.NotificationEntity
import com.ravi.freedium.ui.composable.ClipboardLinkCard
import com.ravi.freedium.ui.composable.ItemActionSheet
import com.ravi.freedium.ui.composable.NotificationCard
import com.ravi.freedium.ui.model.NotificationFilter
import com.ravi.freedium.utils.links.CustomTabs
import com.ravi.freedium.utils.links.LinkHandling
import com.ravi.freedium.utils.links.OpenResult
import com.ravi.freedium.utils.notification.PendingIntentProbe
import com.ravi.freedium.utils.notification.PendingIntentRegistry
import com.ravi.freedium.utils.notification.ProbeResult
import com.ravi.freedium.viewmodel.NotificationViewModel

/**
 * The reading surface: filter chips and the captured articles, nothing else.
 *
 * Configuration used to live at the top of this list and pushed the articles below the
 * fold; it has moved to Settings behind the gear. Everything that is not "open this
 * article" is now a long-press away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: NotificationViewModel,
    onOpenSettings: () -> Unit,
    onInspect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val items by viewModel.notificationsState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var filter by remember { mutableStateOf(NotificationFilter.All) }
    var sheetItem by remember { mutableStateOf<NotificationEntity?>(null) }
    var probeCandidate by remember { mutableStateOf<NotificationEntity?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val openLink: (String) -> Unit = { url ->
        val result = CustomTabs.open(context, url)
        if (result != OpenResult.Opened) message = CustomTabs.describe(result)
    }

    // Recovers the link, then goes straight to the Custom Tab.
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
                    message = "Medium's Intent came back, but it carries no link and no " +
                            "post id we could recognise.\n\n${result.intent}"
                }

                is ProbeResult.NoIntent -> message = result.reason
            }
        }
    }

    val onCardTap: (NotificationEntity) -> Unit = { item ->
        val ready = item.readyUrl
        when {
            ready != null -> {
                viewModel.setRead(item.id, true)
                openLink(ready)
            }

            !PendingIntentRegistry.has(item.notificationKey) ->
                message = "This capture has no link, and its PendingIntent is gone - those " +
                        "live in memory only and die with the app process. Trigger a fresh " +
                        "Medium notification and tap that one."

            PendingIntentProbe.canProbeSilently -> recoverAndOpen(item)
            else -> probeCandidate = item
        }
    }

    val visible = items.filter { filter.matches(it) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Freedium", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {

            // Self-hiding: only renders when a link is actually sitting on the clipboard,
            // so it costs Home nothing the rest of the time.
            ClipboardLinkCard(
                onOpen = openLink,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            FilterRow(
                selected = filter,
                counts = NotificationFilter.entries.associateWith { f ->
                    items.count { f.matches(it) }
                },
                onSelect = { filter = it }
            )

            if (visible.isEmpty()) {
                EmptyState(filter)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = visible, key = { it.id }) { item ->
                        NotificationCard(
                            item = item,
                            onOpen = { onCardTap(item) },
                            onLongPress = { sheetItem = item },
                            onToggleFavourite = {
                                viewModel.setFavorite(item.id, !item.isFavorite)
                            }
                        )
                    }
                }
            }
        }
    }

    sheetItem?.let { item ->
        ItemActionSheet(
            item = item,
            onDismiss = { sheetItem = null },
            onToggleRead = { viewModel.setRead(item.id, !item.isRead) },
            onToggleFavourite = { viewModel.setFavorite(item.id, !item.isFavorite) },
            onOpenExternally = { item.readyUrl?.let { LinkHandling.openExternally(context, it) } },
            onInspect = { onInspect(item.id) },
            onDelete = { viewModel.delete(item.id) }
        )
    }

    probeCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { probeCandidate = null },
            title = { Text("Recover the article link") },
            text = {
                Text(
                    "Freedium fires the notification's own PendingIntent to read the article " +
                            "id out of it. This Android version cannot suppress the launch, so " +
                            "Medium may flash open for a moment."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    probeCandidate = null
                    recoverAndOpen(candidate)
                }) { Text("Recover") }
            },
            dismissButton = {
                TextButton(onClick = { probeCandidate = null }) { Text("Cancel") }
            }
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("Couldn't open this") },
            text = {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    selected: NotificationFilter,
    counts: Map<NotificationFilter, Int>,
    onSelect: (NotificationFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NotificationFilter.entries.forEach { entry ->
            val count = counts[entry] ?: 0
            // Nothing needing attention means the chip is noise - hide it entirely.
            if (entry == NotificationFilter.NeedsAttention && count == 0) return@forEach

            FilterChip(
                selected = selected == entry,
                onClick = { onSelect(entry) },
                label = { Text(entry.label) },
                leadingIcon = {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = if (count > 0) {
                    { Badge { Text("$count") } }
                } else {
                    null
                },
                shape = MaterialTheme.shapes.large,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun EmptyState(filter: NotificationFilter) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Icon(
            imageVector = Icons.Outlined.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(56.dp)
        )
        Text(
            text = filter.emptyMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
