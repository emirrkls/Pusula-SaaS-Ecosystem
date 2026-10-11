#if DEBUG
import AuthenticationServices
import SwiftUI

/// An offline UI regression, not an authenticated app mode. Never configures
/// SessionManager, writes Keychain, calls providers or contacts any API.
enum SocialAuthFlowFixture {
    static var isEnabled: Bool {
#if targetEnvironment(simulator)
        AppStoreScreenshotMode.isEnabled
            && ProcessInfo.processInfo.environment["PUSULA_SOCIAL_AUTH_FIXTURE"] == "1"
#else
        false
#endif
    }
}

struct SocialAuthFlowHarness: View {
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var signedIn = false
    @State private var canSetDesktopPassword = false
    @State private var showDesktopPassword = false
    @State private var attemptCount = 0

    private var environment: [String: String] { ProcessInfo.processInfo.environment }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    if signedIn {
                        Text("Offline authentication completed")
                            .accessibilityIdentifier("auth.fixture.signed-in")
                        if canSetDesktopPassword {
                            Button("auth.desktop_password.action") { showDesktopPassword = true }
                                .accessibilityIdentifier("account.desktop-password.open")
                        }
                    } else {
                        SocialAuthButtons(
                            isLoading: $isLoading,
                            errorMessage: $errorMessage,
                            placement: environment["PUSULA_SOCIAL_AUTH_PLACEMENT"] == "registration" ? .registration : .signIn,
                            testAuthenticate: authenticate,
                            onSuccess: { response in
                                canSetDesktopPassword = response.desktopLoginOptions.canSetPassword
                                signedIn = true
                            }
                        )
                    }
                    if let errorMessage {
                        Text(errorMessage).accessibilityIdentifier("auth.fixture.error")
                    }
                    Text("Attempts: \(attemptCount)").accessibilityIdentifier("auth.fixture.attempts")
                }
                .padding(PusulaTheme.pagePadding)
            }
            .navigationTitle("Offline social sign-in regression")
        }
        .sheet(isPresented: $showDesktopPassword) {
            DesktopPasswordSetupView(
                username: "demo@privaterelay.appleid.com",
                savePassword: { _ in canSetDesktopPassword = false },
                onComplete: { showDesktopPassword = false },
                onCancel: { showDesktopPassword = false }
            )
        }
    }

    @MainActor
    private func authenticate(apple: Bool) async throws -> AuthResponse {
        attemptCount += 1
        switch environment["PUSULA_SOCIAL_AUTH_RESULT"] {
        case "cancel": throw ASAuthorizationError(.canceled)
        case "error": throw FixtureError.failed
        default: break
        }
        return AuthResponse(
            token: "offline-fixture-not-a-session", role: "COMPANY_ADMIN",
            fullName: nil, companyId: 0, companyName: "Offline test", planType: "CIRAK",
            features: nil, quota: nil, isReadOnly: false, trialDaysRemaining: nil,
            onboardingVersion: nil,
            requiresPasswordSetup: environment["PUSULA_LOCAL_PASSWORD_ENABLED"] != "1",
            loginUsername: "demo@privaterelay.appleid.com"
        )
    }

    private enum FixtureError: LocalizedError {
        case failed
        var errorDescription: String? { "Offline provider failure" }
    }
}
#endif
