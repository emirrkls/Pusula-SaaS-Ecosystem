import SwiftUI

/// App entry point — initializes session and routes to login or main content.
@main
struct PusulaServiceApp: App {
    @UIApplicationDelegateAdaptor(PusulaAppDelegate.self) private var appDelegate
    @AppStorage(PusulaAppearance.storageKey) private var appearance = PusulaAppearance.system.rawValue
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Try to restore saved session from Keychain
        SessionManager.shared.tryRestoreSession()
        
        // Configure global appearance
        configureAppearance()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(PusulaAppearance(rawValue: appearance)?.colorScheme)
        }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            PushNotificationManager.shared.appDidBecomeActive()
        }
    }
    
    private func configureAppearance() {
        // Tab bar appearance
        let tabBarAppearance = UITabBarAppearance()
        tabBarAppearance.configureWithDefaultBackground()
        UITabBar.appearance().scrollEdgeAppearance = tabBarAppearance
        
        // Navigation bar
        let navAppearance = UINavigationBarAppearance()
        navAppearance.configureWithDefaultBackground()
        UINavigationBar.appearance().standardAppearance = navAppearance
        UINavigationBar.appearance().scrollEdgeAppearance = navAppearance
    }
}
