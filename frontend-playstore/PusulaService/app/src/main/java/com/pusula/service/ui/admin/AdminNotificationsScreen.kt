package com.pusula.service.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pusula.service.data.model.AdminNotificationDTO
import com.pusula.service.ui.components.AppEmptyState
import com.pusula.service.ui.components.AppGhostCard
import com.pusula.service.ui.theme.BrandCyan
import com.pusula.service.ui.theme.Spacing

@Composable
fun AdminNotificationsScreen(viewModel: AdminViewModel, onOpenTicket: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadNotifications() }
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.unreadNotificationCount == 0L) "Tüm bildirimler okundu" else "${state.unreadNotificationCount} okunmamış",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (state.unreadNotificationCount > 0) TextButton(onClick = viewModel::markAllNotificationsRead) { Text("Tümünü okundu yap") }
        }
        when {
            state.notificationsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.notifications.isEmpty() -> AppEmptyState("Bildirim yok", "Önemli operasyon hareketleri burada görünür.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm), contentPadding = PaddingValues(bottom = Spacing.xxl)) {
                items(state.notifications, key = { it.id }) { item ->
                    AppGhostCard(modifier = Modifier.fillMaxWidth(), onClick = {
                        viewModel.markNotificationRead(item) { ticketId -> ticketId?.let(onOpenTicket) }
                    }) { NotificationRow(item) }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(item: AdminNotificationDTO) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = notificationColor(item).copy(alpha = .12f), modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(notificationIcon(item), null, tint = notificationColor(item), modifier = Modifier.size(20.dp)) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (item.read) FontWeight.Medium else FontWeight.Bold))
            Text(item.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!item.read) Box(Modifier.padding(top = 6.dp).size(7.dp)) { Surface(shape = CircleShape, color = BrandCyan, modifier = Modifier.fillMaxSize()) {} }
    }
}

private fun notificationIcon(item: AdminNotificationDTO): ImageVector = when (item.category) {
    "NEW_SERVICE" -> Icons.Outlined.AddCircle
    "SERVICE_RESCHEDULED" -> Icons.Outlined.CalendarMonth
    "SERVICE_COMPLETED" -> Icons.Outlined.CheckCircle
    "CRITICAL_STOCK" -> Icons.Outlined.Inventory2
    "IMPORTANT_NOTE" -> Icons.Outlined.SpeakerNotes
    else -> Icons.Outlined.Notifications
}
private fun notificationColor(item: AdminNotificationDTO): Color = when (item.severity) {
    "CRITICAL" -> Color(0xFFDC2626); "WARNING" -> Color(0xFFF59E0B); else -> BrandCyan
}
