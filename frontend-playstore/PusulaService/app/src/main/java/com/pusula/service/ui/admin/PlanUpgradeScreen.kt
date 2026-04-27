package com.pusula.service.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pusula.service.ui.components.AppHeroCard
import com.pusula.service.ui.theme.AccentCyan
import com.pusula.service.ui.theme.AccentOrange
import com.pusula.service.ui.theme.AccentPurple
import com.pusula.service.ui.theme.BlueSecondary
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.ui.theme.Success
import com.pusula.service.ui.theme.Warning

private data class PlanCardData(
    val code: String,
    val title: String,
    val tagline: String,
    val features: List<String>,
    val accent: List<Color>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanUpgradeScreen(viewModel: AdminViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val session by viewModel.sessionManager.state.collectAsState()
    LaunchedEffect(context) {
        (context as? android.app.Activity)?.let(viewModel::bindBillingActivity)
    }
    val plans = listOf(
        PlanCardData(
            code = "CIRAK",
            title = "Çırak",
            tagline = "Başlangıç",
            features = listOf("2 teknisyen", "200 müşteri", "Temel modüller"),
            accent = listOf(AccentCyan, BlueSecondary)
        ),
        PlanCardData(
            code = "USTA",
            title = "Usta",
            tagline = "Büyüyen ekipler",
            features = listOf("10 teknisyen", "2000 müşteri", "Finans modülü"),
            accent = listOf(AccentPurple, BlueSecondary)
        ),
        PlanCardData(
            code = "PATRON",
            title = "Patron",
            tagline = "Ölçek için",
            features = listOf("Sınırsız teknisyen", "Sınırsız müşteri", "Tüm premium özellikler"),
            accent = listOf(Warning, AccentOrange)
        )
    )

    Scaffold(topBar = { TopAppBar(title = { Text("Paket Yükselt") }) }) { padding ->
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
                        eyebrow = "Mevcut paket",
                        title = session.planType.ifBlank { "CIRAK" } + " paketi",
                        subtitle = "Daha fazla özellik için yükseltin",
                        badge = "${session.trialDaysRemaining ?: 0} gün deneme kaldı"
                    )
                }
                items(plans, key = { it.code }) { plan ->
                    val isCurrent = session.planType.equals(plan.code, true)
                    PlanRow(plan = plan, isCurrent = isCurrent, onPick = { viewModel.purchasePlan(plan.code) })
                }
            }
            LazyLoadingOverlay(isLoading = uiState.loading)
        }
    }
}

@Composable
private fun PlanRow(plan: PlanCardData, isCurrent: Boolean, onPick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(
            width = if (isCurrent) 2.dp else 1.dp,
            color = if (isCurrent) plan.accent.first() else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(plan.accent.first().copy(alpha = 0.20f), Color.Transparent)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(plan.accent)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WorkspacePremium,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = plan.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = plan.tagline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isCurrent) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Success.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Mevcut",
                                color = Success,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    plan.features.forEach { feature ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = plan.accent.first(),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(text = feature, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                Button(
                    onClick = onPick,
                    enabled = !isCurrent,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isCurrent) {
                                Brush.linearGradient(listOf(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
                            } else {
                                Brush.linearGradient(plan.accent)
                            }
                        )
                ) {
                    Text(
                        text = if (isCurrent) "Aktif Paket" else "Bu Pakete Geç",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
