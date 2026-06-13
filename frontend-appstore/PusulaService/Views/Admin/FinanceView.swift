import SwiftUI
import Charts

struct FinanceView: View {
    @State private var selectedTab = 0
    private let tabs = ["Günlük", "Analiz", "Cari", "Rapor"]
    
    var body: some View {
        VStack(spacing: 0) {
            Picker("Sekme", selection: $selectedTab) {
                ForEach(Array(tabs.enumerated()), id: \.offset) { index, tab in
                    Text(tab).tag(index)
                }
            }
            .pickerStyle(.segmented)
            .padding()
            
            TabView(selection: $selectedTab) {
                FinanceDailyTab().tag(0)
                FinanceAnalysisTab().tag(1)
                FinanceAccountsTab().tag(2)
                FinanceReportsTab().tag(3)
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
        }
        .navigationTitle("Finans")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct FinanceDailyTab: View {
    @State private var summary: DailySummaryDTO?
    @State private var fixedExpenses: [FixedExpenseDefinitionDTO] = []
    @State private var showAddExpense = false
    @State private var isLoading = true
    
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                if let summary {
                    HStack(spacing: 12) {
                        financeMetric("Gelir", value: summary.totalIncome, color: .green)
                        financeMetric("Gider", value: summary.totalExpense, color: .red)
                        financeMetric("Net", value: summary.netCash, color: .cyan)
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
                        .buttonStyle(.borderedProminent)
                        .tint(.cyan)
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
                    ForEach(Array((summary?.expenseDetails ?? []).enumerated()), id: \.offset) { _, item in
                        HStack {
                            VStack(alignment: .leading) {
                                Text(item.description ?? item.category ?? "Gider")
                                if let category = item.category {
                                    Text(category).font(.caption).foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            Text(formatCurrency(item.amount))
                                .foregroundColor(.red)
                        }
                        .font(.subheadline)
                    }
                }
            }
            .padding()
        }
        .overlay { if isLoading { ProgressView() } }
        .task { await load() }
        .refreshable { await load() }
        .sheet(isPresented: $showAddExpense) {
            AddExpenseSheet { await load() }
        }
    }
    
    private func load() async {
        isLoading = true
        do {
            async let summaryTask = FinanceService.getDailySummary()
            async let fixedTask = FinanceService.getFixedExpenses()
            let (loadedSummary, loadedFixed) = try await (summaryTask, fixedTask)
            await MainActor.run {
                summary = loadedSummary
                fixedExpenses = loadedFixed
                isLoading = false
            }
        } catch {
            isLoading = false
        }
    }
    
    private func closeDay() async {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        let today = formatter.string(from: Date())
        _ = try? await FinanceService.closeDay(date: today, companyId: SessionManager.shared.companyId)
        await load()
    }
}

struct FinanceAnalysisTab: View {
    @State private var dailyTotals: [DailyTotalDTO] = []
    @State private var categoryReport: CategoryReportDTO?
    
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
        .task { await load() }
    }
    
    private func load() async {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        let end = formatter.string(from: Date())
        let start = formatter.string(from: Calendar.current.date(from: Calendar.current.dateComponents([.year, .month], from: Date())) ?? Date())
        dailyTotals = (try? await FinanceService.getDailyTotals()) ?? []
        categoryReport = try? await FinanceService.getCategoryReport(startDate: start, endDate: end)
    }
}

struct FinanceAccountsTab: View {
    @State private var accounts: [CurrentAccountDTO] = []
    @State private var selectedAccount: CurrentAccountDTO?
    @State private var showPaySheet = false
    
    var body: some View {
        List(Array(accounts.enumerated()), id: \.offset) { _, account in
            Button(action: {
                selectedAccount = account
                showPaySheet = true
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
            if accounts.isEmpty {
                ContentUnavailableView("Cari hesap yok", systemImage: "person.crop.circle.badge.exclamationmark")
            }
        }
        .task { accounts = (try? await FinanceService.getCurrentAccounts()) ?? [] }
        .sheet(isPresented: $showPaySheet) {
            if let account = selectedAccount {
                PayDebtSheet(account: account) {
                    accounts = (try? await FinanceService.getCurrentAccounts()) ?? []
                }
            }
        }
    }
}

struct FinanceReportsTab: View {
    @State private var archives: [MonthlySummaryDTO] = []
    @State private var downloadingMonth: String?
    
    var body: some View {
        List(archives) { archive in
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(archive.displayPeriod ?? archive.period ?? "Ay")
                        .font(.headline)
                    Text("Net: \(formatCurrency(archive.netProfit))")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button(action: { Task { await download(month: archive.period ?? "") } }) {
                    if downloadingMonth == archive.period {
                        ProgressView()
                    } else {
                        Image(systemName: "arrow.down.doc")
                    }
                }
                .disabled(archive.period == nil)
            }
        }
        .task { archives = (try? await FinanceService.getMonthlyArchives()) ?? [] }
    }
    
    private func download(month: String) async {
        downloadingMonth = month
        if let data = try? await FinanceService.downloadMonthlyPDF(month: month) {
            sharePDF(data: data, fileName: "finans-\(month).pdf")
        }
        downloadingMonth = nil
    }
}

struct AddExpenseSheet: View {
    let onSaved: () async -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var amount = ""
    @State private var description = ""
    @State private var category = ExpenseCategory.other
    
    var body: some View {
        NavigationStack {
            Form {
                TextField("Tutar", text: $amount)
                    .keyboardType(.decimalPad)
                TextField("Açıklama", text: $description)
                Picker("Kategori", selection: $category) {
                    ForEach(ExpenseCategory.allCases, id: \.self) { cat in
                        Text(cat.label).tag(cat)
                    }
                }
            }
            .navigationTitle("Gider Ekle")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("İptal") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") {
                        Task {
                            let formatter = DateFormatter()
                            formatter.dateFormat = "yyyy-MM-dd"
                            let expense = ExpenseDTO(
                                amount: Double(amount.replacingOccurrences(of: ",", with: ".")) ?? 0,
                                description: description,
                                date: formatter.string(from: Date()),
                                category: category.rawValue
                            )
                            _ = try? await FinanceService.addExpense(expense)
                            await onSaved()
                            dismiss()
                        }
                    }
                }
            }
        }
    }
}

struct PayDebtSheet: View {
    let account: CurrentAccountDTO
    let onPaid: () async -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var payment = ""
    @State private var discount = ""
    
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    LabeledContent("Müşteri", value: account.customerName ?? "-")
                    LabeledContent("Bakiye", value: formatCurrency(account.balance))
                }
                TextField("Tahsilat", text: $payment).keyboardType(.decimalPad)
                TextField("İndirim", text: $discount).keyboardType(.decimalPad)
            }
            .navigationTitle("Borç Tahsilatı")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("İptal") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Tahsil Et") {
                        Task {
                            guard let accountId = account.id else { return }
                            _ = try? await FinanceService.payDebt(
                                accountId: accountId,
                                paymentAmount: Double(payment.replacingOccurrences(of: ",", with: ".")) ?? 0,
                                discount: Double(discount.replacingOccurrences(of: ",", with: ".")) ?? 0
                            )
                            await onPaid()
                            dismiss()
                        }
                    }
                    .readOnlyProtected()
                }
            }
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
    .background(.regularMaterial)
    .clipShape(RoundedRectangle(cornerRadius: 12))
}

private func sectionCard<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
    VStack(alignment: .leading, spacing: 10) {
        Text(title).font(.subheadline.weight(.semibold))
        VStack(spacing: 8) { content() }
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding()
    .background(.regularMaterial)
    .clipShape(RoundedRectangle(cornerRadius: 14))
}
