import Foundation
import StoreKit
import UIKit

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
    
    // Product IDs must match exactly what is configured in App Store Connect
    private let productDict: [PlanTier: [SubscriptionBillingCycle: String]] = [
        .usta: [
            .monthly: "com.pusula.usta",
            .yearly: "com.pusula.usta.yearly"
        ],
        .patron: [
            .monthly: "com.pusula.patron",
            .yearly: "com.pusula.patron.yearly"
        ]
    ]
    
    private var transactionUpdates: Task<Void, Never>?
    
    private init() {
        transactionUpdates = listenForTransactions()
    }
    
    deinit {
        transactionUpdates?.cancel()
    }
    
    /// Load products from App Store
    func loadProducts() async {
        isLoadingProducts = true
        purchaseError = nil
        eligibleIntroOffers = [:]
        do {
            let productIDs = productDict.values.flatMap { $0.values }
            let storeProducts = try await Product.products(for: productIDs)
            
            // Sort products by price
            self.products = storeProducts.sorted(by: { $0.price < $1.price })
            await updateIntroductoryOffers(for: storeProducts)
            
            // Check active entitlements
            await updatePurchasedStatus()
        } catch {
            purchaseError = "App Store paketleri yüklenemedi. Lütfen tekrar deneyin."
        }
        isLoadingProducts = false
    }
    
    /// Purchase a specific plan tier
    func purchase(_ plan: PlanTier, billingCycle: SubscriptionBillingCycle) async {
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
        guard !isRestoring else { return }
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
