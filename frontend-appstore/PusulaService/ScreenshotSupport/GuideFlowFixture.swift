#if DEBUG
import SwiftUI

enum GuideFlowFixture {
    static var isEnabled: Bool {
#if targetEnvironment(simulator)
        AppStoreScreenshotMode.isEnabled && ProcessInfo.processInfo.environment["PUSULA_GUIDE_TEST"] == "1"
#else
        false
#endif
    }
    static let defaults = UserDefaults(suiteName: "Pusula.GuideUITests")!
    static var role: String { ProcessInfo.processInfo.environment["PUSULA_GUIDE_ROLE"] ?? "COMPANY_ADMIN" }
    static var scope: String { ProcessInfo.processInfo.environment["PUSULA_GUIDE_ACCOUNT"] ?? "fixture-user-a" }
    static var features: [String:Bool] { ["FINANCE_MODULE":true,"BASIC_INVENTORY":true,"COMPANY_DEBT_TRACKING":true,"BUSINESS_ASSETS":true] }
    static var tourTicket: FieldTicketDTO {
        // Explicitly selected by the UI test, never sent to a server.
        try! JSONDecoder().decode(FieldTicketDTO.self,from:Data(#"{"id":701,"customerName":"Çevrimdışı tur kaydı","description":"Yalnızca test ekranı","status":"ASSIGNED"}"#.utf8))
    }
    static var tourCustomer: CustomerDTO { CustomerDTO(id:701,name:"Çevrimdışı tur müşterisi",phone:nil,address:nil,coordinates:nil) }
}
struct GuideFlowHarness: View {
    @StateObject private var onboarding: OnboardingManager
    private let scenario = ProcessInfo.processInfo.environment["PUSULA_GUIDE_SCENARIO"] ?? "hub"
    init() {
        if ProcessInfo.processInfo.environment["PUSULA_GUIDE_RESET"] != "0" {
            GuideFlowFixture.defaults.removePersistentDomain(forName: "Pusula.GuideUITests")
        }
        _onboarding = StateObject(wrappedValue: OnboardingManager(defaults: GuideFlowFixture.defaults, syncCompletion: { _ in }))
    }
    var body: some View {
        NavigationStack {
            if scenario == "intro" {
                if onboarding.isShowingIntro { WelcomeOnboardingView(onboarding: onboarding) }
                else { Text("Tanıtım tamamlandı").accessibilityIdentifier("intro.finished") }
            } else if scenario == "coach" {
                ZStack {
                    Text("Salt okunur tur")
                    if onboarding.isTourActive { OnboardingCoachOverlay(onboarding: onboarding, targetFrames: [:]) }
                    else { Text("Tur tamamlandı").accessibilityIdentifier("tour.finished") }
                }.onAppear {
                    onboarding.completeIntro()
                    onboarding.restartRoleTour(role: GuideFlowFixture.role, accountIdentifier: "guide-fixture")
                }
            } else if scenario == "admin-entry" { SettingsView() }
            else if scenario == "technician-entry" { ProfileView() }
            else { GuideCenterDestination() }
        }
    }
}
#endif
