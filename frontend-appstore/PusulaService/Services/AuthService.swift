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
    
    /// Refresh feature context (called on app foreground)
    static func refreshFeatureContext() async throws -> [String: Any] {
        try await NetworkManager.shared.get("/api/subscription/my-context")
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
