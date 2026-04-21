import SwiftUI

/// Root content view — routes between login and the role-based dashboard.
struct ContentView: View {
    let session = SessionManager.shared
    
    var body: some View {
        Group {
            if session.isAuthenticated {
                mainView
            } else {
                LoginView()
            }
        }
        .animation(.easeInOut(duration: 0.3), value: session.isAuthenticated)
    }
    
    @ViewBuilder
    private var mainView: some View {
        ZStack(alignment: .top) {
            // Role-based dashboard
            if session.isTechnician {
                TechnicianTabView()
            } else {
                AdminTabView()
            }
            
            // Trial expiry banner
            if session.showTrialBanner {
                trialBanner
            }
            
            // Read-only banner
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
            Button("Yükselt") {
                // Navigate to upgrade screen
            }
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

// MARK: - Technician Tab View

struct TechnicianTabView: View {
    var body: some View {
        TabView {
            // My Jobs — real ticket list (Sprint 4)
            NavigationStack {
                TicketListView()
            }
            .tabItem {
                Label("İşlerim", systemImage: "wrench.and.screwdriver")
            }
            
            // Inventory (sell price only)
            NavigationStack {
                Text("Stok")
                    .navigationTitle("Stok")
            }
            .tabItem {
                Label("Stok", systemImage: "shippingbox")
            }
            .featureGated("BASIC_INVENTORY")
            
            // Profile
            NavigationStack {
                ProfilePlaceholder()
            }
            .tabItem {
                Label("Profil", systemImage: "person.circle")
            }
        }
        .tint(.cyan)
    }
}

// MARK: - Admin Tab View

struct AdminTabView: View {
    let session = SessionManager.shared
    
    var body: some View {
        TabView {
            // Dashboard — real KPI view (Sprint 5)
            NavigationStack {
                AdminDashboardView()
            }
            .tabItem {
                Label("Özet", systemImage: "chart.bar")
            }
            
            // Tickets — admin sees all company tickets
            NavigationStack {
                TicketListView()
            }
            .tabItem {
                Label("İş Emirleri", systemImage: "list.clipboard")
            }
            
            // Finance — profit analysis (feature-gated)
            NavigationStack {
                ProfitAnalysisView()
            }
            .tabItem {
                Label("Finans", systemImage: "turkishlirasign.circle")
            }
            .featureGated("FINANCE_MODULE")
            
            // Catalog — admin sees buy+sell prices
            NavigationStack {
                CatalogView()
            }
            .tabItem {
                Label("Stok", systemImage: "shippingbox")
            }
            
            // Settings
            NavigationStack {
                ProfilePlaceholder()
            }
            .tabItem {
                Label("Ayarlar", systemImage: "gearshape")
            }
        }
        .tint(.cyan)
    }
}

// MARK: - Placeholders (Sprint 4/5 will replace these)

struct TechnicianJobsPlaceholder: View {
    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "wrench.and.screwdriver")
                .font(.system(size: 64))
                .foregroundStyle(.cyan.gradient)
            Text("İşlerim")
                .font(.title2.weight(.bold))
            Text("Atanan işleriniz burada görünecek")
                .foregroundStyle(.secondary)
        }
        .navigationTitle("İşlerim")
    }
}

struct AdminDashboardPlaceholder: View {
    let session = SessionManager.shared
    
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Welcome header
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Merhaba, \(session.fullName)")
                            .font(.title2.weight(.bold))
                        HStack(spacing: 6) {
                            Image(systemName: "building.2")
                            Text(session.companyName ?? "")
                            Text("•")
                            Text(session.planType)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 2)
                                .background(.cyan.opacity(0.2))
                                .clipShape(Capsule())
                        }
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    }
                    Spacer()
                    
                    Button(action: { session.logout() }) {
                        Image(systemName: "rectangle.portrait.and.arrow.right")
                            .font(.title3)
                            .foregroundColor(.red)
                    }
                }
                .padding()
                
                // Placeholder cards
                Text("Dashboard kartları Sprint 5'te eklenecek")
                    .foregroundStyle(.secondary)
                    .padding(.top, 80)
            }
        }
        .navigationTitle("Müdür Paneli")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct ProfilePlaceholder: View {
    let session = SessionManager.shared
    @State private var showDeleteAlert = false
    
    var body: some View {
        List {
            Section("Hesap") {
                LabeledContent("İsim", value: session.fullName)
                LabeledContent("Rol", value: session.role)
                LabeledContent("Paket", value: session.planType)
            }
            
            Section("Şirket") {
                LabeledContent("Şirket", value: session.companyName ?? "-")
                if let days = session.trialDaysRemaining {
                    LabeledContent("Deneme Süresi", value: "\(days) gün kaldı")
                }
            }
            
            Section {
                Button(role: .destructive) {
                    session.logout()
                } label: {
                    HStack {
                        Spacer()
                        Text("Çıkış Yap")
                        Spacer()
                    }
                }
            }
            
            Section {
                Button(role: .destructive) {
                    showDeleteAlert = true
                } label: {
                    HStack {
                        Spacer()
                        Text("Hesabımı Sil")
                        Spacer()
                    }
                }
            }
        }
        .navigationTitle("Profil")
        .alert("Hesabı Sil", isPresented: $showDeleteAlert) {
            Button("İptal", role: .cancel) { }
            Button("Sil", role: .destructive) {
                Task {
                    do {
                        try await session.deleteAccount()
                    } catch {
                        // Handle error in a real app, e.g., show an error alert
                        print("Failed to delete account: \(error)")
                    }
                }
            }
        } message: {
            Text("Hesabınızı ve tüm verilerinizi kalıcı olarak silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.")
        }
    }
}

#Preview {
    ContentView()
}
