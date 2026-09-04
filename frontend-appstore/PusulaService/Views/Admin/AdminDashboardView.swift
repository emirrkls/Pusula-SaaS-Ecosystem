import SwiftUI

struct AdminDashboardView: View {
    @State private var kpis: DashboardKPIs?
    @State private var techStats: [TechnicianStat] = []
    @State private var quotaStatus: QuotaStatus?
    @State private var isLoading = true

    private let metricColumns = [
        GridItem(.flexible(), spacing: 10),
        GridItem(.flexible(), spacing: 10)
    ]

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 22) {
                headerSection

                if let kpis {
                    kpiSection(kpis)
                        .onboardingTarget(.overviewContent)
                }

                if let quotas = quotaStatus?.quotas, !quotas.isEmpty {
                    quotaSection(quotas)
                }

                if !techStats.isEmpty {
                    NavigationLink(destination: FieldRadarView()) {
                        technicianSection
                    }
                    .buttonStyle(.plain)
                }

                quickActionsSection
            }
            .padding(.horizontal, PusulaTheme.pagePadding)
            .padding(.vertical, 16)
        }
        .background(PusulaTheme.page)
        .navigationTitle("Genel Bakış")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await loadData() }
        .task { await loadData() }
        .overlay {
            if isLoading {
                ZStack {
                    PusulaTheme.page.opacity(0.88)
                    ProgressView("Veriler yükleniyor...")
                        .tint(PusulaTheme.accent)
                }
            }
        }
    }

    private var headerSection: some View {
        HStack(alignment: .center, spacing: 12) {
            PusulaBrandMark(size: 44)
            VStack(alignment: .leading, spacing: 3) {
                Text("Merhaba, \(SessionManager.shared.fullName)")
                    .font(.title3.weight(.semibold))
                    .lineLimit(1)
                Text(SessionManager.shared.companyName ?? "Şirket hesabı")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 8)
            Text(SessionManager.shared.planType)
                .font(.caption.weight(.semibold))
                .foregroundStyle(PusulaTheme.accentStrong)
                .padding(.horizontal, 9)
                .padding(.vertical, 5)
                .background(PusulaTheme.accent.opacity(0.10))
                .clipShape(Capsule())
        }
    }

    private func kpiSection(_ kpis: DashboardKPIs) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            PusulaSectionHeader(title: "Bu Ay", subtitle: "Finans ve operasyon özeti")

            LazyVGrid(columns: metricColumns, spacing: 10) {
                metricTile(
                    "Aylık Ciro",
                    value: "₺\(formatAmount(kpis.monthlyRevenue))",
                    icon: "chart.line.uptrend.xyaxis",
                    emphasis: true
                )
                metricTile(
                    "Net Kâr",
                    value: "₺\(formatAmount(kpis.netProfit))",
                    icon: "turkishlirasign.circle",
                    tone: (kpis.netProfit ?? 0) >= 0 ? PusulaTheme.accent : .red,
                    emphasis: true
                )
                metricTile(
                    "Bekleyen Alacak",
                    value: "₺\(formatAmount(kpis.outstandingDebt))",
                    icon: "exclamationmark.triangle",
                    tone: .orange
                )
                metricTile(
                    "Kâr Marjı",
                    value: "%\(String(format: "%.1f", kpis.profitMargin ?? 0))",
                    icon: "percent"
                )
            }

            HStack(spacing: 0) {
                compactMetric("Aktif İş", value: "\(kpis.activeTickets ?? 0)")
                Divider().frame(height: 34)
                compactMetric("Bu Ay Biten", value: "\(kpis.completedThisMonth ?? 0)")
                Divider().frame(height: 34)
                compactMetric("Stok Değeri", value: "₺\(formatAmount(kpis.inventoryValue))")
            }
            .pusulaCard(padding: 12)
        }
    }

    private func metricTile(
        _ title: String,
        value: String,
        icon: String,
        tone: Color = PusulaTheme.accent,
        emphasis: Bool = false
    ) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: icon)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(tone)
                Spacer()
                if emphasis {
                    Circle()
                        .fill(tone)
                        .frame(width: 6, height: 6)
                }
            }
            Text(value)
                .font(.title3.weight(.bold))
                .minimumScaleFactor(0.7)
                .lineLimit(1)
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .pusulaCard(padding: 14)
    }

    private func compactMetric(_ title: String, value: String) -> some View {
        VStack(spacing: 3) {
            Text(value)
                .font(.subheadline.weight(.bold))
                .minimumScaleFactor(0.65)
                .lineLimit(1)
            Text(title)
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
    }

    private func quotaSection(_ quotas: [QuotaItem]) -> some View {
        VStack(alignment: .leading, spacing: 13) {
            PusulaSectionHeader(title: "Kullanım Limitleri", icon: "gauge.with.dots.needle.33percent")

            ForEach(quotas) { quota in
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text(quotaTitle(quota))
                            .font(.caption.weight(.medium))
                        Spacer()
                        Text("\(quota.currentUsage ?? 0) / \(quota.limit ?? 0)")
                            .font(.caption.monospacedDigit().weight(.semibold))
                            .foregroundStyle(quotaColor(quota.usagePercent ?? 0))
                    }
                    ProgressView(value: min(quota.usagePercent ?? 0, 100), total: 100)
                        .tint(quotaColor(quota.usagePercent ?? 0))
                }
            }
        }
        .pusulaCard()
    }

    private var technicianSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            PusulaSectionHeader(
                title: "Saha Ekibi",
                subtitle: "Günlük durum ve tahsilat",
                icon: "person.3"
            )

            ForEach(Array(techStats.enumerated()), id: \.element.id) { index, tech in
                if index > 0 { Divider() }
                HStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 6)
                            .fill(PusulaTheme.accent.opacity(0.10))
                        Image(systemName: "person.fill")
                            .foregroundStyle(PusulaTheme.accent)
                    }
                    .frame(width: 36, height: 36)

                    VStack(alignment: .leading, spacing: 3) {
                        Text(tech.fullName ?? "Teknisyen")
                            .font(.subheadline.weight(.semibold))
                        Text("\(tech.completedToday ?? 0) tamamlandı · \(tech.activeTickets ?? 0) aktif")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 8)
                    Text("₺\(formatAmount(tech.collectedToday))")
                        .font(.subheadline.monospacedDigit().weight(.semibold))
                        .foregroundStyle(PusulaTheme.accentStrong)
                }
            }
        }
        .pusulaCard()
    }

    private var quickActionsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            PusulaSectionHeader(title: "Hızlı İşlemler")

            VStack(spacing: 0) {
                NavigationLink(destination: FieldRadarView()) {
                    actionRow("Saha Radarı", subtitle: "Ekip konumu ve iş yoğunluğu", icon: "map")
                }
                Divider().padding(.leading, 42)
                Button(action: { AppNavigation.shared.openOperations(with: "İşlemde") }) {
                    actionRow("Aktif İşler", subtitle: "Devam eden servis kayıtları", icon: "wrench.and.screwdriver")
                }
                Divider().padding(.leading, 42)
                NavigationLink(destination: ProfitAnalysisView()) {
                    actionRow("Kâr Analizi", subtitle: "Gelir ve maliyet görünümü", icon: "chart.pie")
                }
                .featureGated("FINANCE_MODULE")
                Divider().padding(.leading, 42)
                NavigationLink(destination: PlanUpgradeView()) {
                    actionRow("Paket Yönetimi", subtitle: "Plan ve kullanım limitleri", icon: "arrow.up.circle")
                }
            }
            .pusulaCard(padding: 0)
        }
    }

    private func actionRow(_ title: String, subtitle: String, icon: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(PusulaTheme.accent)
                .frame(width: 26)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.primary)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.tertiary)
        }
        .padding(.horizontal, 14)
        .frame(minHeight: 58)
        .contentShape(Rectangle())
    }

    private func quotaColor(_ percent: Double) -> Color {
        if percent >= 90 { return .red }
        if percent >= 70 { return .orange }
        return PusulaTheme.accent
    }

    private func quotaTitle(_ quota: QuotaItem) -> String {
        let source = quota.featureLabel?.isEmpty == false ? quota.featureLabel! : quota.featureKey
        switch source.uppercased() {
        case "CUSTOMERS": return "Müşteriler"
        case "TICKETS": return "Servis fişleri"
        case "TECHNICIANS": return "Teknisyenler"
        case "INVENTORY": return "Stok kalemleri"
        case "VEHICLES": return "Araçlar"
        default: return source.replacingOccurrences(of: "_", with: " ").capitalized
        }
    }

    private func loadData() async {
        async let kpiTask = AdminService.getDashboardKPIs()
        async let techTask = AdminService.getTechnicianStats()
        async let quotaTask = AdminService.getQuotaStatus()

        do {
            let (loadedKPIs, loadedTechs, loadedQuotas) = try await (kpiTask, techTask, quotaTask)
            await MainActor.run {
                kpis = loadedKPIs
                techStats = loadedTechs
                quotaStatus = loadedQuotas
                isLoading = false
            }
        } catch {
            await MainActor.run { isLoading = false }
        }
    }

    private func formatAmount(_ value: Double?) -> String {
        guard let value else { return "0" }
        if abs(value) >= 1_000_000 {
            return String(format: "%.1fM", value / 1_000_000)
        }
        if abs(value) >= 1_000 {
            return String(format: "%.1fK", value / 1_000)
        }
        return String(format: "%.0f", value)
    }
}
