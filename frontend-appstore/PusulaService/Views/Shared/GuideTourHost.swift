import SwiftUI

@MainActor final class GuideTourRuntime: ObservableObject {
    static let shared = GuideTourRuntime()
    @Published private(set) var activeLease: UUID?
    var isActive: Bool { activeLease != nil }
    func begin() -> UUID? {
        guard activeLease == nil else { return nil }
        let lease = GuideReadOnlyGate.shared.begin(); activeLease = lease; return lease
    }
    func end(_ lease: UUID?) {
        guard let lease else { return }
        GuideReadOnlyGate.shared.end(lease)
        if activeLease == lease { activeLease = nil }
    }
}

private struct GuideTourModeKey: EnvironmentKey { static let defaultValue = false }
private struct GuideTourFocusKey: EnvironmentKey { static let defaultValue: String? = nil }
extension EnvironmentValues {
    var guideTourMode: Bool { get { self[GuideTourModeKey.self] } set { self[GuideTourModeKey.self] = newValue } }
    var guideTourFocus: String? { get { self[GuideTourFocusKey.self] } set { self[GuideTourFocusKey.self] = newValue } }
}
struct GuideTourFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue(), uniquingKeysWith: { _, latest in latest }) }
}
extension View {
    func guideTourTarget(_ key: String) -> some View {
        modifier(GuideTourTargetModifier(key:key))
    }
    func guideTourScrollable() -> some View { modifier(GuideTourRevealModifier()) }
}
private struct GuideTourTargetModifier: ViewModifier {
    let key:String
    @Environment(\.guideTourMode) private var enabled
    @ViewBuilder func body(content:Content)->some View {
        if enabled {
            content.id(key).background {GeometryReader {proxy in
                Color.clear.preference(key:GuideTourFrames.self,value:[key:proxy.frame(in:.global)])
            }}
        } else {content}
    }
}
private struct GuideTourRevealModifier: ViewModifier {
    @Environment(\.guideTourMode) private var enabled
    @Environment(\.guideTourFocus) private var focus
    @State private var revealed: String?
    @State private var available: Set<String> = []
    @ViewBuilder func body(content: Content) -> some View {
        if enabled {
        ScrollViewReader { proxy in
            content.onChange(of:focus) { _, value in
                    revealed=nil
                    if let value,available.contains(value) { proxy.scrollTo(value,anchor:.center);revealed=value }
                }
                .onPreferenceChange(GuideTourFrames.self) { frames in
                    available=Set(frames.keys)
                    guard let focus, focus != revealed, frames[focus] != nil else { return }
                    revealed=focus
                    // Scroll only to an explicitly registered read-only target;
                    // never synthesize a tap or select a business record.
                    proxy.scrollTo(focus,anchor:.center)
                }
        }
        } else {content}
    }
}

/// Uses the production views, not screenshots or a look-alike dashboard.
/// The backdrop is touch/VoiceOver inert; a transport lease rejects writes.
struct GuideTourHost: View {
    let topic: GuideTopic
    @ObservedObject var progress: GuideProgressStore
    let role: String
    let features: [String: Bool]
    let lease: UUID
    @State private var cursor: GuideCursor
    @State private var frames: [String: CGRect] = [:]
    @State private var ticket: FieldTicketDTO?
    @State private var customer: CustomerDTO?
    @State private var tickets: [FieldTicketDTO] = []
    @State private var customers: [CustomerDTO] = []
    @State private var partsTotal: Decimal?
    @State private var loading = false
    @State private var contextRequest: UUID?
    @State private var contextError: String?
    @State private var choicesLoaded = false
    @State private var customerChoicesLoaded = false
    @ObservedObject private var session = SessionManager.shared
    @Environment(\.dismiss) private var dismiss
    private let account: String
    init(topic:GuideTopic,progress:GuideProgressStore,role:String,features:[String:Bool],lease:UUID) {
        self.topic=topic;self.progress=progress;self.role=role;self.features=features;self.lease=lease
        account=SessionManager.shared.onboardingAccountIdentifier
        _cursor=State(initialValue:GuideCursor(count:topic.steps.count,index:progress.record(topic).startIndex(topic)))
    }
    private var step: GuideLessonStep { topic.steps[cursor.index] }
    private var route: GuideTourRoute { step.tour }
    private var contextLoadKey: String {
        "\(route.screen.rawValue):\(ticket?.id ?? 0):\(customer?.id ?? 0)"
    }
    private var locked: Bool { topic.requiredFeature.map { features[$0] != true } ?? false }
    private var needsContext: Bool {
        route.screen.needsTicket(role:role) && ticket == nil || route.screen.needsCustomer(role:role) && customer == nil
            || route.screen == .collection && partsTotal == nil
    }
    var body: some View {
        VStack(spacing:0) {
            HStack(alignment:.center,spacing:10) {
                Label("Salt okunur tur",systemImage:"lock.shield.fill").font(.caption.weight(.semibold)).foregroundStyle(PusulaTheme.accent)
                    .accessibilityIdentifier("guide.tour")
                Spacer()
                Button("Kapat") { close() }.font(.subheadline.weight(.semibold)).accessibilityIdentifier("guide.close")
            }.padding(.horizontal,18).padding(.vertical,12).background(PusulaTheme.surface)
            if locked {
                ContentUnavailableView("Paket veya yetki gerekli",systemImage:"lock",description:Text("Bu özellik için mevcut yetkileriniz değişmez. Tur bir paket veya erişim hakkı vermez."))
            } else if needsContext { contextPicker }
            else {
                ZStack {
                    NavigationStack { actualScreen.guideTourTarget("content") }
                        .id(route.screen.rawValue + ":" + String(ticket?.id ?? 0) + ":" + String(customer?.id ?? 0))
                        .environment(\.guideTourMode,true).environment(\.guideTourFocus,route.target)
                        .allowsHitTesting(false).accessibilityHidden(true)
                    GeometryReader { proxy in
                        if let rect=visibleTarget(in:proxy) {
                            Color.black.opacity(0.16).overlay {
                                RoundedRectangle(cornerRadius:14).frame(width:rect.width,height:rect.height)
                                    .position(x:rect.midX,y:rect.midY).blendMode(.destinationOut)
                            }.compositingGroup()
                            RoundedRectangle(cornerRadius:14).stroke(PusulaTheme.brandCyan,lineWidth:3)
                                .frame(width:rect.width,height:rect.height).position(x:rect.midX,y:rect.midY)
                        }
                    }.allowsHitTesting(false).accessibilityHidden(true)
                }.onPreferenceChange(GuideTourFrames.self) { frames=$0 }
            }
        }
        .safeAreaInset(edge:.bottom,spacing:0) { coach }
        .background(PusulaTheme.page)
        .interactiveDismissDisabled()
        .task(id:contextLoadKey) { await loadContext() }
        .onChange(of:session.onboardingAccountIdentifier) { _, value in if value != account { close() } }
        .onChange(of:session.authenticatedSnapshot?.revision) { _, _ in close() }
    }
    @ViewBuilder private var actualScreen: some View {
        switch route.screen {
        case .home: if role == "TECHNICIAN" { TicketListView(isNavigationBlocked:true) } else { AdminDashboardView() }
        case .customers: CustomerView()
        case .customerForm: CustomerEditorSheet(customer:nil,onSaved:{})
        case .customerDetail: if let customer { CustomerDetailSheet(customer:customer) }
        case .customerContact:
            if role == "TECHNICIAN",let ticket { TicketDetailView(ticket:ticket,isAdmin:false,onComplete:{}) }
            else if let customer { CustomerDetailSheet(customer:customer) }
        case .tickets: TicketListView(isNavigationBlocked:true)
        case .ticketCreate: CreateTicketSheet(customers:customers,technicians:[],onCreated:{})
        case .ticketDetail: if let ticket { TicketDetailView(ticket:ticket,isAdmin:role != "TECHNICIAN",onComplete:{}) }
        case .reschedule: if let ticket { RescheduleTicketSheet(ticket:ticket,onSaved:{ _ in }) }
        case .photos: if let ticket { ServicePhotoView(ticketId:ticket.id) }
        case .photosArchive: ServiceQualityView()
        case .signature: if let ticket { SignatureView(ticketId:ticket.id) }
        case .parts: InventoryPartPickerView(onItemSelected:{ _,_,_ in })
        case .collection: if let ticket,let partsTotal { CollectionView(ticket:ticket,partsTotal:partsTotal,onComplete:{ _ in }) }
        case .catalog: CatalogView()
        case .proposals: ProposalView()
        case .finance: FinanceDailyTab().navigationTitle("Günlük İşlemler")
        case .expenses: AddExpenseSheet(onSaved:{})
        case .accounts: FinanceAccountsTab().navigationTitle("Cari Alacaklar")
        case .organizations: AccountPartiesView()
        case .debts: CompanyDebtsView()
        case .fixedExpenses: FixedExpensesView()
        case .assets: BusinessAssetsView()
        case .reports: FinanceReportsTab().navigationTitle("Aylık Raporlar")
        case .profit: ProfitAnalysisView()
        case .team: SettingsView(guideSection:0)
        case .vehicles: SettingsView(guideSection:1)
        case .company: SettingsView(guideSection:2)
        case .radar: FieldRadarView()
        case .network: ServiceNetworkView()
        case .notifications: if role != "TECHNICIAN" { AdminNotificationsView(onChanged:{}) } else { TicketListView(isNavigationBlocked:true) }
        case .profile: ProfileView()
        case .plans: if role != "TECHNICIAN" { PlanUpgradeView() } else { ProfileView() }
        case .login: LoginView()
        case .register: RegisterView()
        case .desktopPassword: DesktopPasswordSetupView(username:session.desktopLoginUsername,savePassword:{ _ in throw GuideReadOnlyViolation() },onComplete:{},onCancel:{})
        case .support: SupportCenterView()
        case .supportForm: SupportCenterView(tourComposer:true)
        case .diagnostics: ConnectionDiagnosticsView()
        }
    }
    private var contextPicker: some View {
        ScrollView {
            VStack(alignment:.leading,spacing:16) {
                Label("Kayıt seçin · yalnızca görüntülenecek",systemImage:"hand.tap").font(.headline)
                Text("Tur hiçbir kaydı değiştirmez. İncelemek istediğiniz mevcut kaydı siz seçin.").font(.subheadline).foregroundStyle(.secondary)
                if loading { ProgressView() }
                if let contextError { Text(contextError).foregroundStyle(.orange);Button("Tekrar Dene") { Task { await loadContext(force:true) } } }
                if route.screen.needsCustomer(role:role) {
                    ForEach(customers) { value in
                        Button { customer=value } label: { Label(value.name,systemImage:"person.crop.circle").frame(maxWidth:.infinity,alignment:.leading).padding() }.buttonStyle(.bordered)
                    }
                } else if ticket == nil {
                    ForEach(tickets) { value in
                        Button { ticket=value;partsTotal=nil } label: {
                            VStack(alignment:.leading,spacing:4) {Text("#\(value.id) · \(value.customerName ?? "Servis fişi")").font(.headline);Text(value.description ?? "").font(.subheadline).foregroundStyle(.secondary)}.frame(maxWidth:.infinity,alignment:.leading).padding()
                        }.buttonStyle(.bordered).accessibilityIdentifier("guide.context.ticket.\(value.id)")
                    }
                }
                if (route.screen.needsCustomer(role:role) ? customerChoicesLoaded : choicesLoaded) && !loading && contextError == nil && (route.screen.needsCustomer(role:role) ? customers.isEmpty : tickets.isEmpty) {
                    ContentUnavailableView("Uygun kayıt bulunamadı",systemImage:"tray",description:Text("Bu tur mevcut bir kayıt gerektirir. Tur için yeni kayıt oluşturmanız gerekmez; başka bir konu seçebilirsiniz."))
                }
            }.padding(20)
        }
        .accessibilityIdentifier("guide.context")
    }
    private var coach: some View {
        VStack(alignment:.leading,spacing:12) {
            HStack {Text(topic.title).font(.caption.weight(.semibold)).foregroundStyle(.secondary);Spacer();Text("\(cursor.index+1) / \(cursor.count)").font(.caption.monospacedDigit()).accessibilityIdentifier("guide.step.progress")}
            Text(topic.locations["ios"] ?? "").font(.caption2).foregroundStyle(PusulaTheme.accent).accessibilityIdentifier("guide.tour.screen").accessibilityValue(route.screen.rawValue)
            ScrollView {
                VStack(alignment:.leading,spacing:8) {
                    Text(step.title).font(.headline).accessibilityIdentifier("guide.step.title")
                    Text(step.body).font(.subheadline).foregroundStyle(.secondary).fixedSize(horizontal:false,vertical:true)
                }.frame(maxWidth:.infinity,alignment:.leading)
            }.frame(maxHeight:170)
            ViewThatFits(in:.horizontal) {
                HStack {backButton;Spacer(minLength:12);nextButton}
                VStack(spacing:8) {nextButton.frame(maxWidth:.infinity);backButton.frame(maxWidth:.infinity)}
            }
        }.padding(18).background(.regularMaterial)
    }
    private var backButton: some View { Button("Geri") {cursor.back();save()}.buttonStyle(.bordered).disabled(cursor.index==0).accessibilityIdentifier("guide.back") }
    private var nextButton: some View {
        Button(cursor.isLast ? "Bitir" : "Devam") {
            let completed=cursor.advance();progress.save(topic,index:cursor.index,completed:completed)
            if completed { close() }
        }.buttonStyle(.borderedProminent).disabled(needsContext || locked).accessibilityIdentifier("guide.next")
    }
    private func visibleTarget(in proxy:GeometryProxy)->CGRect? {
        guard let target=frames[route.target] else{return nil}
        let origin=proxy.frame(in:.global)
        let rect=target.offsetBy(dx:-origin.minX,dy:-origin.minY).intersection(CGRect(origin:.zero,size:proxy.size).insetBy(dx:6,dy:6))
        guard !rect.isNull,rect.width.isFinite,rect.height.isFinite,rect.width>2,rect.height>2 else{return nil}
        return rect
    }
    private func save() { progress.save(topic,index:cursor.index) }
    // The presenting view releases the lease in fullScreenCover.onDismiss,
    // after the transition. Push navigation must not race the closing cover.
    private func close() { save();dismiss() }
    @MainActor private func loadContext(force:Bool=false) async {
        guard !locked else{return}
        // The lifecycle key includes the chosen record. Start from the updated
        // view state, not a Task capturing the pre-selection SwiftUI value.
        // A cancelled older route must not clear a newer load or apply its data.
        let request=UUID()
        contextRequest=request
        loading=false
        contextError=nil
        defer {if contextRequest==request {loading=false}}
#if DEBUG
        if GuideFlowFixture.isEnabled {
            tickets=[GuideFlowFixture.tourTicket];customers=[GuideFlowFixture.tourCustomer]
            choicesLoaded=true;customerChoicesLoaded=true
            if route.screen == .collection,ticket != nil { partsTotal=0 }
            return
        }
#endif
        guard let snapshot=session.authenticatedSnapshot else {
            contextError="Oturum bilgisi doğrulanamadı. Turdan çıkıp yeniden deneyin.";return
        }
        if route.screen.needsTicket(role:role) && ticket == nil && (!choicesLoaded || force) {
            loading=true
            do {
                let values:[FieldTicketDTO]=try await NetworkManager.shared.request(.GET,path:role == "TECHNICIAN" ? "/api/tickets/my-assigned" : "/api/tickets",authTokenOverride:snapshot.token)
                guard contextRequest==request,session.isCurrent(snapshot),!Task.isCancelled else{return}
                tickets=values.sorted{$0.id>$1.id};choicesLoaded=true
            } catch {if contextRequest==request,session.isCurrent(snapshot),!LoadCancellationPolicy.isCancellation(error,taskIsCancelled:Task.isCancelled) {contextError="Servis fişleri yüklenemedi. Kayıt seçimi yapılmadı."}}
        } else if (route.screen.needsCustomer(role:role) && customer == nil || route.screen == .ticketCreate) && (!customerChoicesLoaded || force) {
            loading=true
            do {let values=try await CustomerService.getCustomers(authToken:snapshot.token);guard contextRequest==request,session.isCurrent(snapshot),!Task.isCancelled else{return};customers=values.filter{$0.id != nil};customerChoicesLoaded=true}
            catch {if contextRequest==request,session.isCurrent(snapshot),!LoadCancellationPolicy.isCancellation(error,taskIsCancelled:Task.isCancelled) {contextError="Müşteriler yüklenemedi. Mevcut kayıtlar korunuyor."}}
        } else if route.screen == .collection,let ticket,partsTotal == nil {
            loading=true
            do {
                let values=try await TicketService.getUsedParts(ticketId:ticket.id,authToken:snapshot.token)
                guard contextRequest==request,session.isCurrent(snapshot),!Task.isCancelled else{return}
                guard values.allSatisfy({$0.totalPrice.isFinite}) else {throw NetworkError.invalidResponse}
                partsTotal=values.reduce(Decimal.zero) {$0+Decimal($1.totalPrice)}
            } catch {if contextRequest==request,session.isCurrent(snapshot),!LoadCancellationPolicy.isCancellation(error,taskIsCancelled:Task.isCancelled) {contextError="Parça toplamı yüklenemedi; sıfır kabul edilmedi."}}
        }
    }
}
