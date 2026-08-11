package com.ravi.freedium.ui.composable

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Context
import android.widget.Toast
import com.ravi.freedium.utils.links.CustomTabs
import com.ravi.freedium.utils.links.OpenResult
import com.ravi.freedium.viewmodel.NotificationViewModel

/**
 * The investigation screen: everything the notification carried, verbatim.
 *
 * This is how you find out where Medium puts the article link. Read the dump, spot the
 * key holding the URL, then add it to UrlExtractor.PREFERRED_KEYS.
 */
@Composable
fun NotificationDetailScreen(
    viewModel: NotificationViewModel,
    id: Long,
    modifier: Modifier = Modifier
) {
    val item by viewModel.notification(id).collectAsStateWithLifecycle(initialValue = null)
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val entity = item

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (entity == null) {
            Text("Notification not found.", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        Text(entity.title ?: "No Title", style = MaterialTheme.typography.titleLarge)
        Text(entity.text ?: "", style = MaterialTheme.typography.bodyMedium)

        entity.readyUrl?.let { url ->
            TextButton(onClick = { openInCustomTab(context, url) }) {
                Text("Open in Custom Tab")
            }
        }

        TextButton(
            onClick = {
                clipboard.setText(AnnotatedString(entity.rawExtras.orEmpty()))
            }
        ) {
            Text("Copy raw dump")
        }

        entity.probeIntent?.let { probed ->
            Text("Recovered Intent", style = MaterialTheme.typography.titleMedium)
            Text(
                text = probed,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = { clipboard.setText(AnnotatedString(probed)) }) {
                Text("Copy recovered Intent")
            }
        }

        Text("Raw notification", style = MaterialTheme.typography.titleMedium)
        Text(
            text = entity.rawExtras ?: "(nothing captured)",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        )
    }
}

private fun openInCustomTab(context: Context, url: String) {
    val result = CustomTabs.open(context, url)
    if (result != OpenResult.Opened) {
        Toast.makeText(context, CustomTabs.describe(result), Toast.LENGTH_LONG).show()
    }
}
