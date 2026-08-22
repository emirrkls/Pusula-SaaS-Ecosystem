import Foundation

/// Auth service — handles login, registration, and token lifecycle.
/// Stores token securely and works with SessionManager for state.
enum AuthService {
    
    /// Individual login — username + password
    static func login(username: String, password: String) async throws -> AuthResponse {
        let body = AuthRequest(username: username, password: password)
        let response: AuthResponse = try await NetworkManager.shared.post(
            "/api/auth/authenticate", body: body, requiresAuth: false
        )
        await NetworkManager.shared.setToken(response.token)
        return response
    }
    
    /// Corporate login — orgCode + username + password
    static func loginCorporate(orgCode: String, username: String, password: String) async throws -> AuthResponse {
        let body = AuthRequest(username: username, password: password, orgCode: orgCode)
        let response: AuthResponse = try await NetworkManager.shared.post(
            "/api/auth/authenticate", body: body, requiresAuth: false
        )
        await NetworkManager.shared.setToken(response.token)
        return response
    }
    
    /// Individual registration — creates company + admin user
    static func registerIndividual(email: String, password: String, fullName: String) async throws -> AuthResponse {
        let body = RegisterRequest(email: email, password: password, fullName: fullName)
        let response: AuthResponse = try await NetworkManager.shared.post(
            "/api/auth/register-individual", body: body, requiresAuth: false
        )
        await NetworkManager.shared.setToken(response.token)
        return response
    }
    
    /// Refresh feature context (called on app foreground / session restore)
    static func refreshFeatureContext() async throws -> SubscriptionContextDTO {
        try await NetworkManager.shared.get("/api/subscription/my-context")
    }

    static func getPlans() async throws -> [PlanSummaryDTO] {
        try await NetworkManager.shared.get("/api/subscription/plans", requiresAuth: false)
    }

    static func fetchAuthProfile() async throws -> AuthProfileResponse {
        try await NetworkManager.shared.get("/api/auth/feature-context")
    }

    static func updateOnboardingVersion(_ version: Int) async throws -> Int {
        let response: OnboardingVersionResponse = try await NetworkManager.shared.put(
            "/api/auth/onboarding-version",
            body: OnboardingVersionRequest(version: version)
        )
        return response.version
    }
    
    /// Logout — clear token
    static func logout() async {
        await NetworkManager.shared.setToken(nil)
    }
    
    /// Delete Account — call backend endpoint
    static func deleteAccount() async throws {
        // Backend endpoint to trigger account deletion
        let _: EmptyResponse = try await NetworkManager.shared.request(.DELETE, path: "/api/auth/delete-account")
    }
}

private struct OnboardingVersionRequest: Encodable {
    let version: Int
}

private struct OnboardingVersionResponse: Decodable {
    let version: Int
}
