import SwiftUI

/// Service pricing and payment summary.
/// Flow: Parts + labor → collected amount → remaining current-account balance.
struct CollectionView: View {
    let ticket: FieldTicketDTO
    let partsTotal: Double
    let onComplete: () async -> Void
    
    @Environment(\.dismiss) private var dismiss
    @State private var laborFee = "0.00"
    @State private var collectedAmount = ""
    @State private var selectedMethod: PaymentMethodOption = .cash
    @State private var showDebtConfirmation = false
    @State private var isProcessing = false
    @State private var errorMessage: String?
    @State private var technicianNote = ""
    
    var isWarranty: Bool { selectedMethod == .warranty }
    var isCurrentAccount: Bool { selectedMethod == .currentAccount }
    var laborValue: Double { isWarranty ? 0 : (Double(laborFee.replacingOccurrences(of: ",", with: ".")) ?? 0) }
    var serviceTotal: Double { isWarranty ? 0 : partsTotal + laborValue }
    var collectedValue: Double {
        (isWarranty || isCurrentAccount) ? 0 : (Double(collectedAmount.replacingOccurrences(of: ",", with: ".")) ?? 0)
    }
    var remainingDebt: Double { isWarranty ? 0 : max(0, serviceTotal - collectedValue) }
    var isOverpayment: Bool { !isWarranty && !isCurrentAccount && collectedValue > serviceTotal + 0.005 }
    var existingDebt: Double { ticket.customerBalance ?? 0 }
    var isFullPayment: Bool { collectedValue >= serviceTotal }
    var finalDebt: Double { existingDebt + remainingDebt }
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    // Service summary card
                    summaryCard
                    
                    // Payment method selector
                    paymentMethodPicker
                    
                    // Amount input
                    amountInput

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Teknisyen Notu").font(.subheadline.weight(.semibold))
                        TextField("Yapılan işlem / kapanış notu", text: $technicianNote, axis: .vertical)
                            .lineLimit(3...8)
                            .padding()
                            .background(PusulaTheme.raisedSurface)
                            .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
                    }
                    
                    paymentSummary
                    
                    // Error
                    if let error = errorMessage {
                        Text(error)
                            .font(.caption)
                            .foregroundColor(.red)
                    }
                    
                    // Submit button
                    submitButton
                }
                .padding()
            }
            .background(PusulaTheme.page)
            .navigationTitle("Tahsilat")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("İptal") { dismiss() }
                }
            }
            .alert("Cari Hesap Uyarısı", isPresented: $showDebtConfirmation) {
                Button("İptal", role: .cancel) {}
                Button("Onayla", role: .destructive) {
                    Task { await processPayment() }
                }
            } message: {
                Text("Dikkat: Kalan ₺\(String(format: "%.2f", remainingDebt)) tutar müşterinin cari hesabına borç olarak işlenecektir.\n\nToplam cari borç: ₺\(String(format: "%.2f", finalDebt))\n\nOnaylıyor musunuz?")
            }
        }
    }
    
    // MARK: - Summary Card
    
    private var summaryCard: some View {
        VStack(spacing: 12) {
            HStack {
                Text("Servis Tutarı")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Spacer()
                Text("₺\(String(format: "%.2f", serviceTotal))")
                    .font(.title2.weight(.bold))
            }
            
            if existingDebt > 0 {
                Divider()
                HStack {
                    HStack(spacing: 4) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundColor(.orange)
                        Text("Geçmiş Cari Borç")
                            .font(.subheadline)
                    }
                    Spacer()
                    Text("₺\(String(format: "%.2f", existingDebt))")
                        .font(.headline.weight(.bold))
                        .foregroundColor(.orange)
                }
                
                HStack {
                    Text("Toplam (Servis + Borç)")
                        .font(.subheadline.weight(.semibold))
                    Spacer()
                    Text("₺\(String(format: "%.2f", serviceTotal + existingDebt))")
                        .font(.headline.weight(.bold))
                        .foregroundColor(.red)
                }
            }
        }
        .pusulaCard()
    }
    
    // MARK: - Payment Method
    
    private var paymentMethodPicker: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Ödeme Yöntemi")
                .font(.subheadline.weight(.semibold))
            
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                ForEach(PaymentMethodOption.allCases, id: \.self) { method in
                    Button(action: {
                        selectedMethod = method
                        collectedAmount = (method == .warranty || method == .currentAccount)
                            ? "0.00"
                            : String(format: "%.2f", partsTotal + laborValue)
                    }) {
                        VStack(spacing: 6) {
                            Image(systemName: method.icon)
                                .font(.title2)
                            Text(method.displayName)
                                .font(.caption)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(selectedMethod == method ? PusulaTheme.accent.opacity(0.10) : PusulaTheme.raisedSurface)
                        .foregroundColor(selectedMethod == method ? PusulaTheme.accent : .secondary)
                        .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
                        .overlay(
                            RoundedRectangle(cornerRadius: PusulaTheme.radius)
                                .stroke(selectedMethod == method ? PusulaTheme.accent : PusulaTheme.border, lineWidth: 1)
                        )
                    }
                }
            }
        }
    }
    
    // MARK: - Amount Input
    
    private var amountInput: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Ücret ve Ödeme Bilgileri")
                .font(.subheadline.weight(.semibold))

            HStack {
                Text("Parça toplamı")
                Spacer()
                Text("₺\(String(format: "%.2f", partsTotal))").fontWeight(.semibold)
            }

            TextField("İşçilik / servis bedeli", text: $laborFee)
                .keyboardType(.decimalPad)
                .textFieldStyle(.roundedBorder)
                .disabled(isWarranty)
                .onChange(of: laborFee) { _, _ in
                    if !isWarranty && !isCurrentAccount {
                        collectedAmount = String(format: "%.2f", serviceTotal)
                    }
                }

            HStack {
                Text("Fiş toplamı")
                Spacer()
                Text("₺\(String(format: "%.2f", serviceTotal))").fontWeight(.bold)
            }

            Text("Tahsil Edilen Tutar")
                .font(.subheadline.weight(.semibold))
            
            HStack {
                Text("₺")
                    .font(.title.weight(.bold))
                    .foregroundColor(PusulaTheme.accent)
                TextField("0.00", text: $collectedAmount)
                    .font(.system(size: 32, weight: .bold, design: .rounded))
                    .keyboardType(.decimalPad)
                    .disabled(isWarranty || isCurrentAccount)
            }
            .padding()
            .background(PusulaTheme.raisedSurface)
            .overlay {
                RoundedRectangle(cornerRadius: PusulaTheme.radius)
                    .stroke(PusulaTheme.border, lineWidth: 1)
            }
            .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))

            if isOverpayment {
                Label(
                    "Tahsil edilen tutar fiş toplamını aşamaz.",
                    systemImage: "exclamationmark.triangle.fill"
                )
                .font(.caption.weight(.semibold))
                .foregroundColor(.red)
            }

            if isWarranty {
                Text("Garanti kapsamında satış, tahsilat ve cari borç oluşmaz. Kullanılan parçaların maliyeti rapora yansır.")
                    .font(.caption)
                    .foregroundColor(.orange)
            }
            
            // Quick amount buttons
            if !isWarranty && !isCurrentAccount {
                HStack(spacing: 8) {
                quickAmountButton("Tam Tutar", amount: serviceTotal)
                }
            }
        }
    }
    
    private func quickAmountButton(_ title: String, amount: Double) -> some View {
        Button(action: { collectedAmount = String(format: "%.2f", amount) }) {
            Text(title)
                .font(.caption.weight(.medium))
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(PusulaTheme.accent.opacity(0.1))
                .foregroundColor(PusulaTheme.accent)
                .clipShape(Capsule())
        }
    }
    
    // MARK: - Payment Summary
    
    private var paymentSummary: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Ödeme Özeti")
                .font(.subheadline.weight(.semibold))
            
            if isWarranty {
                Text("Garanti kapsamında tahsilat veya cari işlem oluşturulmayacak.")
                    .font(.caption)
                    .foregroundColor(.orange)
            } else if isCurrentAccount {
                waterfallRow("Cariye aktarılacak",
                             amount: serviceTotal,
                             icon: "doc.text",
                             color: .orange)
                Divider()
                finalDebtRow
            } else if collectedValue > 0 {
                // Step 1: Service payment
                waterfallRow("1. Servis Ücreti",
                             amount: min(collectedValue, serviceTotal),
                             icon: "wrench.and.screwdriver",
                             color: .green)
                
                if remainingDebt > 0 {
                    waterfallRow("⚠️ Cariye Eklenecek",
                                 amount: remainingDebt,
                                 icon: "exclamationmark.triangle",
                                 color: .orange)
                }
                
                Divider()
                
                // Final debt
                finalDebtRow
            } else {
                Text("Dağılımı görmek için tahsil edilen tutarı girin.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .pusulaCard()
    }

    private var finalDebtRow: some View {
        HStack {
            Text("Son Cari Durum").font(.subheadline.weight(.bold))
            Spacer()
            Text(finalDebt > 0 ? "₺\(String(format: "%.2f", finalDebt)) borç" : "Temiz ✓")
                .font(.subheadline.weight(.bold))
                .foregroundColor(finalDebt > 0 ? .orange : .green)
        }
    }
    
    private func waterfallRow(_ title: String, amount: Double, icon: String, color: Color) -> some View {
        HStack {
            Image(systemName: icon)
                .foregroundColor(color)
            Text(title)
                .font(.caption)
            Spacer()
            Text("₺\(String(format: "%.2f", abs(amount)))")
                .font(.caption.weight(.semibold))
                .foregroundColor(color)
        }
    }
    
    // MARK: - Submit Button
    
    private var submitButton: some View {
        Button(action: handleSubmit) {
            HStack {
                if isProcessing {
                    ProgressView().tint(.white)
                } else {
                    Image(systemName: "checkmark.circle.fill")
                    Text(isWarranty ? "Garanti Kapsamında Kapat" : (isFullPayment ? "Tamamla" : "Kaydet & Cariye Ekle"))
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .font(.headline)
        }
        .background(isFullPayment ? PusulaTheme.accent : Color.orange)
        .foregroundColor(.white)
        .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
        .disabled(isProcessing || isOverpayment || (!isWarranty && !isCurrentAccount && collectedAmount.isEmpty))
    }
    
    // MARK: - Logic
    
    private func handleSubmit() {
        guard !isOverpayment else {
            errorMessage = "Tahsil edilen tutar fiş toplamını aşamaz."
            return
        }
        if isWarranty {
            Task { await processPayment() }
            return
        }
        if !isFullPayment {
            // SAFETY: Show confirmation before adding to cari
            showDebtConfirmation = true
        } else {
            Task { await processPayment() }
        }
    }
    
    private func processPayment() async {
        isProcessing = true
        errorMessage = nil
        
        do {
            _ = try await TicketService.completeService(
                ticketId: ticket.id,
                amount: collectedValue,
                paymentMethod: selectedMethod.apiValue,
                laborFee: laborValue,
                technicianNote: technicianNote
            )
            await onComplete()
        } catch {
            await MainActor.run {
                errorMessage = error.localizedDescription
                isProcessing = false
            }
        }
    }
}

// MARK: - Payment Method Options

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
    
    var icon: String {
        switch self {
        case .cash: return "banknote"
        case .creditCard: return "creditcard"
        case .currentAccount: return "doc.text"
        case .warranty: return "checkmark.shield"
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
