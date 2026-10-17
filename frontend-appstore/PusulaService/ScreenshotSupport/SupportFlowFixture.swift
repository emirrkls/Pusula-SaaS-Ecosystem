#if DEBUG
import SwiftUI
import Foundation

/// Never authenticates, writes Keychain, calls a provider or contacts an API.
@MainActor final class SupportFlowFixture: ObservableObject {
    static var isEnabled:Bool {
#if targetEnvironment(simulator)
        AppStoreScreenshotMode.isEnabled && ProcessInfo.processInfo.environment["PUSULA_SUPPORT_FIXTURE"] != nil
#else
        false
#endif
    }
    private let scenario=ProcessInfo.processInfo.environment["PUSULA_SUPPORT_FIXTURE"] ?? "normal"
    let resetDraft=ProcessInfo.processInfo.environment["PUSULA_SUPPORT_RESET_DRAFT"] == "1"
    // A synthetic subject produces the same protected partition after a UI-test relaunch.
    let snapshot=AuthenticatedSessionSnapshot(token:"offline.eyJzdWIiOiJvZmZsaW5lLXN1cHBvcnQtZml4dHVyZSJ9.fixture",companyId:0,revision:UUID())
    var draftKey:String {SupportService.accountKey(snapshot.token,company:0)!}
    var authenticated:Bool {scenario != "anonymous"}
    @Published private(set) var attempts=0
    @Published private(set) var writes=0
    @Published private(set) var reads=0
    private var saved:[String:SupportDraft]=[:]
    func contact() async throws -> SupportContact {SupportContact(enabled:true,email:SupportPresentation.fallbackEmail,website:"https://example.invalid")}
    func list() async throws -> SupportPageDTO {
        reads+=1
        if scenario=="refresh-failure" && reads>1 {throw URLError(.timedOut)}
        return SupportPageDTO(items:scenario=="refresh-failure" ? [ticket]:[],page:0,hasNext:false,unreadCount:0)
    }
    func send(_ draft:SupportDraft) async throws -> SupportDetailDTO {
        attempts+=1
        if saved[draft.clientKey]==nil {saved[draft.clientKey]=draft;writes+=1}
        try await Task.sleep(nanoseconds:200_000_000)
        if scenario=="lost-response" && attempts==1 {throw URLError(.timedOut)}
        return detail()
    }
    private var ticket:SupportTicketDTO {SupportTicketDTO(id:1,number:"DST-000001",subject:"Synthetic support request",category:"BUG",status:"IN_PROGRESS",priority:"NORMAL",platform:"IOS",appVersion:"local",updatedAt:"2026-10-17T12:00:00Z",unread:false)}
    func detail()->SupportDetailDTO {SupportDetailDTO(ticket:ticket,messages:[SupportMessageDTO(id:1,body:saved.values.first?.body ?? "Synthetic support conversation",staff:false,internalNote:false,createdAt:"2026-10-17T12:00:00Z",attachments:[])],nextBefore:nil)}
}

@MainActor struct SupportFlowHarness:View {
    @StateObject private var fixture=SupportFlowFixture()
    var body:some View {
        NavigationStack {
            SupportCenterView(flowFixture:fixture).safeAreaInset(edge:.bottom) {
                Text("Offline support: \(fixture.attempts) attempts, \(fixture.writes) writes")
                    .font(.caption2).padding(6).accessibilityIdentifier("support.fixture.counts")
                    .accessibilityValue("\(fixture.attempts):\(fixture.writes):\(SessionManager.shared.isAuthenticated)")
            }
        }
    }
}
#endif
