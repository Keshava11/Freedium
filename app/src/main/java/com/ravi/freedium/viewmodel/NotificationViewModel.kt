package com.ravi.freedium.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ravi.freedium.store.CleanupLogDao
import com.ravi.freedium.store.NotificationDao
import com.ravi.freedium.store.NotificationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val dao: NotificationDao,
    private val cleanupLogDao: CleanupLogDao? = null
) : ViewModel() {
    // Collect the Flow and convert it into a State object the UI can track
    val notificationsState = dao.getAllNotifications()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun notification(id: Long): Flow<NotificationEntity?> = dao.getById(id)

    /** Audit trail of the weekly retention sweep; empty flow when no dao was supplied. */
    val cleanupLog = cleanupLogDao?.recent(50)
        ?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setRead(id: Long, isRead: Boolean) {
        viewModelScope.launch { dao.setRead(id, isRead) }
    }

    fun setFavorite(id: Long, isFavorite: Boolean) {
        viewModelScope.launch { dao.setFavorite(id, isFavorite) }
    }

    /** Stores a URL recovered from a notification's PendingIntent. */
    fun setUrl(id: Long, url: String, source: String) {
        viewModelScope.launch { dao.setUrl(id, url, source) }
    }

    /** Stores a canonical URL discovered for this notification. */
    fun setResolvedUrl(id: Long, resolvedUrl: String) {
        viewModelScope.launch { dao.setResolvedUrl(id, resolvedUrl) }
    }

    fun setProbeIntent(id: Long, intent: String) {
        viewModelScope.launch { dao.setProbeIntent(id, intent) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { dao.delete(id) }
    }

    fun clearAll() {
        viewModelScope.launch { dao.clearAll() }
    }
}
