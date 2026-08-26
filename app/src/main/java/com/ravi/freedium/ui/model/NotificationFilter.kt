package com.ravi.freedium.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.ui.graphics.vector.ImageVector
import com.ravi.freedium.store.NotificationEntity

/**
 * The top-level filters on the home screen.
 *
 * [NeedsAttention] is the important one: captures whose link was never recovered are not
 * articles you can read, so they are kept out of every other filter rather than sitting in
 * the main list looking like broken entries.
 */
enum class NotificationFilter(
    val label: String,
    val icon: ImageVector
) {
    All("All", Icons.Outlined.AutoAwesome),
    Unread("Unread", Icons.Outlined.MarkEmailUnread),
    Favourites("Favourites", Icons.Outlined.Favorite),
    NeedsAttention("Needs attention", Icons.Outlined.ReportProblem);

    fun matches(item: NotificationEntity): Boolean = when (this) {
        // A capture with no link is not readable, so it only ever appears under
        // NeedsAttention - never in All, Unread or Favourites.
        All -> item.readyUrl != null
        Unread -> item.readyUrl != null && !item.isRead
        Favourites -> item.readyUrl != null && item.isFavorite
        NeedsAttention -> item.readyUrl == null
    }

    /** Message shown when this filter has nothing to show. */
    val emptyMessage: String
        get() = when (this) {
            All -> "No articles captured yet.\nMedium notifications will appear here."
            Unread -> "Nothing unread. You're all caught up."
            Favourites -> "No favourites yet.\nTap the heart on an article to keep it."
            NeedsAttention -> "Nothing needs attention."
        }
}
