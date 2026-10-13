import SwiftUI

/// Profit analysis view — shows COGS vs revenue, gross margin, and top profitable parts.
struct ProfitAnalysisView: View {
    @State private var analysis: ProfitAnalysis?
    @State private var isLoading = true
    @State private var loadRequest = RefreshRequestState()
    @State private var errorMessage: String?
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                if let errorMessage, analysis != nil { ReadLoadFailureView(message: errorMessage) { Task { await load() } } }
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
                } else if !isLoading {
                    Button("common.retry") { Task { await load() } }
                }
            }
            .padding()
        }
        .background(PusulaTheme.page)
        .navigationTitle("Kâr Analizi")
        .task { await load() }
        .refreshable { await load() }
        .onDisappear { loadRequest.invalidate(); isLoading = false }
        .overlay {
            if isLoading && analysis == nil {
                ProgressView("Analiz hesaplanıyor...")
            }
        }
    }

    private func load() async {
        guard let snapshot = SessionManager.shared.authenticatedSnapshot,
              let request = loadRequest.begin(for: snapshot) else { isLoading = loadRequest.isLoading; return }
        isLoading = true
        defer { loadRequest.finish(request); isLoading = loadRequest.isLoading }
        do {
            let loaded = try await AdminService.getProfitAnalysis()
            guard loadRequest.accepts(request, session: SessionManager.shared.authenticatedSnapshot, isCancelled: Task.isCancelled) else { return }
            analysis = loaded
            errorMessage = nil
        } catch {
            guard !LoadCancellationPolicy.isCancellation(error, taskIsCancelled: Task.isCancelled),
                  loadRequest.accepts(request, session: SessionManager.shared.authenticatedSnapshot, isCancelled: Task.isCancelled) else { return }
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
