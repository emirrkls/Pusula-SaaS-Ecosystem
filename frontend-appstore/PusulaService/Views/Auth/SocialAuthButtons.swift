import AuthenticationServices
import SwiftUI

/// Shared by sign-in and registration; both providers use the same existing account.
struct SocialAuthButtons: View {
    @StateObject private var manager = SocialAuthManager()
    @State private var pendingResponse: AuthResponse?
    @State private var showPasswordSetup = false
    @Binding var isLoading: Bool
    @Binding var errorMessage: String?
    var placement: SocialAuthPlacement = .signIn
    var onSuccess: (AuthResponse) -> Void

    var body: some View {
        VStack(spacing: 18) {
            if placement == .signIn {
                separator
            }

            SocialAuthProviderControls(
                isLoading: isLoading,
                googleAction: { signIn(apple: false) },
                appleAction: { signIn(apple: true) }
            )

            if isLoading {
                ProgressView().accessibilityLabel(Text("auth.social.loading"))
            }
            if placement == .registration {
                separator
            }
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

    private var separator: some View {
        HStack(spacing: 14) {
            Rectangle().fill(PusulaTheme.border).frame(height: 1)
            Text(placement == .registration
                 ? LocalizedStringKey("auth.social.email_separator")
                 : LocalizedStringKey("auth.social.separator"))
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize()
            Rectangle().fill(PusulaTheme.border).frame(height: 1)
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

enum SocialAuthPlacement: Equatable {
    case signIn
    case registration
}

/// Equal prominence, shared geometry and official provider artwork.
/// Authentication still goes through SocialAuthManager; this is presentation only.
private struct SocialAuthProviderControls: View {
    @Environment(\.colorScheme) private var colorScheme
    @ScaledMetric(relativeTo: .body) private var controlHeight = PusulaTheme.controlHeight
    let isLoading: Bool
    let googleAction: () -> Void
    let appleAction: () -> Void

    private let cornerRadius = PusulaTheme.radius

    var body: some View {
        VStack(spacing: 12) {
            Button(action: googleAction) {
                HStack(spacing: 12) {
                    Image("GoogleSignInLogo")
                        .renderingMode(.original)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 20, height: 20)
                        .accessibilityHidden(true)
                    Text("auth.social.google_continue")
                        .font(.custom("GoogleSans-TextMedium", size: 16, relativeTo: .body))
                        .lineLimit(1)
                        .minimumScaleFactor(0.65)
                }
                .padding(.horizontal, 16)
                .frame(maxWidth: .infinity, minHeight: controlHeight)
                .foregroundStyle(Color(red: 31 / 255, green: 31 / 255, blue: 31 / 255))
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .strokeBorder(Color(red: 116 / 255, green: 119 / 255, blue: 117 / 255), lineWidth: 1)
                }
                .contentShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            }
            .buttonStyle(SocialAuthPressStyle())
            .accessibilityIdentifier("auth.google")

            NativeAppleSignInButton(darkMode: colorScheme == .dark,
                                    isEnabled: !isLoading,
                                    cornerRadius: cornerRadius,
                                    action: appleAction)
                .id(colorScheme)
                .frame(maxWidth: .infinity)
                .frame(height: controlHeight)
                .accessibilityIdentifier("auth.apple")
        }
        .disabled(isLoading)
        .opacity(isLoading ? 0.65 : 1)
    }
}

private struct SocialAuthPressStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.85 : 1)
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.985 : 1)
            .animation(reduceMotion ? nil : .easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

/// Apple's official button, with asynchronous server challenge before showing its authorization sheet.
private struct NativeAppleSignInButton: UIViewRepresentable {
    let darkMode: Bool
    let isEnabled: Bool
    let cornerRadius: CGFloat
    let action: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(action: action) }

    func makeUIView(context: Context) -> ASAuthorizationAppleIDButton {
        let button = ASAuthorizationAppleIDButton(type: .continue, style: darkMode ? .white : .black)
        button.cornerRadius = cornerRadius
        button.accessibilityIdentifier = "auth.apple"
        button.addTarget(context.coordinator, action: #selector(Coordinator.performAction), for: .touchUpInside)
        return button
    }

    func updateUIView(_ button: ASAuthorizationAppleIDButton, context: Context) {
        button.isEnabled = isEnabled
        button.cornerRadius = cornerRadius
        context.coordinator.action = action
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: ASAuthorizationAppleIDButton,
                      context: Context) -> CGSize? {
        CGSize(width: proposal.width ?? uiView.intrinsicContentSize.width,
               height: proposal.height ?? PusulaTheme.controlHeight)
    }

    final class Coordinator: NSObject {
        var action: () -> Void
        init(action: @escaping () -> Void) { self.action = action }
        @objc func performAction() { action() }
    }
}

#Preview("Sosyal giriş · Koyu") {
    SocialAuthProviderControls(isLoading: false, googleAction: {}, appleAction: {})
        .padding(20)
        .background(PusulaTheme.page)
        .preferredColorScheme(.dark)
}

#Preview("Sosyal giriş · Açık") {
    SocialAuthProviderControls(isLoading: false, googleAction: {}, appleAction: {})
        .padding(20)
        .background(PusulaTheme.page)
        .preferredColorScheme(.light)
}
