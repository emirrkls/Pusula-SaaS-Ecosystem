import AuthenticationServices
import Combine
import GoogleSignIn
import UIKit

/// Native provider UI only supplies assertions; the server verifies every identity.
@MainActor
final class SocialAuthManager: NSObject, ObservableObject, ASAuthorizationControllerDelegate,
    ASAuthorizationControllerPresentationContextProviding {
    private var continuation: CheckedContinuation<ASAuthorizationAppleIDCredential, Error>?
    private var authorizationController: ASAuthorizationController?
    private var presentationWindow: UIWindow?

    func signInWithGoogle() async throws -> AuthResponse {
        guard let clientID = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String,
              let serverID = Bundle.main.object(forInfoDictionaryKey: "GIDServerClientID") as? String,
              clientID.hasSuffix(".apps.googleusercontent.com"),
              serverID.hasSuffix(".apps.googleusercontent.com"),
              let controller = Self.presentingController else {
            throw SocialAuthError.configuration("Google ile giriş yapılandırması tamamlanmamış.")
        }
        let expectedScheme = clientID.split(separator: ".").reversed().joined(separator: ".")
        let urlTypes = Bundle.main.object(forInfoDictionaryKey: "CFBundleURLTypes") as? [[String: Any]] ?? []
        guard urlTypes.contains(where: { ($0["CFBundleURLSchemes"] as? [String])?.contains(expectedScheme) == true }) else {
            throw SocialAuthError.configuration("Google giriş dönüş adresi eksik.")
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID, serverClientID: serverID)
        let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: controller)
        guard let token = result.user.idToken?.tokenString else {
            throw SocialAuthError.configuration("Google hesabı doğrulanamadı.")
        }
        return try await AuthService.loginGoogle(idToken: token)
    }

    func signInWithApple() async throws -> AuthResponse {
        guard continuation == nil, let window = Self.activeWindow else {
            throw SocialAuthError.configuration("Giriş ekranı hazırlanamadı. Tekrar deneyin.")
        }
        let challenge = try await AuthService.appleChallenge()
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = challenge.nonce
        request.state = challenge.id
        presentationWindow = window
        defer { presentationWindow = nil }
        let credential: ASAuthorizationAppleIDCredential = try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            self.authorizationController = controller
            controller.performRequests()
        }
        guard credential.state == challenge.id,
              let tokenData = credential.identityToken, let token = String(data: tokenData, encoding: .utf8),
              let codeData = credential.authorizationCode, let code = String(data: codeData, encoding: .utf8) else {
            throw SocialAuthError.configuration("Apple hesabı doğrulanamadı. Tekrar deneyin.")
        }
        let name = credential.fullName.map { PersonNameComponentsFormatter().string(from: $0) }
        return try await AuthService.loginApple(idToken: token, authorizationCode: code,
                                               challengeId: challenge.id, fullName: name)
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential else {
            finish(.failure(SocialAuthError.configuration("Apple hesabı doğrulanamadı.")))
            return
        }
        finish(.success(credential))
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        finish(.failure(error))
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        // Captured before the authorization starts, rather than an unattached empty window.
        presentationWindow ?? Self.activeWindow ?? ASPresentationAnchor()
    }

    private func finish(_ result: Result<ASAuthorizationAppleIDCredential, Error>) {
        let pending = continuation
        continuation = nil
        authorizationController = nil
        pending?.resume(with: result)
    }

    static func isCancellation(_ error: Error) -> Bool {
        let nsError = error as NSError
        return (nsError.domain == ASAuthorizationError.errorDomain && nsError.code == ASAuthorizationError.canceled.rawValue)
            || (nsError.domain == kGIDSignInErrorDomain && nsError.code == GIDSignInErrorCode.canceled.rawValue)
    }

    private static var activeWindow: UIWindow? {
        UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .filter { $0.activationState == .foregroundActive }
            .flatMap(\.windows).first(where: \.isKeyWindow)
    }

    private static var presentingController: UIViewController? {
        var controller = activeWindow?.rootViewController
        while let presented = controller?.presentedViewController { controller = presented }
        return controller
    }
}

private enum SocialAuthError: LocalizedError {
    case configuration(String)
    var errorDescription: String? {
        switch self { case .configuration(let message): return message }
    }
}
