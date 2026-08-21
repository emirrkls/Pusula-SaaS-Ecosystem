import SwiftUI

/// App entry point — initializes session and routes to login or main content.
@main
struct PusulaServiceApp: App {
    @UIApplicationDelegateAdaptor(PusulaAppDelegate.self) private var appDelegate
    @AppStorage(PusulaAppearance.storageKey) private var appearance = PusulaAppearance.system.rawValue
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Screenshot tests use deterministic local data and must never touch a
        // real account or production API.
        if !AppStoreScreenshotMode.isEnabled {
            SessionManager.shared.tryRestoreSession()
        }
        
        // Configure global appearance
        configureAppearance()
    }
    
    var body: some Scene {
        WindowGroup {
            Group {
                if AppStoreScreenshotMode.isEnabled {
                    AppStoreScreenshotView(scene: AppStoreScreenshotMode.scene)
                } else {
                    ContentView()
                }
            }
                .preferredColorScheme(PusulaAppearance(rawValue: appearance)?.colorScheme)
        }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active, !AppStoreScreenshotMode.isEnabled else { return }
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
