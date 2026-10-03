import AuthenticationServices
import GoogleSignInSwift
import SwiftUI

/// Shared by sign-in and registration; both providers use the same existing account.
struct SocialAuthButtons: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var manager = SocialAuthManager()
    @State private var pendingResponse: AuthResponse?
    @State private var showPasswordSetup = false
    @Binding var isLoading: Bool
    @Binding var errorMessage: String?
    var onSuccess: (AuthResponse) -> Void

    var body: some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                Rectangle().frame(height: 1)
                Text("veya").font(.caption)
                Rectangle().frame(height: 1)
            }
            .foregroundStyle(.secondary.opacity(0.5))

            GoogleSignInButton(scheme: colorScheme == .dark ? .dark : .light, style: .wide,
                               state: isLoading ? .disabled : .normal) {
                signIn(apple: false)
            }
            .frame(maxWidth: .infinity, minHeight: 48)
            .accessibilityIdentifier("auth.google")

            NativeAppleSignInButton(darkMode: colorScheme == .dark, isEnabled: !isLoading) {
                signIn(apple: true)
            }
            .frame(height: 48)
            .accessibilityIdentifier("auth.apple")

            if isLoading { ProgressView().accessibilityLabel("Giriş yapılıyor") }
        }
        .disabled(isLoading)
        .sheet(isPresented: $showPasswordSetup) {
            SocialPasswordSetupView(username: pendingResponse?.loginUsername, onComplete: {
                if let response = pendingResponse { onSuccess(response) }
                pendingResponse = nil
                showPasswordSetup = false
            }, onCancel: {
                let previousToken = pendingResponse?.token
                pendingResponse = nil
                showPasswordSetup = false
                Task { await AuthService.logout(expectedToken: previousToken) }
            })
            .interactiveDismissDisabled()
        }
    }

    private func signIn(apple: Bool) {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        Task { @MainActor in
            defer { isLoading = false }
            do {
                let response: AuthResponse
                if apple { response = try await manager.signInWithApple() }
                else { response = try await manager.signInWithGoogle() }
                if response.requiresPasswordSetup == true {
                    pendingResponse = response
                    showPasswordSetup = true
                } else {
                    onSuccess(response)
                }
            } catch {
                if !SocialAuthManager.isCancellation(error) { errorMessage = error.localizedDescription }
            }
        }
    }
}

/// Apple's official button, with asynchronous server challenge before showing its authorization sheet.
private struct NativeAppleSignInButton: UIViewRepresentable {
    let darkMode: Bool
    let isEnabled: Bool
    let action: () -> Void

    func makeUIView(context: Context) -> UIView { UIView() }
    func updateUIView(_ view: UIView, context: Context) {
        view.subviews.forEach { $0.removeFromSuperview() }
        let button = ASAuthorizationAppleIDButton(type: .continue, style: darkMode ? .white : .black)
        button.cornerRadius = 10
        button.isEnabled = isEnabled
        button.addAction(UIAction { _ in action() }, for: .touchUpInside)
        button.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(button)
        NSLayoutConstraint.activate([
            button.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            button.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            button.topAnchor.constraint(equalTo: view.topAnchor),
            button.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }
}
