import SwiftUI

/// Selects a real inventory item without requiring a barcode scan.
/// The selected quantity is still validated by the backend when added to a ticket.
struct InventoryPartPickerView: View {
    let onItemSelected: (InventoryItemDTO, Int, Double) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var inventory: [InventoryItemDTO] = []
    @State private var searchText = ""
    @State private var selectedCategory: String?
    @State private var selectedItem: InventoryItemDTO?
    @State private var quantity = 1
    @State private var unitPriceText = ""
    @State private var showPriceChangeConfirmation = false
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
                                            Text("Stok: \(item.quantity)")
                                                .font(.caption)
                                                .foregroundStyle(item.quantity > 0 ? .green : .red)
                                        }
                                    }
                                    .contentShape(Rectangle())
                                }
                                .disabled(item.quantity < 1)
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
            .sheet(item: $selectedItem) { item in
                quantitySheet(item)
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

    private func quantitySheet(_ item: InventoryItemDTO) -> some View {
        NavigationStack {
            Form {
                Section("Yedek Parça") {
                    LabeledContent("Ürün", value: item.partName)
                    LabeledContent("Envanter satış fiyatı", value: formatCurrency(item.sellPrice ?? 0))
                    LabeledContent("Mevcut stok", value: "\(item.quantity)")
                }
                Section("Kullanılacak Adet") {
                    Stepper("\(quantity) adet", value: $quantity, in: 1...max(item.quantity, 1))
                }
                Section("Bu Servisteki Satış Fiyatı") {
                    TextField("Birim satış fiyatı", text: $unitPriceText)
                        .keyboardType(.decimalPad)
                    if let price = parsedUnitPrice {
                        LabeledContent("Toplam", value: formatCurrency(price * Double(quantity)))
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
                    Button("Vazgeç") { selectedItem = nil }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Ekle") {
                        if isPriceChanged(from: item.sellPrice ?? 0) {
                            showPriceChangeConfirmation = true
                        } else {
                            submit(item)
                        }
                    }
                    .disabled(item.quantity < 1 || parsedUnitPrice == nil)
                }
            }
            .alert("Satış fiyatı değiştirilsin mi?", isPresented: $showPriceChangeConfirmation) {
                Button("Vazgeç", role: .cancel) {}
                Button("Onayla ve Ekle") { submit(item) }
            } message: {
                Text("Envanter fiyatı: \(formatCurrency(item.sellPrice ?? 0))\nYeni fiyat: \(formatCurrency(parsedUnitPrice ?? 0))")
            }
        }
        .presentationDetents([.medium])
    }

    private func select(_ item: InventoryItemDTO) {
        quantity = 1
        unitPriceText = String(format: "%.2f", item.sellPrice ?? 0)
        selectedItem = item
    }

    private var parsedUnitPrice: Double? {
        let normalized = unitPriceText
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: ",", with: ".")
        guard let value = Double(normalized), value >= 0, value.isFinite else { return nil }
        return value
    }

    private func isPriceChanged(from inventoryPrice: Double) -> Bool {
        guard let price = parsedUnitPrice else { return false }
        return abs(price - inventoryPrice) >= 0.005
    }

    private func submit(_ item: InventoryItemDTO) {
        guard let price = parsedUnitPrice else { return }
        onItemSelected(item, quantity, price)
        selectedItem = nil
        dismiss()
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
}
