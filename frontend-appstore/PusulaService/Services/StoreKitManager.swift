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
    private var transactionProcessing: [UInt64: Task<Void, Error>] = [:]
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
        let session = SessionManager.shared
        guard session.isAdmin, let snapshot = session.authenticatedSnapshot else { return }
        guard let productID = productDict[plan]?[billingCycle],
              let product = products.first(where: { $0.id == productID }) else {
            self.purchaseError = String(localized: "billing.product_missing")
            return
        }
        
        isPurchasing = true
        purchaseError = nil
        statusMessage = nil
        defer { isPurchasing = false }
        
        do {
            guard await session.refreshSubscriptionContext(expectedSession: snapshot),
                  session.isCurrent(snapshot), let accountToken = session.appAccountToken else {
                throw PurchaseProcessingError.accountUnavailable
            }
            let result = try await product.purchase(options: [.appAccountToken(accountToken)])
            guard session.isCurrent(snapshot) else { return }
            switch result {
            case .success(let verification):
                let transaction = try checkVerified(verification)
                try await processTransaction(
                    transaction: transaction,
                    signedPayload: verification.jwsRepresentation,
                    plan: plan, snapshot: snapshot, accountToken: accountToken
                )
                await updatePurchasedStatus()
                let refreshed = await session.refreshSubscriptionContext(expectedSession: snapshot)
                guard session.isCurrent(snapshot) else { return }
                statusMessage = String(localized: refreshed ? "billing.verified" : "billing.verified_refresh_pending")
                
            case .userCancelled:
                statusMessage = String(localized: "billing.cancelled")
            case .pending:
                statusMessage = String(localized: "billing.pending")
            @unknown default:
                break
            }
        } catch {
            guard session.isCurrent(snapshot), !(error is CancellationError) else { return }
            self.purchaseError = error.localizedDescription
        }
    }

    private func processTransaction(transaction: Transaction, signedPayload: String, plan: PlanTier,
                                    snapshot: AuthenticatedSessionSnapshot, accountToken: UUID,
                                    explicitRestore: Bool = false) async throws {
        let session = SessionManager.shared
        guard session.isCurrent(snapshot) else { throw CancellationError() }
        guard PurchaseAccountPolicy.canProcess(transactionAccount: transaction.appAccountToken,
                currentAccount: accountToken, explicitRestore: explicitRestore) else {
            throw PurchaseProcessingError.accountMismatch
        }
        // StoreKit can deliver the purchase result and an update concurrently.
        if let pending = transactionProcessing[transaction.id] {
            try await pending.value
            guard session.isCurrent(snapshot) else { throw CancellationError() }
            return
        }
        let work = Task { @MainActor in
            guard session.isCurrent(snapshot) else { throw CancellationError() }
            try await self.verifyWithBackend(transaction: transaction, signedPayload: signedPayload,
                                             plan: plan, snapshot: snapshot)
            guard session.isCurrent(snapshot) else { throw CancellationError() }
            // Unverified/failed transactions remain unfinished for a later retry.
            await transaction.finish()
        }
        transactionProcessing[transaction.id] = work
        defer { transactionProcessing[transaction.id] = nil }
        try await work.value
    }
    
    private func verifyWithBackend(
        transaction: Transaction,
        signedPayload: String,
        plan: PlanTier,
        snapshot: AuthenticatedSessionSnapshot
    ) async throws {
        let body = AppleSubscriptionVerificationRequest(
            transactionId: String(transaction.id),
            productId: transaction.productID,
            plan: plan.rawValue,
            signedTransactionInfo: signedPayload
        )
        let _: EmptyResponse = try await NetworkManager.shared.request(.POST,
            path: "/api/subscription/apple-verify", body: body, authTokenOverride: snapshot.token)
    }
    
    private func updatePurchasedStatus() async {
        let session = SessionManager.shared
        guard let snapshot = session.authenticatedSnapshot, let accountToken = session.appAccountToken else {
            purchasedProductIDs = []
            return
        }
        var activePurchases: Set<String> = []
        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result else { continue }
            if transaction.revocationDate == nil,
               SubscriptionProductCatalog.identifiers.contains(transaction.productID),
               transaction.appAccountToken == accountToken {
                activePurchases.insert(transaction.productID)
            }
        }
        guard session.isCurrent(snapshot) else { return }
        self.purchasedProductIDs = activePurchases
    }
    
    private func listenForTransactions() -> Task<Void, Never> {
        return Task {
            for await result in Transaction.updates {
                await self.handlePendingTransaction(result)
            }
        }
    }

    func sessionDidChange() {
        purchasedProductIDs = []
        purchaseError = nil
        statusMessage = nil
    }

    func sessionDidAuthenticate() async {
        let session = SessionManager.shared
        guard let snapshot = session.authenticatedSnapshot,
              await session.refreshSubscriptionContext(expectedSession: snapshot), session.isCurrent(snapshot) else { return }
        for await result in Transaction.unfinished {
            guard session.isCurrent(snapshot) else { return }
            await handlePendingTransaction(result)
        }
        await updatePurchasedStatus()
    }

    private func handlePendingTransaction(_ result: VerificationResult<Transaction>) async {
        let session = SessionManager.shared
        guard session.isAdmin, let snapshot = session.authenticatedSnapshot, let accountToken = session.appAccountToken,
              case .verified(let transaction) = result, let plan = plan(for: transaction.productID),
              PurchaseAccountPolicy.canProcess(transactionAccount: transaction.appAccountToken,
                currentAccount: accountToken, explicitRestore: false) else { return }
        do {
            try await processTransaction(transaction: transaction, signedPayload: result.jwsRepresentation,
                plan: plan, snapshot: snapshot, accountToken: accountToken)
            await updatePurchasedStatus()
            await session.refreshSubscriptionContext(expectedSession: snapshot)
        } catch {
            guard session.isCurrent(snapshot), !(error is CancellationError) else { return }
            purchaseError = String(localized: "billing.verification_pending")
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
        let session = SessionManager.shared
        guard session.isAdmin, let snapshot = session.authenticatedSnapshot else { return }
        isRestoring = true
        purchaseError = nil
        statusMessage = nil
        defer { isRestoring = false }

        do {
            guard await session.refreshSubscriptionContext(expectedSession: snapshot),
                  session.isCurrent(snapshot), let accountToken = session.appAccountToken else {
                throw PurchaseProcessingError.accountUnavailable
            }
            try await AppStore.sync()
            guard session.isCurrent(snapshot) else { return }
            var restoredCount = 0
            for await result in Transaction.currentEntitlements {
                let transaction = try checkVerified(result)
                guard transaction.revocationDate == nil,
                      let plan = plan(for: transaction.productID) else { continue }
                try await processTransaction(
                    transaction: transaction,
                    signedPayload: result.jwsRepresentation,
                    plan: plan, snapshot: snapshot, accountToken: accountToken, explicitRestore: true
                )
                restoredCount += 1
            }
            await updatePurchasedStatus()
            let refreshed = await session.refreshSubscriptionContext(expectedSession: snapshot)
            guard session.isCurrent(snapshot) else { return }
            statusMessage = String(localized: restoredCount == 0 ? "billing.restore_empty"
                : refreshed ? "billing.restored" : "billing.verified_refresh_pending")
        } catch {
            guard session.isCurrent(snapshot), !(error is CancellationError) else { return }
            purchaseError = error.localizedDescription
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
            offers[product.id] = String(format: String(localized: "billing.free_trial"), offer.period.localizedDescription)
        }
        eligibleIntroOffers = offers
    }

    private func plan(for productID: String) -> PlanTier? {
        productDict.first(where: { $0.value.values.contains(productID) })?.key
    }
}

private enum PurchaseProcessingError: LocalizedError {
    case accountUnavailable, accountMismatch

    var errorDescription: String? {
        switch self {
        case .accountUnavailable: return String(localized: "billing.account_unavailable")
        case .accountMismatch: return String(localized: "billing.account_mismatch")
        }
    }
}

enum StoreProductLoadIssue {
    case unavailable
    case incomplete
    case connection
    case requestFailed

    var title: String {
        switch self {
        case .unavailable: return String(localized: "billing.catalog_empty_title")
        case .incomplete: return String(localized: "billing.catalog_partial_title")
        case .connection: return String(localized: "billing.catalog_connection_title")
        case .requestFailed: return String(localized: "billing.catalog_failed_title")
        }
    }

    var message: String {
        switch self {
        case .unavailable:
            return String(localized: "billing.catalog_empty_message")
        case .incomplete:
            return String(localized: "billing.catalog_partial_message")
        case .connection:
            return String(localized: "billing.catalog_connection_message")
        case .requestFailed:
            return String(localized: "billing.catalog_failed_message")
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
        case .monthly: return String(localized: "billing.monthly")
        case .yearly: return String(localized: "billing.yearly")
        }
    }
}

private extension Product.SubscriptionPeriod {
    var localizedDescription: String {
        let formatter = DateComponentsFormatter()
        formatter.unitsStyle = .full
        let components: DateComponents
        switch unit {
        case .day: formatter.allowedUnits = .day; components = DateComponents(day: value)
        case .week: formatter.allowedUnits = .weekOfMonth; components = DateComponents(weekOfMonth: value)
        case .month: formatter.allowedUnits = .month; components = DateComponents(month: value)
        case .year: formatter.allowedUnits = .year; components = DateComponents(year: value)
        @unknown default: return String(localized: "billing.period")
        }
        return formatter.string(from: components) ?? String(localized: "billing.period")
    }

    var localizedSuffix: String {
        "/" + localizedDescription
    }
}

private struct AppleSubscriptionVerificationRequest: Encodable {
    let transactionId: String
    let productId: String
    let plan: String
    let signedTransactionInfo: String
}
