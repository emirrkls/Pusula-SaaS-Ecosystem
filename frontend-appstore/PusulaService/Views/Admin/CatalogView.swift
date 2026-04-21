import SwiftUI

/// Admin-only full catalog view with buy + sell prices and bulk editing.
struct CatalogView: View {
    @State private var inventory: [InventoryItemDTO] = []
    @State private var isLoading = true
    @State private var searchText = ""
    @State private var editingItem: InventoryItemDTO?
    
    var filteredItems: [InventoryItemDTO] {
        if searchText.isEmpty { return inventory }
        return inventory.filter {
            $0.partName.localizedCaseInsensitiveContains(searchText) ||
            ($0.brand ?? "").localizedCaseInsensitiveContains(searchText) ||
            ($0.category ?? "").localizedCaseInsensitiveContains(searchText)
        }
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // Search bar
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.secondary)
                TextField("Parça ara...", text: $searchText)
            }
            .padding(10)
            .background(Color(.systemGray6))
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .padding(.horizontal)
            .padding(.top, 8)
            
            if isLoading {
                Spacer()
                ProgressView("Katalog yükleniyor...")
                Spacer()
            } else {
                List(filteredItems) { item in
                    catalogRow(item)
                        .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle("Katalog Yönetimi")
        .task {
            do {
                inventory = try await TicketService.getInventory()
            } catch {}
            isLoading = false
        }
    }
    
    private func catalogRow(_ item: InventoryItemDTO) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.partName)
                        .font(.subheadline.weight(.medium))
                    HStack(spacing: 8) {
                        if let brand = item.brand {
                            Text(brand)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Text("Stok: \(item.quantity)")
                            .font(.caption)
                            .foregroundColor(item.quantity > 5 ? .green : .orange)
                    }
                }
                Spacer()
            }
            
            // Price section (admin can see both)
            HStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Alış")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                    Text("₺\(String(format: "%.2f", item.buyPrice ?? 0))")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(.red)
                }
                
                Image(systemName: "arrow.right")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text("Satış")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                    Text("₺\(String(format: "%.2f", item.sellPrice ?? 0))")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(.green)
                }
                
                Spacer()
                
                // Margin badge
                let margin = calculateMargin(buy: item.buyPrice, sell: item.sellPrice)
                Text("%\(String(format: "%.0f", margin))")
                    .font(.caption.weight(.bold))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(margin > 30 ? .green.opacity(0.15) : .orange.opacity(0.15))
                    .foregroundColor(margin > 30 ? .green : .orange)
                    .clipShape(Capsule())
            }
        }
        .padding(.vertical, 4)
    }
    
    private func calculateMargin(buy: Double?, sell: Double?) -> Double {
        guard let sell = sell, sell > 0, let buy = buy else { return 0 }
        return ((sell - buy) / sell) * 100
    }
}
