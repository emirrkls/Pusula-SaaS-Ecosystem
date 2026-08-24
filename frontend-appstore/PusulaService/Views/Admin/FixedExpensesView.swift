import SwiftUI

struct FixedExpensesView: View {
    @State private var expenses: [FixedExpenseDefinitionDTO] = []
    @State private var editing: FixedExpenseDefinitionDTO?
    @State private var paying: FixedExpenseDefinitionDTO?
    @State private var showEditor = false
    @State private var isLoading = true
    @State private var errorMessage: String?

    var body: some View {
        List {
            Section {
                ForEach(expenses) { expense in
                    VStack(alignment: .leading, spacing: 7) {
                        HStack {
                            Text(expense.name ?? "Sabit gider").font(.headline)
                            Spacer()
                            Text(formatCurrency(expense.defaultAmount)).font(.headline)
                        }
                        HStack {
                            Text(expense.frequency == "WEEKLY" ? "Haftalık" : "Aylık")
                            Text("• (ExpenseCategory(rawValue: expense.category ?? "")?.label ?? expense.category ?? "Diğer")")
                            Spacer()
                            if expense.paidThisMonth == true {
                                Label("Ödendi", systemImage: "checkmark.circle.fill").foregroundStyle(.green)
                            } else {
                                Text("Ayın \(expense.dayOfMonth ?? 1). günü").foregroundStyle(.orange)
                            }
                        }.font(.caption)
                        if (expense.paidAmountThisMonth ?? 0) > 0 { Text("Bu ay ödenen: \(formatCurrency(expense.paidAmountThisMonth))").font(.caption).foregroundStyle(.secondary) }
                        HStack {
                            Button("Ödeme Gir") { paying = expense }.buttonStyle(.borderedProminent).tint(PusulaTheme.accent)
                            Button("Düzenle") { editing = expense; showEditor = true }.buttonStyle(.bordered)
                        }.readOnlyProtected()
                    }.padding(.vertical, 5)
                    .swipeActions {
                        Button("Sil", role: .destructive) { Task { await delete(expense) } }
                    }
                }
            } header: { Text("Düzenli ödemeler") }
        }
        .listStyle(.insetGrouped)
        .overlay {
            if isLoading { ProgressView("Sabit giderler yükleniyor...") }
            else if expenses.isEmpty { ContentUnavailableView("Sabit gider bulunamadı", systemImage: "repeat.circle", description: Text("Kira, maaş ve diğer düzenli ödemeleri ekleyebilirsiniz.")) }
        }
        .toolbar { ToolbarItem(placement: .primaryAction) { Button { editing = nil; showEditor = true } label: { Image(systemName: "plus") }.readOnlyProtected() } }
        .task { await load() }.refreshable { await load() }
        .sheet(isPresented: $showEditor) { FixedExpenseEditorSheet(expense: editing) { await load() } }
        .sheet(item: $paying) { FixedExpensePaymentSheet(expense: $0) { await load() } }
        .alert("İşlem Başarısız", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) { Button("Tamam", role: .cancel) {} } message: { Text(errorMessage ?? "") }
    }
    private func load() async { isLoading = true; defer { isLoading = false }; do { expenses = try await FinanceService.getFixedExpenses() } catch { errorMessage = error.localizedDescription } }
    private func delete(_ expense: FixedExpenseDefinitionDTO) async { guard let id = expense.id else { return }; do { try await FinanceService.deleteFixedExpense(id: id); await load() } catch { errorMessage = error.localizedDescription } }
}

private struct FixedExpenseEditorSheet: View {
    let expense: FixedExpenseDefinitionDTO?; let onSaved: () async -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""; @State private var amount = ""; @State private var category = ExpenseCategory.other
    @State private var day = 1; @State private var frequency = "MONTHLY"; @State private var description = ""; @State private var errorMessage: String?
    var body: some View {
        NavigationStack {
            Form {
                TextField("Gider adı", text: $name)
                TextField("Varsayılan tutar", text: $amount).keyboardType(.decimalPad)
                Picker("Kategori", selection: $category) { ForEach(ExpenseCategory.allCases.filter { $0 != .deviceSale }, id: \.self) { Text($0.label).tag($0) } }
                Picker("Sıklık", selection: $frequency) { Text("Aylık").tag("MONTHLY"); Text("Haftalık").tag("WEEKLY") }
                Stepper("Ödeme günü: \(day)", value: $day, in: 1...28)
                TextField("Açıklama", text: $description, axis: .vertical)
            }
            .navigationTitle(expense == nil ? "Sabit Gider Ekle" : "Sabit Gideri Düzenle")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("İptal") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Kaydet") { Task { await save() } }.disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || parsedAmount <= 0) }
            }
            .onAppear { if let expense { name = expense.name ?? ""; amount = String(expense.defaultAmount ?? 0); category = ExpenseCategory(rawValue: expense.category ?? "") ?? .other; day = expense.dayOfMonth ?? 1; frequency = expense.frequency ?? "MONTHLY"; description = expense.description ?? "" } }
            .alert("Sabit Gider Kaydedilemedi", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) { Button("Tamam", role: .cancel) {} } message: { Text(errorMessage ?? "") }
        }
    }
    private var parsedAmount: Double { Double(amount.replacingOccurrences(of: ",", with: ".")) ?? 0 }
    private func save() async {
        let dto = FixedExpenseDefinitionDTO(id: expense?.id, name: name, defaultAmount: parsedAmount, category: category.rawValue, dayOfMonth: day, description: description, paidThisMonth: expense?.paidThisMonth, paidAmountThisMonth: expense?.paidAmountThisMonth, frequency: frequency, linkedExpenseId: expense?.linkedExpenseId, linkedExpenseName: expense?.linkedExpenseName, linkedPaymentsThisMonth: expense?.linkedPaymentsThisMonth)
        do { if expense == nil { _ = try await FinanceService.createFixedExpense(dto) } else { _ = try await FinanceService.updateFixedExpense(dto) }; await onSaved(); dismiss() } catch { errorMessage = error.localizedDescription }
    }
}

private struct FixedExpensePaymentSheet: View {
    let expense: FixedExpenseDefinitionDTO; let onSaved: () async -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var amount = ""; @State private var date = Date(); @State private var errorMessage: String?
    var body: some View {
        NavigationStack {
            Form {
                LabeledContent("Gider", value: expense.name ?? "-")
                TextField("Ödenen tutar", text: $amount).keyboardType(.decimalPad)
                DatePicker("Ödeme tarihi", selection: $date, displayedComponents: .date)
            }
            .navigationTitle("Sabit Gider Ödemesi")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("İptal") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Öde") { Task { await pay() } }.disabled(parsedAmount <= 0) }
            }
            .onAppear { amount = String(expense.defaultAmount ?? 0) }
            .alert("Ödeme Kaydedilemedi", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) { Button("Tamam", role: .cancel) {} } message: { Text(errorMessage ?? "") }
        }
    }
    private var parsedAmount: Double { Double(amount.replacingOccurrences(of: ",", with: ".")) ?? 0 }
    private func pay() async { guard let id = expense.id else { return }; do { _ = try await FinanceService.payFixedExpense(id: id, date: financeDate(date), amount: parsedAmount); await onSaved(); dismiss() } catch { errorMessage = error.localizedDescription } }
}
