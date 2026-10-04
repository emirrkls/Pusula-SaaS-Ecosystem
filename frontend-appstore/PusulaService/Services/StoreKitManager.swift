import Foundation
import StoreKit
import UIKit
import OSLog

@MainActor
class StoreKitManager: ObservableObject {
    static let shared = StoreKitManager()
    
    @Published var products: [Product] = []
    @Published var purchasedProductIDs: Set<String> = []
    @Published var isPurchasing = false
    @Published var isLoadingProducts = false
    @Published var isRestoring = false
    @Published var purchaseError: String?
    @Published var statusMessage: String?
    @Published var eligibleIntroOffers: [String: String] = [:]
    @Published private(set) var productLoadIssue: StoreProductLoadIssue?
    @Published private(set) var productDiagnostics = ""
    
    // Product IDs must match exactly what is configured in App Store Connect
    private let productDict: [PlanTier: [SubscriptionBillingCycle: String]] = [
        .usta: [
            .monthly: SubscriptionProductCatalog.ustaMonthly,
            .yearly: SubscriptionProductCatalog.ustaYearly
        ],
        .patron: [
            .monthly: SubscriptionProductCatalog.patronMonthly,
            .yearly: SubscriptionProductCatalog.patronYearly
        ]
    ]
    
    private var transactionUpdates: Task<Void, Never>?
    private let logger = Logger(subsystem: "com.pusula.service", category: "StoreKitCatalog")
    
    private init() {
        transactionUpdates = listenForTransactions()
    }
    
    deinit {
        transactionUpdates?.cancel()
    }
    
    /// Load products from App Store
    func loadProducts() async {
        guard !isLoadingProducts, !isPurchasing, !isRestoring else { return }
        isLoadingProducts = true
        productLoadIssue = nil
        defer { isLoadingProducts = false }
        let productIDs = SubscriptionProductCatalog.identifiers.sorted()
        var responseIDs: [String] = []
        var errorCode: String?
        do {
            let response = try await Product.products(for: productIDs)
            try Task.checkCancellation()
            let storeProducts = response.filter {
                SubscriptionProductCatalog.identifiers.contains($0.id) && $0.type == .autoRenewable
            }
            responseIDs = storeProducts.map(\.id).sorted()
            
            // Sort products by price
            self.products = storeProducts.sorted(by: { $0.price < $1.price })
            switch SubscriptionProductCatalog.availability(returnedIDs: Set(responseIDs)) {
            case .empty: productLoadIssue = .unavailable
            case .partial: productLoadIssue = .incomplete
            case .complete: productLoadIssue = nil
            }
            await updateIntroductoryOffers(for: storeProducts)
            
            // Check active entitlements
            await updatePurchasedStatus()
        } catch {
            guard !Task.isCancelled else { return }
            let storeError = error as NSError
            errorCode = "\(storeError.domain) (\(storeError.code))"
            productLoadIssue = storeError.domain == NSURLErrorDomain ? .connection : .requestFailed
            // Keep previously retrieved Apple products on a transient refresh failure.
        }
        guard !Task.isCancelled else { return }
        let storefront = await Storefront.current
        let info = Bundle.main.infoDictionary ?? [:]
        let version = info["CFBundleShortVersionString"] as? String ?? "?"
        let build = info["CFBundleVersion"] as? String ?? "?"
        let missing = SubscriptionProductCatalog.identifiers.subtracting(responseIDs).sorted()
        productDiagnostics = [
            "Bundle: \(Bundle.main.bundleIdentifier ?? "?")",
            "Version: \(version) (\(build))",
            "Storefront: \(storefront?.countryCode ?? "unknown")",
            "Requested: \(productIDs.count) · Returned: \(responseIDs.count)",
            "Missing: \(missing.isEmpty ? "none" : missing.joined(separator: ", "))",
            errorCode.map { "Error: \($0)" }
        ].compactMap { $0 }.joined(separator: "\n")
        // No account details, tokens, signed transactions or receipts in diagnostics.
        if productLoadIssue != nil {
            let diagnosticSummary = productDiagnostics
            logger.error("Product catalog unavailable: \(diagnosticSummary, privacy: .public)")
        }
    }
    
    /// Purchase a specific plan tier
    func purchase(_ plan: PlanTier, billingCycle: SubscriptionBillingCycle) async {
        guard !isPurchasing, !isRestoring else { return }
        guard let productID = productDict[plan]?[billingCycle],
              let product = products.first(where: { $0.id == productID }) else {
            self.purchaseError = "Paket bulunamadı."
            return
        }
        
        isPurchasing = true
        purchaseError = nil
        
        do {
            let result = try await product.purchase()
            switch result {
            case .success(let verification):
                let transaction = try checkVerified(verification)
                try await verifyWithBackend(
                    transaction: transaction,
                    signedPayload: verification.jwsRepresentation,
                    plan: plan
                )
                await transaction.finish()
                await updatePurchasedStatus()
                await SessionManager.shared.refreshSubscriptionContext()
                statusMessage = "Satın alma doğrulandı ve paketiniz güncellendi."
                
            case .userCancelled:
                statusMessage = "Satın alma iptal edildi."
            case .pending:
                statusMessage = "Satın alma onay bekliyor. Tamamlandığında otomatik işlenecek."
            @unknown default:
                break
            }
        } catch {
            self.purchaseError = error.localizedDescription
        }
        
        isPurchasing = false
    }
    
    private func verifyWithBackend(
        transaction: Transaction,
        signedPayload: String,
        plan: PlanTier
    ) async throws {
        let body = AppleSubscriptionVerificationRequest(
            transactionId: String(transaction.id),
            productId: transaction.productID,
            plan: plan.rawValue,
            signedTransactionInfo: signedPayload
        )
        let _: EmptyResponse = try await NetworkManager.shared.post("/api/subscription/apple-verify", body: body)
    }
    
    private func updatePurchasedStatus() async {
        var activePurchases: Set<String> = []
        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result else { continue }
            if transaction.revocationDate == nil {
                activePurchases.insert(transaction.productID)
            }
        }
        self.purchasedProductIDs = activePurchases
    }
    
    private func listenForTransactions() -> Task<Void, Never> {
        return Task {
            for await result in Transaction.updates {
                do {
                    let transaction = try self.checkVerified(result)
                    guard let plan = self.plan(for: transaction.productID) else { continue }
                    try await self.verifyWithBackend(
                        transaction: transaction,
                        signedPayload: result.jwsRepresentation,
                        plan: plan
                    )
                    await transaction.finish()
                    await self.updatePurchasedStatus()
                    await SessionManager.shared.refreshSubscriptionContext()
                } catch {
                    self.purchaseError = "Bekleyen satın alma sunucuda doğrulanamadı. Daha sonra tekrar denenecek."
                }
            }
        }
    }
    
    private func checkVerified<T>(_ result: VerificationResult<T>) throws -> T {
        switch result {
        case .unverified(_, let error):
            throw error
        case .verified(let safe):
            return safe
        }
    }
    
    func formattedPrice(for plan: PlanTier, billingCycle: SubscriptionBillingCycle) -> String? {
        guard let productID = productDict[plan]?[billingCycle],
              let product = products.first(where: { $0.id == productID }) else {
            return nil
        }
        return product.displayPrice
    }

    func billingPeriod(for plan: PlanTier, billingCycle: SubscriptionBillingCycle) -> String? {
        guard let product = product(for: plan, billingCycle: billingCycle),
              let period = product.subscription?.subscriptionPeriod else { return nil }
        return period.localizedSuffix
    }

    func introductoryOffer(for plan: PlanTier, billingCycle: SubscriptionBillingCycle) -> String? {
        guard let productID = productDict[plan]?[billingCycle] else { return nil }
        return eligibleIntroOffers[productID]
    }

    func isAvailable(_ plan: PlanTier, billingCycle: SubscriptionBillingCycle) -> Bool {
        product(for: plan, billingCycle: billingCycle) != nil
    }

    func isPurchased(_ plan: PlanTier, billingCycle: SubscriptionBillingCycle) -> Bool {
        guard let productID = productDict[plan]?[billingCycle] else { return false }
        return purchasedProductIDs.contains(productID)
    }

    func restorePurchases() async {
        guard !isRestoring, !isPurchasing else { return }
        isRestoring = true
        purchaseError = nil
        statusMessage = nil
        defer { isRestoring = false }

        do {
            try await AppStore.sync()
            var restoredCount = 0
            for await result in Transaction.currentEntitlements {
                let transaction = try checkVerified(result)
                guard transaction.revocationDate == nil,
                      let plan = plan(for: transaction.productID) else { continue }
                try await verifyWithBackend(
                    transaction: transaction,
                    signedPayload: result.jwsRepresentation,
                    plan: plan
                )
                await transaction.finish()
                restoredCount += 1
            }
            await updatePurchasedStatus()
            await SessionManager.shared.refreshSubscriptionContext()
            statusMessage = restoredCount > 0
                ? "Satın alımlarınız geri yüklendi."
                : "Geri yüklenecek aktif abonelik bulunamadı."
        } catch {
            purchaseError = "Satın alımlar geri yüklenemedi: \(error.localizedDescription)"
        }
    }

    func manageSubscriptions() {
        UIApplication.shared.open(AppLinks.subscriptionManagement)
    }

    private func product(for plan: PlanTier, billingCycle: SubscriptionBillingCycle) -> Product? {
        guard let productID = productDict[plan]?[billingCycle] else { return nil }
        return products.first(where: { $0.id == productID })
    }

    private func updateIntroductoryOffers(for products: [Product]) async {
        var offers: [String: String] = [:]
        for product in products {
            guard let subscription = product.subscription,
                  let offer = subscription.introductoryOffer,
                  offer.paymentMode == .freeTrial,
                  await subscription.isEligibleForIntroOffer else { continue }
            offers[product.id] = "\(offer.period.localizedDescription) ücretsiz deneme"
        }
        eligibleIntroOffers = offers
    }

    private func plan(for productID: String) -> PlanTier? {
        productDict.first(where: { $0.value.values.contains(productID) })?.key
    }
}

enum StoreProductLoadIssue {
    case unavailable
    case incomplete
    case connection
    case requestFailed

    var title: String {
        switch self {
        case .unavailable: return "Satın alma seçenekleri alınamadı"
        case .incomplete: return "Bazı satın alma seçenekleri alınamadı"
        case .connection: return "App Store’a bağlanılamadı"
        case .requestFailed: return "App Store isteği tamamlanamadı"
        }
    }

    var message: String {
        switch self {
        case .unavailable:
            return "App Store bu uygulama için abonelik ürünü döndürmedi. Paket içeriklerini inceleyebilirsiniz; fiyat ve satın alma seçenekleri Apple’dan geldiğinde etkinleşir."
        case .incomplete:
            return "Apple bazı abonelik ürünlerini döndürmedi. Mevcut seçenekleri kullanabilir veya tekrar deneyebilirsiniz."
        case .connection:
            return "İnternet bağlantınızı kontrol edip tekrar deneyin. Paket içerikleri aşağıda görünmeye devam eder."
        case .requestFailed:
            return "Apple’ın abonelik bilgileri şu anda alınamıyor. Tekrar deneyin; sorun sürerse aşağıdaki teknik bilgiyi destek ekibine iletin."
        }
    }

    var systemImage: String {
        switch self {
        case .connection: return "wifi.exclamationmark"
        default: return "info.circle"
        }
    }
}

enum SubscriptionBillingCycle: String, CaseIterable {
    case monthly
    case yearly

    var displayName: String {
        switch self {
        case .monthly: return "Aylık"
        case .yearly: return "Yıllık"
        }
    }
}

private extension Product.SubscriptionPeriod {
    var localizedDescription: String {
        let unitText: String
        switch unit {
        case .day: unitText = value == 1 ? "gün" : "gün"
        case .week: unitText = value == 1 ? "hafta" : "hafta"
        case .month: unitText = value == 1 ? "ay" : "ay"
        case .year: unitText = value == 1 ? "yıl" : "yıl"
        @unknown default: unitText = "dönem"
        }
        return "\(value) \(unitText)"
    }

    var localizedSuffix: String {
        switch unit {
        case .day: return value == 1 ? "/gün" : "/\(value) gün"
        case .week: return value == 1 ? "/hafta" : "/\(value) hafta"
        case .month: return value == 1 ? "/ay" : "/\(value) ay"
        case .year: return value == 1 ? "/yıl" : "/\(value) yıl"
        @unknown default: return "/dönem"
        }
    }
}

private struct AppleSubscriptionVerificationRequest: Encodable {
    let transactionId: String
    let productId: String
    let plan: String
    let signedTransactionInfo: String
}
