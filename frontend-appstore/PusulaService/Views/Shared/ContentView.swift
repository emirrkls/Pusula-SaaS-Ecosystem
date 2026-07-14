import SwiftUI

/// Root content view — routes between login and the role-based dashboard.
struct ContentView: View {
    @StateObject private var session = SessionManager.shared
    @State private var showPlanUpgrade = false
    
    var body: some View {
        Group {
            if session.isRestoringSession {
                sessionRestoreView
            } else if session.isAuthenticated {
                mainView
            } else {
                LoginView()
            }
        }
        .tint(PusulaTheme.accent)
        .animation(.easeInOut(duration: 0.3), value: session.isAuthenticated)
        .sheet(isPresented: $showPlanUpgrade) {
            NavigationStack { PlanUpgradeView() }
        }
    }
    
    @ViewBuilder
    private var mainView: some View {
        if session.isTechnician {
            ZStack(alignment: .top) {
                TechnicianTabView()
                sessionBanners
            }
        } else if session.isAdmin {
            ZStack(alignment: .top) {
                AdminTabView()
                sessionBanners
            }
        } else {
            ContentUnavailableView(
                "Desteklenmeyen kullanıcı rolü",
                systemImage: "person.crop.circle.badge.exclamationmark",
                description: Text("Bu hesap mobil uygulamada kullanılamıyor.")
            )
            .safeAreaInset(edge: .bottom) {
                Button("Oturumu Kapat") { session.logout() }
                    .buttonStyle(.borderedProminent)
                    .padding()
            }
        }
    }

    private var sessionRestoreView: some View {
        VStack(spacing: 16) {
            PusulaBrandMark(size: 52)
            ProgressView()
                .tint(PusulaTheme.accent)
            Text("Oturum doğrulanıyor...")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(PusulaTheme.page)
    }

    @ViewBuilder
    private var sessionBanners: some View {
        VStack(spacing: 6) {
            if session.isReadOnly { readOnlyBanner }
            if session.showTrialBanner { trialBanner }
        }
        .padding(.horizontal, 12)
        .padding(.top, 4)
    }
    
    private var trialBanner: some View {
        HStack {
            Image(systemName: "clock.badge.exclamationmark")
            Text("Deneme süreniz \(session.trialDaysRemaining ?? 0) gün sonra bitiyor")
                .font(.caption.weight(.medium))
            Spacer()
            if session.isAdmin {
                Button { showPlanUpgrade = true } label: {
                    Image(systemName: "arrow.up.right")
                        .font(.caption.weight(.bold))
                }
                .accessibilityLabel("Paketi yükselt")
            }
        }
        .padding(.horizontal, 12)
        .frame(minHeight: 36)
        .background(PusulaTheme.raisedSurface)
        .overlay {
            RoundedRectangle(cornerRadius: PusulaTheme.radius)
                .stroke(.orange.opacity(0.35), lineWidth: 1)
        }
        .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
        .foregroundColor(.orange)
    }
    
    private var readOnlyBanner: some View {
        HStack {
            Image(systemName: "exclamationmark.triangle.fill")
            Text("Aboneliğiniz sona erdi. Sadece görüntüleme modundasınız.")
                .font(.caption.weight(.medium))
            Spacer()
        }
        .padding(.horizontal, 12)
        .frame(minHeight: 36)
        .background(PusulaTheme.raisedSurface)
        .overlay {
            RoundedRectangle(cornerRadius: PusulaTheme.radius)
                .stroke(.red.opacity(0.35), lineWidth: 1)
        }
        .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
        .foregroundColor(.red)
    }
}

// MARK: - Technician Tab View (Android: İşlerim + Hesap)

struct TechnicianTabView: View {
    var body: some View {
        TabView {
            NavigationStack {
                TicketListView()
            }
            .tabItem {
                Label("İşlerim", systemImage: "wrench.and.screwdriver")
            }
            
            NavigationStack {
                ProfileView()
            }
            .tabItem {
                Label("Hesap", systemImage: "person.circle")
            }
        }
        .tint(PusulaTheme.accent)
    }
}

// MARK: - Admin Tab View (Android: Özet, Operasyon, Diğer, Finans, Hesap)

struct AdminTabView: View {
    @StateObject private var session = SessionManager.shared
    @StateObject private var navigation = AppNavigation.shared
    @State private var selectedTab: AdminTab = .overview
    @State private var lastRealTab: AdminTab = .overview
    @State private var showQuickActions = false
    @State private var quickActionDestination: QuickAction?
    
    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                AdminDashboardView()
            }
            .tabItem { Label("Özet", systemImage: "chart.bar") }
            .tag(AdminTab.overview)
            
            NavigationStack {
                TicketListView(
                    requestedFilter: navigation.operationFilter,
                    onRequestedFilterApplied: { navigation.operationFilter = nil }
                )
            }
            .tabItem { Label("Operasyon", systemImage: "list.clipboard") }
            .tag(AdminTab.operations)
            
            Color.clear
                .tabItem { Label("Diğer", systemImage: "plus.circle.fill") }
                .tag(AdminTab.more)
            
            NavigationStack {
                FinanceView()
            }
            .tabItem { Label("Finans", systemImage: "turkishlirasign.circle") }
            .tag(AdminTab.finance)
            .featureGated("FINANCE_MODULE")
            
            NavigationStack {
                SettingsView()
            }
            .tabItem { Label("Hesap", systemImage: "gearshape") }
            .tag(AdminTab.account)
        }
        .tint(PusulaTheme.accent)
        .onChange(of: selectedTab) { oldValue, newValue in
            if newValue == .more {
                showQuickActions = true
                selectedTab = lastRealTab
            } else {
                lastRealTab = newValue
                navigation.adminSelectedTab = newValue
            }
        }
        .onChange(of: navigation.adminSelectedTab) { _, newValue in
            if newValue != .more {
                selectedTab = newValue
            }
        }
        .confirmationDialog("Diğer Modüller", isPresented: $showQuickActions, titleVisibility: .visible) {
            Button("Müşteriler") { quickActionDestination = .customers }
            Button("Teklifler") { quickActionDestination = .proposals }
            if session.isFeatureEnabled("BASIC_INVENTORY") {
                Button("Stok") { quickActionDestination = .catalog }
            }
            Button("Servis Kalite") { quickActionDestination = .serviceQuality }
            Button("İptal", role: .cancel) { }
        }
        .sheet(item: $quickActionDestination) { destination in
            NavigationStack {
                quickActionView(for: destination)
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button("Kapat") { quickActionDestination = nil }
                        }
                    }
            }
        }
    }
    
    @ViewBuilder
    private func quickActionView(for destination: QuickAction) -> some View {
        switch destination {
        case .customers: CustomerView()
        case .proposals: ProposalView()
        case .catalog: CatalogView()
        case .serviceQuality: ServiceQualityView()
        }
    }
}

enum QuickAction: String, Identifiable {
    case customers, proposals, catalog, serviceQuality
    var id: String { rawValue }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
