import SwiftUI

struct CatalogView: View {
    @State private var inventory: [InventoryItemDTO] = []
    @State private var isLoading = true
    @State private var isLookingUpBarcode = false
    @State private var searchText = ""
    @State private var presentedSheet: CatalogSheet?
    @State private var itemPendingDeletion: InventoryItemDTO?
    @State private var deletingItemId: Int?
    @State private var errorMessage: String?

    private var filteredItems: [InventoryItemDTO] {
        guard !searchText.isEmpty else { return inventory }
        return inventory.filter {
            $0.partName.localizedCaseInsensitiveContains(searchText) ||
            ($0.brand ?? "").localizedCaseInsensitiveContains(searchText) ||
            ($0.category ?? "").localizedCaseInsensitiveContains(searchText) ||
            ($0.barcode ?? "").localizedCaseInsensitiveContains(searchText)
        }
    }

    var body: some View {
        catalogContent
        .navigationTitle("Katalog Yönetimi")
        .searchable(text: $searchText, prompt: "Parça, marka veya barkod ara")
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { presentedSheet = .scanner } label: {
                    Image(systemName: "barcode.viewfinder")
                }
                .disabled(isLookingUpBarcode)
                .readOnlyProtected()
                .accessibilityLabel("Barkod tara")
                .help("Barkod tara")

                Button { presentedSheet = .editor(item: nil, barcode: nil) } label: {
                    Image(systemName: "plus")
                }
                .readOnlyProtected()
                .accessibilityLabel("Stok kalemi ekle")
                .help("Yeni stok kalemi")
            }
        }
        .overlay {
            if isLookingUpBarcode {
                ProgressView("Barkod aranıyor...")
                    .padding()
                    .background(.regularMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            }
        }
        .task { await loadInventory() }
        .sheet(item: $presentedSheet) { sheet in
            switch sheet {
            case .editor(let item, let barcode):
                InventoryEditorSheet(item: item, initialBarcode: barcode) { savedItem in
                    apply(savedItem)
                }
            case .scanner:
                InventoryBarcodeScannerSheet { code in
                    presentedSheet = nil
                    Task { await handleScannedBarcode(code) }
                }
            }
        }
        .confirmationDialog(
            "Stok kalemi silinsin mi?",
            isPresented: Binding(get: { itemPendingDeletion != nil }, set: { if !$0 { itemPendingDeletion = nil } }),
            titleVisibility: .visible
        ) {
            Button("Sil", role: .destructive) {
                guard let item = itemPendingDeletion else { return }
                itemPendingDeletion = nil
                Task { await delete(item) }
            }
            Button("Vazgeç", role: .cancel) { itemPendingDeletion = nil }
        } message: {
            Text("Bu işlem geri alınamaz ve stok kalemi katalogdan kaldırılır.")
        }
        .alert("Katalog İşlemi Başarısız", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Tamam", role: .cancel) { errorMessage = nil }
        } message: {
            Text(errorMessage ?? "")
        }
    }

    @ViewBuilder
    private var catalogContent: some View {
        if isLoading && inventory.isEmpty {
            ProgressView("Katalog yükleniyor...")
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if filteredItems.isEmpty {
            ContentUnavailableView(
                searchText.isEmpty ? "Henüz Stok Kalemi Yok" : "Sonuç Bulunamadı",
                systemImage: searchText.isEmpty ? "shippingbox" : "magnifyingglass",
                description: Text(searchText.isEmpty ? "İlk stok kalemini ekleyerek başlayın." : "Arama metnini değiştirip tekrar deneyin.")
            )
        } else {
            List(filteredItems) { item in
                CatalogItemRow(
                    item: item,
                    isDeleting: deletingItemId == item.id,
                    onEdit: { presentedSheet = .editor(item: item, barcode: nil) },
                    onDelete: { itemPendingDeletion = item }
                )
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                    Button(role: .destructive) { itemPendingDeletion = item } label: {
                        Label("Sil", systemImage: "trash")
                    }
                    .readOnlyProtected()

                    Button { presentedSheet = .editor(item: item, barcode: nil) } label: {
                        Label("Düzenle", systemImage: "pencil")
                    }
                    .tint(.blue)
                    .readOnlyProtected()
                }
            }
            .listStyle(.plain)
            .refreshable { await loadInventory(refresh: true) }
        }
    }

    private func loadInventory(refresh: Bool = false) async {
        if !refresh { isLoading = true }
        do {
            inventory = try await TicketService.getInventory()
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    private func handleScannedBarcode(_ code: String) async {
        isLookingUpBarcode = true
        defer { isLookingUpBarcode = false }
        do {
            let item = try await TicketService.lookupBarcode(code)
            presentedSheet = .editor(item: item, barcode: nil)
        } catch NetworkError.serverError(let statusCode) where statusCode == 404 {
            presentedSheet = .editor(item: nil, barcode: code)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func apply(_ savedItem: InventoryItemDTO) {
        if let index = inventory.firstIndex(where: { $0.id == savedItem.id }) {
            inventory[index] = savedItem
        } else {
            inventory.insert(savedItem, at: 0)
        }
    }

    private func delete(_ item: InventoryItemDTO) async {
        deletingItemId = item.id
        defer { deletingItemId = nil }
        do {
            try await TicketService.deleteInventory(id: item.id)
            inventory.removeAll { $0.id == item.id }
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

private struct CatalogItemRow: View {
    let item: InventoryItemDTO
    let isDeleting: Bool
    let onEdit: () -> Void
    let onDelete: () -> Void

    private var margin: Double {
        guard let sell = item.sellPrice, sell > 0, let buy = item.buyPrice else { return 0 }
        return ((sell - buy) / sell) * 100
    }

    private var stockColor: Color {
        if item.quantity == 0 { return .red }
        if let critical = item.criticalLevel, item.quantity <= critical { return .orange }
        return .green
    }

    private var stockIcon: String {
        if item.quantity == 0 { return "exclamationmark.octagon.fill" }
        if let critical = item.criticalLevel, item.quantity <= critical { return "exclamationmark.triangle.fill" }
        return "shippingbox.fill"
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: stockIcon)
                .foregroundStyle(stockColor)
                .frame(width: 32, height: 32)

            VStack(alignment: .leading, spacing: 7) {
                titleRow
                priceRow
                if let barcode = item.barcode?.nilIfBlank {
                    Label(barcode, systemImage: "barcode")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }

            actionMenu
        }
        .padding(.vertical, 6)
    }

    private var titleRow: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                Text(item.partName).font(.headline)
                Text([item.brand, item.category].compactMap { $0?.nilIfBlank }.joined(separator: " · "))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Text("\(item.quantity) adet")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(stockColor)
        }
    }

    private var priceRow: some View {
        HStack(spacing: 16) {
            priceLabel("Alış", value: item.buyPrice)
            priceLabel("Satış", value: item.sellPrice)
            Spacer()
            Text("%\(String(format: "%.0f", margin)) marj")
                .font(.caption.weight(.semibold))
                .foregroundStyle(margin >= 30 ? .green : .orange)
        }
    }

    @ViewBuilder
    private var actionMenu: some View {
        if isDeleting {
            ProgressView().controlSize(.small)
        } else {
            Menu {
                Button(action: onEdit) { Label("Düzenle", systemImage: "pencil") }
                Button(role: .destructive, action: onDelete) { Label("Sil", systemImage: "trash") }
            } label: {
                Image(systemName: "ellipsis.circle")
                    .frame(width: 32, height: 32)
            }
            .readOnlyProtected()
            .accessibilityLabel("Stok kalemi işlemleri")
        }
    }

    private func priceLabel(_ title: String, value: Double?) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(title).font(.caption2).foregroundStyle(.secondary)
            Text(value.map { "₺\(String(format: "%.2f", $0))" } ?? "-")
                .font(.caption.weight(.semibold))
        }
    }
}

private enum CatalogSheet: Identifiable {
    case editor(item: InventoryItemDTO?, barcode: String?)
    case scanner

    var id: String {
        switch self {
        case .editor: return "editor"
        case .scanner: return "scanner"
        }
    }
}

private struct InventoryEditorSheet: View {
    let item: InventoryItemDTO?
    let initialBarcode: String?
    let onSaved: (InventoryItemDTO) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var partName = ""
    @State private var quantity = "1"
    @State private var buyPrice = ""
    @State private var sellPrice = ""
    @State private var criticalLevel = ""
    @State private var brand = ""
    @State private var category = ""
    @State private var barcode = ""
    @State private var showScanner = false
    @State private var isSaving = false
    @State private var errorMessage: String?

    init(item: InventoryItemDTO?, initialBarcode: String?, onSaved: @escaping (InventoryItemDTO) -> Void) {
        self.item = item
        self.initialBarcode = initialBarcode
        self.onSaved = onSaved
        _partName = State(initialValue: item?.partName ?? "")
        _quantity = State(initialValue: String(item?.quantity ?? 1))
        _buyPrice = State(initialValue: item?.buyPrice.map { String(format: "%.2f", $0) } ?? "")
        _sellPrice = State(initialValue: item?.sellPrice.map { String(format: "%.2f", $0) } ?? "")
        _criticalLevel = State(initialValue: item?.criticalLevel.map(String.init) ?? "")
        _brand = State(initialValue: item?.brand ?? "")
        _category = State(initialValue: item?.category ?? "")
        _barcode = State(initialValue: initialBarcode ?? item?.barcode ?? "")
    }

    private var canSave: Bool {
        !partName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        nonNegativeInteger(quantity) != nil &&
        optionalNonNegativeDecimal(buyPrice).isValid &&
        optionalNonNegativeDecimal(sellPrice).isValid &&
        optionalNonNegativeInteger(criticalLevel).isValid &&
        !isSaving
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Ürün") {
                    TextField("Parça adı", text: $partName)
                    TextField("Marka (opsiyonel)", text: $brand)
                    TextField("Kategori (opsiyonel)", text: $category)
                }

                Section("Stok ve Fiyat") {
                    TextField("Adet", text: $quantity)
                        .keyboardType(.numberPad)
                    TextField("Kritik stok seviyesi (opsiyonel)", text: $criticalLevel)
                        .keyboardType(.numberPad)
                    TextField("Alış fiyatı (opsiyonel)", text: $buyPrice)
                        .keyboardType(.decimalPad)
                    TextField("Satış fiyatı (opsiyonel)", text: $sellPrice)
                        .keyboardType(.decimalPad)
                }

                Section("Barkod") {
                    TextField("Barkod (opsiyonel)", text: $barcode)
                        .keyboardType(.asciiCapable)
                    Button { showScanner = true } label: {
                        Label("Kamerayla Tara", systemImage: "barcode.viewfinder")
                    }
                }
            }
            .navigationTitle(item == nil ? "Yeni Stok Kalemi" : "Stok Kalemini Düzenle")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Vazgeç") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") { Task { await save() } }
                        .disabled(!canSave)
                }
            }
            .interactiveDismissDisabled(isSaving)
            .sheet(isPresented: $showScanner) {
                InventoryBarcodeScannerSheet { code in
                    barcode = code
                    showScanner = false
                }
            }
            .alert("Stok Kalemi Kaydedilemedi", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Tamam", role: .cancel) { errorMessage = nil }
            } message: {
                Text(errorMessage ?? "")
            }
        }
    }

    private func save() async {
        guard let parsedQuantity = nonNegativeInteger(quantity) else { return }
        isSaving = true
        defer { isSaving = false }

        let dto = InventoryItemDTO(
            id: item?.id ?? 0,
            partName: partName.trimmingCharacters(in: .whitespacesAndNewlines),
            quantity: parsedQuantity,
            buyPrice: optionalNonNegativeDecimal(buyPrice).value,
            sellPrice: optionalNonNegativeDecimal(sellPrice).value,
            criticalLevel: optionalNonNegativeInteger(criticalLevel).value,
            brand: brand.nilIfBlank,
            category: category.nilIfBlank,
            barcode: barcode.nilIfBlank
        )

        do {
            let saved = if let id = item?.id {
                try await TicketService.updateInventory(id: id, item: dto)
            } else {
                try await TicketService.createInventory(dto)
            }
            onSaved(saved)
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func nonNegativeInteger(_ text: String) -> Int? {
        guard let value = Int(text.trimmingCharacters(in: .whitespaces)), value >= 0 else { return nil }
        return value
    }

    private func optionalNonNegativeInteger(_ text: String) -> (value: Int?, isValid: Bool) {
        let trimmed = text.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return (nil, true) }
        guard let value = Int(trimmed), value >= 0 else { return (nil, false) }
        return (value, true)
    }

    private func optionalNonNegativeDecimal(_ text: String) -> (value: Double?, isValid: Bool) {
        let normalized = text.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: ".")
        guard !normalized.isEmpty else { return (nil, true) }
        guard let value = Double(normalized), value >= 0 else { return (nil, false) }
        return (value, true)
    }
}

private struct InventoryBarcodeScannerSheet: View {
    let onScanned: (String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var detectedCode: String?

    var body: some View {
        NavigationStack {
            ZStack {
                CameraPreview { code in
                    guard detectedCode == nil else { return }
                    detectedCode = code
                    onScanned(code)
                }
                .ignoresSafeArea()

                RoundedRectangle(cornerRadius: 8)
                    .stroke(PusulaTheme.accent, lineWidth: 3)
                    .frame(width: 290, height: 150)
            }
            .navigationTitle("Barkod Tara")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Vazgeç") { dismiss() }
                }
            }
        }
    }
}

private extension String {
    var nilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
