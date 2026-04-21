import SwiftUI

/// Admin dashboard — financial KPIs, quota bars, technician summary, and quick actions.
struct AdminDashboardView: View {
    @State private var kpis: DashboardKPIs?
    @State private var techStats: [TechnicianStat] = []
    @State private var quotaStatus: QuotaStatus?
    @State private var isLoading = true
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Welcome header
                headerSection
                
                // KPI Cards
                if let kpis = kpis {
                    kpiCardsSection(kpis)
                }
                
                // Quota Progress
                if let quotas = quotaStatus?.quotas, !quotas.isEmpty {
                    quotaSection(quotas)
                }
                
                // Technician performance
                if !techStats.isEmpty {
                    technicianSection
                }
                
                // Quick Actions
                quickActionsSection
            }
            .padding()
        }
        .navigationTitle("Müdür Paneli")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await loadData() }
        .task { await loadData() }
        .overlay {
            if isLoading {
                ProgressView("Yükleniyor...")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(.ultraThinMaterial)
            }
        }
    }
    
    // MARK: - Header
    
    private var headerSection: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("Merhaba, \(SessionManager.shared.fullName)")
                    .font(.title2.weight(.bold))
                HStack(spacing: 6) {
                    Image(systemName: "building.2")
                    Text(SessionManager.shared.companyName ?? "")
                    Text("•")
                    Text(SessionManager.shared.planType)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 2)
                        .background(.cyan.opacity(0.2))
                        .clipShape(Capsule())
                }
                .font(.subheadline)
                .foregroundStyle(.secondary)
            }
            Spacer()
            Button(action: { SessionManager.shared.logout() }) {
                Image(systemName: "rectangle.portrait.and.arrow.right")
                    .font(.title3)
                    .foregroundColor(.red)
            }
        }
    }
    
    // MARK: - KPI Cards
    
    private func kpiCardsSection(_ kpis: DashboardKPIs) -> some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                kpiCard("Aylık Ciro",
                        value: "₺\(formatAmount(kpis.monthlyRevenue))",
                        icon: "chart.line.uptrend.xyaxis",
                        gradient: [.cyan, .blue])
                
                kpiCard("Bekleyen Alacak",
                        value: "₺\(formatAmount(kpis.outstandingDebt))",
                        icon: "exclamationmark.triangle",
                        gradient: [.orange, .red])
            }
            
            HStack(spacing: 12) {
                kpiCard("Net Kâr",
                        value: "₺\(formatAmount(kpis.netProfit))",
                        icon: "turkishlirasign.circle",
                        gradient: kpis.netProfit ?? 0 >= 0 ? [.green, .mint] : [.red, .pink])
                
                kpiCard("Kâr Marjı",
                        value: "%\(String(format: "%.1f", kpis.profitMargin ?? 0))",
                        icon: "percent",
                        gradient: [.purple, .indigo])
            }
            
            HStack(spacing: 12) {
                miniCard("Aktif İş", value: "\(kpis.activeTickets ?? 0)", icon: "wrench.and.screwdriver", color: .blue)
                miniCard("Bu Ay Biten", value: "\(kpis.completedThisMonth ?? 0)", icon: "checkmark.circle", color: .green)
                miniCard("Envanter", value: "₺\(formatAmount(kpis.inventoryValue))", icon: "shippingbox", color: .purple)
            }
        }
    }
    
    private func kpiCard(_ title: String, value: String, icon: String, gradient: [Color]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: icon)
                    .font(.title3)
                Spacer()
            }
            .foregroundColor(.white.opacity(0.9))
            
            Text(value)
                .font(.title2.weight(.bold))
                .foregroundColor(.white)
                .minimumScaleFactor(0.7)
                .lineLimit(1)
            
            Text(title)
                .font(.caption)
                .foregroundColor(.white.opacity(0.8))
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(colors: gradient, startPoint: .topLeading, endPoint: .bottomTrailing)
        )
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
    
    private func miniCard(_ title: String, value: String, icon: String, color: Color) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundColor(color)
            Text(value)
                .font(.subheadline.weight(.bold))
                .minimumScaleFactor(0.7)
            Text(title)
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
    
    // MARK: - Quota Section
    
    private func quotaSection(_ quotas: [QuotaItem]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("Kullanım Limitleri", systemImage: "gauge.with.dots.needle.33percent")
                .font(.subheadline.weight(.semibold))
            
            ForEach(quotas) { quota in
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(quota.featureLabel ?? quota.featureKey)
                            .font(.caption.weight(.medium))
                        Spacer()
                        Text("\(quota.currentUsage ?? 0) / \(quota.limit ?? 0)")
                            .font(.caption.weight(.semibold))
                            .foregroundColor(quotaColor(quota.usagePercent ?? 0))
                    }
                    
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 4)
                                .fill(Color(.systemGray5))
                                .frame(height: 6)
                            
                            RoundedRectangle(cornerRadius: 4)
                                .fill(quotaColor(quota.usagePercent ?? 0))
                                .frame(width: geo.size.width * min(CGFloat(quota.usagePercent ?? 0) / 100, 1), height: 6)
                                .animation(.easeInOut(duration: 0.5), value: quota.usagePercent)
                        }
                    }
                    .frame(height: 6)
                }
            }
        }
        .padding()
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
    
    private func quotaColor(_ percent: Double) -> Color {
        if percent >= 90 { return .red }
        if percent >= 70 { return .orange }
        return .green
    }
    
    // MARK: - Technician Section
    
    private var technicianSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("Saha Personeli", systemImage: "person.3")
                .font(.subheadline.weight(.semibold))
            
            ForEach(techStats) { tech in
                HStack {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(tech.fullName ?? "Teknisyen")
                            .font(.subheadline.weight(.medium))
                        HStack(spacing: 8) {
                            HStack(spacing: 2) {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundColor(.green)
                                Text("\(tech.completedToday ?? 0) bugün")
                            }
                            HStack(spacing: 2) {
                                Image(systemName: "wrench")
                                    .foregroundColor(.blue)
                                Text("\(tech.activeTickets ?? 0) aktif")
                            }
                        }
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    }
                    
                    Spacer()
                    
                    VStack(alignment: .trailing, spacing: 2) {
                        Text("₺\(formatAmount(tech.collectedToday))")
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(.cyan)
                        Text("bugün")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(.vertical, 6)
                
                if tech.id != techStats.last?.id {
                    Divider()
                }
            }
        }
        .padding()
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
    
    // MARK: - Quick Actions
    
    private var quickActionsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Hızlı İşlemler")
                .font(.subheadline.weight(.semibold))
            
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                NavigationLink(destination: FieldRadarView()) {
                    actionCard("Saha Radarı", icon: "map", color: .blue)
                }
                
                NavigationLink(destination: ProfitAnalysisView()) {
                    actionCard("Kâr Analizi", icon: "chart.pie", color: .green)
                }
                
                NavigationLink(destination: CatalogView()) {
                    actionCard("Katalog", icon: "shippingbox", color: .purple)
                }
                
                NavigationLink(destination: PlanUpgradeView()) {
                    actionCard("Paket Yükselt", icon: "arrow.up.circle", color: .orange)
                }
            }
        }
    }
    
    private func actionCard(_ title: String, icon: String, color: Color) -> some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(color)
            Text(title)
                .font(.caption.weight(.medium))
                .foregroundColor(.primary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
    
    // MARK: - Data Loading
    
    private func loadData() async {
        async let kpiTask = AdminService.getDashboardKPIs()
        async let techTask = AdminService.getTechnicianStats()
        async let quotaTask = AdminService.getQuotaStatus()
        
        do {
            let (k, t, q) = try await (kpiTask, techTask, quotaTask)
            await MainActor.run {
                self.kpis = k
                self.techStats = t
                self.quotaStatus = q
                self.isLoading = false
            }
        } catch {
            await MainActor.run { isLoading = false }
        }
    }
    
    private func formatAmount(_ value: Double?) -> String {
        guard let val = value else { return "0" }
        if abs(val) >= 1000 {
            return String(format: "%.1fK", val / 1000)
        }
        return String(format: "%.0f", val)
    }
}
