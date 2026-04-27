package com.pusula.service.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Upgrade
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pusula.service.data.model.QuotaItem
import com.pusula.service.data.model.TechnicianStat
import com.pusula.service.ui.components.AppDashboardSection
import com.pusula.service.ui.components.AppDestructiveButton
import com.pusula.service.ui.components.AppGhostCard
import com.pusula.service.ui.components.AppHeroCard
import com.pusula.service.ui.components.AppInitialsAvatar
import com.pusula.service.ui.components.AppMiniMetricChip
import com.pusula.service.ui.components.AppQuickActionTile
import com.pusula.service.ui.theme.AccentCyan
import com.pusula.service.ui.theme.AccentOrange
import com.pusula.service.ui.theme.AccentPurple
import com.pusula.service.ui.theme.BlueSecondary
import com.pusula.service.ui.theme.ErrorTone
import com.pusula.service.ui.theme.Info
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.ui.theme.Success
import com.pusula.service.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onLogout: () -> Unit,
    onOpenRadar: () -> Unit,
    onOpenProfit: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenUpgrade: () -> Unit,
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
                        AppDashboardSection(title = "Bu Ay") {
                            MainKpiGrid(state = uiState)
                        }
                    }

                    item {
                        AppDashboardSection(title = "Bugün") {
                            TodayMetricsRow(state = uiState)
                        }
                    }

                    item {
                        AppDashboardSection(title = "Hızlı Erişim") {
                            QuickActionRow(
                                onOpenRadar = onOpenRadar,
                                onOpenProfit = onOpenProfit,
                                onOpenCatalog = onOpenCatalog,
                                onOpenUpgrade = onOpenUpgrade
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
                            AppDashboardSection(
                                title = "Saha Personeli",
                                subtitle = "${uiState.technicianStats.size} aktif"
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    uiState.technicianStats.take(4).forEach { tech ->
                                        TechnicianRow(stat = tech)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        LazyLoadingOverlay(isLoading = uiState.loading)
    }
}

@Composable
private fun MainKpiGrid(state: AdminUiState) {
    val netProfit = state.kpis.netProfit ?: 0.0
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.fillMaxWidth()
        ) {
            HeroKpiCard(
                title = "Aylık Ciro",
                value = "₺${formatAmount(state.kpis.monthlyRevenue)}",
                accent = listOf(Info.copy(alpha = 0.8f), Info),
                modifier = Modifier.weight(1f)
            )
            HeroKpiCard(
                title = "Bekleyen Alacak",
                value = "₺${formatAmount(state.kpis.outstandingDebt)}",
                accent = listOf(Warning, AccentOrange),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.fillMaxWidth()
        ) {
            HeroKpiCard(
                title = "Net Kâr",
                value = "₺${formatAmount(state.kpis.netProfit)}",
                accent = if (netProfit < 0) {
                    listOf(ErrorTone, ErrorTone.copy(alpha = 0.7f))
                } else {
                    listOf(Success, AccentCyan)
                },
                modifier = Modifier.weight(1f)
            )
            HeroKpiCard(
                title = "Kâr Marjı",
                value = "%${state.kpis.profitMargin?.toInt() ?: 0}",
                accent = listOf(AccentPurple, BlueSecondary),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HeroKpiCard(
    title: String,
    value: String,
    accent: List<Color>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(124.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(accent.first().copy(alpha = 0.30f), Color.Transparent)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AutoSizeText(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxFontSize = 26.sp,
                    minFontSize = 14.sp
                )
                Box(
                    modifier = Modifier
                        .height(3.dp)
                        .fillMaxWidth(0.4f)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(accent))
                )
            }
        }
    }
}

@Composable
private fun TodayMetricsRow(state: AdminUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        AppMiniMetricChip(
            icon = Icons.Default.PlayArrow,
            label = "Aktif İş",
            value = "${state.kpis.activeTickets ?: 0}",
            tint = Info,
            modifier = Modifier.weight(1f)
        )
        AppMiniMetricChip(
            icon = Icons.Default.TaskAlt,
            label = "Bu Ay Biten",
            value = "${state.kpis.completedThisMonth ?: 0}",
            tint = Success,
            modifier = Modifier.weight(1f)
        )
        AppMiniMetricChip(
            icon = Icons.Default.Inventory2,
            label = "Envanter",
            value = "₺${formatAmount(state.kpis.inventoryValue)}",
            tint = AccentPurple,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickActionRow(
    onOpenRadar: () -> Unit,
    onOpenProfit: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenUpgrade: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        AppQuickActionTile(
            label = "Saha Radarı",
            icon = Icons.Outlined.Map,
            tint = Info,
            onClick = onOpenRadar,
            modifier = Modifier.weight(1f)
        )
        AppQuickActionTile(
            label = "Kâr Analizi",
            icon = Icons.Outlined.AutoGraph,
            tint = Success,
            onClick = onOpenProfit,
            modifier = Modifier.weight(1f)
        )
        AppQuickActionTile(
            label = "Katalog",
            icon = Icons.Outlined.Storefront,
            tint = AccentPurple,
            onClick = onOpenCatalog,
            modifier = Modifier.weight(1f)
        )
        AppQuickActionTile(
            label = "Yükselt",
            icon = Icons.Outlined.Upgrade,
            tint = AccentOrange,
            onClick = onOpenUpgrade,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuotaRow(quota: QuotaItem) {
    val progress = ((quota.usagePercent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val barColor = when {
        progress >= 0.9f -> ErrorTone
        progress >= 0.7f -> Warning
        else -> Success
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

@Composable
private fun TechnicianRow(stat: TechnicianStat) {
    AppGhostCard(
        padding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            AppInitialsAvatar(text = stat.fullName ?: "T")
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stat.fullName ?: "Teknisyen",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1
                )
                Text(
                    text = "Bugün ${stat.completedToday ?: 0} • Aktif ${stat.activeTickets ?: 0}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "₺${formatAmount(stat.collectedToday)}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Success
            )
        }
    }
}
