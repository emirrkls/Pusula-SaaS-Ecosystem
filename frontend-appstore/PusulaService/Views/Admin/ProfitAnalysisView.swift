import SwiftUI

/// Profit analysis view — shows COGS vs revenue, gross margin, and top profitable parts.
struct ProfitAnalysisView: View {
    @State private var analysis: ProfitAnalysis?
    @State private var isLoading = true
    @State private var errorMessage: String?
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                if let a = analysis {
                    // Summary cards
                    summarySection(a)
                    
                    // Top parts table
                    if let parts = a.topProfitableParts, !parts.isEmpty {
                        topPartsSection(parts)
                    }
                } else if let errorMessage {
                    ContentUnavailableView {
                        Label("Analiz Yüklenemedi", systemImage: "exclamationmark.triangle")
                    } description: {
                        Text(errorMessage)
                    } actions: {
                        Button("Tekrar Dene") { Task { await load() } }
                            .buttonStyle(.borderedProminent)
                    }
                }
            }
            .padding()
        }
        .background(PusulaTheme.page)
        .navigationTitle("Kâr Analizi")
        .task { await load() }
        .overlay {
            if isLoading {
                ProgressView("Analiz hesaplanıyor...")
            }
        }
    }

    private func load() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            analysis = try await AdminService.getProfitAnalysis()
        } catch {
            analysis = nil
            errorMessage = error.localizedDescription
        }
    }
    
    private func summarySection(_ a: ProfitAnalysis) -> some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                summaryCard("Maliyet (COGS)", value: a.totalCostOfGoodsSold, color: .red)
                summaryCard("Parça Geliri", value: a.totalRevenueFromParts, color: PusulaTheme.accent)
            }
            HStack(spacing: 12) {
                summaryCard("Brüt Kâr", value: a.grossProfit,
                            color: (a.grossProfit ?? 0) >= 0 ? .green : .red)
                
                VStack(alignment: .leading, spacing: 6) {
                    Text("Brüt Marj")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text("%\(String(format: "%.1f", a.grossMarginPercent ?? 0))")
                        .font(.title.weight(.bold))
                        .foregroundColor(PusulaTheme.accentStrong)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .pusulaCard()
            }
        }
    }
    
    private func summaryCard(_ title: String, value: Double?, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text("₺\(String(format: "%.2f", value ?? 0))")
                .font(.title3.weight(.bold))
                .foregroundColor(color)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .pusulaCard()
    }
    
    private func topPartsSection(_ parts: [PartProfit]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("En Kârlı Parçalar (Top 10)", systemImage: "star.fill")
                .font(.subheadline.weight(.semibold))
            
            ForEach(parts) { part in
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(part.partName)
                            .font(.subheadline.weight(.medium))
                        HStack(spacing: 8) {
                            Text("Alış: ₺\(String(format: "%.2f", part.buyPrice ?? 0))")
                            Text("→")
                            Text("Satış: ₺\(String(format: "%.2f", part.sellPrice ?? 0))")
                        }
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    }
                    
                    Spacer()
                    
                    VStack(alignment: .trailing, spacing: 2) {
                        Text("₺\(String(format: "%.2f", part.totalProfit ?? 0))")
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(.green)
                        Text("%\(String(format: "%.0f", part.marginPercent ?? 0)) marj")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(.vertical, 4)
                
                if part.id != parts.last?.id {
                    Divider()
                }
            }
        }
        .pusulaCard()
    }
}
