import SwiftUI
import Observation

/// Central session state — drives the entire app's navigation and feature visibility.
/// Uses @Observable (iOS 17+) for efficient SwiftUI reactivity.
@Observable
final class SessionManager {
    static let shared = SessionManager()
    
    // MARK: - Auth State
    var isAuthenticated = false
    var token: String?
    var role: String = ""
    var fullName: String = ""
    var companyId: Int?
    var companyName: String?
    
    // MARK: - SaaS State
    var planType: String = "CIRAK"
    var features: [String: Bool] = [:]
    var quota: QuotaDTO?
    var isReadOnly: Bool = false
    var trialDaysRemaining: Int?
    
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
    }
    
    func logout() {
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
        
        Task { await AuthService.logout() }
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
              let savedRole = KeychainHelper.load(key: "user_role") else {
            return
        }
        self.token = savedToken
        self.role = savedRole
        self.isAuthenticated = true
        
        Task {
            await NetworkManager.shared.setToken(savedToken)
        }
    }
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
