package com.pusula.service.ui.technician

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pusula.service.core.CollectionPricing
import com.pusula.service.core.readOnlyProtected
import com.pusula.service.data.model.CollectionRequest
import com.pusula.service.ui.components.AppTopBar
import com.pusula.service.ui.components.AppGhostCard
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import com.pusula.service.ui.guide.*

@Composable
fun CollectionScreen(ticketId: Long, onBack: () -> Unit, onDone: () -> Unit,
                     viewModel: TicketViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val guideList=rememberGuideListState(mapOf("collection-fees" to 1,"collection-discount" to 1,"collection-summary" to 1,"collection-allocation" to 2,"collection-payment" to 3,"collection-warranty" to 4))
    val session by viewModel.sessionManager.state.collectAsState()
    var laborText by rememberSaveable(ticketId) { mutableStateOf("") }
    var discountEnabled by rememberSaveable(ticketId) { mutableStateOf(false) }
    var discountType by rememberSaveable(ticketId) { mutableStateOf("AMOUNT") }
    var discountText by rememberSaveable(ticketId) { mutableStateOf("") }
    var organizationEnabled by rememberSaveable(ticketId) { mutableStateOf(false) }
    var organizationId by rememberSaveable(ticketId) { mutableStateOf<Long?>(null) }
    var organizationText by rememberSaveable(ticketId) { mutableStateOf("") }
    var method by rememberSaveable(ticketId) { mutableStateOf("CASH") }
    var amountText by rememberSaveable(ticketId) { mutableStateOf("") }
    var warranty by rememberSaveable(ticketId) { mutableStateOf(false) }
    var extras by rememberSaveable(ticketId) { mutableStateOf(false) }
    var note by rememberSaveable(ticketId) { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val tourFocus=LocalGuideFocus.current;val touring=LocalGuideTour.current
    LaunchedEffect(tourFocus) { if(touring) {
        if(tourFocus=="collection-discount"){discountEnabled=true;warranty=false}
        if(tourFocus=="collection-allocation")organizationEnabled=true
        if(tourFocus=="collection-warranty")extras=true
    } }
    val editable = !state.collecting && !state.collectionLocked
    val internalWarranty = warranty && !organizationEnabled
    val noCollection = warranty || method == "CURRENT_ACCOUNT"
    val actualParts = state.usedParts.fold(BigDecimal.ZERO) { sum, part ->
        sum + BigDecimal.valueOf(part.sellingPriceSnapshot) * BigDecimal.valueOf(part.quantityUsed)
    }
    val parts = if (internalWarranty) BigDecimal.ZERO else actualParts
    val labor = runCatching { CollectionPricing.parse(if (internalWarranty) "0" else laborText) }
    val discount = runCatching { CollectionPricing.parse(if (discountEnabled && !warranty) discountText else "0") }
    val institution = runCatching { CollectionPricing.parse(if (organizationEnabled) organizationText else "0") }
    val collected = runCatching { CollectionPricing.parse(if (noCollection) "0" else amountText) }
    val price = runCatching { CollectionPricing.calculate(parts, labor.getOrThrow(), discountType,
        discount.getOrThrow(), BigDecimal.ZERO, BigDecimal.ZERO) }
    val totals = runCatching {
        require(state.selectedTicket?.id == ticketId && state.usedPartsLoadedTicketId == ticketId && !state.usedPartsLoading) { "Parça bilgileri henüz yüklenmedi. Yenilemeyi deneyin." }
        val value = CollectionPricing.calculate(parts, labor.getOrThrow(), discountType, discount.getOrThrow(), institution.getOrThrow(), collected.getOrThrow())
        require(!organizationEnabled || (organizationId != null && institution.getOrThrow() > BigDecimal.ZERO)) { "Kurum seçin ve aktarılacak tutarı girin." }
        require(!warranty || !organizationEnabled || institution.getOrThrow().compareTo(value.net) == 0) { "Kurum garantisinde fişin tamamını kuruma aktarın." }
        value
    }
    val customerShare = price.getOrNull()?.net?.subtract(institution.getOrDefault(BigDecimal.ZERO))?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
    fun submit() {
        if (state.collectionLocked || totals.isSuccess) {
            viewModel.completeService(ticketId, CollectionRequest(
                collectedAmount = collected.getOrDefault(BigDecimal.ZERO).toDouble(),
                paymentMethod = if (warranty) "WARRANTY" else method,
                laborFee = labor.getOrDefault(BigDecimal.ZERO).toDouble(), technicianNote = note.trim(),
                discountType = if (warranty) "AMOUNT" else discountType,
                discountValue = discount.getOrDefault(BigDecimal.ZERO),
                billingPartyId = if (organizationEnabled) organizationId else null,
                billingPartyAmount = if (organizationEnabled) institution.getOrNull() else null,
                expectedPartsTotal = CollectionPricing.money(actualParts)
            ))
        }
    }
    LaunchedEffect(ticketId) { viewModel.selectTicket(ticketId); viewModel.loadBillingOrganizations() }
    LaunchedEffect(state.serviceCompletedTicketId) {
        if (state.serviceCompletedTicketId == ticketId) { viewModel.consumeServiceCompleted(); onDone() }
    }
    BackHandler(enabled = !state.collecting, onBack = onBack)
    Scaffold(topBar = { AppTopBar(title = "Tahsilat", onBack = { if (!state.collecting) onBack() }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(onClick = {
                    if (!state.collectionLocked && (totals.getOrNull()?.customerDebt ?: BigDecimal.ZERO) + institution.getOrDefault(BigDecimal.ZERO) > BigDecimal.ZERO) confirm = true
                    else submit()
                }, enabled = !state.collecting && (state.collectionLocked || totals.isSuccess),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp).readOnlyProtected(session.isReadOnly)) {
                    Text(if (state.collecting) "Kaydediliyor…" else if (state.collectionLocked) "Aynı İşlemi Tekrar Dene" else "Tahsilatı Kaydet ve İşi Tamamla")
                }
            }
        }) { padding ->
        LazyColumn(state=guideList,modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { Text("Servis fişi #$ticketId · ${state.selectedTicket?.customerName.orEmpty()}", style = MaterialTheme.typography.bodyMedium) }
            item {
                AppGhostCard {
                    Column(Modifier.guideTourTarget("collection-fees").guideTourTarget("collection-discount").guideTourTarget("collection-summary"),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("1. Ücret Bilgileri", fontWeight = FontWeight.Bold)
                        MoneyRow("Parça toplamı", parts)
                        MoneyField("Servis / işçilik bedeli", laborText, editable && !internalWarranty) { laborText = it }
                        CheckRow("İskonto ekle", discountEnabled, editable && !warranty) { discountEnabled = it }
                        if (discountEnabled && !warranty) {
                            Row {
                                FilterChip(selected = discountType == "AMOUNT", onClick = { discountType = "AMOUNT" }, enabled = editable, label = { Text("TL") })
                                Spacer(Modifier.width(12.dp))
                                FilterChip(selected = discountType == "PERCENT", onClick = { discountType = "PERCENT" }, enabled = editable, label = { Text("%") })
                            }
                            MoneyField(if (discountType == "PERCENT") "İskonto yüzdesi" else "İskonto tutarı", discountText, editable) { discountText = it }
                            MoneyRow("Ara toplam", price.getOrNull()?.subtotal)
                            MoneyRow("İskonto", price.getOrNull()?.discount?.negate())
                        }
                        HorizontalDivider()
                        MoneyRow("Fiş toplamı", price.getOrNull()?.net)
                    }
                }
            }
            item {
                AppGhostCard {
                    Column(Modifier.guideTourTarget("collection-allocation"),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("2. Ödemeyi Üstlenen", fontWeight = FontWeight.Bold)
                        CheckRow("Tamamını veya bir kısmını kurum carisine aktar", organizationEnabled, editable) { organizationEnabled = it }
                        if (organizationEnabled) {
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(onClick = { expanded = true }, enabled = editable) {
                                    Text(state.billingOrganizations.firstOrNull { it.id == organizationId }?.displayName ?: "Kurum seçin")
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    state.billingOrganizations.forEach { party ->
                                        DropdownMenuItem(text = { Text(party.displayName) }, onClick = { organizationId = party.id; expanded = false })
                                    }
                                }
                            }
                            state.billingOrganizationsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            if (state.billingOrganizations.isEmpty()) TextButton(onClick = { viewModel.loadBillingOrganizations() }, enabled = editable) { Text("Kurumları tekrar yükle") }
                            MoneyField("Kuruma aktarılacak tutar", organizationText, editable) { organizationText = it }
                            TextButton(onClick = { organizationText = price.getOrNull()?.net?.toPlainString().orEmpty() }, enabled = editable && price.isSuccess) { Text("Fişin tamamını aktar") }
                            Text("Bu tutar kurumun cari borcudur; alınmış ödeme değildir.", style = MaterialTheme.typography.bodySmall)
                        }
                        MoneyRow("Müşteriye düşen", customerShare)
                    }
                }
            }
            item {
                AppGhostCard {
                    Column(Modifier.guideTourTarget("collection-payment"),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("3. Tahsilat", fontWeight = FontWeight.Bold)
                        if (!warranty) {
                            listOf("CASH" to "Nakit", "CREDIT_CARD" to "Kredi Kartı", "CURRENT_ACCOUNT" to "Şimdi ödeme alınmadı").forEach { (value, label) ->
                                Row { RadioButton(selected = method == value, onClick = { method = value }, enabled = editable); Text(label, modifier = Modifier.padding(top = 12.dp)) }
                            }
                        } else Text(if (internalWarranty) "İşletme garantisi: tahsilat ve cari borç oluşmaz." else "Kurum garantisi: müşteriden tahsilat alınmaz.")
                        if (!noCollection) {
                            MoneyField("Tahsil edilen tutar", amountText, editable) { amountText = it }
                            TextButton(onClick = { amountText = customerShare.toPlainString() }, enabled = editable) { Text("Müşteri payının tamamı") }
                        }
                        HorizontalDivider()
                        MoneyRow("Kurum carisine", institution.getOrNull())
                        MoneyRow("Tahsil edilen", collected.getOrNull())
                        MoneyRow("Müşteri carisine kalan", totals.getOrNull()?.customerDebt)
                    }
                }
            }
            item {
                TextButton(onClick = { extras = !extras }, enabled = editable,modifier=Modifier.guideTourTarget("collection-warranty")) { Text("Kapanış notu ve garanti") }
                if (extras) {
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Yapılan işlem / kapanış notu") }, minLines = 2, enabled = editable, modifier = Modifier.fillMaxWidth())
                    CheckRow("Garanti kapsamında kapat", warranty, editable) { warranty = it }
                    Text("Garanti, iskonto ile ücretsiz işlemden farklıdır. Kurum garantisinde fişin tamamı kuruma aktarılır.", style = MaterialTheme.typography.bodySmall)
                }
                state.selectedTicket?.customerBalance?.takeIf { it != 0.0 }?.let {
                    Text("Önceki cari bakiye: ${currency(BigDecimal.valueOf(it))}. Bu fişe dahil değildir.", style = MaterialTheme.typography.bodySmall)
                }
                totals.exceptionOrNull()?.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.usedPartsLoadedTicketId != ticketId && !state.usedPartsLoading) TextButton(onClick = { viewModel.loadUsedParts(ticketId) }) { Text("Parçaları tekrar yükle") }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.collectionLocked) Text("Sonuç teyit edilene kadar aynı bilgilerle tekrar deneyin; ikinci tahsilat oluşturulmaz.")
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Cari aktarımı onayla") },
        text = { Text("Kurum carisine: ${currency(institution.getOrDefault(BigDecimal.ZERO))}\nMüşteri carisine: ${currency(totals.getOrNull()?.customerDebt ?: BigDecimal.ZERO)}") },
        confirmButton = { TextButton(onClick = { confirm = false; submit() }) { Text("Onayla ve Tamamla") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Vazgeç") } })
}

@Composable private fun MoneyField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, placeholder = { Text("0,00") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
}
@Composable private fun CheckRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row { Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled); Text(label, modifier = Modifier.padding(top = 12.dp)) }
}
@Composable private fun MoneyRow(label: String, amount: BigDecimal?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f)); Text(amount?.let(::currency) ?: "—", fontWeight = FontWeight.SemiBold)
    }
}
private fun currency(value: BigDecimal): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("tr-TR")).format(value)
