import SwiftUI
import SafariServices

/// Subscription plan comparison and upgrade view with payment integration.
struct PlanUpgradeView: View {
    @StateObject private var storeManager = StoreKitManager.shared
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
                        .foregroundStyle(
                            LinearGradient(colors: [.yellow, .orange], startPoint: .top, endPoint: .bottom)
                        )
                    Text("Paketinizi Yükseltin")
                        .font(.title2.weight(.bold))
                    Text("İşletmenizi bir üst seviyeye taşıyın")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .padding(.top, 10)
                
                // Plan cards
                if storeManager.products.isEmpty {
                    ProgressView("Paketler Yükleniyor...")
                        .padding(40)
                } else {
                    ForEach(PlanTier.allCases, id: \.self) { plan in
                        planCard(plan)
                    }
                }
                
                // Footer
                Text("Tüm paketler 14 gün ücretsiz deneme ile başlar.\nİstediğiniz zaman iptal edebilirsiniz.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding()
            }
            .padding()
        }
        .navigationTitle("Paketler")
        .task {
            await storeManager.loadProducts()
        }
        .onChange(of: storeManager.purchaseError) { _, error in
            if let error = error {
                alertMessage = error
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
        let isCurrent = SessionManager.shared.planType.uppercased() == plan.rawValue
        
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
                    HStack(alignment: .firstTextBaseline, spacing: 2) {
                        if let priceStr = storeManager.formattedPrice(for: plan) {
                            Text(priceStr)
                                .font(.title.weight(.bold))
                                .foregroundColor(plan.color)
                        } else {
                            Text("₺\(plan.price)")
                                .font(.title.weight(.bold))
                                .foregroundColor(plan.color)
                        }
                        Text("/ay")
                            .font(.caption)
                            .foregroundStyle(.secondary)
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
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            } else {
                Button(action: { handleUpgrade(plan) }) {
                    HStack {
                        if storeManager.isPurchasing && selectedPlan == plan {
                            ProgressView()
                                .tint(.white)
                        }
                        Text(plan == .patron ? "Patron'a Geç" : "Yükselt")
                            .font(.subheadline.weight(.bold))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                }
                .background(
                    LinearGradient(colors: plan.gradientColors, startPoint: .leading, endPoint: .trailing)
                )
                .foregroundColor(.white)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .disabled(storeManager.isPurchasing)
            }
        }
        .padding()
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(isPopular ? .orange : .clear, lineWidth: 2)
        )
    }
    
    private func handleUpgrade(_ plan: PlanTier) {
        selectedPlan = plan
        Task {
            await storeManager.purchase(plan)
        }
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
    
    var price: String {
        switch self {
        case .cirak: return "99"
        case .usta: return "299"
        case .patron: return "699"
        }
    }
    
    var color: Color {
        switch self {
        case .cirak: return .blue
        case .usta: return .orange
        case .patron: return .purple
        }
    }
    
    var gradientColors: [Color] {
        switch self {
        case .cirak: return [.blue, .cyan]
        case .usta: return [.orange, .red]
        case .patron: return [.purple, .indigo]
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
