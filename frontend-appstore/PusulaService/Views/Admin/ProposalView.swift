import SwiftUI

struct ProposalView: View {
    @State private var proposals: [ProposalDTO] = []
    @State private var searchText = ""
    @State private var isLoading = true
    @State private var editingProposal: ProposalDTO?
    @State private var showCreate = false
    
    private var filtered: [ProposalDTO] {
        guard !searchText.isEmpty else { return proposals }
        return proposals.filter {
            ($0.title ?? "").localizedCaseInsensitiveContains(searchText) ||
            ($0.customerName ?? "").localizedCaseInsensitiveContains(searchText)
        }
    }
    
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                TextField("Teklif ara...", text: $searchText)
            }
            .padding(10)
            .background(Color(.systemGray6))
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .padding()
            
            if isLoading {
                Spacer(); ProgressView(); Spacer()
            } else {
                List(filtered) { proposal in
                    proposalRow(proposal)
                }
                .listStyle(.plain)
                .refreshable { await load() }
            }
        }
        .navigationTitle("Teklifler")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: { showCreate = true }) { Image(systemName: "plus") }
                    .readOnlyProtected()
            }
        }
        .task { await load() }
        .sheet(isPresented: $showCreate) {
            ProposalEditorSheet(proposal: nil) { await load() }
        }
        .sheet(item: $editingProposal) { proposal in
            ProposalEditorSheet(proposal: proposal) { await load() }
        }
    }
    
    private func proposalRow(_ proposal: ProposalDTO) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(proposal.title ?? "Teklif")
                    .font(.headline)
                Spacer()
                Text(proposal.status ?? "TASLAK")
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(.cyan.opacity(0.15))
                    .clipShape(Capsule())
            }
            
            Text(proposal.customerName ?? "Müşteri")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            
            Text(formatCurrency(proposal.totalPrice))
                .font(.title3.weight(.bold))
                .foregroundColor(.cyan)
            
            HStack {
                Button("Düzenle") { editingProposal = proposal }
                Spacer()
                Button("PDF") { Task { await downloadPDF(proposal) } }
                Button("İşe Dönüştür") { Task { await convert(proposal) } }
                    .readOnlyProtected()
            }
            .font(.caption.weight(.semibold))
        }
        .padding(.vertical, 6)
    }
    
    private func load() async {
        isLoading = true
        proposals = (try? await ProposalService.getProposals()) ?? []
        isLoading = false
    }
    
    private func downloadPDF(_ proposal: ProposalDTO) async {
        guard let id = proposal.id else { return }
        if let data = try? await ProposalService.downloadPDF(id: id) {
            sharePDF(data: data, fileName: "teklif-\(id).pdf")
        }
    }
    
    private func convert(_ proposal: ProposalDTO) async {
        guard let id = proposal.id else { return }
        _ = try? await ProposalService.convertToJob(id: id)
        await load()
    }
}

struct ProposalEditorSheet: View {
    let proposal: ProposalDTO?
    let onSaved: () async -> Void
    
    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var note = ""
    @State private var customerName = ""
    @State private var itemDescription = ""
    @State private var quantity = "1"
    @State private var unitPrice = ""
    
    var body: some View {
        NavigationStack {
            Form {
                TextField("Başlık", text: $title)
                TextField("Müşteri", text: $customerName)
                TextField("Not", text: $note, axis: .vertical)
                Section("Kalem") {
                    TextField("Açıklama", text: $itemDescription)
                    TextField("Adet", text: $quantity).keyboardType(.numberPad)
                    TextField("Birim Fiyat", text: $unitPrice).keyboardType(.decimalPad)
                }
            }
            .navigationTitle(proposal == nil ? "Yeni Teklif" : "Teklif Düzenle")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("İptal") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") { Task { await save() } }
                        .readOnlyProtected()
                }
            }
            .onAppear {
                title = proposal?.title ?? ""
                note = proposal?.note ?? ""
                customerName = proposal?.customerName ?? ""
            }
        }
    }
    
    private func save() async {
        let qty = Int(quantity) ?? 1
        let price = Double(unitPrice.replacingOccurrences(of: ",", with: ".")) ?? 0
        let item = ProposalItemDTO(id: nil, description: itemDescription, quantity: qty, unitCost: nil, unitPrice: price, totalPrice: Double(qty) * price)
        var dto = ProposalDTO(
            id: proposal?.id,
            companyId: proposal?.companyId,
            customerId: proposal?.customerId,
            customerName: customerName.nilIfEmpty,
            preparedById: proposal?.preparedById,
            preparedByName: proposal?.preparedByName,
            status: proposal?.status ?? "DRAFT",
            validUntil: proposal?.validUntil,
            note: note.nilIfEmpty,
            title: title.nilIfEmpty,
            taxRate: proposal?.taxRate ?? 20,
            discount: proposal?.discount ?? 0,
            subtotal: item.totalPrice,
            taxAmount: nil,
            totalPrice: item.totalPrice,
            items: [item]
        )
        if let id = proposal?.id {
            _ = try? await ProposalService.updateProposal(id: id, proposal: dto)
        } else {
            _ = try? await ProposalService.createProposal(dto)
        }
        await onSaved()
        await MainActor.run { dismiss() }
    }
}

private extension String {
    var nilIfEmpty: String? {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : self
    }
}
