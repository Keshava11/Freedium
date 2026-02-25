package com.ravi.freedium.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ravi.freedium.store.NotificationEntity
import com.ravi.freedium.utils.datetime.formatTimestamp
import com.ravi.freedium.viewmodel.NotificationViewModel


@Composable
fun NotificationListScreen(viewModel: NotificationViewModel, onNavigateToWeb: (String) -> Unit) {

    val items by viewModel.notificationsState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxWidth()
        //.padding(12.dp)
        , contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // show items from the database
        items(items = items, key = { it.id }) { item ->
            NotificationItem(
                item = item, onItemClick = { url -> onNavigateToWeb(url) })
        }
    }
}

@Composable
fun NotificationItem(item: NotificationEntity, onItemClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // on click run the lambda with url param
                item.url?.let { onItemClick(it) }
            }, elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)

    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = item.title ?: "No Title", style = MaterialTheme.typography.titleMedium)
            Text(text = item.text ?: "", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = formatTimestamp(item.timestamp),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,          // android:gravity="end"
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}




