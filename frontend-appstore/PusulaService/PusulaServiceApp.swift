import SwiftUI
import GoogleSignIn

/// App entry point — initializes session and routes to login or main content.
@main
struct PusulaServiceApp: App {
    @UIApplicationDelegateAdaptor(PusulaAppDelegate.self) private var appDelegate
    @AppStorage(PusulaAppearance.storageKey) private var appearance = PusulaAppearance.system.rawValue
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Session restoration starts from ContentView's task, after the root
        // view is mounted. Screenshot mode never creates ContentView and thus
        // never restores an account or contacts the production API.
        
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
                .onAppear {
                    guard !AppStoreScreenshotMode.isEnabled else { return }
                    AppNavigation.shared.setSceneActive(scenePhase == .active)
                }
                .onOpenURL { url in
                    guard !AppStoreScreenshotMode.isEnabled else { return }
                    GIDSignIn.sharedInstance.handle(url)
                }
                .preferredColorScheme(PusulaAppearance(rawValue: appearance)?.colorScheme)
        }
        .onChange(of: scenePhase) { _, phase in
            guard !AppStoreScreenshotMode.isEnabled else { return }
            AppNavigation.shared.setSceneActive(phase == .active)
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
