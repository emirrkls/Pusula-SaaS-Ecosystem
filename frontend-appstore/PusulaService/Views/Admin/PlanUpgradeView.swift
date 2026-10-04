import SwiftUI

/// Subscription plan comparison and upgrade view with payment integration.
struct PlanUpgradeView: View {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var storeManager = StoreKitManager.shared
    @StateObject private var session = SessionManager.shared
    @State private var selectedPlan: PlanTier = .usta
    @State private var billingCycle: SubscriptionBillingCycle = .monthly
    @State private var showAlert = false
    @State private var alertMessage = ""
    @State private var planDefinitions: [String: PlanSummaryDTO] = [:]
    @State private var planLoadFailed = false
    
    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                // Header
                VStack(spacing: 8) {
                    Image(systemName: "crown.fill")
                        .font(.system(size: 40))
                        .foregroundStyle(PusulaTheme.amber)
                    Text("Paketinizi Yönetin")
                        .font(.title2.weight(.bold))
                    Text("İşletmenize uygun hizmet seviyesini seçin")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .padding(.top, 10)
                
                // Plan cards
                Picker("Ödeme dönemi", selection: $billingCycle) {
                    ForEach(SubscriptionBillingCycle.allCases, id: \.self) { cycle in
                        Text(cycle.displayName).tag(cycle)
                    }
                }
                .pickerStyle(.segmented)

                if storeManager.isLoadingProducts {
                    HStack(spacing: 10) {
                        ProgressView()
                        Text("App Store fiyatları alınıyor…")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                } else if let issue = storeManager.productLoadIssue {
                    productAvailabilityNotice(issue)
                }

                if planLoadFailed {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Paket içerikleri sunucudan alınamadı.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Button("İçerikleri Tekrar Yükle") {
                            Task { await loadPlanDefinitions() }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .pusulaCard(padding: 14)
                }

                // Apple product availability must not hide the free plan or the
                // server's real feature comparison. Never invent fallback prices.
                ForEach(PlanTier.allCases, id: \.self) { plan in
                    planCard(plan)
                }
                
                subscriptionDisclosure

                HStack {
                    Button {
                        Task { await storeManager.restorePurchases() }
                    } label: {
                        if storeManager.isRestoring {
                            ProgressView()
                        } else {
                            Label("Satın Alımları Geri Yükle", systemImage: "arrow.clockwise")
                        }
                    }
                    .disabled(storeManager.isRestoring || storeManager.isPurchasing)

                    Spacer()

                    Button("Aboneliği Yönet") {
                        storeManager.manageSubscriptions()
                    }
                }
                .font(.caption.weight(.medium))
            }
            .padding()
        }
        .background(PusulaTheme.page)
        .navigationTitle("Paketler")
        .refreshable {
            guard session.isAdmin else { return }
            async let products: Void = storeManager.loadProducts()
            await loadPlanDefinitions()
            await products
        }
        .task {
            guard session.isAdmin else {
                alertMessage = "Paket değişikliklerini yalnızca şirket yöneticisi yapabilir."
                showAlert = true
                return
            }
            async let products: Void = storeManager.loadProducts()
            await loadPlanDefinitions()
            await products
        }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active, session.isAdmin,
                  !storeManager.isPurchasing, !storeManager.isRestoring,
                  storeManager.products.isEmpty || storeManager.productLoadIssue != nil else { return }
            Task { await storeManager.loadProducts() }
        }
        .onChange(of: storeManager.purchaseError) { _, error in
            if let error = error {
                alertMessage = error
                showAlert = true
            }
        }
        .onChange(of: storeManager.statusMessage) { _, message in
            if let message {
                alertMessage = message
                showAlert = true
            }
        }
        .alert("Ödeme", isPresented: $showAlert) {
            Button("Tamam", role: .cancel) {}
        } message: {
            Text(alertMessage)
        }
    }

    private func loadPlanDefinitions() async {
        planLoadFailed = false
        do {
            let definitions = try await AuthService.getPlans()
            guard !Task.isCancelled else { return }
            planDefinitions = Dictionary(definitions.map { ($0.name, $0) }, uniquingKeysWith: { _, latest in latest })
        } catch {
            guard !Task.isCancelled else { return }
            planLoadFailed = true
        }
    }

    private func productAvailabilityNotice(_ issue: StoreProductLoadIssue) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Label(issue.title, systemImage: issue.systemImage)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(PusulaTheme.accentStrong)
            Text(issue.message)
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            Button {
                Task { await storeManager.loadProducts() }
            } label: {
                Label("Tekrar Dene", systemImage: "arrow.clockwise")
                    .font(.subheadline.weight(.semibold))
            }
            .disabled(storeManager.isLoadingProducts || storeManager.isPurchasing || storeManager.isRestoring)

            if !storeManager.productDiagnostics.isEmpty {
                DisclosureGroup("Teknik Bilgi") {
                    Text(storeManager.productDiagnostics)
                        .font(.caption2.monospaced())
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .textSelection(.enabled)
                        .padding(.top, 8)
                }
                .font(.caption)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .pusulaCard(padding: 16)
        .accessibilityIdentifier("plans.store_issue")
    }
    
    private func planCard(_ plan: PlanTier) -> some View {
        let isPopular = plan == .usta
        let currentPlan = PlanTier(rawValue: session.planType.uppercased()) ?? .cirak
        let isCurrentTier = currentPlan == plan
        let hasAppStorePurchase = !storeManager.purchasedProductIDs.isEmpty
        let isCurrent = isCurrentTier && (!hasAppStorePurchase || storeManager.isPurchased(plan, billingCycle: billingCycle))
        let isAvailable = plan == .cirak || storeManager.isAvailable(plan, billingCycle: billingCycle)
        
        return VStack(spacing: 14) {
            // Header ribbon
            if isPopular {
                Text("EN POPÜLER")
                    .font(.caption2.weight(.bold))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 4)
                    .background(.orange)
                    .foregroundColor(.white)
                    .clipShape(Capsule())
            }
            
            // Plan name + price
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(plan.displayName)
                        .font(.title3.weight(.bold))
                    Text(plan.subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                VStack(alignment: .trailing) {
                    if plan == .cirak {
                        Text("Ücretsiz")
                            .font(.headline.weight(.bold))
                            .foregroundColor(plan.color)
                    } else {
                        HStack(alignment: .firstTextBaseline, spacing: 2) {
                            if let priceStr = storeManager.formattedPrice(for: plan, billingCycle: billingCycle) {
                                Text(priceStr)
                                    .font(.title.weight(.bold))
                                    .foregroundColor(plan.color)
                            } else {
                                Text(storeManager.isLoadingProducts ? "Yükleniyor…" : "Fiyat alınamadı")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(.secondary)
                            }
                            Text(storeManager.billingPeriod(for: plan, billingCycle: billingCycle) ?? "")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    if let offer = storeManager.introductoryOffer(for: plan, billingCycle: billingCycle) {
                        Text(offer)
                            .font(.caption)
                            .foregroundStyle(.green)
                    }
                    if plan == .cirak {
                        Text("Süresiz")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            
            Divider()
            
            // Features list
            VStack(alignment: .leading, spacing: 8) {
                ForEach(plan.features(using: planDefinitions[plan.rawValue]), id: \.self) { feature in
                    HStack(spacing: 8) {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                            .font(.caption)
                        Text(feature)
                            .font(.caption)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            
            // CTA button
            if isCurrent {
                Text("Mevcut Paketiniz")
                    .font(.subheadline.weight(.medium))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color(.systemGray5))
                    .foregroundColor(.secondary)
                    .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
            } else if plan == .cirak {
                Text("Ücretsiz başlangıç paketi")
                    .font(.subheadline.weight(.medium))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color(.systemGray5))
                    .foregroundColor(.secondary)
                    .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
            } else {
                Button(action: { handleUpgrade(plan) }) {
                    HStack {
                        if storeManager.isPurchasing && selectedPlan == plan {
                            ProgressView()
                                .tint(.white)
                        }
                        Text(isAvailable ? actionTitle(from: currentPlan, to: plan) : "Satın alma şu an kullanılamıyor")
                            .font(.subheadline.weight(.bold))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                }
                .background(plan.color)
                .foregroundColor(.white)
                .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
                .disabled(storeManager.isPurchasing || storeManager.isRestoring || !session.isAdmin || !isAvailable)
            }
        }
        .accessibilityIdentifier("plans.card.\(plan.rawValue)")
        .padding()
        .background(PusulaTheme.raisedSurface)
        .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
        .overlay(
            RoundedRectangle(cornerRadius: PusulaTheme.radius)
                .stroke(isPopular ? Color.orange : PusulaTheme.border, lineWidth: 1)
        )
    }
    
    private func handleUpgrade(_ plan: PlanTier) {
        guard session.isAdmin else {
            alertMessage = "Paket değişikliklerini yalnızca şirket yöneticisi yapabilir."
            showAlert = true
            return
        }
        selectedPlan = plan
        Task {
            await storeManager.purchase(plan, billingCycle: billingCycle)
        }
    }

    private func actionTitle(from current: PlanTier, to target: PlanTier) -> String {
        if target.rank > current.rank { return "\(target.transitionName) Yükselt" }
        return "\(target.transitionName) Düşür"
    }

    private var subscriptionDisclosure: some View {
        VStack(spacing: 10) {
            Text("Ödeme Apple Kimliğinize yansıtılır. Abonelik, dönem bitiminden en az 24 saat önce iptal edilmediği sürece otomatik yenilenir. Fiyat ve dönem satın alma onay ekranında gösterilir.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            HStack(spacing: 16) {
                Link("Gizlilik Politikası", destination: AppLinks.privacyPolicy)
                Link("Kullanım Koşulları", destination: AppLinks.termsOfUse)
            }
            .font(.caption.weight(.medium))
        }
        .padding(.horizontal)
    }
}

// MARK: - Plan Data

enum PlanTier: String, CaseIterable {
    case cirak = "CIRAK"
    case usta = "USTA"
    case patron = "PATRON"
    
    var displayName: String {
        switch self {
        case .cirak: return "Çırak"
        case .usta: return "Usta"
        case .patron: return "Patron"
        }
    }
    
    var subtitle: String {
        switch self {
        case .cirak: return "Bireysel ustalar için"
        case .usta: return "Büyüyen ekipler için"
        case .patron: return "Kurumsal firmalar için"
        }
    }
    
    var rank: Int {
        switch self {
        case .cirak: return 0
        case .usta: return 1
        case .patron: return 2
        }
    }

    var transitionName: String {
        switch self {
        case .cirak: return "Çırak'a"
        case .usta: return "Usta'ya"
        case .patron: return "Patron'a"
        }
    }
    
    var color: Color {
        switch self {
        case .cirak: return .blue
        case .usta: return .orange
        case .patron: return .purple
        }
    }
    
    func features(using plan: PlanSummaryDTO?) -> [String] {
        guard let plan else { return ["Paket bilgileri yükleniyor"] }
        var result: [String] = []
        if let value = plan.maxCompanyAdmins { result.append("\(value) şirket yöneticisi") }
        if let value = plan.maxTechnicians { result.append("\(value) aktif teknisyen") }
        if let value = plan.maxCustomers { result.append("\(value) müşteri") }
        if let value = plan.maxMonthlyTickets { result.append("Ayda \(value) servis fişi") }
        if let value = plan.maxMonthlyProposals, value > 0 { result.append("Ayda \(value) teklif") }
        if let value = plan.maxInventoryItems { result.append("\(value) envanter kalemi") }
        if let value = plan.storageLimitMb {
            result.append(value >= 1024 ? "\(value / 1024) GB depolama" : "\(value) MB depolama")
        }
        return result
    }
}
