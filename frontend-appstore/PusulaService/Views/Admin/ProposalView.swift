import SwiftUI

struct ProposalView: View {
    @State private var proposals: [ProposalDTO] = []
    @State private var customers: [CustomerDTO] = []
    @State private var searchText = ""
    @State private var isLoading = true
    @State private var editingProposal: ProposalDTO?
    @State private var showCreate = false
    @State private var proposalPendingConversion: ProposalDTO?
    @State private var downloadingPDFId: Int?
    @State private var convertingProposalId: Int?
    @State private var pdfPreview: PDFPreviewItem?
    @State private var errorMessage: String?
    
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
            ProposalEditorSheet(proposal: nil, customers: customers) { await load() }
        }
        .sheet(item: $editingProposal) { proposal in
            ProposalEditorSheet(proposal: proposal, customers: customers) { await load() }
        }
        .sheet(item: $pdfPreview) { item in
            PDFPreviewSheet(item: item)
        }
        .confirmationDialog(
            "Teklif işe dönüştürülsün mü?",
            isPresented: Binding(
                get: { proposalPendingConversion != nil },
                set: { if !$0 { proposalPendingConversion = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("İş Emri Oluştur") {
                guard let proposal = proposalPendingConversion else { return }
                proposalPendingConversion = nil
                Task { await convert(proposal) }
            }
            Button("Vazgeç", role: .cancel) {
                proposalPendingConversion = nil
            }
        } message: {
            Text("Teklif onaylanacak ve yeni bir servis iş emri oluşturulacak. Bu işlem yalnızca bir kez yapılabilir.")
        }
        .alert("Hata", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Tamam", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }
    
    private func proposalRow(_ proposal: ProposalDTO) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(proposal.title ?? "Teklif")
                    .font(.headline)
                Spacer()
                Text(proposal.status ?? "TASLAK")
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(PusulaTheme.accent.opacity(0.10))
                    .clipShape(Capsule())
            }
            
            Text(proposal.customerName ?? "Müşteri")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            Label(proposal.preparedByName ?? "Hazırlayan bilgisi yok", systemImage: "signature")
                .font(.caption)
                .foregroundStyle(.secondary)
            
            Text(formatCurrency(proposal.totalPrice))
                .font(.title3.weight(.bold))
                .foregroundColor(PusulaTheme.accent)
            
            Divider()

            HStack(spacing: 10) {
                Button {
                    editingProposal = proposal
                } label: {
                    Label("Düzenle", systemImage: "pencil")
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 36)
                }
                .buttonStyle(.bordered)
                .readOnlyProtected()

                Button {
                    Task { await downloadPDF(proposal) }
                } label: {
                    HStack(spacing: 6) {
                        if downloadingPDFId == proposal.id {
                            ProgressView().controlSize(.small)
                        } else {
                            Image(systemName: "doc.richtext")
                        }
                        Text(downloadingPDFId == proposal.id ? "Açılıyor…" : "PDF")
                    }
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 36)
                }
                .buttonStyle(.bordered)
                .disabled(proposal.id == nil || downloadingPDFId != nil)
            }

            Button {
                proposalPendingConversion = proposal
            } label: {
                HStack(spacing: 8) {
                    if convertingProposalId == proposal.id {
                        ProgressView().controlSize(.small)
                    } else {
                        Image(systemName: conversionAllowed(proposal) ? "arrow.trianglehead.branch" : "checkmark.circle")
                    }
                    Text(conversionButtonTitle(proposal))
                }
                .frame(maxWidth: .infinity)
                .frame(minHeight: 40)
            }
            .buttonStyle(.bordered)
            .tint(conversionAllowed(proposal) ? .green : .secondary)
            .disabled(!conversionAllowed(proposal) || convertingProposalId != nil)
            .readOnlyProtected()
        }
        .padding(.vertical, 8)
    }
    
    private func load() async {
        isLoading = true
        do {
            async let proposalRequest = ProposalService.getProposals()
            async let customerRequest = CustomerService.getCustomers()
            let (loadedProposals, loadedCustomers) = try await (proposalRequest, customerRequest)
            proposals = loadedProposals
            customers = loadedCustomers.sorted {
                $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
            }
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
    
    private func downloadPDF(_ proposal: ProposalDTO) async {
        guard let id = proposal.id else { return }
        downloadingPDFId = id
        defer { downloadingPDFId = nil }
        do {
            let data = try await ProposalService.downloadPDF(id: id)
            pdfPreview = try PDFPreviewItem(
                data: data,
                fileName: "teklif-\(id).pdf",
                title: "Teklif #\(id)"
            )
        } catch {
            errorMessage = error.localizedDescription
        }
    }
    
    private func convert(_ proposal: ProposalDTO) async {
        guard let id = proposal.id, conversionAllowed(proposal) else { return }
        convertingProposalId = id
        defer { convertingProposalId = nil }
        do {
            _ = try await ProposalService.convertToJob(id: id)
            await load()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func conversionAllowed(_ proposal: ProposalDTO) -> Bool {
        guard proposal.id != nil else { return false }
        let status = proposal.status?.uppercased() ?? "DRAFT"
        return status != "APPROVED" && status != "REJECTED"
    }

    private func conversionButtonTitle(_ proposal: ProposalDTO) -> String {
        if convertingProposalId == proposal.id { return "İş Emri Oluşturuluyor…" }
        return conversionAllowed(proposal) ? "İşe Dönüştür" : "İşe Dönüştürülemez"
    }
}

struct ProposalEditorSheet: View {
    let proposal: ProposalDTO?
    let customers: [CustomerDTO]
    let onSaved: () async -> Void
    
    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var note = ""
    @State private var selectedCustomerId: Int?
    @State private var itemDescription = ""
    @State private var quantity = "1"
    @State private var unitPrice = ""
    @State private var items: [ProposalItemDraft] = []
    @State private var editingItemId: UUID?
    @State private var isSaving = false
    @State private var errorMessage: String?

    private var subtotal: Double {
        items.reduce(0) { $0 + $1.totalPrice }
    }

    private var taxRate: Double {
        proposal?.taxRate ?? 20
    }

    private var discount: Double {
        proposal?.discount ?? 0
    }

    private var total: Double {
        subtotal + (subtotal * taxRate / 100) - discount
    }
    
    var body: some View {
        NavigationStack {
            Form {
                TextField("Başlık", text: $title)
                Section("Müşteri") {
                    if customers.isEmpty {
                        ContentUnavailableView(
                            "Müşteri Bulunamadı",
                            systemImage: "person.crop.circle.badge.exclamationmark",
                            description: Text("Teklif oluşturmadan önce bir müşteri kaydı ekleyin.")
                        )
                    } else {
                        Picker("Müşteri Seç", selection: $selectedCustomerId) {
                            Text("Seçiniz").tag(Optional<Int>.none)
                            ForEach(customers) { customer in
                                Text(customer.name).tag(customer.id)
                            }
                        }
                    }
                }
                TextField("Not", text: $note, axis: .vertical)
                Section(editingItemId == nil ? "Yeni Kalem" : "Kalemi Düzenle") {
                    TextField("Açıklama", text: $itemDescription)
                    TextField("Adet", text: $quantity).keyboardType(.numberPad)
                    TextField("Birim Fiyat", text: $unitPrice).keyboardType(.decimalPad)

                    Button(editingItemId == nil ? "Kalem Ekle" : "Değişiklikleri Uygula") {
                        addOrUpdateItem()
                    }
                    .disabled(itemDescription.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                    if editingItemId != nil {
                        Button("Düzenlemeyi İptal", role: .cancel) {
                            resetItemEditor()
                        }
                    }
                }

                Section("Teklif Kalemleri (\(items.count))") {
                    if items.isEmpty {
                        Text("Henüz ürün veya hizmet eklenmedi.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(items) { item in
                            Button {
                                beginEditing(item)
                            } label: {
                                HStack(alignment: .top, spacing: 12) {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(item.description)
                                            .foregroundStyle(.primary)
                                        Text("\(item.quantity) × \(formatCurrency(item.unitPrice))")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Text(formatCurrency(item.totalPrice))
                                        .fontWeight(.semibold)
                                        .foregroundStyle(PusulaTheme.accent)
                                }
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                        .onDelete(perform: deleteItems)
                    }
                }

                Section("Toplam") {
                    LabeledContent("Ara toplam", value: formatCurrency(subtotal))
                    if taxRate != 0 {
                        LabeledContent("KDV (%\(taxRate.formatted()))", value: formatCurrency(subtotal * taxRate / 100))
                    }
                    if discount != 0 {
                        LabeledContent("İndirim", value: "−\(formatCurrency(discount))")
                    }
                    LabeledContent("Genel toplam") {
                        Text(formatCurrency(total))
                            .fontWeight(.bold)
                            .foregroundStyle(PusulaTheme.accent)
                    }
                }
            }
            .navigationTitle(proposal == nil ? "Yeni Teklif" : "Teklif Düzenle")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("İptal") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") { Task { await save() } }
                        .disabled(selectedCustomerId == nil || items.isEmpty || isSaving)
                        .readOnlyProtected()
                }
            }
            .onAppear {
                title = proposal?.title ?? ""
                note = proposal?.note ?? ""
                selectedCustomerId = proposal?.customerId
                items = (proposal?.items ?? []).map(ProposalItemDraft.init)
            }
            .alert("Teklif Kaydedilemedi", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Tamam", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
        }
    }

    private func addOrUpdateItem() {
        let description = itemDescription.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !description.isEmpty else {
            errorMessage = "Kalem açıklaması boş bırakılamaz."
            return
        }
        guard let qty = Int(quantity), qty > 0 else {
            errorMessage = "Adet sıfırdan büyük olmalıdır."
            return
        }
        guard let price = Double(unitPrice.replacingOccurrences(of: ",", with: ".")), price >= 0 else {
            errorMessage = "Geçerli bir birim fiyat girin."
            return
        }

        if let editingItemId,
           let index = items.firstIndex(where: { $0.id == editingItemId }) {
            items[index].description = description
            items[index].quantity = qty
            items[index].unitPrice = price
        } else {
            items.append(
                ProposalItemDraft(
                    proposalItemId: nil,
                    description: description,
                    quantity: qty,
                    unitCost: nil,
                    unitPrice: price
                )
            )
        }
        resetItemEditor()
    }

    private func beginEditing(_ item: ProposalItemDraft) {
        editingItemId = item.id
        itemDescription = item.description
        quantity = String(item.quantity)
        unitPrice = item.unitPrice.formatted(.number.precision(.fractionLength(0...2)))
    }

    private func resetItemEditor() {
        editingItemId = nil
        itemDescription = ""
        quantity = "1"
        unitPrice = ""
    }

    private func deleteItems(at offsets: IndexSet) {
        let deletedIds = offsets.map { items[$0].id }
        items.remove(atOffsets: offsets)
        if let editingItemId, deletedIds.contains(editingItemId) {
            resetItemEditor()
        }
    }
    
    private func save() async {
        guard let selectedCustomerId else {
            errorMessage = "Lütfen teklif için bir müşteri seçin."
            return
        }
        guard !items.isEmpty else {
            errorMessage = "Teklife en az bir ürün veya hizmet ekleyin."
            return
        }
        let proposalItems = items.map(\.dto)
        let dto = ProposalDTO(
            id: proposal?.id,
            companyId: proposal?.companyId,
            customerId: selectedCustomerId,
            customerName: customers.first(where: { $0.id == selectedCustomerId })?.name,
            preparedById: proposal?.preparedById,
            preparedByName: proposal?.preparedByName,
            status: proposal?.status ?? "DRAFT",
            validUntil: proposal?.validUntil,
            note: note.nilIfEmpty,
            title: title.nilIfEmpty,
            taxRate: proposal?.taxRate ?? 20,
            discount: proposal?.discount ?? 0,
            subtotal: subtotal,
            taxAmount: subtotal * taxRate / 100,
            totalPrice: total,
            items: proposalItems
        )
        isSaving = true
        defer { isSaving = false }
        do {
            if let id = proposal?.id {
                _ = try await ProposalService.updateProposal(id: id, proposal: dto)
            } else {
                _ = try await ProposalService.createProposal(dto)
            }
            await onSaved()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

private struct ProposalItemDraft: Identifiable {
    let id: UUID
    let proposalItemId: Int?
    var description: String
    var quantity: Int
    let unitCost: Double?
    var unitPrice: Double

    var totalPrice: Double {
        Double(quantity) * unitPrice
    }

    init(
        id: UUID = UUID(),
        proposalItemId: Int?,
        description: String,
        quantity: Int,
        unitCost: Double?,
        unitPrice: Double
    ) {
        self.id = id
        self.proposalItemId = proposalItemId
        self.description = description
        self.quantity = quantity
        self.unitCost = unitCost
        self.unitPrice = unitPrice
    }

    init(_ item: ProposalItemDTO) {
        self.init(
            proposalItemId: item.id,
            description: item.description,
            quantity: item.quantity,
            unitCost: item.unitCost,
            unitPrice: item.unitPrice
        )
    }

    var dto: ProposalItemDTO {
        ProposalItemDTO(
            id: proposalItemId,
            description: description,
            quantity: quantity,
            unitCost: unitCost,
            unitPrice: unitPrice,
            totalPrice: totalPrice
        )
    }
}

private extension String {
    var nilIfEmpty: String? {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : self
    }
}
