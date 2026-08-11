package com.ravi.freedium.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ravi.freedium.utils.links.LinkHandling
import com.ravi.freedium.utils.notification.FreediumNotificationListener

/**
 * Shown once a URL has been recovered, so the link is a decision point rather than an
 * automatic hand-off. Deliberately includes "open in Medium" - being able to compare
 * Freedium's render against the real thing is half the point of the experiment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlActionSheet(
    url: String,
    source: String?,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Recovered link", style = MaterialTheme.typography.titleMedium)
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary
            )
            if (source != null) {
                Text(
                    text = "found in $source",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            SheetAction("Open in Chrome Custom Tab") {
                onDismiss()
                onOpen(url)
            }
            SheetAction("Open in another app") {
                onDismiss()
                LinkHandling.openExternally(context, url)
            }
            SheetAction("Open in Medium app") {
                onDismiss()
                LinkHandling.openInPackage(
                    context,
                    url,
                    FreediumNotificationListener.MEDIUM_PACKAGE_NAME
                )
            }
            SheetAction("Copy link") {
                onDismiss()
                clipboard.setText(AnnotatedString(url))
            }
            SheetAction("Share") {
                onDismiss()
                LinkHandling.share(context, url)
            }
        }
    }
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    )
}
