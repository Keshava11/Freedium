package com.ravi.freedium.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ravi.freedium.store.NotificationDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class NotificationViewModel(private val dao: NotificationDao) : ViewModel() {
    // Collect the Flow and convert it into a State object the UI can track
    val notificationsState = dao.getAllNotifications()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}