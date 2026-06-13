package com.pusula.service.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pusula.service.data.model.QuotaItem
import com.pusula.service.ui.components.AppDashboardSection
import com.pusula.service.ui.components.AppDestructiveButton
import com.pusula.service.ui.components.AppGhostCard
import com.pusula.service.ui.components.AppHeroCard
import com.pusula.service.ui.components.AppMiniMetricChip
import com.pusula.service.ui.theme.BrandCyan
import com.pusula.service.ui.theme.BrandNavy
import com.pusula.service.ui.theme.ErrorTone
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.ui.theme.Success
import com.pusula.service.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onLogout: () -> Unit,
    onOpenRadar: () -> Unit,
    onOpenOperationFilter: (String) -> Unit,
    viewModel: AdminViewModel = hiltViewModel(),
    embedded: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    val session by viewModel.sessionManager.state.collectAsState()

    LaunchedEffect(embedded) {
        if (!embedded) viewModel.loadDashboard()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!embedded) {
                TopAppBar(
                    title = { Text("Yönetim Paneli") },
                    actions = { AppDestructiveButton(text = "Çıkış", onClick = onLogout) }
                )
            }

            PullToRefreshBox(
                isRefreshing = uiState.refreshing,
                onRefresh = { viewModel.loadDashboard(refresh = true) }
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = if (embedded) Spacing.lg else Spacing.md,
                        bottom = Spacing.xxl
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl)
                ) {
                    item {
                        AppHeroCard(
                            eyebrow = "Merhaba",
                            title = session.fullName.ifBlank { "Yönetici" },
                            subtitle = session.companyName.orEmpty().ifBlank { null },
                            badge = "${session.planType.ifBlank { "CIRAK" }} paketi"
                        )
                    }

                    item {
                        AppDashboardSection(
                            title = "Operasyon Durumu",
                            subtitle = "Kartlara dokunarak Operasyon filtresine git"
                        ) {
                            TodayMetricsRow(
                                state = uiState,
                                onOpenOperationFilter = onOpenOperationFilter
                            )
                        }
                    }

                    val highlightedQuotas = uiState.quotaStatus.quotas
                        .orEmpty()
                        .filter { (it.usagePercent ?: 0.0) >= 60.0 }
                        .take(3)
                    if (highlightedQuotas.isNotEmpty()) {
                        item {
                            AppDashboardSection(
                                title = "Kullanım Uyarıları",
                                subtitle = "%60 ve üzeri kotalar"
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                    highlightedQuotas.forEach { quota ->
                                        QuotaRow(quota = quota)
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.technicianStats.isNotEmpty()) {
                        item {
                            FieldTeamSummaryRow(
                                technicianCount = uiState.technicianStats.size,
                                activeTickets = uiState.technicianStats.sumOf { it.activeTickets ?: 0 },
                                completedToday = uiState.technicianStats.sumOf { it.completedToday ?: 0 },
                                onOpenRadar = onOpenRadar
                            )
                        }
                    }
                }
            }
        }
        LazyLoadingOverlay(isLoading = uiState.loading)
    }
}

@Composable
private fun FieldTeamSummaryRow(
    technicianCount: Int,
    activeTickets: Int,
    completedToday: Int,
    onOpenRadar: () -> Unit
) {
    AppGhostCard(onClick = onOpenRadar) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(
                imageVector = Icons.Outlined.Groups,
                contentDescription = null,
                tint = BrandNavy,
                modifier = Modifier.size(28.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Saha ekibi",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "$technicianCount teknisyen · $activeTickets aktif iş · bugün $completedToday kapanan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Outlined.Map,
                contentDescription = "Saha Radarı",
                tint = BrandCyan,
                modifier = Modifier.size(22.dp)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TodayMetricsRow(
    state: AdminUiState,
    onOpenOperationFilter: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        AppMiniMetricChip(
            icon = Icons.Default.PlayArrow,
            label = "Yeni Açılan",
            value = "${state.kpis.openedToday ?: 0}",
            tint = BrandCyan,
            modifier = Modifier.weight(1f),
            onClick = { onOpenOperationFilter("Bugün Açılan") }
        )
        AppMiniMetricChip(
            icon = Icons.Default.TaskAlt,
            label = "Kapanan",
            value = "${state.kpis.closedToday ?: 0}",
            tint = Success,
            modifier = Modifier.weight(1f),
            onClick = { onOpenOperationFilter("Kapanan") }
        )
        AppMiniMetricChip(
            icon = Icons.Default.Inventory2,
            label = "Bekleyen",
            value = "${state.kpis.pendingNow ?: 0}",
            tint = BrandNavy,
            modifier = Modifier.weight(1f),
            onClick = { onOpenOperationFilter("Atama Bekleyen") }
        )
    }
}

@Composable
private fun QuotaRow(quota: QuotaItem) {
    val progress = ((quota.usagePercent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val barColor = when {
        progress >= 0.9f -> ErrorTone
        progress >= 0.7f -> Warning
        else -> BrandCyan
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = quota.featureLabel ?: quota.featureKey,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${quota.currentUsage ?: 0} / ${quota.limit ?: 0}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50)),
            color = barColor,
            trackColor = barColor.copy(alpha = 0.15f)
        )
    }
}
