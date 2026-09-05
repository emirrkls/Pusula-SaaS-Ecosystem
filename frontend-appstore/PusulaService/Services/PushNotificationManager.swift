import UIKit
import UserNotifications

@MainActor
final class PushNotificationManager: NSObject, UNUserNotificationCenterDelegate {
    static let shared = PushNotificationManager()

    private let tokenDefaultsKey = "pusula.apns.device-token"

    private var isEnabled: Bool {
        Bundle.main.object(forInfoDictionaryKey: "PusulaPushNotificationsEnabled") as? Bool ?? false
    }

    func configure() {
        UNUserNotificationCenter.current().delegate = self
    }

    func sessionDidAuthenticate() {
        guard isEnabled else { return }
        Task { await requestAuthorizationAndRegister() }
    }

    func appDidBecomeActive() {
        guard isEnabled, SessionManager.shared.isAuthenticated else { return }
        if let token = storedToken {
            Task { await registerWithBackend(token: token) }
        } else {
            UIApplication.shared.registerForRemoteNotifications()
        }
    }

    func didRegisterForRemoteNotifications(deviceToken: Data) {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        UserDefaults.standard.set(token, forKey: tokenDefaultsKey)
        guard SessionManager.shared.isAuthenticated else { return }
        Task { await registerWithBackend(token: token) }
    }

    func didFailToRegisterForRemoteNotifications(error: Error) {
#if DEBUG
        print("APNs registration failed: \(error.localizedDescription)")
#endif
    }

    func unregisterCurrentDevice() async {
        guard isEnabled, let token = storedToken else { return }
        let request = PushDeviceRequest(
            token: token,
            platform: "IOS",
            environment: pushEnvironment,
            bundleId: Bundle.main.bundleIdentifier ?? "com.pusula.service"
        )
        do {
            let _: EmptyResponse = try await NetworkManager.shared.post(
                "/api/push-devices/unregister",
                body: request
            )
        } catch {
#if DEBUG
            print("Push device unregister failed: \(error.localizedDescription)")
#endif
        }
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        await MainActor.run {
            NotificationCenter.default.post(name: .pusulaAdminNotificationReceived, object: nil)
        }
        return [.banner, .sound, .badge]
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        let userInfo = response.notification.request.content.userInfo
        let ticketId = (userInfo["ticketId"] as? Int)
            ?? (userInfo["ticketId"] as? String).flatMap(Int.init)
        guard let ticketId else { return }
        await MainActor.run {
            AppNavigation.shared.openTicket(id: ticketId)
        }
    }

    private var storedToken: String? {
        UserDefaults.standard.string(forKey: tokenDefaultsKey)
    }

    private var pushEnvironment: String {
#if DEBUG
        "SANDBOX"
#else
        "PRODUCTION"
#endif
    }

    private func requestAuthorizationAndRegister() async {
        do {
            let granted = try await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .badge, .sound])
            guard granted else { return }
            UIApplication.shared.registerForRemoteNotifications()
        } catch {
#if DEBUG
            print("Notification authorization failed: \(error.localizedDescription)")
#endif
        }
    }

    private func registerWithBackend(token: String) async {
        let request = PushDeviceRequest(
            token: token,
            platform: "IOS",
            environment: pushEnvironment,
            bundleId: Bundle.main.bundleIdentifier ?? "com.pusula.service"
        )
        do {
            let _: EmptyResponse = try await NetworkManager.shared.post(
                "/api/push-devices/register",
                body: request
            )
        } catch {
#if DEBUG
            print("Push device register failed: \(error.localizedDescription)")
#endif
        }
    }
}

extension Notification.Name {
    static let pusulaAdminNotificationReceived = Notification.Name("pusulaAdminNotificationReceived")
}

private struct PushDeviceRequest: Encodable {
    let token: String
    let platform: String
    let environment: String
    let bundleId: String
}

final class PusulaAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        PushNotificationManager.shared.configure()
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        PushNotificationManager.shared.didRegisterForRemoteNotifications(deviceToken: deviceToken)
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        PushNotificationManager.shared.didFailToRegisterForRemoteNotifications(error: error)
    }
}
