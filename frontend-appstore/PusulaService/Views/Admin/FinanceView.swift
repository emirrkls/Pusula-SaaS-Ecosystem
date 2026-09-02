import SwiftUI
import Charts

struct FinanceView: View {
    var body: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 155), spacing: 14)], spacing: 14) {
                financeDestination("Günlük İşlemler", "Gelir, gider ve sabit ödemeler", "calendar", .blue) {
                    FinanceDailyTab().navigationTitle("Günlük İşlemler")
                }
                financeDestination("Cari Alacaklar", "Müşteri bakiyeleri ve tahsilatlar", "person.2.fill", .orange) {
                    FinanceAccountsTab().navigationTitle("Cari Alacaklar")
                }
                financeDestination("İşletme Borçları", "Borç, ödeme ve ilave hareketleri", "creditcard.fill", .red) {
                    CompanyDebtsView().navigationTitle("İşletme Borçları")
                }
                .featureGated("COMPANY_DEBT_TRACKING", showUpgradeHint: true)
                financeDestination("Sabit Giderler", "Kira, maaş ve düzenli ödemeler", "repeat.circle.fill", .purple) {
                    FixedExpensesView().navigationTitle("Sabit Giderler")
                }
                financeDestination("Analiz ve Raporlar", "Aylık özet, analiz ve PDF", "chart.xyaxis.line", .green) {
                    FinanceInsightsView().navigationTitle("Analiz ve Raporlar")
                }
                financeDestination("İşletme Mülkiyeti", "Takım ve demirbaş değerleri", "wrench.and.screwdriver.fill", .indigo) {
                    BusinessAssetsView().navigationTitle("İşletme Mülkiyeti")
                }
                .featureGated("BUSINESS_ASSETS", showUpgradeHint: true)
            }
            .padding()
        }
        .onboardingTarget(.financeContent)
        .background(PusulaTheme.page)
        .navigationTitle("Finans")
    }

    private func financeDestination<Destination: View>(
        _ title: String,
        _ subtitle: String,
        _ icon: String,
        _ color: Color,
        @ViewBuilder destination: () -> Destination
    ) -> some View {
        NavigationLink(destination: destination()) {
            VStack(alignment: .leading, spacing: 12) {
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundStyle(color)
                Text(title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.leading)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, minHeight: 120, alignment: .leading)
            .padding()
            .pusulaCard()
        }
        .buttonStyle(.plain)
    }
}

struct FinanceInsightsView: View {
    @State private var selectedTab = 0

    var body: some View {
        VStack(spacing: 0) {
            Picker("Bölüm", selection: $selectedTab) {
                Text("Analiz").tag(0)
                Text("Aylık Raporlar").tag(1)
            }
            .pickerStyle(.segmented)
            .padding()
            if selectedTab == 0 { FinanceAnalysisTab() } else { FinanceReportsTab() }
        }
        .background(PusulaTheme.page)
    }
}

struct FinanceDailyTab: View {
    @State private var selectedDate = Date()
    @State private var summary: DailySummaryDTO?
    @State private var expenses: [ExpenseDTO] = []
    @State private var editingExpense: ExpenseDTO?
    @State private var showAddExpense = false
    @State private var isLoading = true
    @State private var isClosingDay = false
    @State private var errorMessage: String?
    
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                DatePicker("İşlem tarihi", selection: $selectedDate, displayedComponents: .date)
                    .datePickerStyle(.compact)
                    .onChange(of: selectedDate) { _, _ in Task { await load() } }
                if let summary {
                    HStack(spacing: 12) {
                        financeMetric("Gelir", value: summary.totalIncome, color: .green)
                        financeMetric("Gider", value: summary.totalExpense, color: .red)
                        financeMetric("Net", value: summary.netCash, color: PusulaTheme.accent)
                    }
                    
                    if summary.dayClosed {
                        Label("Gün kapatıldı", systemImage: "lock.fill")
                            .font(.caption.weight(.semibold))
                            .foregroundColor(.orange)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    } else {
                        Button("Günü Kapat") {
                            Task { await closeDay() }
                        }
                        .disabled(isClosingDay)
                        .buttonStyle(.borderedProminent)
                        .tint(PusulaTheme.accent)
                        .readOnlyProtected()
                    }
                }
                
                Button(action: { showAddExpense = true }) {
                    Label("Gider Ekle", systemImage: "plus.circle.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .readOnlyProtected()
                
                sectionCard("Gelir Detayları") {
                    ForEach(Array((summary?.incomeDetails ?? []).enumerated()), id: \.offset) { _, item in
                        HStack {
                            Text(item.customerName ?? "Müşteri")
                            Spacer()
                            Text(formatCurrency(item.amount))
                                .foregroundColor(.green)
                        }
                        .font(.subheadline)
                    }
                }
                
                sectionCard("Gider Detayları") {
                    ForEach(expenses.filter { $0.date == financeDate(selectedDate) }, id: \.id) { item in
                        Button {
                            editingExpense = item
                            showAddExpense = true
                        } label: {
                          HStack {
                            VStack(alignment: .leading) {
                                Text(item.description)
                                Text(ExpenseCategory(rawValue: item.category)?.label ?? item.category).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Text(formatCurrency(item.amount))
                                .foregroundColor(.red)
                          }
                        }.buttonStyle(.plain)
                        .font(.subheadline)
                        .swipeActions { Button("Sil", role: .destructive) { Task { await deleteExpense(item) } } }
                    }
                }
            }
            .padding()
        }
        .overlay { if isLoading { ProgressView() } }
        .task { await load() }
        .refreshable { await load() }
        .sheet(isPresented: $showAddExpense) {
            AddExpenseSheet(date: selectedDate, expense: editingExpense) { await load() }
        }
        .onChange(of: showAddExpense) { _, shown in if !shown { editingExpense = nil } }
        .alert("Finans İşlemi Başarısız", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Tamam", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }
    
    private func load() async {
        isLoading = true
        do {
            async let summaryTask = FinanceService.getDailySummary(date: financeDate(selectedDate))
            async let expenseTask = FinanceService.getExpenses(date: financeDate(selectedDate))
            let (loadedSummary, loadedExpenses) = try await (summaryTask, expenseTask)
            await MainActor.run {
                summary = loadedSummary
                expenses = loadedExpenses
                isLoading = false
            }
        } catch {
            errorMessage = error.localizedDescription
            isLoading = false
        }
    }

    private func deleteExpense(_ expense: ExpenseDTO) async {
        guard let id = expense.id else { return }
        do { try await FinanceService.deleteExpense(id: id); await load() } catch { errorMessage = error.localizedDescription }
    }
    
    private func closeDay() async {
        isClosingDay = true
        defer { isClosingDay = false }
        do {
            _ = try await FinanceService.closeDay(date: financeDate(selectedDate), companyId: SessionManager.shared.companyId)
            await load()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

struct FinanceAnalysisTab: View {
    @State private var dailyTotals: [DailyTotalDTO] = []
    @State private var categoryReport: CategoryReportDTO?
    @State private var isLoading = true
    @State private var errorMessage: String?
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                sectionCard("Aylık Trend") {
                    if dailyTotals.isEmpty {
                        Text("Veri yok").foregroundStyle(.secondary)
                    } else {
                        Chart(dailyTotals) { item in
                            LineMark(
                                x: .value("Tarih", item.date ?? ""),
                                y: .value("Gelir", item.income ?? 0)
                            )
                            .foregroundStyle(.green)
                            LineMark(
                                x: .value("Tarih", item.date ?? ""),
                                y: .value("Gider", item.expense ?? 0)
                            )
                            .foregroundStyle(.red)
                        }
                        .frame(height: 220)
                    }
                }
                
                sectionCard("Kategori Dağılımı") {
                    ForEach(Array((categoryReport?.breakdown ?? [:]).sorted(by: { $0.value > $1.value })), id: \.key) { key, value in
                        HStack {
                            Text(key)
                            Spacer()
                            Text(formatCurrency(value))
                        }
                        .font(.subheadline)
                    }
                }
            }
            .padding()
        }
        .overlay { if isLoading { ProgressView("Analiz yükleniyor...") } }
        .task { await load() }
        .refreshable { await load() }
        .alert("Finans Analizi Yüklenemedi", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Tekrar Dene") { Task { await load() } }
            Button("Tamam", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }
    
    private func load() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        let end = formatter.string(from: Date())
        let start = formatter.string(from: Calendar.current.date(from: Calendar.current.dateComponents([.year, .month], from: Date())) ?? Date())
        do {
            async let totalsTask = FinanceService.getDailyTotals()
            async let categoryTask = FinanceService.getCategoryReport(startDate: start, endDate: end)
            (dailyTotals, categoryReport) = try await (totalsTask, categoryTask)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

struct FinanceAccountsTab: View {
    @State private var accounts: [CurrentAccountDTO] = []
    @State private var selectedAccount: CurrentAccountDTO?
    @State private var isLoading = true
    @State private var pdfPreview: PDFPreviewItem?
    @State private var errorMessage: String?
    
    var body: some View {
        List(Array(accounts.enumerated()), id: \.offset) { _, account in
            Button(action: {
                selectedAccount = account
            }) {
                HStack {
                    VStack(alignment: .leading) {
                        Text(account.customerName ?? "Müşteri")
                            .font(.headline)
                        Text("Son güncelleme: \(account.lastUpdated ?? "-")")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Text(formatCurrency(account.balance))
                        .font(.headline)
                        .foregroundColor(.orange)
                }
            }
            .foregroundColor(.primary)
        }
        .listStyle(.plain)
        .overlay {
            if isLoading {
                ProgressView("Cari hesaplar yükleniyor...")
            } else if accounts.isEmpty {
                ContentUnavailableView("Cari hesap yok", systemImage: "person.crop.circle.badge.exclamationmark")
            }
        }
        .task { await load() }
        .refreshable { await load() }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { Task { await downloadPDF() } } label: { Label("PDF", systemImage: "arrow.down.doc") }
                    .featureGated("ADVANCED_REPORT_EXPORT", showUpgradeHint: true)
            }
        }
        .sheet(item: $pdfPreview) { PDFPreviewSheet(item: $0) }
        .sheet(item: $selectedAccount) { account in
            CurrentAccountHistorySheet(account: account) {
                await load()
            }
        }
        .alert("Cari Hesaplar Yüklenemedi", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Tekrar Dene") { Task { await load() } }
            Button("Tamam", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }

    private func load() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            accounts = try await FinanceService.getCurrentAccounts()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func downloadPDF() async {
        do { pdfPreview = try PDFPreviewItem(data: try await FinanceService.downloadCurrentAccountsPDF(), fileName: "acik-cari-hesaplar.pdf", title: "Açık Cari Hesaplar") }
        catch { errorMessage = error.localizedDescription }
    }
}

struct CurrentAccountHistorySheet: View {
    let account: CurrentAccountDTO
    let onChanged: () async -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var history: CurrentAccountHistoryDTO?
    @State private var isLoading = true
    @State private var showPaySheet = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    LabeledContent("Müşteri", value: history?.customerName ?? account.customerName ?? "-")
                    LabeledContent("Güncel cari bakiye", value: formatCurrency(history?.currentBalance ?? account.balance))
                }
                Section("Cari hareketleri") {
                    if isLoading {
                        HStack { Spacer(); ProgressView(); Spacer() }
                    } else if let history, history.transactions.isEmpty {
                        ContentUnavailableView("Cari hareket bulunamadı", systemImage: "clock.arrow.circlepath")
                    } else {
                        ForEach(history?.transactions ?? []) { transaction in
                            currentAccountTransactionRow(transaction)
                        }
                    }
                }
            }
            .navigationTitle("Cari Geçmişi")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Kapat") { dismiss() } }
                ToolbarItem(placement: .primaryAction) {
                    Button { showPaySheet = true } label: { Label("Tahsilat", systemImage: "banknote") }
                        .disabled((history?.currentBalance ?? account.balance ?? 0) <= 0)
                        .readOnlyProtected()
                }
            }
            .task { await loadHistory() }
            .refreshable { await loadHistory() }
            .sheet(isPresented: $showPaySheet) {
                PayDebtSheet(account: refreshedAccount) {
                    await onChanged()
                    await loadHistory()
                }
            }
            .alert("Cari Geçmişi Yüklenemedi", isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )) { Button("Tamam", role: .cancel) { errorMessage = nil } }
            message: { Text(errorMessage ?? "") }
        }
    }

    private var refreshedAccount: CurrentAccountDTO {
        CurrentAccountDTO(
            id: account.id,
            customerId: account.customerId,
            customerName: history?.customerName ?? account.customerName,
            balance: history?.currentBalance ?? account.balance,
            lastUpdated: account.lastUpdated
        )
    }

    private func loadHistory() async {
        guard let accountId = account.id else {
            errorMessage = "Cari hesap kimliği bulunamadı."
            isLoading = false
            return
        }
        isLoading = true
        defer { isLoading = false }
        do { history = try await FinanceService.getCurrentAccountHistory(accountId: accountId) }
        catch { errorMessage = error.localizedDescription }
    }
}

private func currentAccountTransactionRow(_ transaction: CurrentAccountTransactionDTO) -> some View {
    HStack(alignment: .top, spacing: 12) {
        Image(systemName: currentAccountTransactionIcon(transaction.type))
            .foregroundStyle(transaction.amount >= 0 ? Color.orange : Color.green)
            .frame(width: 28, height: 28)
            .background((transaction.amount >= 0 ? Color.orange : Color.green).opacity(0.12))
            .clipShape(Circle())
        VStack(alignment: .leading, spacing: 4) {
            Text(currentAccountTransactionTitle(transaction.type)).font(.subheadline.weight(.semibold))
            Text(transaction.description ?? "Cari hesap hareketi").font(.caption).foregroundStyle(.secondary)
            HStack(spacing: 6) {
                Text(displayFinanceDate(transaction.effectiveDate))
                if let method = transaction.paymentMethod { Text("• \(financePaymentMethodLabel(method))") }
                if let sourceId = transaction.sourceId, transaction.sourceType?.contains("TICKET") == true {
                    Text("• Fiş #\(sourceId)")
                }
            }
            .font(.caption2).foregroundStyle(.secondary)
        }
        Spacer()
        VStack(alignment: .trailing, spacing: 4) {
            Text((transaction.amount >= 0 ? "+" : "") + formatCurrency(transaction.amount))
                .font(.subheadline.weight(.bold))
                .foregroundStyle(transaction.amount >= 0 ? Color.orange : Color.green)
            Text("Bakiye \(formatCurrency(transaction.balanceAfter))")
                .font(.caption2).foregroundStyle(.secondary)
        }
    }
    .padding(.vertical, 4)
}

private func currentAccountTransactionTitle(_ type: String) -> String {
    switch type {
    case "CHARGE": return "Borç oluştu"
    case "PAYMENT": return "Tahsilat"
    case "DISCOUNT": return "İndirim"
    case "REVERSAL": return "Borç geri alındı"
    default: return "Bakiye düzeltmesi"
    }
}

private func currentAccountTransactionIcon(_ type: String) -> String {
    switch type {
    case "CHARGE": return "plus"
    case "PAYMENT": return "banknote"
    case "DISCOUNT": return "percent"
    case "REVERSAL": return "arrow.uturn.backward"
    default: return "slider.horizontal.3"
    }
}

private func financePaymentMethodLabel(_ method: String) -> String {
    switch method {
    case "CASH": return "Nakit"
    case "CREDIT_CARD": return "Kart"
    case "CURRENT_ACCOUNT": return "Cari"
    default: return method
    }
}

private func displayFinanceDate(_ value: String) -> String {
    let input = DateFormatter(); input.locale = Locale(identifier: "en_US_POSIX"); input.dateFormat = "yyyy-MM-dd"
    let output = DateFormatter(); output.locale = Locale(identifier: "tr_TR"); output.dateFormat = "dd.MM.yyyy"
    return input.date(from: value).map(output.string) ?? value
}

struct FinanceReportsTab: View {
    @State private var archives: [MonthlySummaryDTO] = []
    @State private var downloadingMonth: String?
    @State private var pdfPreview: PDFPreviewItem?
    @State private var errorMessage: String?
    @State private var isLoading = true
    
    var body: some View {
        List(archives) { archive in
            DisclosureGroup {
                VStack(spacing: 8) {
                    reportLine("Satış / Ciro", archive.totalIncome, .green)
                    reportLine("Cariye Aktarılan", archive.currentAccountTransferred, .orange)
                    reportLine("Peşin / Kart Tahsilatı", archive.cashCardCollections, .green)
                    reportLine("Eski Cariden Tahsilat", archive.currentAccountCollections, .green)
                    reportLine("Toplam Tahsilat", archive.totalCollected, .green)
                    Divider()
                    reportLine("Servis Doğrudan Maliyeti", archive.serviceDirectCost, .red)
                    reportLine("Diğer Faaliyet Giderleri", archive.otherOperatingExpenses, .red)
                    reportLine("Toplam Kârlılık Gideri", archive.totalProfitExpenses, .red)
                    reportLine("Aylık Faaliyet Kâr / Zarar", archive.netProfit, (archive.netProfit ?? 0) >= 0 ? .green : .red)
                    reportLine("Dönem Sonu Birikimli Kâr / Zarar", archive.closingCumulativeProfit, (archive.closingCumulativeProfit ?? 0) >= 0 ? .green : .red)
                    Button { Task { await download(month: archive.period ?? "") } } label: {
                        if downloadingMonth == archive.period { ProgressView() } else { Label("PDF Raporunu Aç", systemImage: "arrow.down.doc") }
                    }
                    .disabled(archive.period == nil)
                    .buttonStyle(.bordered)
                }
                .padding(.vertical, 8)
            } label: {
                VStack(alignment: .leading, spacing: 4) {
                    Text(archive.displayPeriod ?? archive.period ?? "Ay").font(.headline)
                    Text("Aylık faaliyet sonucu: \(formatCurrency(archive.netProfit))").font(.caption).foregroundStyle((archive.netProfit ?? 0) >= 0 ? .green : .red)
                }
            }
        }
        .overlay { if isLoading { ProgressView("Raporlar yükleniyor...") } }
        .task { await load() }
        .refreshable { await load() }
        .sheet(item: $pdfPreview) { item in
            PDFPreviewSheet(item: item)
        }
        .alert("Rapor İşlemi Başarısız", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Tekrar Dene") { Task { await load() } }
            Button("Tamam", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }

    private func load() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            archives = try await FinanceService.getMonthlyArchives()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
    
    private func download(month: String) async {
        downloadingMonth = month
        do {
            let data = try await FinanceService.downloadMonthlyPDF(month: month)
            pdfPreview = try PDFPreviewItem(
                data: data,
                fileName: "finans-\(month).pdf",
                title: "Aylık Finans Raporu"
            )
        } catch {
            errorMessage = error.localizedDescription
        }
        downloadingMonth = nil
    }

    private func reportLine(_ title: String, _ value: Double?, _ color: Color) -> some View {
        HStack { Text(title).font(.caption); Spacer(); Text(formatCurrency(value)).font(.caption.weight(.semibold)).foregroundStyle(color) }
    }
}

struct AddExpenseSheet: View {
    let date: Date
    let expense: ExpenseDTO?
    let onSaved: () async -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var amount = ""
    @State private var description = ""
    @State private var category = ExpenseCategory.other
    @State private var treatment = FinancialTreatment.operatingExpense
    @State private var selectedDate: Date
    @State private var isSaving = false
    @State private var errorMessage: String?
    
    init(date: Date = Date(), expense: ExpenseDTO? = nil, onSaved: @escaping () async -> Void) {
        self.date = date
        self.expense = expense
        self.onSaved = onSaved
        _selectedDate = State(initialValue: parseFinanceDate(expense?.date) ?? date)
    }

    var body: some View {
        NavigationStack {
            Form {
                TextField("Tutar", text: $amount)
                    .keyboardType(.decimalPad)
                TextField("Açıklama", text: $description)
                DatePicker("Gider tarihi", selection: $selectedDate, displayedComponents: .date)
                Picker("Kategori", selection: $category) {
                    ForEach(ExpenseCategory.allCases, id: \.self) { cat in
                        Text(cat.label).tag(cat)
                    }
                }
                Picker("Muhasebe etkisi", selection: $treatment) {
                    ForEach(FinancialTreatment.allCases, id: \.self) { Text($0.label).tag($0) }
                }
                Section {
                    Text(treatment == .cashOnly ? "Bu işlem kasayı etkiler ancak faaliyet kârından düşmez." : treatment == .serviceDirectExpense ? "Bu gider servislerin doğrudan maliyetinde gösterilir." : "Bu gider faaliyet kârından düşer.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Gider Ekle")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("İptal") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") {
                        Task { await save() }
                    }
                    .disabled(isSaving || parsedAmount <= 0)
                }
            }
            .alert("Gider Kaydedilemedi", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Tamam", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
        }
        .onAppear {
            guard let expense else { return }
            amount = String(expense.amount); description = expense.description
            category = ExpenseCategory(rawValue: expense.category) ?? .other
            treatment = FinancialTreatment(rawValue: expense.financialTreatment ?? "") ?? .operatingExpense
        }
    }

    private var parsedAmount: Double {
        Double(amount.replacingOccurrences(of: ",", with: ".")) ?? 0
    }

    private func save() async {
        isSaving = true
        defer { isSaving = false }
        let value = ExpenseDTO(id: expense?.id, amount: parsedAmount, description: description, date: financeDate(selectedDate), category: category.rawValue, fixedExpenseId: expense?.fixedExpenseId, paymentMethod: expense?.paymentMethod, financialTreatment: treatment.rawValue)
        do {
            if expense == nil { _ = try await FinanceService.addExpense(value) } else { _ = try await FinanceService.updateExpense(value) }
            await onSaved()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

struct PayDebtSheet: View {
    let account: CurrentAccountDTO
    let onPaid: () async -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var payment = ""
    @State private var discount = ""
    @State private var collectionDate = Date()
    @State private var paymentMethod = FinancePaymentMethod.cash
    @State private var notes = ""
    @State private var isPaying = false
    @State private var errorMessage: String?
    
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    LabeledContent("Müşteri", value: account.customerName ?? "-")
                    LabeledContent("Bakiye", value: formatCurrency(account.balance))
                }
                TextField("Tahsilat", text: $payment).keyboardType(.decimalPad)
                TextField("İndirim", text: $discount).keyboardType(.decimalPad)
                DatePicker("Tahsilat tarihi", selection: $collectionDate, displayedComponents: .date)
                Picker("Ödeme yöntemi", selection: $paymentMethod) {
                    ForEach(FinancePaymentMethod.allCases, id: \.self) { Text($0.label).tag($0) }
                }
                TextField("Tahsilat notu", text: $notes, axis: .vertical)
            }
            .navigationTitle("Borç Tahsilatı")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("İptal") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Tahsil Et") {
                        Task { await payDebt() }
                    }
                    .disabled(isPaying || parsedPayment <= 0)
                    .readOnlyProtected()
                }
            }
            .alert("Tahsilat Kaydedilemedi", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Tamam", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
        }
    }

    private var parsedPayment: Double {
        Double(payment.replacingOccurrences(of: ",", with: ".")) ?? 0
    }

    private func payDebt() async {
        guard let accountId = account.id else {
            errorMessage = "Cari hesap kimliği bulunamadı."
            return
        }
        isPaying = true
        defer { isPaying = false }
        do {
            _ = try await FinanceService.payDebt(
                accountId: accountId,
                paymentAmount: parsedPayment,
                discount: Double(discount.replacingOccurrences(of: ",", with: ".")) ?? 0,
                collectionDate: financeDate(collectionDate),
                paymentMethod: paymentMethod.rawValue,
                notes: notes.isEmpty ? nil : notes
            )
            await onPaid()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

private func financeMetric(_ title: String, value: Double?, color: Color) -> some View {
    VStack(spacing: 6) {
        Text(title).font(.caption).foregroundStyle(.secondary)
        Text(formatCurrency(value))
            .font(.subheadline.weight(.bold))
            .foregroundColor(color)
    }
    .frame(maxWidth: .infinity)
    .padding(.vertical, 14)
    .background(PusulaTheme.raisedSurface)
    .overlay {
        RoundedRectangle(cornerRadius: PusulaTheme.radius)
            .stroke(PusulaTheme.border, lineWidth: 1)
    }
    .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
}

private func sectionCard<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
    VStack(alignment: .leading, spacing: 10) {
        Text(title).font(.subheadline.weight(.semibold))
        VStack(spacing: 8) { content() }
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .pusulaCard()
}
