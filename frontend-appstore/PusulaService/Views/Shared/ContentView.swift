import SwiftUI

/// Root content view — routes between login and the role-based dashboard.
struct ContentView: View {
    let session = SessionManager.shared
    @State private var showPlanUpgrade = false
    
    var body: some View {
        Group {
            if session.isAuthenticated {
                mainView
            } else {
                LoginView()
            }
        }
        .animation(.easeInOut(duration: 0.3), value: session.isAuthenticated)
        .sheet(isPresented: $showPlanUpgrade) {
            NavigationStack { PlanUpgradeView() }
        }
    }
    
    @ViewBuilder
    private var mainView: some View {
        ZStack(alignment: .top) {
            if session.isTechnician {
                TechnicianTabView()
            } else {
                AdminTabView()
            }
            
            if session.showTrialBanner {
                trialBanner
            }
            
            if session.isReadOnly {
                readOnlyBanner
            }
        }
    }
    
    private var trialBanner: some View {
        HStack {
            Image(systemName: "clock.badge.exclamationmark")
            Text("Deneme süreniz \(session.trialDaysRemaining ?? 0) gün sonra bitiyor")
                .font(.caption.weight(.medium))
            Spacer()
            Button("Yükselt") { showPlanUpgrade = true }
                .font(.caption.weight(.bold))
                .foregroundColor(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 4)
                .background(.orange)
                .clipShape(Capsule())
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
        .background(.orange.opacity(0.15))
        .foregroundColor(.orange)
        .padding(.top, session.isReadOnly ? 36 : 0)
    }
    
    private var readOnlyBanner: some View {
        HStack {
            Image(systemName: "exclamationmark.triangle.fill")
            Text("Aboneliğiniz sona erdi. Sadece görüntüleme modundasınız.")
                .font(.caption.weight(.medium))
            Spacer()
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
        .background(.red.opacity(0.15))
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
        .tint(.cyan)
    }
}

// MARK: - Admin Tab View (Android: Özet, Operasyon, Diğer, Finans, Hesap)

struct AdminTabView: View {
    let session = SessionManager.shared
    @State private var navigation = AppNavigation.shared
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
        .tint(.cyan)
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

#Preview {
    ContentView()
}
