import SwiftUI

/// Collection view with Waterfall (Şelale) payment model.
/// Flow: Service total → Collected amount → Remaining → Cari (with safety confirmation)
struct CollectionView: View {
    let ticket: FieldTicketDTO
    let partsTotal: Double
    let onComplete: () async -> Void
    
    @Environment(\.dismiss) private var dismiss
    @State private var collectedAmount = ""
    @State private var selectedMethod: PaymentMethodOption = .cash
    @State private var showDebtConfirmation = false
    @State private var isProcessing = false
    @State private var errorMessage: String?
    
    var serviceTotal: Double { partsTotal }
    var collectedValue: Double { Double(collectedAmount) ?? 0 }
    var remainingDebt: Double { max(0, serviceTotal - collectedValue) }
    var existingDebt: Double { ticket.customerBalance ?? 0 }
    var totalDebtAfter: Double { existingDebt + remainingDebt }
    var isFullPayment: Bool { collectedValue >= serviceTotal }
    var overpayment: Double { max(0, collectedValue - serviceTotal) }
    
    /// Waterfall: if customer pays more than current service, excess reduces cari
    var debtReduction: Double { min(overpayment, existingDebt) }
    var finalDebt: Double { existingDebt - debtReduction + remainingDebt }
    
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
                    
                    // Waterfall breakdown
                    waterfallBreakdown
                    
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
        .padding()
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
    
    // MARK: - Payment Method
    
    private var paymentMethodPicker: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Ödeme Yöntemi")
                .font(.subheadline.weight(.semibold))
            
            HStack(spacing: 12) {
                ForEach(PaymentMethodOption.allCases, id: \.self) { method in
                    Button(action: { selectedMethod = method }) {
                        VStack(spacing: 6) {
                            Image(systemName: method.icon)
                                .font(.title2)
                            Text(method.displayName)
                                .font(.caption)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(selectedMethod == method ? .cyan.opacity(0.15) : Color(.systemGray6))
                        .foregroundColor(selectedMethod == method ? .cyan : .secondary)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(selectedMethod == method ? .cyan : .clear, lineWidth: 2)
                        )
                    }
                }
            }
        }
    }
    
    // MARK: - Amount Input
    
    private var amountInput: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Tahsil Edilen Tutar")
                .font(.subheadline.weight(.semibold))
            
            HStack {
                Text("₺")
                    .font(.title.weight(.bold))
                    .foregroundColor(.cyan)
                TextField("0.00", text: $collectedAmount)
                    .font(.system(size: 32, weight: .bold, design: .rounded))
                    .keyboardType(.decimalPad)
            }
            .padding()
            .background(Color(.systemGray6))
            .clipShape(RoundedRectangle(cornerRadius: 14))
            
            // Quick amount buttons
            HStack(spacing: 8) {
                quickAmountButton("Tam Tutar", amount: serviceTotal)
                if existingDebt > 0 {
                    quickAmountButton("Servis + Borç", amount: serviceTotal + existingDebt)
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
                .background(.cyan.opacity(0.1))
                .foregroundColor(.cyan)
                .clipShape(Capsule())
        }
    }
    
    // MARK: - Waterfall Breakdown
    
    private var waterfallBreakdown: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Şelale Dağılımı")
                .font(.subheadline.weight(.semibold))
            
            if collectedValue > 0 {
                // Step 1: Service payment
                waterfallRow("1. Servis Ücreti",
                             amount: min(collectedValue, serviceTotal),
                             icon: "wrench.and.screwdriver",
                             color: .green)
                
                // Step 2: If overpayment, reduce old debt
                if debtReduction > 0 {
                    waterfallRow("2. Geçmiş Borç Düşümü",
                                 amount: -debtReduction,
                                 icon: "arrow.down.circle",
                                 color: .blue)
                }
                
                // Step 3: If underpayment, add to cari
                if remainingDebt > 0 {
                    waterfallRow("⚠️ Cariye Eklenecek",
                                 amount: remainingDebt,
                                 icon: "exclamationmark.triangle",
                                 color: .orange)
                }
                
                Divider()
                
                // Final debt
                HStack {
                    Text("Son Cari Durum")
                        .font(.subheadline.weight(.bold))
                    Spacer()
                    Text(finalDebt > 0 ? "₺\(String(format: "%.2f", finalDebt)) borç" : "Temiz ✓")
                        .font(.subheadline.weight(.bold))
                        .foregroundColor(finalDebt > 0 ? .orange : .green)
                }
            } else {
                Text("Tutar girin")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding()
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 14))
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
                    Text(isFullPayment ? "Tamamla" : "Kaydet & Cariye Ekle")
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .font(.headline)
        }
        .background(
            LinearGradient(
                colors: isFullPayment ? [.green, .cyan] : [.orange, .red.opacity(0.8)],
                startPoint: .leading, endPoint: .trailing
            )
        )
        .foregroundColor(.white)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .disabled(isProcessing || collectedAmount.isEmpty)
    }
    
    // MARK: - Logic
    
    private func handleSubmit() {
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
                paymentMethod: selectedMethod.apiValue
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
    case cash, creditCard, currentAccount
    
    var displayName: String {
        switch self {
        case .cash: return "Nakit"
        case .creditCard: return "Kredi Kartı"
        case .currentAccount: return "Cari"
        }
    }
    
    var icon: String {
        switch self {
        case .cash: return "banknote"
        case .creditCard: return "creditcard"
        case .currentAccount: return "doc.text"
        }
    }
    
    var apiValue: String {
        switch self {
        case .cash: return "CASH"
        case .creditCard: return "CREDIT_CARD"
        case .currentAccount: return "CURRENT_ACCOUNT"
        }
    }
}
