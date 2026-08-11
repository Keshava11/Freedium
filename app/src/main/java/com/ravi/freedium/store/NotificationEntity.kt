package com.ravi.freedium.store

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String?,
    val title: String?,
    val text: String?,
    val url: String?,
    val timestamp: Long = System.currentTimeMillis(),

    /** The system's key for the posted notification, e.g. "0|com.medium.reader|1234|null|10123". */
    val notificationKey: String? = null,

    /** Where [url] was found, e.g. "extras[android.text]". Null when no URL was recoverable. */
    val urlSource: String? = null,

    /**
     * Every extra the notification carried, plus what we could learn about its
     * contentIntent. This is the raw material for figuring out where Medium hides
     * the article link - inspect it in the app, then teach [com.ravi.freedium.utils.notification.UrlExtractor]
     * the right key.
     */
    val rawExtras: String? = null,

    /**
     * The flattened Intent recovered by probing the contentIntent. Kept even when no URL
     * could be built from it - that string is the only evidence of what Medium actually
     * hands the system, and it is what tells us which key holds the post id.
     */
    val probeIntent: String? = null,

    /**
     * The canonical article URL, after following the redirects from a `/p/<postId>` stub.
     * This is the link that is actually ready to read or share.
     */
    val resolvedUrl: String? = null,

    /** Set when the article has been opened, or marked by hand from the list. */
    val isRead: Boolean = false,

    /**
     * Favourites are kept indefinitely - the weekly retention sweep skips them. This is
     * the only thing standing between a blog you care about and the 3-month cutoff.
     */
    val isFavorite: Boolean = false
) {
    /** The best link we have for this notification: canonical if resolved, else raw. */
    val readyUrl: String?
        get() = resolvedUrl ?: url
}
