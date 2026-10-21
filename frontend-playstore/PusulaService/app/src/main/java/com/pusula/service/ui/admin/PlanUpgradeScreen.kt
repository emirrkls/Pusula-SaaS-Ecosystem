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
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import com.pusula.service.ui.components.AppTopBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pusula.service.ui.components.AppHeroCard
import com.pusula.service.ui.theme.BrandCyan
import com.pusula.service.ui.theme.BrandNavy
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.ui.theme.Success
import com.pusula.service.data.model.PlanDTO
import com.pusula.service.core.BillingPeriod
import com.pusula.service.core.PlayPlanOption
import com.pusula.service.core.PlayBillingUiState
import com.pusula.service.core.PlanSubscriptionPolicy
import com.pusula.service.core.SessionState

private data class PlanCardData(
    val code: String,
    val title: String,
    val tagline: String,
    val features: List<String>,
    val accent: List<Color>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanUpgradeScreen(
    onBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val session by viewModel.sessionManager.state.collectAsState()
    val billing by viewModel.billingState.collectAsState()
    val guide by com.pusula.service.core.GuideReadOnlyGate.active.collectAsState()
    val uriHandler = LocalUriHandler.current
    var period by rememberSaveable { mutableStateOf(BillingPeriod.MONTHLY) }
    var linkError by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(context) {
        if(!com.pusula.service.core.GuideReadOnlyGate.active.value) (context as? android.app.Activity)?.let(viewModel::bindBillingActivity)
        viewModel.loadPlans()
    }
    val plans = uiState.plans
        .filter { it.name != "CIRAK" }

    BackHandler(onBack = onBack)

    Scaffold(topBar = { AppTopBar(title = "Paket Yükselt", onBack = onBack) }) { padding ->
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
                        eyebrow = if (session.isReadOnly) "Son paket" else "Mevcut paket",
                        title = session.planType.ifBlank { "CIRAK" } + " paketi",
                        subtitle = if (session.isReadOnly) "Hesabınız yalnızca görüntüleme modunda. Uygun bir paket seçebilirsiniz."
                            else if (session.trialDaysRemaining != null) "Deneme erişiminiz ücretli abonelik değildir."
                            else "Daha fazla özellik için yükseltin",
                        badge = session.trialDaysRemaining?.let { "$it gün deneme kaldı" }
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
                        BillingPeriod.entries.forEach { choice ->
                            FilterChip(selected = period == choice, onClick = { period = choice },
                                label = { Text(choice.title) }, modifier = Modifier.weight(1f), enabled = !billing.busy)
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        if (billing.loading) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Text("Google Play fiyatları yükleniyor…")
                        }
                        if (billing.busy) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Text("İşleminiz kontrol ediliyor. Yeni bir satın alma başlatmayın.")
                        }
                        (billing.error ?: uiState.error)?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        billing.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (billing.server.reason=="another_provider_active") Text("Etkin paketiniz başka bir ödeme kanalıyla yönetiliyor. Çift ödeme oluşmaması için Google Play'den yeni abonelik başlatılamaz.")
                        if (billing.server.reason=="account_restricted") Text("Hesabınız için satın alma şu anda kapalı. Destek ile iletişime geçin.")
                        if (billing.server.reason=="server_not_ready") Text("Satın alma doğrulama sistemi henüz etkin değil. İşlem başlatılamaz.")
                        TextButton(onClick = { viewModel.retryBillingProducts() }, enabled = !guide && !billing.loading && !billing.busy) {
                            Text("Fiyatları Yenile")
                        }
                    }
                }
                items(plans, key = { it.name }) { plan ->
                    PlanSubscriptionCard(plan, session, billing, period, guide,
                        onPick = { viewModel.purchasePlan(plan.name, period) })
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("Seçilen tutar ve dönem Google Play'den alınır. Abonelik iptal edilmedikçe seçtiğiniz dönemle otomatik yenilenir. Yenilemeyi Google Play abonelik ayarlarından durdurabilirsiniz. Hesabınızı silmek aboneliği iptal etmez.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Paket düşürmede ödenmiş dönemin sonuna kadar mevcut haklarınız korunur. Yükseltmede kalan süre Google tarafından hesaba katılır. Dönem değişimi sonraki ödeme tarihinde uygulanır; kesin tutar ve tarih Google Play onay ekranında gösterilir.",
                            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        billing.server.scheduledPlan?.let { Text("Planlanmış paket: $it. Google Play abonelik ayarlarından yönetebilirsiniz.") }
                        TextButton(onClick = { viewModel.restorePurchases() }, enabled = !guide && !billing.busy) {
                            Text("Satın Alımları Geri Yükle")
                        }
                        TextButton(onClick = {
                            linkError = runCatching { uriHandler.openUri("https://play.google.com/store/account/subscriptions?package=com.pusula.service") }.isFailure
                        }, enabled = !guide) { Text("Google Play Aboneliğini Yönet") }
                        if (linkError) Text("Google Play abonelik ayarları açılamadı.", color = MaterialTheme.colorScheme.error)
                        com.pusula.service.ui.components.ProductLegalLinks()
                    }
                }
            }
            LazyLoadingOverlay(isLoading = uiState.loading)
        }
    }
}

/** Stateless production card, also exercised with offline instrumentation fixtures. */
@Composable
internal fun PlanSubscriptionCard(plan: PlanDTO, session: SessionState, billing: PlayBillingUiState,
                                  period: BillingPeriod, guide: Boolean = false, onPick: () -> Unit) {
    val option = billing.options.singleOrNull { it.productId.equals(plan.name, true) && it.period == period }
    val state = PlanSubscriptionPolicy.present(session, billing, plan.name, option, guide)
    PlanRow(toPlanCardData(plan), state.isCurrent, option, state.canBuy, state.actionTitle, onPick)
}

private fun toPlanCardData(plan: PlanDTO): PlanCardData {
    val patron = plan.name == "PATRON"
    val features = buildList {
        plan.maxCompanyAdmins?.let { add("$it şirket yöneticisi") }
        plan.maxTechnicians?.let { add("$it aktif teknisyen") }
        plan.maxCustomers?.let { add("$it müşteri") }
        plan.maxMonthlyTickets?.let { add("Ayda $it servis fişi") }
        plan.maxMonthlyProposals?.takeIf { it > 0 }?.let { add("Ayda $it teklif") }
        plan.maxInventoryItems?.let { add("$it envanter kalemi") }
        plan.storageLimitMb?.let {
            add(if (it >= 1024) "${it / 1024} GB depolama" else "$it MB depolama")
        }
    }
    return PlanCardData(
        code = plan.name,
        title = plan.displayName.removeSuffix(" Paketi"),
        tagline = if (patron) "Kapsamlı operasyonlar" else "Büyüyen ekipler",
        features = features,
        accent = if (patron) {
            listOf(BrandNavy, BrandNavy.copy(alpha = 0.65f))
        } else {
            listOf(BrandNavy, BrandCyan.copy(alpha = 0.75f))
        }
    )
}

@Composable
private fun PlanRow(plan: PlanCardData, isCurrent: Boolean, option: PlayPlanOption?, canBuy: Boolean, actionTitle: String, onPick: () -> Unit) {
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
                Text(option?.let { "${it.formattedPrice} ${it.period.suffix}" } ?: "Bu dönem için fiyat alınamadı",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = if (option == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
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
                    enabled = !isCurrent && option != null && canBuy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("plan.purchase.${plan.code.lowercase(java.util.Locale.ROOT)}")
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
                        text = if (isCurrent) "Aktif Paket" else if (option == null) "Satın Alma Kullanılamıyor" else actionTitle,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
