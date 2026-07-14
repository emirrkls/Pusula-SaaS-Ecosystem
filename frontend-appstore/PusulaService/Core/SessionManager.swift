import SwiftUI

/// Central session state — drives the entire app's navigation and feature visibility.
final class SessionManager: ObservableObject {
    static let shared = SessionManager()
    
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
    
    // MARK: - Computed Properties
    
    var isAdmin: Bool {
        role == "COMPANY_ADMIN" || role == "SUPER_ADMIN"
    }
    
    var isTechnician: Bool {
        role == "TECHNICIAN"
    }
    
    var showTrialBanner: Bool {
        guard let days = trialDaysRemaining else { return false }
        return days <= 7 && days > 0
    }
    
    var isTrialExpired: Bool {
        trialDaysRemaining == 0 && planType == "CIRAK"
    }
    
    // MARK: - Feature Gate
    
    func isFeatureEnabled(_ key: String) -> Bool {
        features[key] ?? false
    }
    
    // MARK: - Session Lifecycle
    
    func configure(from response: AuthResponse) {
        self.isRestoringSession = false
        self.sessionMessage = nil
        self.isAuthenticated = true
        self.token = response.token
        self.role = response.role
        self.fullName = response.fullName ?? ""
        self.companyId = response.companyId
        self.companyName = response.companyName
        self.planType = response.planType ?? "CIRAK"
        self.features = response.features ?? [:]
        self.quota = response.quota
        self.isReadOnly = response.isReadOnly ?? false
        self.trialDaysRemaining = response.trialDaysRemaining
        
        // Persist token to Keychain
        KeychainHelper.save(key: "auth_token", value: response.token)
        KeychainHelper.save(key: "user_role", value: response.role)
        Task { @MainActor in
            PushNotificationManager.shared.sessionDidAuthenticate()
        }
    }
    
    func logout() {
        clearLocalSession()
        Task {
            await PushNotificationManager.shared.unregisterCurrentDevice()
            await AuthService.logout()
        }
    }

    func handleUnauthorized() {
        guard isAuthenticated || token != nil else { return }
        clearLocalSession(message: "Oturum süreniz doldu. Lütfen tekrar giriş yapın.")
        Task { await AuthService.logout() }
    }

    private func clearLocalSession(message: String? = nil) {
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
        guard let savedToken = KeychainHelper.load(key: "auth_token"),
              KeychainHelper.load(key: "user_role") != nil else {
            isRestoringSession = false
            return
        }
        isRestoringSession = true
        self.token = savedToken
        self.role = ""
        self.isAuthenticated = false
        
        Task {
            await NetworkManager.shared.setToken(savedToken)
            await validateRestoredSession()
        }
    }

    @MainActor
    private func validateRestoredSession() async {
        do {
            async let profileRequest = AuthService.fetchAuthProfile()
            async let subscriptionRequest = AuthService.refreshFeatureContext()
            let (profile, context) = try await (profileRequest, subscriptionRequest)

            guard let restoredRole = profile.role,
                  ["TECHNICIAN", "COMPANY_ADMIN", "SUPER_ADMIN"].contains(restoredRole) else {
                throw SessionRestoreError.unsupportedRole
            }

            role = restoredRole
            fullName = profile.fullName ?? ""
            companyId = profile.companyId
            companyName = profile.companyName
            KeychainHelper.save(key: "user_role", value: restoredRole)
            applySubscriptionContext(context)
            isAuthenticated = true
            isRestoringSession = false
            sessionMessage = nil
            PushNotificationManager.shared.sessionDidAuthenticate()
        } catch {
            clearLocalSession(message: "Oturum doğrulanamadı. Lütfen tekrar giriş yapın.")
            await AuthService.logout()
        }
    }
    
    @MainActor
    func refreshSubscriptionContext() async {
        do {
            let context = try await AuthService.refreshFeatureContext()
            applySubscriptionContext(context)
            self.isAuthenticated = true
            self.isRestoringSession = false
        } catch {
            if case NetworkError.unauthorized = error {
                handleUnauthorized()
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
        trialDaysRemaining = context.trialDaysRemaining
    }
}

private enum SessionRestoreError: Error {
    case unsupportedRole
}

// MARK: - Keychain Helper (Secure Token Storage)

enum KeychainHelper {
    static func save(key: String, value: String) {
        let data = value.data(using: .utf8)!
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
