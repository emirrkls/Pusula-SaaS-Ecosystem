import Foundation
import GoogleSignIn

/// Auth service — handles login, registration, and token lifecycle.
/// Stores token securely and works with SessionManager for state.
enum AuthService {
    static func setInitialSocialPassword(_ password: String) async throws {
        let _: EmptyResponse = try await NetworkManager.shared.post(
            "/api/auth/initial-password", body: InitialPasswordBody(password: password))
    }
    static func loginGoogle(idToken: String) async throws -> AuthResponse {
        let response: AuthResponse = try await NetworkManager.shared.post(
            "/api/auth/google", body: GoogleAuthBody(idToken: idToken), requiresAuth: false)
        await NetworkManager.shared.setToken(response.token)
        return response
    }

    static func appleChallenge() async throws -> AppleAuthChallenge {
        try await NetworkManager.shared.post("/api/auth/apple/challenge", body: EmptyAuthBody(), requiresAuth: false)
    }

    static func loginApple(idToken: String, authorizationCode: String, challengeId: String, fullName: String?) async throws -> AuthResponse {
        let response: AuthResponse = try await NetworkManager.shared.post(
            "/api/auth/apple", body: AppleAuthBody(idToken: idToken, authorizationCode: authorizationCode,
                                                 challengeId: challengeId, fullName: fullName), requiresAuth: false)
        await NetworkManager.shared.setToken(response.token)
        return response
    }
    
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
    static func logout(expectedToken: String?) async {
        guard await NetworkManager.shared.clearToken(ifMatching: expectedToken) else { return }
        await MainActor.run {
            guard SessionManager.shared.token == nil else { return }
            GIDSignIn.sharedInstance.signOut()
        }
    }
    
    /// Delete Account — call backend endpoint
    static func deleteAccount() async throws {
        // Backend endpoint to trigger account deletion
        let _: EmptyResponse = try await NetworkManager.shared.request(.DELETE, path: "/api/auth/delete-account")
    }
}

struct AppleAuthChallenge: Decodable {
    let id: String
    let nonce: String
}

private struct GoogleAuthBody: Encodable { let idToken: String }
private struct InitialPasswordBody: Encodable { let password: String }
private struct EmptyAuthBody: Encodable {}
private struct AppleAuthBody: Encodable {
    let idToken: String
    let authorizationCode: String
    let challengeId: String
    let fullName: String?
}

private struct OnboardingVersionRequest: Encodable {
    let version: Int
}

private struct OnboardingVersionResponse: Decodable {
    let version: Int
}
