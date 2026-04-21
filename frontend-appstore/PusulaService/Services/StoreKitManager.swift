import Foundation
import StoreKit

@MainActor
class StoreKitManager: ObservableObject {
    static let shared = StoreKitManager()
    
    @Published var products: [Product] = []
    @Published var purchasedProductIDs: Set<String> = []
    @Published var isPurchasing = false
    @Published var purchaseError: String?
    
    // Product IDs must match exactly what is configured in App Store Connect
    private let productDict: [String: String] = [
        "CIRAK": "com.pusula.cirak",
        "USTA": "com.pusula.usta",
        "PATRON": "com.pusula.patron"
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
        do {
            let productIDs = Array(productDict.values)
            let storeProducts = try await Product.products(for: productIDs)
            
            // Sort products by price
            self.products = storeProducts.sorted(by: { $0.price < $1.price })
            
            // Check active entitlements
            await updatePurchasedStatus()
        } catch {
            print("Failed to load products: \(error)")
        }
    }
    
    /// Purchase a specific plan tier
    func purchase(_ plan: PlanTier) async {
        guard let productID = productDict[plan.rawValue],
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
                
                // Backend verification step
                await verifyWithBackend(transaction: transaction, plan: plan)
                
                await transaction.finish()
                await updatePurchasedStatus()
                
            case .userCancelled:
                print("User cancelled purchase")
            case .pending:
                print("Purchase is pending")
            @unknown default:
                break
            }
        } catch {
            self.purchaseError = error.localizedDescription
        }
        
        isPurchasing = false
    }
    
    private func verifyWithBackend(transaction: Transaction, plan: PlanTier) async {
        do {
            // Convert App Store transaction details to our backend payload
            // In a real app, you send the JWS representation of the transaction or receipt
            let body: [String: Any] = [
                "transactionId": String(transaction.id),
                "productId": transaction.productID,
                "plan": plan.rawValue
            ]
            
            // Expected backend endpoint to verify Apple receipts
            let _: EmptyResponse = try await NetworkManager.shared.post("/api/subscription/apple-verify", body: body)
            
            // Refresh local feature context so app knows about the new plan
            _ = try await AuthService.refreshFeatureContext()
            
        } catch {
            print("Backend verification failed: \(error)")
            self.purchaseError = "Ödeme alındı ancak sunucuya iletilemedi. Lütfen destekle iletişime geçin."
        }
    }
    
    private func updatePurchasedStatus() async {
        var activePurchases: Set<String> = []
        for await result in Transaction.currentEntitlements {
            guard case .success(let transaction) = result else { continue }
            if transaction.revocationDate == nil {
                activePurchases.insert(transaction.productID)
            }
        }
        self.purchasedProductIDs = activePurchases
    }
    
    private func listenForTransactions() -> Task<Void, Never> {
        return Task.detached {
            for await result in Transaction.updates {
                do {
                    let transaction = try self.checkVerified(result)
                    
                    // We shouldn't necessarily assume the plan mapped easily from a background update, 
                    // but we can finish it. In production, send transaction.id to backend.
                    await transaction.finish()
                    await self.updatePurchasedStatus()
                } catch {
                    print("Transaction failed verification")
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
    
    func formattedPrice(for plan: PlanTier) -> String? {
        guard let productID = productDict[plan.rawValue],
              let product = products.first(where: { $0.id == productID }) else {
            return nil
        }
        return product.displayPrice
    }
}
