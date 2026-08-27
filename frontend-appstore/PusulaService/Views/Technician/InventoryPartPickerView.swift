import SwiftUI

/// Selects a real inventory item without requiring a barcode scan.
/// The selected quantity is still validated by the backend when added to a ticket.
struct InventoryPartPickerView: View {
    let onItemSelected: (InventoryItemDTO, Double, Double) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var inventory: [InventoryItemDTO] = []
    @State private var searchText = ""
    @State private var selectedCategory: String?
    @State private var selectionDraft: InventoryPartSelectionDraft?
    @State private var isLoading = true
    @State private var errorMessage: String?

    private var filteredItems: [InventoryItemDTO] {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        return inventory.filter {
            let matchesCategory = selectedCategory == nil || $0.category == selectedCategory
            let matchesQuery = query.isEmpty ||
                $0.partName.localizedCaseInsensitiveContains(query) ||
                ($0.brand ?? "").localizedCaseInsensitiveContains(query) ||
                ($0.category ?? "").localizedCaseInsensitiveContains(query) ||
                ($0.barcode ?? "").localizedCaseInsensitiveContains(query)
            return matchesCategory && matchesQuery
        }
    }

    private var categories: [String] {
        Array(Set(inventory.compactMap { item in
            guard let category = item.category?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !category.isEmpty else { return nil }
            return category
        })).sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
    }

    var body: some View {
        NavigationStack {
            Group {
                if isLoading {
                    ProgressView("Stok yükleniyor...")
                } else if inventory.isEmpty {
                    ContentUnavailableView(
                        "Stok bulunamadı",
                        systemImage: "shippingbox",
                        description: Text("Envantere ürün eklendiğinde burada görüntülenecek.")
                    )
                } else {
                    List {
                        if !categories.isEmpty {
                            Picker("Kategori", selection: $selectedCategory) {
                                Text("Tüm kategoriler").tag(Optional<String>.none)
                                ForEach(categories, id: \.self) { category in
                                    Text(category).tag(Optional(category))
                                }
                            }
                            .pickerStyle(.menu)
                        }

                        if filteredItems.isEmpty {
                            ContentUnavailableView(
                                "Yedek parça bulunamadı",
                                systemImage: "magnifyingglass",
                                description: Text(searchText.isEmpty
                                    ? "Seçilen kategoride kayıtlı parça yok."
                                    : "Aramayı veya kategori filtresini değiştirin.")
                            )
                        } else {
                            ForEach(filteredItems) { item in
                                Button { select(item) } label: {
                                    HStack(spacing: 12) {
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(item.partName)
                                                .font(.subheadline.weight(.semibold))
                                                .foregroundStyle(.primary)
                                            HStack(spacing: 8) {
                                                if let brand = item.brand, !brand.isEmpty { Text(brand) }
                                                if let barcode = item.barcode, !barcode.isEmpty {
                                                    Label(barcode, systemImage: "barcode")
                                                }
                                            }
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                        }
                                        Spacer()
                                        VStack(alignment: .trailing, spacing: 4) {
                                            Text(formatCurrency(item.sellPrice ?? 0))
                                                .font(.subheadline.weight(.bold))
                                                .foregroundStyle(PusulaTheme.accent)
                                            Text("Stok: \(formatQuantity(item.quantity)) \(item.unitLabel)")
                                                .font(.caption)
                                                .foregroundStyle(item.quantity > 0 ? .green : .red)
                                        }
                                    }
                                    .contentShape(Rectangle())
                                }
                                .disabled(item.quantity <= 0)
                            }
                        }
                    }
                    .listStyle(.plain)
                    .searchable(text: $searchText, prompt: "Parça, marka veya barkod ara")
                    .refreshable { await loadInventory() }
                }
            }
            .navigationTitle("Yedek Parça Seç")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("İptal") { dismiss() }
                }
            }
            .task { await loadInventory() }
            .sheet(item: $selectionDraft) { draft in
                InventoryPartQuantitySheet(item: draft.item) { item, quantity, unitPrice in
                    onItemSelected(item, quantity, unitPrice)
                    selectionDraft = nil
                    dismiss()
                }
            }
            .alert("Stok Yüklenemedi", isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )) {
                Button("Tamam", role: .cancel) { errorMessage = nil }
            } message: {
                Text(errorMessage ?? "")
            }
        }
    }

    private func select(_ item: InventoryItemDTO) {
        selectionDraft = InventoryPartSelectionDraft(item: item)
    }

    @MainActor
    private func loadInventory() async {
        isLoading = true
        defer { isLoading = false }
        do {
            inventory = try await TicketService.getInventory().sorted {
                $0.partName.localizedCaseInsensitiveCompare($1.partName) == .orderedAscending
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func formatCurrency(_ value: Double) -> String {
        value.formatted(.currency(code: "TRY").locale(Locale(identifier: "tr_TR")))
    }

    private func formatQuantity(_ value: Double) -> String {
        value.formatted(.number.precision(.fractionLength(0...3)).locale(Locale(identifier: "tr_TR")))
    }
}

private struct InventoryPartSelectionDraft: Identifiable {
    let id = UUID()
    let item: InventoryItemDTO
}

private struct InventoryPartQuantitySheet: View {
    let item: InventoryItemDTO
    let onSubmit: (InventoryItemDTO, Double, Double) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var quantity: Double
    @State private var unitPriceText: String
    @State private var showPriceChangeConfirmation = false

    init(
        item: InventoryItemDTO,
        onSubmit: @escaping (InventoryItemDTO, Double, Double) -> Void
    ) {
        self.item = item
        self.onSubmit = onSubmit
        _quantity = State(initialValue: min(item.allowsFractionalQuantity ? 0.001 : 1, max(item.quantity, 0.001)))
        _unitPriceText = State(initialValue: (item.sellPrice ?? 0).formatted(
            .number.precision(.fractionLength(2)).locale(Locale(identifier: "tr_TR"))
        ))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Yedek Parça") {
                    LabeledContent("Ürün", value: item.partName)
                    LabeledContent("Envanter satış fiyatı", value: formatCurrency(item.sellPrice ?? 0))
                    LabeledContent("Mevcut stok", value: "\(formatQuantity(item.quantity)) \(item.unitLabel)")
                }
                Section("Kullanılacak Miktar") {
                    TextField("Miktar (\(item.unitLabel))", value: $quantity, format: .number)
                        .keyboardType(.decimalPad)
                    Stepper("\(formatQuantity(quantity)) \(item.unitLabel)", value: $quantity,
                            in: quantityStep...max(item.quantity, quantityStep), step: quantityStep)
                }
                Section("Bu Servisteki Satış Fiyatı") {
                    TextField("Birim satış fiyatı", text: $unitPriceText)
                        .keyboardType(.decimalPad)
                    if let price = parsedUnitPrice {
                        LabeledContent("Toplam", value: formatCurrency(price * quantity))
                    }
                    Text("Değiştirmezseniz envanterdeki satış fiyatı kullanılır.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Parçayı Ekle")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Vazgeç") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Ekle") {
                        if isPriceChanged {
                            showPriceChangeConfirmation = true
                        } else {
                            submit()
                        }
                    }
                    .disabled(!isValid)
                }
            }
            .alert("Satış fiyatı değiştirilsin mi?", isPresented: $showPriceChangeConfirmation) {
                Button("Vazgeç", role: .cancel) {}
                Button("Onayla ve Ekle") { submit() }
            } message: {
                Text("Envanter fiyatı: \(formatCurrency(item.sellPrice ?? 0))\nYeni fiyat: \(formatCurrency(parsedUnitPrice ?? 0))")
            }
        }
        .presentationDetents([.medium])
    }

    private var parsedUnitPrice: Double? {
        let normalized = unitPriceText
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: ",", with: ".")
        guard let value = Double(normalized), value >= 0, value.isFinite else { return nil }
        return value
    }

    private var isValid: Bool {
        item.quantity > 0 && quantity > 0 && quantity <= item.quantity && parsedUnitPrice != nil &&
            (item.allowsFractionalQuantity || quantity.rounded() == quantity)
    }

    private var isPriceChanged: Bool {
        guard let price = parsedUnitPrice else { return false }
        return abs(price - (item.sellPrice ?? 0)) >= 0.005
    }

    private func submit() {
        guard isValid, let price = parsedUnitPrice else { return }
        onSubmit(item, quantity, price)
    }

    private func formatCurrency(_ value: Double) -> String {
        value.formatted(.currency(code: "TRY").locale(Locale(identifier: "tr_TR")))
    }

    private var quantityStep: Double { item.allowsFractionalQuantity ? 0.001 : 1 }

    private func formatQuantity(_ value: Double) -> String {
        value.formatted(.number.precision(.fractionLength(0...3)).locale(Locale(identifier: "tr_TR")))
    }
}
