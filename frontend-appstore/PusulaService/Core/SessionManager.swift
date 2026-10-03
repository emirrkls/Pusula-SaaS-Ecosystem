import SwiftUI
import OSLog

/// Central session state — drives the entire app's navigation and feature visibility.
@MainActor
final class SessionManager: ObservableObject {
    static let shared = SessionManager()
    private var restoreTask: Task<Void, Never>?
    private var hasAttemptedRestore = false
    private var sessionRevision = UUID()
    private let logger = Logger(subsystem: "com.pusula.service", category: "Session")
    
    // MARK: - Auth State
    @Published var isAuthenticated = false
    @Published var isRestoringSession = true
    @Published var sessionMessage: String?
    @Published var token: String?
    @Published var role: String = ""
    @Published var fullName: String = ""
    @Published var companyId: Int?
    @Published var companyName: String?
    
    // MARK: - SaaS State
    @Published var planType: String = "CIRAK"
    @Published var features: [String: Bool] = [:]
    @Published var quota: QuotaDTO?
    @Published var isReadOnly: Bool = false
    @Published var trialDaysRemaining: Int?
    @Published var onboardingVersion: Int = 0
    
    // MARK: - Computed Properties
    
    var isAdmin: Bool {
        role == "COMPANY_ADMIN" || role == "SUPER_ADMIN"
    }
    
    var isTechnician: Bool {
        role == "TECHNICIAN"
    }

    var onboardingAccountIdentifier: String {
        let normalizedName = fullName
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "tr_TR"))
            .replacingOccurrences(of: " ", with: "-")
        return "\(companyId ?? 0)-\(normalizedName)-\(role.lowercased())"
    }
    
    var showTrialBanner: Bool {
        guard planType != "CIRAK", let days = trialDaysRemaining else { return false }
        return days <= 7 && days > 0
    }
    
    var isTrialExpired: Bool {
        trialDaysRemaining == 0 && planType != "CIRAK"
    }
    
    // MARK: - Feature Gate
    
    func isFeatureEnabled(_ key: String) -> Bool {
        features[key] ?? false
    }
    
    // MARK: - Session Lifecycle
    
    func configure(from response: AuthResponse) {
        restoreTask?.cancel()
        restoreTask = nil
        sessionRevision = UUID()
        hasAttemptedRestore = true
        self.isRestoringSession = false
        self.sessionMessage = nil
        self.token = response.token
        self.role = response.role
        self.fullName = response.fullName ?? ""
        self.companyId = response.companyId
        self.companyName = response.companyName
        self.planType = response.planType ?? "CIRAK"
        self.features = response.features ?? [:]
        self.quota = response.quota
        self.isReadOnly = response.isReadOnly ?? false
        self.trialDaysRemaining = planType == "CIRAK" ? nil : response.trialDaysRemaining
        self.onboardingVersion = response.onboardingVersion ?? 0
        self.isAuthenticated = true
        
        // Persist token to Keychain
        KeychainHelper.save(key: "auth_token", value: response.token)
        KeychainHelper.save(key: "user_role", value: response.role)
        Task { @MainActor in
            PushNotificationManager.shared.sessionDidAuthenticate()
        }
    }
    
    func logout() {
        let previousToken = token
        clearLocalSession()
        Task {
            await PushNotificationManager.shared.unregisterCurrentDevice(authToken: previousToken)
            await AuthService.logout(expectedToken: previousToken)
        }
    }

    func handleUnauthorized(expectedToken: String) {
        guard SessionResponsePolicy.belongsToCurrentSession(requestToken: expectedToken, currentToken: token)
        else { return }
        logger.notice("Current session rejected; returning safely to login")
        clearLocalSession(message: "Oturum süreniz doldu. Lütfen tekrar giriş yapın.")
        Task { await AuthService.logout(expectedToken: expectedToken) }
    }

    private func clearLocalSession(message: String? = nil) {
        restoreTask?.cancel()
        restoreTask = nil
        sessionRevision = UUID()
        AppNavigation.shared.resetForLogout()
        isRestoringSession = false
        sessionMessage = message
        isAuthenticated = false
        token = nil
        role = ""
        fullName = ""
        companyId = nil
        companyName = nil
        planType = "CIRAK"
        features = [:]
        quota = nil
        isReadOnly = false
        trialDaysRemaining = nil
        onboardingVersion = 0
        
        KeychainHelper.delete(key: "auth_token")
        KeychainHelper.delete(key: "user_role")
        
    }
    
    func deleteAccount() async throws {
        // Perform backend deletion
        try await AuthService.deleteAccount()
        
        // Log out locally
        await MainActor.run {
            self.logout()
        }
    }
    
    func tryRestoreSession() {
        guard !hasAttemptedRestore else { return }
        hasAttemptedRestore = true
        guard let savedToken = KeychainHelper.load(key: "auth_token"),
              KeychainHelper.load(key: "user_role") != nil else {
            isRestoringSession = false
            return
        }
        isRestoringSession = true
        self.token = savedToken
        self.role = ""
        self.isAuthenticated = false
        
        let revision = sessionRevision
        restoreTask = Task {
            guard !Task.isCancelled, revision == sessionRevision else { return }
            logger.info("Saved session validation started after root view mounted")
            await NetworkManager.shared.setToken(savedToken)
            guard !Task.isCancelled, revision == sessionRevision else { return }
            await validateRestoredSession(expectedToken: savedToken, revision: revision)
        }
    }

    @MainActor
    private func validateRestoredSession(expectedToken: String, revision: UUID) async {
        do {
            async let profileRequest = AuthService.fetchAuthProfile()
            async let subscriptionRequest = AuthService.refreshFeatureContext()
            let (profile, context) = try await (profileRequest, subscriptionRequest)
            guard !Task.isCancelled, revision == sessionRevision,
                  SessionResponsePolicy.belongsToCurrentSession(requestToken: expectedToken, currentToken: token)
            else { return }

            guard let restoredRole = profile.role,
                  ["TECHNICIAN", "COMPANY_ADMIN", "SUPER_ADMIN"].contains(restoredRole) else {
                throw SessionRestoreError.unsupportedRole
            }

            role = restoredRole
            fullName = profile.fullName ?? ""
            companyId = profile.companyId
            companyName = profile.companyName
            onboardingVersion = profile.onboardingVersion ?? 0
            KeychainHelper.save(key: "user_role", value: restoredRole)
            applySubscriptionContext(context)
            isAuthenticated = true
            isRestoringSession = false
            sessionMessage = nil
            logger.info("Saved session validation completed")
            PushNotificationManager.shared.sessionDidAuthenticate()
        } catch {
            guard !Task.isCancelled, revision == sessionRevision,
                  SessionResponsePolicy.belongsToCurrentSession(requestToken: expectedToken, currentToken: token)
            else { return }
            logger.notice("Saved session validation failed; returning safely to login")
            clearLocalSession(message: "Oturum doğrulanamadı. Lütfen tekrar giriş yapın.")
            await AuthService.logout(expectedToken: expectedToken)
        }
    }
    
    @MainActor
    func refreshSubscriptionContext() async {
        guard isAuthenticated, !isRestoringSession, let requestToken = token else { return }
        do {
            let context = try await AuthService.refreshFeatureContext()
            guard !Task.isCancelled, isAuthenticated,
                  SessionResponsePolicy.belongsToCurrentSession(requestToken: requestToken, currentToken: token)
            else { return }
            applySubscriptionContext(context)
        } catch {
            guard !Task.isCancelled,
                  SessionResponsePolicy.belongsToCurrentSession(requestToken: requestToken, currentToken: token)
            else { return }
            if case NetworkError.unauthorized = error {
                handleUnauthorized(expectedToken: requestToken)
            } else {
                self.sessionMessage = "Sunucuya ulaşılamadı. Bazı bilgiler güncel olmayabilir."
            }
        }
    }

    private func applySubscriptionContext(_ context: SubscriptionContextDTO) {
        if let plan = context.planType { planType = plan }
        if let enabledFeatures = context.features { features = enabledFeatures }
        if let currentQuota = context.quota { quota = currentQuota }
        if let readOnly = context.isReadOnly { isReadOnly = readOnly }
        trialDaysRemaining = planType == "CIRAK" ? nil : context.trialDaysRemaining
    }
}

private enum SessionRestoreError: Error {
    case unsupportedRole
}

// MARK: - Keychain Helper (Secure Token Storage)

enum KeychainHelper {
    static func save(key: String, value: String) {
        let data = Data(value.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecValueData as String: data
        ]
        SecItemDelete(query as CFDictionary)
        SecItemAdd(query as CFDictionary, nil)
    }
    
    static func load(key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: AnyObject?
        SecItemCopyMatching(query as CFDictionary, &result)
        guard let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }
    
    static func delete(key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key
        ]
        SecItemDelete(query as CFDictionary)
    }
}
