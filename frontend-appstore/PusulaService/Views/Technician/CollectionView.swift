import SwiftUI

/// Price → payer allocation → collection. A discount is never a payment.
struct CollectionView: View {
    let ticket: FieldTicketDTO
    let partsTotal: Decimal
    let onComplete: () async -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var laborFee = ""
    @State private var collectedAmount = ""
    @State private var selectedMethod: PaymentMethodOption = .cash
    @State private var discountEnabled = false
    @State private var discountType: CollectionPricing.DiscountType = .amount
    @State private var discountText = ""
    @State private var warranty = false
    @State private var transferToOrganization = false
    @State private var billingPartyAmount = ""
    @State private var billingOrganizations: [AccountPartyOptionDTO] = []
    @State private var selectedBillingPartyId: Int?
    @State private var organizationsError: String?
    @State private var technicianNote = ""
    @State private var isProcessing = false
    @State private var errorMessage: String?
    @State private var showDebtConfirmation = false
    // An ambiguous response must be retried with exactly the same key and payload.
    @State private var requestId = UUID()
    @State private var pendingRequest: CollectionRequest?
#if DEBUG
    var testLoaders: CollectionTestLoaders?
#endif

    init(ticket: FieldTicketDTO, partsTotal: Decimal, onComplete: @escaping () async -> Void) {
        self.ticket = ticket; self.partsTotal = partsTotal; self.onComplete = onComplete
        func text(_ value: Double?) -> String { value.map { NSDecimalNumber(decimal: CollectionPricing.round(Decimal(string: String($0)) ?? 0)).stringValue } ?? "" }
        _laborFee = State(initialValue: text(ticket.laborFee))
        _collectedAmount = State(initialValue: text(ticket.collectedAmount))
        _discountEnabled = State(initialValue: (ticket.discountAmount ?? 0) > 0)
        _discountType = State(initialValue: ticket.discountType == "PERCENT" ? .percent : .amount)
        _discountText = State(initialValue: text(ticket.discountValue))
        _transferToOrganization = State(initialValue: (ticket.billingPartyAmount ?? 0) > 0)
        _billingPartyAmount = State(initialValue: text(ticket.billingPartyAmount))
        _selectedBillingPartyId = State(initialValue: ticket.billingPartyId)
        _warranty = State(initialValue: ticket.paymentMethod == "WARRANTY")
        _selectedMethod = State(initialValue: ticket.paymentMethod == "CREDIT_CARD" ? .creditCard
                                : ticket.paymentMethod == "CURRENT_ACCOUNT" ? .currentAccount : .cash)
    }

    private var internalWarranty: Bool { warranty && !transferToOrganization }
    private var noCollection: Bool { warranty || selectedMethod == .currentAccount }
    private var parts: Decimal { internalWarranty ? 0 : partsTotal }
    private var labor: Decimal { (try? CollectionPricing.parse(internalWarranty ? "0" : laborFee)) ?? 0 }
    private var discount: Decimal { (try? CollectionPricing.parse(discountEnabled && !warranty ? discountText : "0")) ?? 0 }
    private var organization: Decimal { (try? CollectionPricing.parse(transferToOrganization ? billingPartyAmount : "0")) ?? 0 }
    private var collected: Decimal { (try? CollectionPricing.parse(noCollection ? "0" : collectedAmount)) ?? 0 }
    private var totals: CollectionPricing.Totals? {
        try? CollectionPricing.calculate(parts: parts, labor: labor, discountType: discountType,
                                         discountValue: discount, organization: organization, collected: collected)
    }
    private var pricing: CollectionPricing.Totals? {
        try? CollectionPricing.calculate(parts: parts, labor: labor, discountType: discountType,
                                         discountValue: discount, organization: 0, collected: 0)
    }
    private var validation: String? {
        do {
            _ = try CollectionPricing.parse(internalWarranty ? "0" : laborFee)
            _ = try CollectionPricing.parse(discountEnabled && !warranty ? discountText : "0")
            _ = try CollectionPricing.parse(transferToOrganization ? billingPartyAmount : "0")
            _ = try CollectionPricing.parse(noCollection ? "0" : collectedAmount)
            _ = try CollectionPricing.calculate(parts: parts, labor: labor, discountType: discountType,
                                                discountValue: discount, organization: organization, collected: collected)
            if transferToOrganization && (selectedBillingPartyId == nil || organization <= 0) {
                return "Kurum seçin ve aktarılacak tutarı girin."
            }
            if warranty && transferToOrganization && organization != (pricing?.net ?? 0) {
                return "Kurum garantisinde fiş toplamının tamamını kuruma aktarın."
            }
            return nil
        } catch { return error.localizedDescription }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Servis fişi #\(ticket.id) · \(ticket.customerName ?? "Müşteri")")
                        .font(.subheadline).foregroundStyle(.secondary)
                    feesCard
                    organizationCard
                    collectionCard
                    DisclosureGroup("Kapanış notu ve garanti") {
                        VStack(alignment: .leading, spacing: 12) {
                            TextField("Yapılan işlem / kapanış notu", text: $technicianNote, axis: .vertical)
                                .lineLimit(2...6).textFieldStyle(.roundedBorder)
                            Toggle("Garanti kapsamında kapat", isOn: $warranty)
                            Text("İşletme garantisinde satış oluşmaz; kurum garantisinde fişin tamamı kurum carisine yazılır. Ücretsiz normal işlem için iskonto kullanın.")
                                .font(.caption).foregroundStyle(.secondary)
                        }.padding(.top, 8)
                    }.pusulaCard()
                    if let previous = ticket.customerBalance, previous != 0 {
                        Text("Önceki cari bakiye: \(money(Decimal(string: String(previous)) ?? 0)). Bu fişin toplamına dahil değildir.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    if let validation { Text(validation).font(.caption).foregroundStyle(.red) }
                    if let errorMessage { Text(errorMessage).font(.caption).foregroundStyle(.red) }
                    if pendingRequest != nil {
                        Text("İşlem sonucu henüz teyit edilemedi. Aynı bilgilerle tekrar deneyin; ikinci tahsilat oluşturulmaz.")
                            .font(.caption).foregroundStyle(.orange)
                    }
                }.padding().disabled(isProcessing || pendingRequest != nil)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(PusulaTheme.page)
            .navigationTitle("Tahsilat").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) { Button("İptal") { dismiss() }.disabled(isProcessing) } }
            .safeAreaInset(edge: .bottom) {
                Button {
                    if pendingRequest != nil { Task { await processPayment() } }
                    else if (totals?.customerDebt ?? 0) > 0 || organization > 0 { showDebtConfirmation = true }
                    else { Task { await processPayment() } }
                } label: {
                    HStack {
                        if isProcessing { ProgressView().tint(.white) }
                        Text(pendingRequest != nil ? "Aynı İşlemi Tekrar Dene" : "Tahsilatı Kaydet ve İşi Tamamla")
                            .font(.headline).multilineTextAlignment(.center)
                    }.frame(maxWidth: .infinity).padding(.vertical, 14)
                }
                .buttonStyle(.borderedProminent).tint(PusulaTheme.accent)
                .disabled(isProcessing || (pendingRequest == nil && validation != nil))
                .padding().background(PusulaTheme.page)
            }
            .alert("Cari aktarımı onayla", isPresented: $showDebtConfirmation) {
                Button("Vazgeç", role: .cancel) {}
                Button("Onayla ve Tamamla") { Task { await processPayment() } }
            } message: {
                Text("Kurum carisine: \(money(organization))\nMüşteri carisine: \(money(totals?.customerDebt ?? 0))")
            }
            .task { await loadOrganizations() }
        }
    }

    private var feesCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("1. Ücret Bilgileri").font(.headline)
            row("Parça toplamı", parts)
            moneyField("Servis / işçilik bedeli", text: $laborFee).disabled(internalWarranty)
            if !warranty {
                Toggle("İskonto ekle", isOn: $discountEnabled)
                if discountEnabled {
                    Picker("İskonto türü", selection: $discountType) {
                        Text("TL").tag(CollectionPricing.DiscountType.amount)
                        Text("% ").tag(CollectionPricing.DiscountType.percent)
                    }.pickerStyle(.segmented)
                    moneyField(discountType == .percent ? "İskonto yüzdesi" : "İskonto tutarı", text: $discountText)
                    row("Ara toplam", pricing?.subtotal ?? 0)
                    row("İskonto", -(pricing?.discount ?? 0))
                }
            }
            Divider()
            row("Fiş toplamı", pricing?.net ?? 0, emphasized: true)
        }.pusulaCard()
    }

    private var organizationCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("2. Ödemeyi Üstlenen").font(.headline)
            Toggle("Tamamını veya bir kısmını kurum carisine aktar", isOn: $transferToOrganization)
            if transferToOrganization {
                Picker("Kurum", selection: $selectedBillingPartyId) {
                    Text("Kurum seçin").tag(nil as Int?)
                    ForEach(billingOrganizations) { Text($0.displayName).tag(Optional($0.id)) }
                }
                if let organizationsError {
                    Text(organizationsError).font(.caption).foregroundStyle(.red)
                    Button("Kurumları Tekrar Yükle") { Task { await loadOrganizations() } }
                }
                moneyField("Kuruma aktarılacak tutar", text: $billingPartyAmount)
                Button("Fişin tamamını aktar") { billingPartyAmount = raw(pricing?.net ?? 0) }
                    .font(.caption.weight(.semibold))
                Text("Bu tutar kurumun borcudur; alınmış ödeme değildir.")
                    .font(.caption).foregroundStyle(.secondary)
            }
            row("Müşteriye düşen", max(0, (pricing?.net ?? 0) - organization), emphasized: true)
        }.pusulaCard()
    }

    private var collectionCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("3. Tahsilat").font(.headline)
            if warranty {
                Text(internalWarranty ? "İşletme garantisi: tahsilat ve cari borç oluşmaz." : "Kurum garantisi: müşteriden tahsilat alınmaz.")
                    .font(.subheadline)
            } else {
                Picker("Ödeme yöntemi", selection: $selectedMethod) {
                    ForEach(PaymentMethodOption.allCases.filter { $0 != .warranty }, id: \.self) { method in
                        Text(method == .currentAccount ? "Şimdi ödeme alınmadı" : method.displayName).tag(method)
                    }
                }
                if !noCollection {
                    moneyField("Tahsil edilen tutar", text: $collectedAmount)
                    Button("Müşteri payının tamamı") { collectedAmount = raw(max(0, (pricing?.net ?? 0) - organization)) }
                        .font(.caption.weight(.semibold))
                }
            }
            Divider()
            row("Kurum carisine", organization)
            row("Tahsil edilen", collected)
            row("Müşteri carisine kalan", totals?.customerDebt ?? 0, emphasized: true)
        }.pusulaCard()
    }

    private func moneyField(_ label: String, text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(label).font(.subheadline)
            TextField("0,00", text: text).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
                .accessibilityIdentifier("collection.input.\(label)")
        }
    }
    private func row(_ title: String, _ value: Decimal, emphasized: Bool = false) -> some View {
        HStack {
            Text(title)
            Spacer(minLength: 12)
            Text(money(value)).fontWeight(emphasized ? .bold : .medium)
        }.font(.subheadline).accessibilityElement(children: .combine)
    }
    private func raw(_ value: Decimal) -> String { NSDecimalNumber(decimal: value).stringValue }
    private func money(_ value: Decimal) -> String {
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "tr_TR"); formatter.numberStyle = .currency
        formatter.currencyCode = "TRY"
        return formatter.string(from: NSDecimalNumber(decimal: value)) ?? raw(value)
    }
    @MainActor private func loadOrganizations() async {
#if DEBUG
        if let testLoaders {
            do { billingOrganizations = try await testLoaders.organizations(); organizationsError = nil }
            catch { organizationsError = error.localizedDescription }
            return
        }
#endif
        do { billingOrganizations = try await TicketService.getBillingOrganizations(); organizationsError = nil }
        catch { if !(error is CancellationError) { organizationsError = "Kurumlar yüklenemedi. Tekrar deneyin." } }
    }
    @MainActor private func processPayment() async {
        guard !isProcessing else { return }
        if pendingRequest == nil {
            guard validation == nil else { errorMessage = validation; return }
            var request = CollectionRequest(collectedAmount: NSDecimalNumber(decimal: collected).doubleValue,
                paymentMethod: warranty ? "WARRANTY" : selectedMethod.apiValue,
                laborFee: NSDecimalNumber(decimal: labor).doubleValue,
                technicianNote: technicianNote.trimmingCharacters(in: .whitespacesAndNewlines),
                billingPartyId: transferToOrganization ? selectedBillingPartyId : nil,
                billingResponsibility: nil,
                billingPartyAmount: transferToOrganization ? NSDecimalNumber(decimal: organization).doubleValue : nil)
            request.discountType = warranty ? "AMOUNT" : discountType.rawValue
            request.discountValue = discount
            request.expectedPartsTotal = partsTotal
            pendingRequest = request
        }
        guard let request = pendingRequest else { return }
        isProcessing = true; errorMessage = nil
        defer { isProcessing = false }
        do {
            _ = try await submit(request)
            await onComplete()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
            switch error {
            case NetworkError.badRequest, NetworkError.forbidden, NetworkError.unauthorized,
                 NetworkError.quotaExceeded, NetworkError.payloadTooLarge:
                pendingRequest = nil; requestId = UUID()
            case NetworkError.serverError(let status) where (400...499).contains(status):
                pendingRequest = nil; requestId = UUID()
            default: break
            }
        }
    }

    @MainActor private func submit(_ request: CollectionRequest) async throws -> FieldTicketDTO {
#if DEBUG
        if let testLoaders { return try await testLoaders.complete(request, requestId) }
#endif
        return try await TicketService.completeService(ticketId: ticket.id, request: request, requestId: requestId)
    }
}

enum PaymentMethodOption: CaseIterable {
    case cash, creditCard, currentAccount, warranty
    var displayName: String {
        switch self {
        case .cash: return "Nakit"
        case .creditCard: return "Kredi Kartı"
        case .currentAccount: return "Cari"
        case .warranty: return "Garanti"
        }
    }
    var apiValue: String {
        switch self {
        case .cash: return "CASH"
        case .creditCard: return "CREDIT_CARD"
        case .currentAccount: return "CURRENT_ACCOUNT"
        case .warranty: return "WARRANTY"
        }
    }
}
