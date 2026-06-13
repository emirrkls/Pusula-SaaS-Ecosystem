package com.pusula.service.ui.finance

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import com.pusula.service.data.model.DailyTotalDTO
import com.pusula.service.data.model.CurrentAccountDTO
import com.pusula.service.data.model.ExpenseItemDTO
import com.pusula.service.data.model.FixedExpenseDefinitionDTO
import com.pusula.service.ui.components.AppDashboardSection
import com.pusula.service.ui.components.AppPickerField
import com.pusula.service.ui.components.AppEmptyState
import com.pusula.service.ui.components.AppGhostCard
import com.pusula.service.ui.components.AppIconBadge
import com.pusula.service.ui.components.AppInitialsAvatar
import com.pusula.service.ui.components.AppPrimaryButton
import com.pusula.service.ui.components.AppTextField
import com.pusula.service.ui.theme.AccentPurple
import com.pusula.service.ui.theme.ErrorTone
import com.pusula.service.ui.theme.Info
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.ui.theme.Success
import com.pusula.service.ui.theme.Warning
import com.pusula.service.util.FinancePdfHelper
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val expenseCategories = listOf("RENT", "SALARY", "BILLS", "FUEL", "FOOD", "OTHER")
private val financeTabs = listOf("Günlük", "Analiz", "Cari", "Rapor")
private val expenseCategoryOptions = listOf(
    "RENT" to "Kira",
    "SALARY" to "Maaş",
    "BILLS" to "Faturalar",
    "FUEL" to "Yakıt",
    "FOOD" to "Yemek",
    "OTHER" to "Diğer"
)

private fun fixedExpenseTargetAmount(item: FixedExpenseDefinitionDTO): Double =
    item.defaultAmount ?: 0.0

private fun fixedExpensePaidAmount(item: FixedExpenseDefinitionDTO): Double =
    item.paidAmountThisMonth ?: 0.0

private fun fixedExpenseRemainingAmount(item: FixedExpenseDefinitionDTO): Double =
    (fixedExpenseTargetAmount(item) - fixedExpensePaidAmount(item)).coerceAtLeast(0.0)

private fun formatFixedDueSummary(item: FixedExpenseDefinitionDTO): String {
    val freq = item.frequency?.uppercase(Locale.US) ?: "MONTHLY"
    return when (freq) {
        "WEEKLY" -> "Haftalık ödeme"
        else -> {
            val d = item.dayOfMonth
            if (d != null && d in 1..31) "Her ayın $d. günü"
            else "Aylık ödeme"
        }
    }
}

private fun fixedExpenseOptionLabel(item: FixedExpenseDefinitionDTO): String =
    buildString {
        append(item.name.orEmpty().ifBlank { "Sabit gider" })
        append(" · kalan ₺${formatMoney(fixedExpenseRemainingAmount(item))}")
    }

private data class FinanceHeaderInfo(
    val eyebrow: String,
    val title: String,
    val subtitle: String
)

private fun financeHeaderInfo(
    selectedTab: String,
    headerIncome: Double,
    headerExpense: Double,
    headerNet: Double,
    currentAccounts: List<CurrentAccountDTO>,
    dailyTotals: List<DailyTotalDTO>
): FinanceHeaderInfo = when (selectedTab) {
    "Analiz" -> {
        val income = dailyTotals.sumOf { it.income ?: 0.0 }
        val expense = dailyTotals.sumOf { it.expense ?: 0.0 }
        FinanceHeaderInfo(
            eyebrow = "Son 30 gün",
            title = "Net ₺${formatMoney(income - expense)}",
            subtitle = "Gelir ₺${formatMoney(income)} • Gider ₺${formatMoney(expense)}"
        )
    }
    "Cari" -> {
        val debtors = currentAccounts.count { (it.balance ?: 0.0) > 0.0 }
        val total = currentAccounts.sumOf { (it.balance ?: 0.0).coerceAtLeast(0.0) }
        FinanceHeaderInfo(
            eyebrow = "Toplam alacak",
            title = "₺${formatMoney(total)}",
            subtitle = "$debtors borçlu müşteri · ${currentAccounts.size} cari kayıt"
        )
    }
    "Rapor" -> FinanceHeaderInfo(
        eyebrow = "Rapor özeti",
        title = "₺${formatMoney(headerNet)}",
        subtitle = "Gelir ₺${formatMoney(headerIncome)} • Gider ₺${formatMoney(headerExpense)}"
    )
    else -> FinanceHeaderInfo(
        eyebrow = "Bugünkü kasa",
        title = "₺${formatMoney(headerNet)}",
        subtitle = "Gelir ₺${formatMoney(headerIncome)} • Gider ₺${formatMoney(headerExpense)}"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(
    viewModel: FinanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("OTHER") }
    var linkedFixedExpenseId by remember { mutableStateOf<Long?>(null) }
    var showSmartLinkDialog by remember { mutableStateOf(false) }
    var smartLinkSelectionId by remember { mutableStateOf<Long?>(null) }
    var selectedTab by remember { mutableStateOf("Günlük") }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var paymentText by remember { mutableStateOf("") }
    var discountText by remember { mutableStateOf("0") }
    var selectedYear by remember { mutableStateOf("Tümü") }
    var selectedMonth by remember { mutableStateOf("Tümü") }
    var showIncome by remember { mutableStateOf(true) }
    var showExpense by remember { mutableStateOf(true) }
    var showNet by remember { mutableStateOf(true) }
    var pdfMenuMonth by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val filteredArchives = uiState.monthlyArchives.filter { monthly ->
        val parts = monthly.period?.split("-").orEmpty()
        val y = parts.getOrNull(0)
        val m = parts.getOrNull(1)
        val yearOk = selectedYear == "Tümü" || y == selectedYear
        val monthOk = selectedMonth == "Tümü" || m == selectedMonth
        yearOk && monthOk
    }
    val headerIncome = if (selectedTab == "Rapor") {
        filteredArchives.sumOf { it.totalIncome ?: 0.0 }
    } else {
        uiState.summary.totalIncome ?: 0.0
    }
    val headerExpense = if (selectedTab == "Rapor") {
        filteredArchives.sumOf { it.totalExpense ?: 0.0 }
    } else {
        uiState.summary.totalExpense ?: 0.0
    }
    val headerNet = if (selectedTab == "Rapor") {
        filteredArchives.sumOf { it.netProfit ?: 0.0 }
    } else {
        uiState.summary.netCash ?: 0.0
    }
    val unpaidFixedMatches = remember(uiState.fixedExpenses, category) {
        uiState.fixedExpenses.filter { !it.paidThisMonth && it.category == category }
    }
    val headerInfo = financeHeaderInfo(
        selectedTab = selectedTab,
        headerIncome = headerIncome,
        headerExpense = headerExpense,
        headerNet = headerNet,
        currentAccounts = uiState.currentAccounts,
        dailyTotals = uiState.dailyTotals
    )

    LaunchedEffect(Unit) { viewModel.loadDashboard() }
    LaunchedEffect(uiState.debtPaymentSavedAt) {
        if (uiState.debtPaymentSavedAt != null) {
            selectedAccountId = null
            paymentText = ""
            discountText = "0"
            viewModel.consumeDebtPaymentSavedEvent()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            uiState.loading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            else -> {
                PullToRefreshBox(
                    isRefreshing = uiState.refreshing,
                    onRefresh = { viewModel.loadDashboard(refresh = true) },
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Spacing.lg,
                            end = Spacing.lg,
                            top = Spacing.md,
                            bottom = Spacing.xxl
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                    ) {
                        item {
                            FinanceCompactHeader(
                                eyebrow = headerInfo.eyebrow,
                                title = headerInfo.title,
                                subtitle = headerInfo.subtitle
                            )
                        }
                        item {
                            FinanceTabs(
                                tabs = financeTabs,
                                selected = selectedTab,
                                onSelect = { selectedTab = it }
                            )
                        }
                        when (selectedTab) {
                            "Günlük" -> {
                                item {
                                    AddExpenseCard(
                                        amountText = amountText,
                                        description = description,
                                        category = category,
                                        fixedExpenses = unpaidFixedMatches,
                                        linkedFixedExpenseId = linkedFixedExpenseId,
                                        adding = uiState.addingExpense,
                                        onAmountChange = { amountText = it },
                                        onDescriptionChange = { description = it },
                                        onCategoryChange = {
                                            category = it
                                            linkedFixedExpenseId = null
                                        },
                                        onLinkedFixedExpenseChange = { linkedFixedExpenseId = it },
                                        onAdd = {
                                            val amount = amountText.toDoubleOrNull() ?: 0.0
                                            if (amount > 0.0 && description.isNotBlank()) {
                                                if (linkedFixedExpenseId == null && unpaidFixedMatches.isNotEmpty()) {
                                                    smartLinkSelectionId = unpaidFixedMatches.firstOrNull()?.id
                                                    showSmartLinkDialog = true
                                                } else {
                                                    viewModel.addExpense(
                                                        amount = amount,
                                                        description = description.trim(),
                                                        category = category,
                                                        fixedExpenseId = linkedFixedExpenseId
                                                    )
                                                    amountText = ""
                                                    description = ""
                                                    linkedFixedExpenseId = null
                                                }
                                            }
                                        }
                                    )
                                }
                                if (uiState.overdueFixedCount > 0 || uiState.upcomingFixedCount > 0) {
                                    item {
                                        AlertsCard(
                                            overdueCount = uiState.overdueFixedCount,
                                            upcomingCount = uiState.upcomingFixedCount
                                        )
                                    }
                                }
                                if (uiState.fixedExpenses.isNotEmpty()) {
                                    item {
                                        FixedExpensesCard(fixedExpenses = uiState.fixedExpenses)
                                    }
                                }
                                item {
                                    AppDashboardSection(
                                        title = "Bugünün Giderleri",
                                        subtitle = "${uiState.expenses.size} kayıt"
                                    ) {}
                                }
                                if (uiState.expenses.isEmpty()) {
                                    item {
                                        AppEmptyState(
                                            title = "Bugün gider yok",
                                            subtitle = "Yukarıdaki form ile gider kaydı ekleyebilirsiniz.",
                                            icon = Icons.Outlined.Inbox,
                                            tint = AccentPurple
                                        )
                                    }
                                } else {
                                    items(uiState.expenses, key = { it.id ?: it.description.orEmpty() }) { expense ->
                                        ExpenseRow(expense = expense)
                                    }
                                }
                                item {
                                    AppPrimaryButton(
                                        text = if (uiState.closingDay) "Gün kapatılıyor..." else "Günü Kapat",
                                        onClick = { viewModel.closeDay() },
                                        enabled = !uiState.closingDay,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            "Analiz" -> {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                    ) {
                                        ToggleSeriesChip("Gelir", showIncome, Success) { showIncome = !showIncome }
                                        ToggleSeriesChip("Gider", showExpense, ErrorTone) { showExpense = !showExpense }
                                        ToggleSeriesChip("Net", showNet, Info) { showNet = !showNet }
                                    }
                                }
                                item {
                                    TrendCard(
                                        dailyTotals = uiState.dailyTotals,
                                        showIncome = showIncome,
                                        showExpense = showExpense,
                                        showNet = showNet
                                    )
                                }
                                item { CategoryBreakdownCard(breakdown = uiState.categoryReport.breakdown) }
                            }
                            "Cari" -> {
                                item {
                                    AppDashboardSection(
                                        title = "Cari Hesaplar",
                                        subtitle = "${uiState.currentAccounts.size} müşteri"
                                    ) {}
                                }
                                if (uiState.currentAccounts.isEmpty()) {
                                    item {
                                        AppEmptyState(
                                            title = "Cari hesap yok",
                                            subtitle = "Henüz cari hesap kaydı bulunmuyor.",
                                            icon = Icons.Outlined.PersonOff,
                                            tint = AccentPurple
                                        )
                                    }
                                } else {
                                    items(uiState.currentAccounts, key = { it.id ?: it.customerName.orEmpty() }) { account ->
                                        CurrentAccountRow(
                                            account = account,
                                            paying = uiState.payingAccountId == account.id,
                                            onPay = { selectedAccountId = account.id }
                                        )
                                    }
                                }
                            }
                            else -> {
                                val yearOptions = buildList {
                                    add("Tümü")
                                    addAll(
                                        uiState.monthlyArchives
                                            .mapNotNull { it.period?.split("-")?.firstOrNull() }
                                            .distinct()
                                            .sortedDescending()
                                    )
                                }
                                val monthOptions = buildList {
                                    add("Tümü")
                                    addAll((1..12).map { it.toString().padStart(2, '0') })
                                }
                                item {
                                    AppDashboardSection(
                                        title = "Aylık Arşiv",
                                        subtitle = "${filteredArchives.size} dönem"
                                    ) {}
                                }
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        AppPickerField(
                                            label = "Yıl",
                                            selectedValue = selectedYear,
                                            options = yearOptions,
                                            onSelected = { selectedYear = it },
                                            modifier = Modifier.weight(1f)
                                        )
                                        AppPickerField(
                                            label = "Ay",
                                            selectedValue = selectedMonth,
                                            options = monthOptions,
                                            onSelected = { selectedMonth = it },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                if (filteredArchives.isEmpty()) {
                                    item {
                                        AppEmptyState(
                                            title = "Arşiv yok",
                                            subtitle = "Bu filtreye uygun aylık kayıt bulunamadı.",
                                            icon = Icons.Outlined.ReceiptLong,
                                            tint = AccentPurple
                                        )
                                    }
                                } else {
                                    items(filteredArchives, key = { it.period ?: it.displayPeriod.orEmpty() }) { monthly ->
                                        Card {
                                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(monthly.displayPeriod ?: monthly.period.orEmpty(), fontWeight = FontWeight.SemiBold)
                                                Text("Gelir: ₺${formatMoney(monthly.totalIncome ?: 0.0)}")
                                                Text("Gider: ₺${formatMoney(monthly.totalExpense ?: 0.0)}")
                                                Text("Kar: ₺${formatMoney(monthly.netProfit ?: 0.0)}")
                                                val month = monthly.period
                                                AppPrimaryButton(
                                                    text = if (uiState.downloadingMonth == month) {
                                                        "PDF Hazırlanıyor..."
                                                    } else {
                                                        "PDF"
                                                    },
                                                    onClick = { if (!month.isNullOrBlank()) pdfMenuMonth = month },
                                                    enabled = !month.isNullOrBlank() && uiState.downloadingMonth != month,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (uiState.error != null) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(uiState.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                                    AppPrimaryButton(
                                        text = "Tekrar Dene",
                                        onClick = { viewModel.loadDashboard(refresh = true) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedAccountId != null) {
        AlertDialog(
            onDismissRequest = { selectedAccountId = null },
            title = { Text("Cari Ödeme") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = paymentText,
                        onValueChange = { paymentText = it },
                        label = { Text("Ödeme Tutarı") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = discountText,
                        onValueChange = { discountText = it },
                        label = { Text("İndirim") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val payment = paymentText.toDoubleOrNull() ?: 0.0
                    val discount = discountText.toDoubleOrNull() ?: 0.0
                    val accountId = selectedAccountId
                    if (payment > 0.0 && accountId != null) {
                        viewModel.payDebt(accountId, payment, discount)
                    }
                }) { Text("Kaydet") }
            },
            dismissButton = {
                TextButton(onClick = { selectedAccountId = null }) { Text("Vazgeç") }
            }
        )
    }

    if (showSmartLinkDialog) {
        AlertDialog(
            onDismissRequest = { showSmartLinkDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = Info
                )
            },
            title = {
                Text(
                    "Sabit ödemeye bağlamak ister misiniz?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        "Bu kategoride henüz tamamlanmamış bir sabit gider tanımınız var. " +
                            "Bu kaydı seçtiğinizde tutar, o sabit ödemenin bu ayki ödemeleriyle birlikte takip edilir.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Info.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Info.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Text(
                                "Hangi sabit kayda yazılsın?",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Info
                            )
                            FixedExpensePickerField(
                                items = unpaidFixedMatches,
                                selectedId = smartLinkSelectionId,
                                onSelected = { smartLinkSelectionId = it }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (amount > 0.0 && description.isNotBlank()) {
                        viewModel.addExpense(
                            amount = amount,
                            description = description.trim(),
                            category = category,
                            fixedExpenseId = smartLinkSelectionId
                        )
                        amountText = ""
                        description = ""
                        linkedFixedExpenseId = null
                    }
                    showSmartLinkDialog = false
                }) {
                    Text("Bağla ve kaydet", color = Info, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        if (amount > 0.0 && description.isNotBlank()) {
                            viewModel.addExpense(
                                amount = amount,
                                description = description.trim(),
                                category = category,
                                fixedExpenseId = null
                            )
                            amountText = ""
                            description = ""
                            linkedFixedExpenseId = null
                        }
                        showSmartLinkDialog = false
                    }) { Text("Bağlamadan kaydet") }
                    TextButton(onClick = { showSmartLinkDialog = false }) { Text("İptal") }
                }
            }
        )
    }

    pdfMenuMonth?.let { month ->
        AlertDialog(
            onDismissRequest = { pdfMenuMonth = null },
            title = { Text("PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        "Aylık finans raporu için bir işlem seçin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            pdfMenuMonth = null
                            scope.launch {
                                runCatching {
                                    val data = viewModel.downloadMonthlyPdf(month)
                                    FinancePdfHelper.saveAndShare(context, month, data)
                                }.onFailure {
                                    snackbarHostState.showSnackbar("PDF indirilemedi: ${it.message}")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Paylaş", modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(
                        onClick = {
                            pdfMenuMonth = null
                            scope.launch {
                                runCatching {
                                    val data = viewModel.downloadMonthlyPdf(month)
                                    FinancePdfHelper.saveToDownloads(context, month, data)
                                        .onSuccess { path ->
                                            snackbarHostState.showSnackbar("PDF kaydedildi: $path")
                                        }
                                        .onFailure {
                                            snackbarHostState.showSnackbar("PDF kaydedilemedi: ${it.message}")
                                        }
                                }.onFailure {
                                    snackbarHostState.showSnackbar("PDF indirilemedi: ${it.message}")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Downloads'a Kaydet", modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pdfMenuMonth = null }) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
private fun FinanceCompactHeader(
    eyebrow: String,
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrendCard(
    dailyTotals: List<DailyTotalDTO>,
    showIncome: Boolean,
    showExpense: Boolean,
    showNet: Boolean
) {
    var selectedIndex by remember(dailyTotals) { mutableStateOf<Int?>(null) }

    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Son 30 Gün Gelir/Gider/Net", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (dailyTotals.isEmpty()) {
                Text("Trend verisi yok.")
            } else {
                val points = dailyTotals.takeLast(14)
                val incomeSeries = points.map { it.income ?: 0.0 }
                val expenseSeries = points.map { it.expense ?: 0.0 }
                val netSeries = points.map { (it.income ?: 0.0) - (it.expense ?: 0.0) }
                val allValues = buildList {
                    if (showIncome) addAll(incomeSeries)
                    if (showExpense) addAll(expenseSeries)
                    if (showNet) addAll(netSeries)
                }.ifEmpty { listOf(0.0, 1.0) }
                val minValue = allValues.minOrNull() ?: 0.0
                val maxValue = allValues.maxOrNull() ?: 1.0
                val range = (maxValue - minValue).coerceAtLeast(1.0)
                val safeSelected = selectedIndex?.coerceIn(0, points.lastIndex)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .pointerInput(points.size) {
                            detectTapGestures { tapOffset ->
                                val leftPad = 16f
                                val rightPad = 8f
                                val chartW = size.width - leftPad - rightPad
                                val steps = (points.size - 1).coerceAtLeast(1)
                                val normalized = ((tapOffset.x - leftPad) / chartW).coerceIn(0f, 1f)
                                selectedIndex = (normalized * steps).roundToInt().coerceIn(0, points.lastIndex)
                            }
                        }
                ) {
                    val leftPad = 16f
                    val rightPad = 8f
                    val topPad = 12f
                    val bottomPad = 18f
                    val chartW = size.width - leftPad - rightPad
                    val chartH = size.height - topPad - bottomPad
                    val steps = (points.size - 1).coerceAtLeast(1)

                    fun xAt(i: Int): Float = leftPad + (i.toFloat() / steps) * chartW
                    fun yAt(value: Double): Float {
                        val norm = ((value - minValue) / range).toFloat()
                        return topPad + (1f - norm) * chartH
                    }

                    repeat(4) { i ->
                        val y = topPad + (i / 3f) * chartH
                        drawLine(
                            color = Color(0xFFE0E0E0),
                            start = Offset(leftPad, y),
                            end = Offset(leftPad + chartW, y),
                            strokeWidth = 1f
                        )
                    }

                    fun drawSeries(values: List<Double>, color: Color) {
                        val path = Path()
                        values.forEachIndexed { idx, value ->
                            val x = xAt(idx)
                            val y = yAt(value)
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path = path, color = color, style = Stroke(width = 3f))
                        safeSelected?.let { idx ->
                            drawCircle(
                                color = color,
                                radius = 5f,
                                center = Offset(xAt(idx), yAt(values[idx]))
                            )
                        }
                    }

                    if (showIncome) drawSeries(incomeSeries, Success)
                    if (showExpense) drawSeries(expenseSeries, ErrorTone)
                    if (showNet) drawSeries(netSeries, Info)

                    safeSelected?.let { idx ->
                        val x = xAt(idx)
                        drawLine(
                            color = Color(0xFF9E9E9E),
                            start = Offset(x, topPad),
                            end = Offset(x, topPad + chartH),
                            strokeWidth = 1.5f
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("● Gelir", color = Success)
                    Text("● Gider", color = ErrorTone)
                    Text("● Net", color = Info)
                }
                safeSelected?.let { idx ->
                    val day = points[idx]
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(day.date?.take(10).orEmpty(), fontWeight = FontWeight.SemiBold)
                            Text("Gelir: ₺${formatMoney(incomeSeries[idx])}", color = Success)
                            Text("Gider: ₺${formatMoney(expenseSeries[idx])}", color = ErrorTone)
                            Text("Net: ₺${formatMoney(netSeries[idx])}", color = Info)
                        }
                    }
                }
                val firstDate = points.firstOrNull()?.date?.take(10).orEmpty()
                val lastDate = points.lastOrNull()?.date?.take(10).orEmpty()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(firstDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(lastDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(breakdown: Map<String, Double>) {
    AppGhostCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Kategori Dağılımı", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (breakdown.isEmpty()) {
                Text(
                    "Kategori verisi yok.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxVal = breakdown.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                breakdown.entries.sortedByDescending { it.value }.forEach { entry ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(entry.key)
                        Text("₺${formatMoney(entry.value)}")
                    }
                    Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
                        val fraction = (entry.value / maxVal).toFloat().coerceIn(0f, 1f)
                        drawLine(
                            color = Color(0xFFE0E0E0),
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = size.height
                        )
                        drawLine(
                            color = Info,
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width * fraction, size.height / 2f),
                            strokeWidth = size.height
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentAccountRow(
    account: CurrentAccountDTO,
    paying: Boolean,
    onPay: () -> Unit
) {
    val balance = account.balance ?: 0.0
    AppGhostCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            AppInitialsAvatar(text = account.customerName ?: "?", size = 40.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = account.customerName ?: "Müşteri",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1
                )
                Text(
                    text = "Bakiye",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "₺${formatMoney(balance)}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (balance > 0) Warning else Success
            )
        }
        if (balance > 0) {
            androidx.compose.foundation.layout.Spacer(Modifier.height(Spacing.sm))
            AppPrimaryButton(
                text = if (paying) "Kaydediliyor..." else "Ödeme Al",
                onClick = onPay,
                enabled = !paying,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FinanceTabs(tabs: List<String>, selected: String, onSelect: (String) -> Unit) {
    val selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 0.dp,
        divider = {}
    ) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(tab) },
                text = {
                    Text(
                        text = tab,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun ToggleSeriesChip(
    label: String,
    selected: Boolean,
    tint: Color,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) tint.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) tint.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(tint, CircleShape)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun AlertsCard(overdueCount: Int, upcomingCount: Int) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Warning.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Warning.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            if (overdueCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AppIconBadge(icon = Icons.Outlined.ReceiptLong, tint = ErrorTone, size = 28.dp, iconSize = 14.dp, cornerRadius = 9.dp)
                    Text("Geciken sabit ödeme: $overdueCount", color = ErrorTone, fontWeight = FontWeight.SemiBold)
                }
            }
            if (upcomingCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AppIconBadge(icon = Icons.Outlined.ReceiptLong, tint = Warning, size = 28.dp, iconSize = 14.dp, cornerRadius = 9.dp)
                    Text("3 gün içinde yaklaşan ödeme: $upcomingCount", color = Warning, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FixedExpensesCard(fixedExpenses: List<FixedExpenseDefinitionDTO>) {
    var expanded by remember { mutableStateOf(false) }
    val sorted = remember(fixedExpenses) { fixedExpenses.sortedBy { it.dayOfMonth ?: Int.MAX_VALUE } }
    val currentDay = LocalDate.now().dayOfMonth
    val overdueCount = sorted.count { !it.paidThisMonth && (it.dayOfMonth ?: 99) < currentDay }

    AppGhostCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Sabit Gider Takvimi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        buildString {
                            append("${sorted.size} kayıt")
                            if (overdueCount > 0) append(" · $overdueCount geciken")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Daralt" else "Göster")
                }
            }
            if (expanded) {
                sorted.forEach { item ->
                    FixedExpenseRow(item = item)
                }
            }
        }
    }
}

@Composable
private fun FixedExpenseRow(item: FixedExpenseDefinitionDTO) {
    val target = fixedExpenseTargetAmount(item)
    val paid = fixedExpensePaidAmount(item)
    val remaining = fixedExpenseRemainingAmount(item)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.name.orEmpty().ifBlank { "Sabit Gider" }, fontWeight = FontWeight.Medium)
            Text(
                formatFixedDueSummary(item),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                expenseCategoryOptions.firstOrNull { it.first == item.category }?.second
                    ?: (item.category ?: "Diğer"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                buildString {
                    append("Bu ay ödenen ₺${formatMoney(paid)}")
                    append(" · ")
                    if (item.paidThisMonth || remaining < 0.005) {
                        append("Tamamlandı")
                    } else {
                        append("Kalan ₺${formatMoney(remaining)}")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (item.paidThisMonth) Success else Warning,
                fontWeight = FontWeight.SemiBold
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "₺${formatMoney(target)}",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                if (item.paidThisMonth) "Ödendi" else "Bekliyor",
                style = MaterialTheme.typography.labelSmall,
                color = if (item.paidThisMonth) Success else Warning,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AddExpenseCard(
    amountText: String,
    description: String,
    category: String,
    fixedExpenses: List<FixedExpenseDefinitionDTO>,
    linkedFixedExpenseId: Long?,
    adding: Boolean,
    onAmountChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onLinkedFixedExpenseChange: (Long?) -> Unit,
    onAdd: () -> Unit
) {
    val selectedCategoryLabel = expenseCategoryOptions.firstOrNull { it.first == category }?.second ?: category
    val selectedFixed = fixedExpenses.firstOrNull { it.id == linkedFixedExpenseId }
    val selectedFixedLabel = when {
        selectedFixed == null -> "Bağlama yapma (genel gider)"
        else -> fixedExpenseOptionLabel(selectedFixed)
    }
    val fixedOptions = remember(fixedExpenses) {
        listOf("Bağlama yapma (genel gider)") + fixedExpenses.map { fixedExpenseOptionLabel(it) }
    }

    AppGhostCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Gider Ekle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = amountText,
                onValueChange = onAmountChange,
                label = { Text("Tutar") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            AppTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = "Açıklama",
                modifier = Modifier.fillMaxWidth()
            )
            AppPickerField(
                label = "Kategori",
                selectedValue = selectedCategoryLabel,
                options = expenseCategoryOptions.map { it.second },
                onSelected = { label ->
                    expenseCategoryOptions.firstOrNull { it.second == label }?.first?.let(onCategoryChange)
                }
            )
            AppPickerField(
                label = "Sabit kayıtla eşleştir",
                selectedValue = selectedFixedLabel,
                options = fixedOptions,
                supportingText = "Varsayılan gider olarak kaydeder veya seçtiğiniz sabit ödemeye bağlar.",
                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null, tint = Info) },
                onSelected = { option ->
                    if (option == "Bağlama yapma (genel gider)") {
                        onLinkedFixedExpenseChange(null)
                    } else {
                        fixedExpenses.firstOrNull { fixedExpenseOptionLabel(it) == option }?.id?.let(onLinkedFixedExpenseChange)
                    }
                }
            )
            AppPrimaryButton(
                text = if (adding) "Ekleniyor..." else "Gideri Kaydet",
                onClick = onAdd,
                enabled = !adding,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FixedExpensePickerField(
    items: List<FixedExpenseDefinitionDTO>,
    selectedId: Long?,
    onSelected: (Long?) -> Unit
) {
    val options = remember(items) { items.map { fixedExpenseOptionLabel(it) } }
    val selected = items.firstOrNull { it.id == selectedId }
    val label = selected?.let { fixedExpenseOptionLabel(it) } ?: "Kayıt seçin"
    AppPickerField(
        label = "Önerilen sabit kayıt",
        selectedValue = label,
        options = options,
        onSelected = { option ->
            items.firstOrNull { fixedExpenseOptionLabel(it) == option }?.id?.let(onSelected)
        }
    )
}

@Composable
private fun ExpenseRow(expense: ExpenseItemDTO) {
    AppGhostCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            AppIconBadge(icon = Icons.Outlined.ReceiptLong, tint = ErrorTone)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = expenseCategoryOptions.firstOrNull { it.first == expense.category }?.second
                        ?: expense.category ?: "Diğer",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = expense.description.orEmpty().ifBlank { "Açıklama yok" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
            }
            Text(
                text = "₺${formatMoney(expense.amount ?: 0.0)}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = ErrorTone
            )
        }
    }
}

private fun formatMoney(value: Double): String = String.format(Locale.US, "%,.2f", value)
