import SwiftUI

struct TicketDetailView: View {
    let ticket: FieldTicketDTO
    var isAdmin: Bool = false
    var technicians: [TechnicianDTO] = []
    let onComplete: () async -> Void
    
    @Environment(\.dismiss) private var dismiss
    @State private var usedParts: [UsedPartDTO] = []
    @State private var showScanner = false
    @State private var showCollection = false
    @State private var showSignature = false
    @State private var showPhotos = false
    @State private var isLoadingParts = false
    @State private var isGeneratingPDF = false
    @State private var errorMessage: String?
    
    private var isEditable: Bool {
        ticket.statusEnum != .completed && ticket.statusEnum != .cancelled
    }
    
    var totalPartsValue: Double {
        usedParts.reduce(0) { $0 + $1.totalPrice }
    }
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                heroCard
                customerCard
                
                if let desc = ticket.description, !desc.isEmpty {
                    infoCard(title: "İş Açıklaması", icon: "doc.text", content: desc)
                }
                
                partsSection
                
                if isEditable {
                    quickActionsGrid
                    primaryActions
                } else if ticket.statusEnum == .completed {
                    secondaryActions
                }
            }
            .padding()
        }
        .navigationTitle("İş Emri #\(ticket.id)")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Kapat") { dismiss() }
            }
        }
        .task { await loadParts() }
        .sheet(isPresented: $showScanner) {
            BarcodeScannerView { item, quantity in
                Task { await addPart(from: item, quantity: quantity) }
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
        .sheet(isPresented: $showPhotos) {
            NavigationStack {
                ServicePhotoView(ticketId: ticket.id)
            }
        }
        .alert("Hata", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Tamam", role: .cancel) { errorMessage = nil }
        } message: {
            Text(errorMessage ?? "")
        }
    }
    
    private var heroCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Servis fişi #\(ticket.id)")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(ticket.customerName ?? "Müşteri")
                .font(.title2.weight(.bold))
            if totalPartsValue > 0 {
                Text(formatCurrency(totalPartsValue))
                    .font(.headline)
                    .foregroundColor(.cyan)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(
            LinearGradient(colors: [.cyan.opacity(0.25), .blue.opacity(0.15)], startPoint: .topLeading, endPoint: .bottomTrailing)
        )
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
    
    private var customerCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Müşteri", systemImage: "person.crop.circle")
                .font(.subheadline.weight(.semibold))
            
            if let phone = ticket.customerPhone, !phone.isEmpty {
                HStack {
                    Label(phone, systemImage: "phone.fill")
                        .font(.subheadline)
                    Spacer()
                    Link(destination: URL(string: "tel:\(phone)")!) {
                        Image(systemName: "phone.circle.fill")
                            .font(.title2)
                            .foregroundColor(.green)
                    }
                }
            }
            
            if let address = ticket.customerAddress, !address.isEmpty {
                Button(action: { openMaps(address: address) }) {
                    HStack(alignment: .top) {
                        Image(systemName: "mappin.and.ellipse")
                            .foregroundColor(.red)
                        Text(address)
                            .font(.subheadline)
                            .foregroundStyle(.primary)
                            .multilineTextAlignment(.leading)
                        Spacer()
                        Image(systemName: "arrow.up.right")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            
            if isAdmin, !technicians.isEmpty, isEditable {
                Menu {
                    ForEach(technicians) { tech in
                        Button(tech.fullName ?? "Teknisyen") {
                            Task { await assign(techId: tech.id) }
                        }
                    }
                } label: {
                    Label(ticket.assignedTechnicianName ?? "Teknisyen Seç", systemImage: "person.badge.plus")
                        .font(.subheadline.weight(.medium))
                        .foregroundColor(.cyan)
                }
                .readOnlyProtected()
            }
            
            if ticket.hasOutstandingBalance {
                HStack {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.orange)
                    Text("Geçmiş Cari Borç: \(formatCurrency(ticket.customerBalance))")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(.orange)
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(.orange.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }
        }
        .padding()
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
    
    private func infoCard(title: String, icon: String, content: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(title, systemImage: icon)
                .font(.subheadline.weight(.semibold))
            Text(content)
                .font(.body)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
    
    private var partsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Label("Kullanılan Parçalar", systemImage: "shippingbox")
                    .font(.subheadline.weight(.semibold))
                Spacer()
                if isEditable {
                    Button(action: { showScanner = true }) {
                        Label("Barkod", systemImage: "barcode.viewfinder")
                            .font(.caption.weight(.semibold))
                    }
                    .readOnlyProtected()
                }
            }
            
            if isLoadingParts {
                ProgressView()
            } else if usedParts.isEmpty {
                Text("Henüz parça eklenmedi")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(.vertical, 8)
            } else {
                ForEach(usedParts) { part in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(part.partName)
                                .font(.subheadline.weight(.medium))
                            Text("\(formatCurrency(part.sellingPriceSnapshot)) × \(part.quantityUsed)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text(formatCurrency(part.totalPrice))
                            .font(.subheadline.weight(.semibold))
                    }
                    Divider()
                }
                
                HStack {
                    Text("Parça Toplamı")
                        .font(.subheadline.weight(.bold))
                    Spacer()
                    Text(formatCurrency(totalPartsValue))
                        .font(.headline.weight(.bold))
                        .foregroundColor(.cyan)
                }
            }
        }
        .padding()
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
    
    private var quickActionsGrid: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
            actionTile("Barkod Okut", icon: "barcode.viewfinder", color: .cyan) { showScanner = true }
            actionTile("Görseller", icon: "photo.on.rectangle", color: .purple) { showPhotos = true }
            actionTile("İmza", icon: "pencil.tip.crop.circle", color: .indigo) { showSignature = true }
            actionTile("PDF", icon: "doc.richtext", color: .orange) { Task { await generatePDF() } }
        }
    }
    
    private var primaryActions: some View {
        Button(action: { showCollection = true }) {
            Label("Servisi Tamamla & Tahsilat", systemImage: "checkmark.circle.fill")
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .font(.headline)
        }
        .background(LinearGradient(colors: [.green, .cyan], startPoint: .leading, endPoint: .trailing))
        .foregroundColor(.white)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .readOnlyProtected()
    }
    
    private var secondaryActions: some View {
        Button(action: { Task { await generatePDF() } }) {
            Label(isGeneratingPDF ? "PDF Hazırlanıyor..." : "Servis Formu PDF", systemImage: "doc.richtext")
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
        }
        .buttonStyle(.borderedProminent)
        .tint(.orange)
        .disabled(isGeneratingPDF)
    }
    
    private func actionTile(_ title: String, icon: String, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.title3)
                    .foregroundColor(color)
                Text(title)
                    .font(.caption.weight(.medium))
                    .foregroundColor(.primary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(.regularMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .readOnlyProtected()
    }
    
    private func loadParts() async {
        isLoadingParts = true
        do {
            usedParts = try await TicketService.getUsedParts(ticketId: ticket.id)
        } catch {}
        isLoadingParts = false
    }
    
    private func addPart(from item: InventoryItemDTO, quantity: Int) async {
        let part = UsedPartDTO(
            id: nil,
            ticketId: ticket.id,
            inventoryId: item.id,
            partName: item.partName,
            quantityUsed: quantity,
            sellingPriceSnapshot: item.sellPrice ?? 0
        )
        do {
            let saved = try await TicketService.addUsedPart(ticketId: ticket.id, part: part)
            await MainActor.run { usedParts.append(saved) }
        } catch {
            await MainActor.run { errorMessage = error.localizedDescription }
        }
    }
    
    private func assign(techId: Int) async {
        do {
            _ = try await TicketService.assignTechnician(ticketId: ticket.id, technicianId: techId)
            await onComplete()
        } catch {
            await MainActor.run { errorMessage = error.localizedDescription }
        }
    }
    
    private func generatePDF() async {
        isGeneratingPDF = true
        do {
            let data = try await TicketService.downloadServiceReportPDF(ticketId: ticket.id)
            await MainActor.run {
                sharePDF(data: data, fileName: "servis-formu-\(ticket.id).pdf")
                isGeneratingPDF = false
            }
        } catch {
            await MainActor.run {
                errorMessage = error.localizedDescription
                isGeneratingPDF = false
            }
        }
    }
    
    private func openMaps(address: String) {
        let encoded = address.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? address
        if let url = URL(string: "http://maps.apple.com/?q=\(encoded)") {
            UIApplication.shared.open(url)
        }
    }
}

import UIKit
