package com.ravi.freedium.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ravi.freedium.store.NotificationEntity

/**
 * The long-press menu for a captured article.
 *
 * Everything that is not "read this" lives here. Inspect in particular is a diagnostic,
 * not a daily action, so it sits behind a deliberate gesture rather than occupying a
 * button on every card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemActionSheet(
    item: NotificationEntity,
    onDismiss: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleFavourite: () -> Unit,
    onOpenExternally: () -> Unit,
    onInspect: () -> Unit,
    onDelete: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val url = item.readyUrl

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 28.dp)) {

            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                Text(
                    text = item.title ?: "Untitled capture",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (url != null) {
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SheetRow(
                icon = if (item.isRead) Icons.Outlined.MarkEmailUnread else Icons.Outlined.MarkEmailRead,
                label = if (item.isRead) "Mark as unread" else "Mark as read"
            ) {
                onToggleRead()
                onDismiss()
            }

            SheetRow(
                icon = if (item.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label = if (item.isFavorite) "Remove from favourites" else "Add to favourites",
                tint = if (item.isFavorite) MaterialTheme.colorScheme.tertiary else null
            ) {
                onToggleFavourite()
                onDismiss()
            }

            if (url != null) {
                SheetRow(icon = Icons.Outlined.ContentCopy, label = "Copy link") {
                    clipboard.setText(AnnotatedString(url))
                    onDismiss()
                }
                SheetRow(icon = Icons.Outlined.OpenInBrowser, label = "Open in another app") {
                    onOpenExternally()
                    onDismiss()
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SheetRow(icon = Icons.Outlined.BugReport, label = "Inspect raw notification") {
                onInspect()
                onDismiss()
            }

            SheetRow(
                icon = Icons.Outlined.DeleteOutline,
                label = "Delete capture",
                tint = MaterialTheme.colorScheme.error
            ) {
                onDelete()
                onDismiss()
            }
        }
    }
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    label: String,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val contentColour = tint ?: MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColour,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColour
        )
    }
}
