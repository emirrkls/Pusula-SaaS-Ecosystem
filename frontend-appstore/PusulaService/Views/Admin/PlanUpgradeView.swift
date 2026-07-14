import SwiftUI

/// Subscription plan comparison and upgrade view with payment integration.
struct PlanUpgradeView: View {
    @StateObject private var storeManager = StoreKitManager.shared
    @StateObject private var session = SessionManager.shared
    @State private var selectedPlan: PlanTier = .usta
    @State private var showAlert = false
    @State private var alertMessage = ""
    
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
                if storeManager.isLoadingProducts {
                    ProgressView("Paketler Yükleniyor...")
                        .padding(40)
                } else if storeManager.products.isEmpty {
                    ContentUnavailableView {
                        Label("Paketler Yüklenemedi", systemImage: "wifi.exclamationmark")
                    } description: {
                        Text("App Store bağlantısını kontrol edip tekrar deneyin.")
                    } actions: {
                        Button("Tekrar Dene") {
                            Task { await storeManager.loadProducts() }
                        }
                        .buttonStyle(.borderedProminent)
                    }
                } else {
                    ForEach(PlanTier.allCases, id: \.self) { plan in
                        planCard(plan)
                    }
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
        .task {
            guard session.isAdmin else {
                alertMessage = "Paket değişikliklerini yalnızca şirket yöneticisi yapabilir."
                showAlert = true
                return
            }
            await storeManager.loadProducts()
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
    
    private func planCard(_ plan: PlanTier) -> some View {
        let isPopular = plan == .usta
        let currentPlan = PlanTier(rawValue: session.planType.uppercased()) ?? .cirak
        let isCurrent = currentPlan == plan
        
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
                        Text("Başlangıç")
                            .font(.headline.weight(.bold))
                            .foregroundColor(plan.color)
                    } else {
                        HStack(alignment: .firstTextBaseline, spacing: 2) {
                            if let priceStr = storeManager.formattedPrice(for: plan) {
                            Text(priceStr)
                                .font(.title.weight(.bold))
                                .foregroundColor(plan.color)
                            }
                            Text(storeManager.billingPeriod(for: plan) ?? "")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    if let offer = storeManager.introductoryOffer(for: plan) {
                        Text(offer)
                            .font(.caption)
                            .foregroundStyle(.green)
                    }
                }
            }
            
            Divider()
            
            // Features list
            VStack(alignment: .leading, spacing: 8) {
                ForEach(plan.features, id: \.self) { feature in
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
                        Text(actionTitle(from: currentPlan, to: plan))
                            .font(.subheadline.weight(.bold))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                }
                .background(plan.color)
                .foregroundColor(.white)
                .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
                .disabled(storeManager.isPurchasing || !session.isAdmin)
            }
        }
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
            await storeManager.purchase(plan)
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
    
    var features: [String] {
        switch self {
        case .cirak:
            return [
                "50 Servis Fişi / Ay",
                "1 Teknisyen",
                "100 Stok Kalemi",
                "Temel Raporlama",
                "WhatsApp Bildirimi"
            ]
        case .usta:
            return [
                "200 Servis Fişi / Ay",
                "5 Teknisyen",
                "500 Stok Kalemi",
                "Gelişmiş Raporlama",
                "Saha Radarı",
                "Kâr Analizi",
                "WhatsApp Bildirimi"
            ]
        case .patron:
            return [
                "Sınırsız Servis Fişi",
                "Sınırsız Teknisyen",
                "Sınırsız Stok",
                "Tüm Raporlar",
                "API Erişimi",
                "Öncelikli Destek",
                "Özel Marka Logosu"
            ]
        }
    }
}
