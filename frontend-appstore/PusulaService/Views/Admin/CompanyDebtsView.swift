import SwiftUI

struct CompanyDebtsView: View {
    private enum Filter: String, CaseIterable { case open = "Açık", partial = "Kısmi", paid = "Ödenmiş", all = "Tümü" }
    @State private var debts: [CompanyDebtDTO] = []
    @State private var filter: Filter = .open
    @State private var selectedDebt: CompanyDebtDTO?
    @State private var editingDebt: CompanyDebtDTO?
    @State private var showEditor = false
    @State private var pdfPreview: PDFPreviewItem?
    @State private var isLoading = true
    @State private var errorMessage: String?

    private var filtered: [CompanyDebtDTO] {
        debts.filter { debt in
            let remaining = debt.remainingAmount ?? 0
            let original = debt.originalAmount ?? 0
            switch filter {
            case .open: return remaining > 0
            case .partial: return remaining > 0 && remaining < original
            case .paid: return remaining <= 0
            case .all: return true
            }
        }
    }

    private var totalRemaining: Double { debts.reduce(0) { $0 + max(0, $1.remainingAmount ?? 0) } }

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 10) {
                HStack {
                    VStack(alignment: .leading) {
                        Text("Toplam kalan borç").font(.caption).foregroundStyle(.secondary)
                        Text(formatCurrency(totalRemaining)).font(.title2.bold()).foregroundStyle(.red)
                    }
                    Spacer()
                    Button { Task { await downloadPDF() } } label: { Label("PDF", systemImage: "arrow.down.doc") }
                        .featureGated("ADVANCED_REPORT_EXPORT", showUpgradeHint: true)
                }
                Picker("Filtre", selection: $filter) {
                    ForEach(Filter.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                }.pickerStyle(.segmented)
            }
            .padding()

            List(filtered) { debt in
                Button { selectedDebt = debt } label: {
                    VStack(alignment: .leading, spacing: 7) {
                        HStack {
                            Text(debt.creditorName ?? "Alacaklı").font(.headline)
                            Spacer()
                            Text(formatCurrency(debt.remainingAmount)).font(.headline).foregroundStyle(.red)
                        }
                        Text(debt.description ?? "Açıklama yok").font(.subheadline).foregroundStyle(.secondary)
                        HStack {
                            Label(debt.debtDate ?? "-", systemImage: "calendar")
                            Spacer()
                            if let due = debt.dueDate { Text("Vade: \(due)") }
                        }.font(.caption).foregroundStyle(.secondary)
                    }.padding(.vertical, 4)
                }.buttonStyle(.plain)
                .swipeActions(edge: .leading) {
                    Button("Düzenle") { editingDebt = debt; showEditor = true }.tint(.blue)
                }
            }
            .listStyle(.plain)
            .overlay {
                if isLoading { ProgressView("Borçlar yükleniyor...") }
                else if filtered.isEmpty { ContentUnavailableView("Borç bulunamadı", systemImage: "checkmark.circle") }
            }
        }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { editingDebt = nil; showEditor = true } label: { Image(systemName: "plus") }.readOnlyProtected()
            }
        }
        .task { await load() }
        .refreshable { await load() }
        .sheet(isPresented: $showEditor) { DebtEditorSheet(debt: editingDebt) { await load() } }
        .sheet(item: $selectedDebt) { debt in DebtDetailSheet(debt: debt) { await load() } }
        .sheet(item: $pdfPreview) { PDFPreviewSheet(item: $0) }
        .alert("İşlem Başarısız", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Tamam", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }

    private func load() async {
        isLoading = true; defer { isLoading = false }
        do { debts = try await FinanceService.getCompanyDebts() } catch { errorMessage = error.localizedDescription }
    }

    private func downloadPDF() async {
        do {
            pdfPreview = try PDFPreviewItem(data: try await FinanceService.downloadCompanyDebtsPDF(), fileName: "acik-isletme-borclari.pdf", title: "Açık İşletme Borçları")
        } catch { errorMessage = error.localizedDescription }
    }
}

private struct DebtEditorSheet: View {
    let debt: CompanyDebtDTO?
    let onSaved: () async -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var creditorName = ""
    @State private var description = ""
    @State private var amount = ""
    @State private var category = ExpenseCategory.other
    @State private var debtDate = Date()
    @State private var dueDate = Date()
    @State private var hasDueDate = false
    @State private var phone = ""
    @State private var notes = ""
    @State private var isSaving = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                TextField("Alacaklı kişi / firma", text: $creditorName)
                TextField("Açıklama", text: $description, axis: .vertical)
                TextField("Borç tutarı", text: $amount).keyboardType(.decimalPad).disabled(debt != nil)
                Picker("Kategori", selection: $category) { ForEach(ExpenseCategory.allCases.filter { $0 != .deviceSale }, id: \.self) { Text($0.label).tag($0) } }
                DatePicker("Borç tarihi", selection: $debtDate, displayedComponents: .date)
                Toggle("Vade tarihi", isOn: $hasDueDate)
                if hasDueDate { DatePicker("Vade", selection: $dueDate, displayedComponents: .date) }
                TextField("Telefon", text: $phone).keyboardType(.phonePad)
                TextField("Not", text: $notes, axis: .vertical)
            }
            .navigationTitle(debt == nil ? "Borç Ekle" : "Borcu Düzenle")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("İptal") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Kaydet") { Task { await save() } }.disabled(isSaving || creditorName.trimmingCharacters(in: .whitespaces).isEmpty || parsedAmount <= 0) }
            }
            .onAppear { populate() }
            .alert("Borç Kaydedilemedi", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) { Button("Tamam", role: .cancel) {} } message: { Text(errorMessage ?? "") }
        }
    }

    private var parsedAmount: Double { Double(amount.replacingOccurrences(of: ",", with: ".")) ?? 0 }
    private func populate() {
        guard let debt else { return }
        creditorName = debt.creditorName ?? ""; description = debt.description ?? ""; amount = String(debt.originalAmount ?? 0)
        category = ExpenseCategory(rawValue: debt.expenseCategory ?? "") ?? .other
        debtDate = parseFinanceDate(debt.debtDate) ?? Date(); phone = debt.creditorPhone ?? ""; notes = debt.notes ?? ""
        if let date = parseFinanceDate(debt.dueDate) { dueDate = date; hasDueDate = true }
    }
    private func save() async {
        isSaving = true; defer { isSaving = false }
        var value = debt ?? CompanyDebtDTO()
        value.creditorName = creditorName.trimmingCharacters(in: .whitespacesAndNewlines)
        value.description = description; value.originalAmount = parsedAmount; value.remainingAmount = debt?.remainingAmount ?? parsedAmount
        value.expenseCategory = category.rawValue; value.debtDate = financeDate(debtDate); value.dueDate = hasDueDate ? financeDate(dueDate) : nil
        value.creditorPhone = phone.isEmpty ? nil : phone; value.notes = notes.isEmpty ? nil : notes
        do {
            if debt == nil { _ = try await FinanceService.createCompanyDebt(value) } else { _ = try await FinanceService.updateCompanyDebt(value) }
            await onSaved(); dismiss()
        } catch { errorMessage = error.localizedDescription }
    }
}

private struct DebtDetailSheet: View {
    let debt: CompanyDebtDTO
    let onChanged: () async -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var payments: [CompanyDebtPaymentDTO] = []
    @State private var additions: [CompanyDebtAdditionDTO] = []
    @State private var mode: DebtMovementSheet.Mode?
    @State private var deleteConfirmation = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            List {
                Section("Özet") {
                    LabeledContent("Alacaklı", value: debt.creditorName ?? "-")
                    LabeledContent("Toplam", value: formatCurrency(debt.originalAmount))
                    LabeledContent("Kalan", value: formatCurrency(debt.remainingAmount))
                    if let due = debt.dueDate { LabeledContent("Vade", value: due) }
                }
                Section {
                    Button { mode = .payment } label: { Label("Ödeme Yap", systemImage: "banknote") }.disabled((debt.remainingAmount ?? 0) <= 0)
                    Button { mode = .addition } label: { Label("Borç Üzerine Ekle", systemImage: "plus.circle") }
                }.readOnlyProtected()
                Section("Hareketler") {
                    ForEach(Array(movements.enumerated()), id: \.offset) { _, item in
                        HStack {
                            VStack(alignment: .leading) { Text(item.title); Text(item.date).font(.caption).foregroundStyle(.secondary) }
                            Spacer(); Text(item.amount).foregroundStyle(item.isPayment ? .green : .red)
                        }
                        .swipeActions {
                            if item.isPayment, let paymentId = item.paymentId, let debtId = debt.id {
                                Button("Geri Al", role: .destructive) { Task { await deletePayment(debtId, paymentId) } }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Borç Detayı")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Kapat") { dismiss() } }
                ToolbarItem(placement: .primaryAction) { Button(role: .destructive) { deleteConfirmation = true } label: { Image(systemName: "trash") } }
            }
            .task { await load() }
            .sheet(item: $mode) { selected in DebtMovementSheet(debt: debt, mode: selected) { await load(); await onChanged() } }
            .confirmationDialog("Bu borç silinsin mi?", isPresented: $deleteConfirmation) { Button("Borcu Sil", role: .destructive) { Task { await deleteDebt() } } }
            .alert("İşlem Başarısız", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) { Button("Tamam", role: .cancel) {} } message: { Text(errorMessage ?? "") }
        }
    }

    private var movements: [(key: String, title: String, date: String, amount: String, isPayment: Bool, paymentId: Int?)] {
        let p = payments.map { ("p\($0.id ?? 0)", $0.notes?.isEmpty == false ? $0.notes! : "Ödeme", $0.paymentDate ?? "-", "−" + formatCurrency($0.amount), true, $0.id) }
        let a = additions.map { ("a\($0.id ?? 0)", $0.notes?.isEmpty == false ? $0.notes! : "Borç ilavesi", $0.additionDate ?? "-", "+" + formatCurrency($0.amount), false, nil as Int?) }
        return (p + a).sorted { $0.2 > $1.2 }
    }
    private func load() async {
        guard let id = debt.id else { return }
        do { async let p = FinanceService.getCompanyDebtPayments(id: id); async let a = FinanceService.getCompanyDebtAdditions(id: id); (payments, additions) = try await (p, a) } catch { errorMessage = error.localizedDescription }
    }
    private func deletePayment(_ debtId: Int, _ paymentId: Int) async { do { try await FinanceService.deleteCompanyDebtPayment(debtId: debtId, paymentId: paymentId); await load(); await onChanged() } catch { errorMessage = error.localizedDescription } }
    private func deleteDebt() async { guard let id = debt.id else { return }; do { try await FinanceService.deleteCompanyDebt(id: id); await onChanged(); dismiss() } catch { errorMessage = error.localizedDescription } }
}

private struct DebtMovementSheet: View, Identifiable {
    enum Mode: String, Identifiable { case payment, addition; var id: String { rawValue } }
    let debt: CompanyDebtDTO; let mode: Mode; let onSaved: () async -> Void
    var id: String { mode.rawValue }
    @Environment(\.dismiss) private var dismiss
    @State private var amount = ""; @State private var date = Date(); @State private var notes = ""; @State private var errorMessage: String?
    var body: some View {
        NavigationStack {
            Form {
                LabeledContent("Kalan borç", value: formatCurrency(debt.remainingAmount))
                TextField("Tutar", text: $amount).keyboardType(.decimalPad)
                DatePicker(mode == .payment ? "Ödeme tarihi" : "İlave tarihi", selection: $date, displayedComponents: .date)
                TextField("Not", text: $notes, axis: .vertical)
            }
            .navigationTitle(mode == .payment ? "Borç Ödemesi" : "Borç İlavesi")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("İptal") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Kaydet") { Task { await save() } }.disabled(parsedAmount <= 0) }
            }
            .alert("Hareket Kaydedilemedi", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) { Button("Tamam", role: .cancel) {} } message: { Text(errorMessage ?? "") }
        }
    }
    private var parsedAmount: Double { Double(amount.replacingOccurrences(of: ",", with: ".")) ?? 0 }
    private func save() async {
        guard let id = debt.id else { return }
        do {
            if mode == .payment { _ = try await FinanceService.payCompanyDebt(id: id, request: .init(amount: parsedAmount, paymentDate: financeDate(date), notes: notes.isEmpty ? nil : notes)) }
            else { _ = try await FinanceService.addToCompanyDebt(id: id, request: .init(amount: parsedAmount, additionDate: financeDate(date), notes: notes.isEmpty ? nil : notes)) }
            await onSaved(); dismiss()
        } catch { errorMessage = error.localizedDescription }
    }
}

func financeDate(_ date: Date) -> String { let f = DateFormatter(); f.calendar = Calendar(identifier: .gregorian); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd"; return f.string(from: date) }
func parseFinanceDate(_ value: String?) -> Date? { guard let value else { return nil }; let f = DateFormatter(); f.calendar = Calendar(identifier: .gregorian); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd"; return f.date(from: value) }
