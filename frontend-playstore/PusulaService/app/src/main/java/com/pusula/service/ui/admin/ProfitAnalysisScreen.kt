package com.pusula.service.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pusula.service.core.FeatureGated
import com.pusula.service.data.model.PartProfit
import com.pusula.service.ui.components.AppDashboardSection
import com.pusula.service.ui.components.AppEmptyState
import com.pusula.service.ui.components.AppGhostCard
import com.pusula.service.ui.components.AppHeroCard
import com.pusula.service.ui.components.AppIconBadge
import com.pusula.service.ui.components.AppMiniMetricChip
import com.pusula.service.ui.theme.AccentCyan
import com.pusula.service.ui.theme.AccentPurple
import com.pusula.service.ui.theme.BlueSecondary
import com.pusula.service.ui.theme.ErrorTone
import com.pusula.service.ui.theme.Info
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.ui.theme.Success
import java.util.Locale

private fun money2(d: Double?): String =
    String.format(Locale.getDefault(), "₺%.2f", d ?: 0.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfitAnalysisScreen(viewModel: AdminViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val analysis = uiState.profitAnalysis
    val kpis = uiState.kpis

    FeatureGated(
        sessionManager = viewModel.sessionManager,
        featureKey = "FINANCE_MODULE",
        showUpgradeHint = true
    ) {
        Scaffold(topBar = { TopAppBar(title = { Text("Kâr Analizi") }) }) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.md,
                        bottom = Spacing.xxl
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl)
                ) {
                    item {
                        AppHeroCard(
                            eyebrow = "Bu ay",
                            title = money2(kpis.netProfit),
                            subtitle = "Net kâr • %${kpis.profitMargin?.toInt() ?: 0} marj",
                            badge = "Dashboard ile aynı veri kaynağı",
                            gradient = if ((kpis.netProfit ?: 0.0) >= 0) {
                                listOf(Success, AccentCyan)
                            } else {
                                listOf(ErrorTone, ErrorTone.copy(alpha = 0.7f))
                            }
                        )
                    }

                    item {
                        AppDashboardSection(title = "Parça kârlılığı") {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                                ) {
                                    AppMiniMetricChip(
                                        icon = Icons.Outlined.Receipt,
                                        label = "COGS",
                                        value = money2(analysis.totalCostOfGoodsSold),
                                        tint = ErrorTone,
                                        modifier = Modifier.weight(1f)
                                    )
                                    AppMiniMetricChip(
                                        icon = Icons.Outlined.Paid,
                                        label = "Parça Geliri",
                                        value = money2(analysis.totalRevenueFromParts),
                                        tint = Info,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                                ) {
                                    AppMiniMetricChip(
                                        icon = Icons.Outlined.Savings,
                                        label = "Brüt Kâr",
                                        value = money2(analysis.grossProfit),
                                        tint = if ((analysis.grossProfit ?: 0.0) >= 0) Success else ErrorTone,
                                        modifier = Modifier.weight(1f)
                                    )
                                    AppMiniMetricChip(
                                        icon = Icons.Outlined.TrendingUp,
                                        label = "Brüt Marj",
                                        value = "%${String.format(Locale.US, "%.1f", analysis.grossMarginPercent ?: 0.0)}",
                                        tint = AccentPurple,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    val parts = analysis.topProfitableParts.orEmpty().take(10)
                    item {
                        AppDashboardSection(
                            title = "En kârlı parçalar",
                            subtitle = if (parts.isEmpty()) null else "Top ${parts.size}"
                        ) {
                            if (parts.isEmpty()) {
                                AppEmptyState(
                                    title = "Henüz parça satışı yok",
                                    subtitle = "İş emirlerinde parça kullandıkça burada listelenecek.",
                                    icon = Icons.Outlined.Inventory2,
                                    tint = AccentPurple
                                )
                            }
                        }
                    }
                    if (parts.isNotEmpty()) {
                        itemsIndexed(parts, key = { idx, p -> "${p.partName}_$idx" }) { _, part ->
                            TopPartRow(part = part)
                        }
                    }
                }
                LazyLoadingOverlay(isLoading = uiState.loading)
            }
        }
    }
}

@Composable
private fun TopPartRow(part: PartProfit) {
    val margin = (part.marginPercent ?: 0.0).toInt()
    val tint: Color = when {
        margin >= 30 -> Success
        margin >= 15 -> AccentPurple
        else -> BlueSecondary
    }
    AppGhostCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            AppIconBadge(icon = Icons.Outlined.Inventory2, tint = tint)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = part.partName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1
                )
                Text(
                    text = "Alış ${money2(part.buyPrice)} → Satış ${money2(part.sellPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                part.quantitySold?.let { q ->
                    Text(
                        text = "Adet: $q",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = money2(part.totalProfit),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Success
                )
                Text(
                    text = "%$margin",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
