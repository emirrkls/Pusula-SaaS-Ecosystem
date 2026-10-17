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
    @Published private(set) var restoreNeedsRetry = false
    @Published private(set) var appAccountToken: UUID?
    @Published var token: String?
    @Published var role: String = ""
    @Published var fullName: String = ""
    @Published var companyId: Int?
    @Published var companyName: String?
    @Published private(set) var desktopLoginOptions: DesktopLoginOptions = .unavailable

    var canSetDesktopPassword: Bool { desktopLoginOptions.canSetPassword }
    var desktopLoginUsername: String? { desktopLoginOptions.loginUsername }

    var authenticatedSnapshot: AuthenticatedSessionSnapshot? {
        guard isAuthenticated, !isRestoringSession, let token, !token.isEmpty, let companyId else { return nil }
        return AuthenticatedSessionSnapshot(token: token, companyId: companyId, revision: sessionRevision)
    }

    func isCurrent(_ snapshot: AuthenticatedSessionSnapshot) -> Bool {
        authenticatedSnapshot == snapshot
    }
    
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
        restoreNeedsRetry = false
        appAccountToken = nil
        self.token = response.token
        self.role = response.role
        self.fullName = response.fullName ?? ""
        self.companyId = response.companyId
        self.companyName = response.companyName
        self.desktopLoginOptions = response.desktopLoginOptions
        self.planType = response.planType ?? "CIRAK"
        self.features = response.features ?? [:]
        self.quota = response.quota
        self.isReadOnly = response.isReadOnly ?? false
        self.trialDaysRemaining = planType == "CIRAK" ? nil : response.trialDaysRemaining
        self.onboardingVersion = response.onboardingVersion ?? 0
        self.isAuthenticated = true
        StoreKitManager.shared.sessionDidChange()
        
        // Persist token to Keychain
        KeychainHelper.save(key: "auth_token", value: response.token)
        KeychainHelper.save(key: "user_role", value: response.role)
        Task { @MainActor in
            PushNotificationManager.shared.sessionDidAuthenticate()
            await StoreKitManager.shared.sessionDidAuthenticate()
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
        restoreNeedsRetry = false
        appAccountToken = nil
        sessionMessage = message
        isAuthenticated = false
        token = nil
        role = ""
        fullName = ""
        companyId = nil
        companyName = nil
        desktopLoginOptions = .unavailable
        planType = "CIRAK"
        features = [:]
        quota = nil
        isReadOnly = false
        trialDaysRemaining = nil
        onboardingVersion = 0
        StoreKitManager.shared.sessionDidChange()
        
        KeychainHelper.delete(key: "auth_token")
        KeychainHelper.delete(key: "user_role")
        
    }
    
    func deleteAccount() async throws {
        guard let snapshot = authenticatedSnapshot else { throw NetworkError.unauthorized }
        try await AuthService.deleteAccount(authToken: snapshot.token)
        if let supportKey=SupportService.accountKey(snapshot.token,company:snapshot.companyId) {
            await SupportDraftStore.shared.clear(supportKey)
            GuideProgressStore.clear(scope: supportKey)
        }
        guard isCurrent(snapshot) else { return }
        logout()
    }

    func retrySessionRestore() {
        guard restoreNeedsRetry else { return }
        hasAttemptedRestore = false
        tryRestoreSession()
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
        restoreNeedsRetry = false
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
            async let profileRequest = AuthService.fetchAuthProfile(authToken: expectedToken)
            async let subscriptionRequest = AuthService.refreshFeatureContext(authToken: expectedToken)
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
            desktopLoginOptions = profile.desktopLoginOptions
            onboardingVersion = profile.onboardingVersion ?? 0
            KeychainHelper.save(key: "user_role", value: restoredRole)
            applySubscriptionContext(context)
            isAuthenticated = true
            isRestoringSession = false
            sessionMessage = nil
            restoreNeedsRetry = false
            logger.info("Saved session validation completed")
            PushNotificationManager.shared.sessionDidAuthenticate()
            await StoreKitManager.shared.sessionDidAuthenticate()
        } catch {
            guard !Task.isCancelled, revision == sessionRevision,
                  SessionResponsePolicy.belongsToCurrentSession(requestToken: expectedToken, currentToken: token)
            else { return }
            let status: Int?
            switch error {
            case NetworkError.unauthorized: status = 401
            case NetworkError.forbidden: status = 403
            default: status = nil
            }
            if SessionRestorePolicy.shouldDiscardCredentials(
                httpStatus: status, unsupportedRole: error is SessionRestoreError
            ) {
                clearLocalSession(message: String(localized: "session.invalid"))
                await AuthService.logout(expectedToken: expectedToken)
            } else {
                // No cached privileged dashboard. Keep Keychain and pending navigation
                // intact until the server can validate the session or the user signs out.
                logger.notice("Session validation temporarily unavailable; credentials preserved")
                restoreNeedsRetry = true
            }
        }
    }
    
    @MainActor
    @discardableResult
    func refreshSubscriptionContext(expectedSession: AuthenticatedSessionSnapshot? = nil) async -> Bool {
        guard let snapshot = authenticatedSnapshot,
              expectedSession == nil || expectedSession == snapshot else { return false }
        do {
            let context = try await AuthService.refreshFeatureContext(authToken: snapshot.token)
            guard !Task.isCancelled, isCurrent(snapshot) else { return false }
            applySubscriptionContext(context)
            return true
        } catch {
            guard !Task.isCancelled, isCurrent(snapshot) else { return false }
            if case NetworkError.unauthorized = error {
                handleUnauthorized(expectedToken: snapshot.token)
            } else {
                self.sessionMessage = String(localized: "session.refresh_unavailable")
            }
            return false
        }
    }

    private func applySubscriptionContext(_ context: SubscriptionContextDTO) {
        appAccountToken = context.appAccountToken
        if let plan = context.planType { planType = plan }
        if let enabledFeatures = context.features { features = enabledFeatures }
        if let currentQuota = context.quota { quota = currentQuota }
        if let readOnly = context.isReadOnly { isReadOnly = readOnly }
        trialDaysRemaining = planType == "CIRAK" ? nil : context.trialDaysRemaining
    }

    /// Explicit account-setting action, independent of Apple/Google sign-in.
    func setDesktopPassword(_ password: String) async throws {
        guard isAuthenticated, canSetDesktopPassword, let requestToken = token else {
            throw NetworkError.unauthorized
        }
        let revision = sessionRevision
        try await AuthService.setInitialSocialPassword(password, authToken: requestToken)
        guard !Task.isCancelled, revision == sessionRevision,
              SessionResponsePolicy.belongsToCurrentSession(requestToken: requestToken, currentToken: token)
        else { throw CancellationError() }
        desktopLoginOptions = desktopLoginOptions.afterSettingPassword
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
