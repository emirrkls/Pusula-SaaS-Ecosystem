import SwiftUI

struct TicketDetailView: View {
    let ticket: FieldTicketDTO
    let onComplete: () async -> Void
    
    @Environment(\.dismiss) private var dismiss
    @State private var usedParts: [UsedPartDTO] = []
    @State private var showScanner = false
    @State private var showCollection = false
    @State private var showSignature = false
    @State private var isLoading = false
    
    var totalPartsValue: Double {
        usedParts.reduce(0) { $0 + $1.totalPrice }
    }
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Customer Info Card
                customerCard
                
                // Description
                if let desc = ticket.description, !desc.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        Label("İş Açıklaması", systemImage: "doc.text")
                            .font(.subheadline.weight(.semibold))
                        Text(desc)
                            .font(.body)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
                    .background(.regularMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                }
                
                // Used Parts
                partsSection
                
                // Action Buttons
                if ticket.statusEnum != .completed && ticket.statusEnum != .cancelled {
                    actionButtons
                }
            }
            .padding()
        }
        .navigationTitle("İş Detayı #\(ticket.id)")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Kapat") { dismiss() }
            }
        }
        .task { await loadParts() }
        .sheet(isPresented: $showScanner) {
            BarcodeScannerView { scannedItem in
                let part = UsedPartDTO(
                    inventoryId: scannedItem.id,
                    partName: scannedItem.partName,
                    quantityUsed: 1,
                    sellingPriceSnapshot: scannedItem.sellPrice ?? 0
                )
                usedParts.append(part)
            }
        }
        .sheet(isPresented: $showCollection) {
            CollectionView(
                ticket: ticket,
                partsTotal: totalPartsValue,
                onComplete: {
                    await onComplete()
                    dismiss()
                }
            )
        }
        .sheet(isPresented: $showSignature) {
            SignatureView(ticketId: ticket.id)
        }
    }
    
    // MARK: - Customer Card
    
    private var customerCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(ticket.customerName ?? "Müşteri")
                        .font(.title3.weight(.bold))
                    
                    if let address = ticket.customerAddress {
                        HStack(spacing: 4) {
                            Image(systemName: "mappin")
                                .foregroundColor(.red)
                            Text(address)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                
                Spacer()
                
                if let phone = ticket.customerPhone {
                    Link(destination: URL(string: "tel:\(phone)")!) {
                        Image(systemName: "phone.circle.fill")
                            .font(.title)
                            .foregroundColor(.green)
                    }
                }
            }
            
            // Cari balance warning
            if ticket.hasOutstandingBalance {
                HStack {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.orange)
                    Text("Geçmiş Cari Borç: ₺\(String(format: "%.2f", ticket.customerBalance ?? 0))")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(.orange)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(.orange.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }
        }
        .padding()
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
    
    // MARK: - Parts Section
    
    private var partsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Label("Kullanılan Parçalar", systemImage: "shippingbox")
                    .font(.subheadline.weight(.semibold))
                Spacer()
                if ticket.statusEnum != .completed {
                    Button(action: { showScanner = true }) {
                        HStack(spacing: 4) {
                            Image(systemName: "barcode.viewfinder")
                            Text("Barkod Okut")
                        }
                        .font(.caption.weight(.semibold))
                        .foregroundColor(.cyan)
                    }
                }
            }
            
            if usedParts.isEmpty {
                Text("Henüz parça eklenmedi")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(.vertical, 12)
            } else {
                ForEach(usedParts) { part in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(part.partName)
                                .font(.subheadline.weight(.medium))
                            Text("₺\(String(format: "%.2f", part.sellingPriceSnapshot)) × \(part.quantityUsed)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text("₺\(String(format: "%.2f", part.totalPrice))")
                            .font(.subheadline.weight(.semibold))
                    }
                    .padding(.vertical, 4)
                    Divider()
                }
                
                // Total
                HStack {
                    Text("Parça Toplamı")
                        .font(.subheadline.weight(.bold))
                    Spacer()
                    Text("₺\(String(format: "%.2f", totalPartsValue))")
                        .font(.headline.weight(.bold))
                        .foregroundColor(.cyan)
                }
                .padding(.top, 4)
            }
        }
        .padding()
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
    
    // MARK: - Action Buttons
    
    private var actionButtons: some View {
        VStack(spacing: 12) {
            // Signature
            Button(action: { showSignature = true }) {
                Label("Müşteri İmzası Al", systemImage: "pencil.tip.crop.circle")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
            }
            .background(.cyan.opacity(0.15))
            .foregroundColor(.cyan)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            
            // Complete & Collect
            Button(action: { showCollection = true }) {
                HStack {
                    Image(systemName: "checkmark.circle.fill")
                    Text("Servisi Tamamla & Tahsilat")
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .font(.headline)
            }
            .background(
                LinearGradient(colors: [.green, .cyan], startPoint: .leading, endPoint: .trailing)
            )
            .foregroundColor(.white)
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
    }
    
    private func loadParts() async {
        do {
            usedParts = try await TicketService.getUsedParts(ticketId: ticket.id)
        } catch {
            // Parts may not exist yet
        }
    }
}
