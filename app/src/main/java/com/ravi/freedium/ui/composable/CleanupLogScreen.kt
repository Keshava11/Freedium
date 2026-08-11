package com.ravi.freedium.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ravi.freedium.store.CleanupLogEntity
import com.ravi.freedium.store.CleanupStatus
import com.ravi.freedium.utils.datetime.formatTimestamp
import com.ravi.freedium.viewmodel.NotificationViewModel
import com.ravi.freedium.work.CleanupWorker
import com.ravi.freedium.work.CleanupScheduler

/**
 * The audit trail for the weekly retention sweep, plus a way to run it on demand rather
 * than waiting until 2am to find out whether it works.
 */
@Composable
fun CleanupLogScreen(
    viewModel: NotificationViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val entries by (viewModel.cleanupLog?.collectAsStateWithLifecycle()
        ?: return EmptyLog(modifier))

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Weekly retention sweep", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Every 7 days, around 2am: deletes captures older than " +
                                "${CleanupWorker.RETENTION_DAYS} days unless they are favourited. " +
                                "WorkManager batches background work, so the run lands in the " +
                                "first maintenance window at or after 2am rather than exactly on it.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { CleanupScheduler.runNow(context) }) {
                        Text("Run sweep now")
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    text = "No sweeps have run yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        items(items = entries, key = { it.id }) { entry -> CleanupLogRow(entry) }
    }
}

@Composable
private fun CleanupLogRow(entry: CleanupLogEntity) {
    val failed = entry.status == CleanupStatus.FAILED

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.status.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Text(
                    text = formatTimestamp(entry.runAt),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Text(
                text = "${entry.deletedCount} deleted in ${entry.durationMs}ms",
                style = MaterialTheme.typography.bodySmall
            )
            entry.message?.let {
                Text(text = it, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun EmptyLog(modifier: Modifier = Modifier) {
    Text(
        text = "Cleanup log unavailable.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.padding(16.dp)
    )
}
