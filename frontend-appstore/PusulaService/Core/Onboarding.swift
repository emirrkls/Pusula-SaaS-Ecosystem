import Foundation
import SwiftUI

@MainActor
final class OnboardingManager: ObservableObject {
    static let shared = OnboardingManager()

    static let introVersion = 1
    static let roleTourVersion = 2

    @Published var isShowingIntro: Bool
    @Published private(set) var activeRole: String?
    @Published private(set) var currentStepIndex = 0

    private let defaults: UserDefaults
    private var activeAccountIdentifier: String?
    private var activeSteps: [OnboardingStep] = []
    private let syncCompletion: @MainActor (Int) async -> Void

    init(defaults: UserDefaults = .standard, syncCompletion: @escaping @MainActor (Int) async -> Void = { version in
        if let synced = try? await AuthService.updateOnboardingVersion(version) { SessionManager.shared.onboardingVersion = synced }
    }) {
        self.syncCompletion = syncCompletion
        self.defaults = defaults
        isShowingIntro = defaults.integer(forKey: Self.introStorageKey) < Self.introVersion
    }

    var currentStep: OnboardingStep? {
        guard activeRole != nil, activeSteps.indices.contains(currentStepIndex) else { return nil }
        return activeSteps[currentStepIndex]
    }

    var stepCount: Int {
        activeSteps.count
    }

    var isTourActive: Bool {
        currentStep != nil
    }

    func completeIntro() {
        defaults.set(Self.introVersion, forKey: Self.introStorageKey)
        withAnimation(.easeInOut(duration: 0.25)) {
            isShowingIntro = false
        }
    }

    func startRoleTourIfNeeded(role: String, accountIdentifier: String, serverVersion: Int) {
        let localVersion = defaults.integer(
            forKey: roleStorageKey(role, accountIdentifier: accountIdentifier)
        )
        guard !isShowingIntro,
              activeRole == nil,
              supports(role: role),
              max(localVersion, serverVersion) < Self.roleTourVersion else { return }
        startRoleTour(role: role, accountIdentifier: accountIdentifier)
    }

    func restartRoleTour(role: String, accountIdentifier: String) {
        guard supports(role: role) else { return }
        startRoleTour(role: role, accountIdentifier: accountIdentifier)
    }

    func advance() {
        guard activeRole != nil else { return }
        let nextIndex = currentStepIndex + 1
        if nextIndex < activeSteps.count {
            withAnimation(.easeInOut(duration: 0.22)) {
                currentStepIndex = nextIndex
            }
        } else {
            completeRoleTour()
        }
    }

    func goBack() {
        guard currentStepIndex > 0 else { return }
        withAnimation(.easeInOut(duration: 0.22)) {
            currentStepIndex -= 1
        }
    }

    func completeRoleTour() {
        guard let activeRole else { return }
        if let activeAccountIdentifier {
            defaults.set(
                Self.roleTourVersion,
                forKey: roleStorageKey(activeRole, accountIdentifier: activeAccountIdentifier)
            )
        }
        Task { await syncCompletion(Self.roleTourVersion) }
        dismissRoleTour()
    }

    func cancelRoleTour() {
        dismissRoleTour()
    }

    func steps(for role: String) -> [OnboardingStep] {
        if role == "TECHNICIAN" {
            return Self.technicianSteps
        }
        if role == "COMPANY_ADMIN" || role == "SUPER_ADMIN" {
            return Self.adminSteps
        }
        return []
    }

    private func startRoleTour(role: String, accountIdentifier: String) {
        currentStepIndex = 0
        activeSteps = steps(for: role).filter { step in
            step.target != .financeContent || SessionManager.shared.features["FINANCE_MODULE"] == true
        }
        activeAccountIdentifier = accountIdentifier
        withAnimation(.easeInOut(duration: 0.25)) {
            activeRole = role
        }
    }

    private func dismissRoleTour() {
        withAnimation(.easeInOut(duration: 0.25)) {
            activeRole = nil
            currentStepIndex = 0
        }
        activeAccountIdentifier = nil
        activeSteps = []
    }

    private func supports(role: String) -> Bool {
        role == "TECHNICIAN" || role == "COMPANY_ADMIN" || role == "SUPER_ADMIN"
    }

    private func roleStorageKey(_ role: String, accountIdentifier: String) -> String {
        "pusula.onboarding.role.\(role).\(accountIdentifier).version"
    }

    private static let introStorageKey = "pusula.onboarding.intro.version"

    private static let adminSteps: [OnboardingStep] = [
        .init(
            title: "İşletmenin nabzı",
            detail: "Aylık ciro, net kâr, bekleyen alacak, aktif işler, stok değeri ve paket kullanım limitlerini Genel Bakış ekranında izleyin.",
            icon: "chart.bar.fill",
            target: .overviewContent,
            destination: .admin(.overview)
        ),
        .init(
            title: "Operasyonu yönetin",
            detail: "İş emirlerini durum ve tarihe göre filtreleyin; atama bekleyen işleri, teknisyenleri ve planlanan servis saatlerini takip edin.",
            icon: "list.clipboard.fill",
            target: .ticketSummary,
            destination: .admin(.operations)
        ),
        .init(
            title: "Yeni servis kaydı",
            detail: "Servis Fişi Oluştur düğmesinden mevcut müşteriyi arayın veya yeni müşteri kaydedin; tarih, saat ve teknisyen bilgisini aynı akışta belirleyin.",
            icon: "plus.circle.fill",
            target: .createTicketAction,
            destination: .admin(.operations)
        ),
        .init(
            title: "Diğer iş araçları",
            detail: "Alt menüdeki Diğer düğmesine dokunduğunuzda Müşteriler, Teklifler, Stok ve Servis Görselleri modülleri açılır. Bu adım yalnızca menüyü tanıtır; sizin yerinize seçim yapmaz.",
            icon: "square.grid.2x2.fill",
            target: .information,
            destination: .admin(.overview)
        ),
        .init(
            title: "Finans takibi",
            detail: "Günlük gelir ve giderleri, cari tahsilatları, işletme borçlarını, sabit giderleri, raporları ve demirbaşları bu ekrandan yönetin.",
            icon: "turkishlirasign.circle.fill",
            target: .financeContent,
            destination: .admin(.finance)
        ),
        .init(
            title: "Ekip ve ayarlar",
            detail: "Ekip kullanıcılarını, araçları, firma bilgilerini ve hesap tercihlerini üstteki bölümler arasında geçiş yaparak yönetin.",
            icon: "gearshape.fill",
            target: .settingsSections,
            destination: .admin(.account)
        )
    ]

    private static let technicianSteps: [OnboardingStep] = [
        .init(
            title: "Atanan işleriniz",
            detail: "Bugünün çağrılarını, ileri tarihli işleri ve kapanan ya da iptal edilen servisleri İşlerim ekranından takip edin.",
            icon: "wrench.and.screwdriver.fill",
            target: .ticketSummary,
            destination: .technician(.jobs)
        ),
        .init(
            title: "Duruma göre filtreleyin",
            detail: "Üstteki filtrelerden doğru iş grubunu seçin. Ekrandaki iş emri sayısı seçtiğiniz kategoriye göre güncellenir.",
            icon: "line.3.horizontal.decrease.circle.fill",
            target: .ticketFilters,
            destination: .technician(.jobs)
        ),
        .init(
            title: "Servis fişini açın",
            detail: "Listeden bir servis kartına dokunduğunuzda müşteri, adres, planlanan saat, servis açıklaması ve işlem geçmişi açılır. Tur sizin yerinize rastgele bir fiş açmaz.",
            icon: "doc.text.magnifyingglass",
            target: .information,
            destination: .technician(.jobs)
        ),
        .init(
            title: "Fiş içinde yapabilecekleriniz",
            detail: "Açtığınız fişte işe başlayabilir, parça ve özel satış fiyatı ekleyebilir; teknisyen notu, fotoğraf, müşteri imzası, garanti ve tahsilat bilgilerini tamamlayabilirsiniz.",
            icon: "checklist.checked",
            target: .information,
            destination: .technician(.jobs)
        ),
        .init(
            title: "Hesabınız",
            detail: "Profilinizi, paket bilgilerinizi, görünüm tercihini, kategorilere ayrılmış Uygulama Rehberini ve yasal bağlantıları burada bulabilirsiniz.",
            icon: "person.crop.circle.fill",
            target: .profileContent,
            destination: .technician(.account)
        )
    ]
}

struct OnboardingStep: Identifiable {
    let id = UUID()
    let title: String
    let detail: String
    let icon: String
    let target: OnboardingTarget
    let destination: OnboardingDestination
}

enum OnboardingDestination {
    case admin(AdminTab)
    case technician(TechnicianTab)
}

enum OnboardingTarget: Hashable {
    case overviewContent
    case ticketSummary
    case ticketFilters
    case createTicketAction
    case financeContent
    case settingsSections
    case profileContent
    case information
}

struct OnboardingTargetFramePreferenceKey: PreferenceKey {
    static let defaultValue: [OnboardingTarget: CGRect] = [:]

    static func reduce(
        value: inout [OnboardingTarget: CGRect],
        nextValue: () -> [OnboardingTarget: CGRect]
    ) {
        value.merge(nextValue(), uniquingKeysWith: { _, new in new })
    }
}

extension View {
    func onboardingTarget(_ target: OnboardingTarget) -> some View {
        background {
            GeometryReader { proxy in
                Color.clear.preference(
                    key: OnboardingTargetFramePreferenceKey.self,
                    value: [target: proxy.frame(in: .global)]
                )
            }
        }
    }
}

enum TechnicianTab: Hashable {
    case jobs
    case account
}

struct WelcomeOnboardingView: View {
    @ObservedObject var onboarding: OnboardingManager
    @State private var cursor = GuideCursor(count: 4)

    private let pages: [WelcomeOnboardingPage] = [
        .init(
            title: "Servis yönetimi tek merkezde",
            detail: "Müşteriden iş emrine, sahadan rapora kadar tüm servis sürecini düzenli biçimde yönetin.",
            icon: "location.north.circle.fill",
            accent: PusulaTheme.brandCyan
        ),
        .init(
            title: "Ekibiniz sahada hep güncel",
            detail: "İşleri teknisyenlere atayın, durumları izleyin ve servis notlarını anında paylaşın.",
            icon: "person.3.sequence.fill",
            accent: .orange
        ),
        .init(
            title: "Parça ve teklif kontrolü",
            detail: "Envanteri, kullanılan parçaları, özel satış fiyatlarını ve teklif kalemlerini aynı sistemde takip edin.",
            icon: "shippingbox.and.arrow.backward.fill",
            accent: .purple
        ),
        .init(
            title: "Finans ve raporlar elinizde",
            detail: "Tahsilat, cari, gider ve kârlılık bilgilerine ihtiyaç duyduğunuz anda ulaşın.",
            icon: "chart.line.uptrend.xyaxis.circle.fill",
            accent: .green
        )
    ]

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [PusulaTheme.ink, PusulaTheme.accentStrong],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                HStack {
                    PusulaBrandMark(size: 44)
                    Text("Pusula")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(.white)
                    Spacer()
                    Button("Atla") { onboarding.completeIntro() }.accessibilityIdentifier("intro.skip")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white.opacity(0.9))
                }
                .padding(.horizontal, 22)
                .padding(.top, 12)

                ScrollView {
                    WelcomeOnboardingPageView(page: pages[cursor.index]).padding(24)
                }
            }
        }
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 12) {
                Text("\(cursor.index + 1) / \(pages.count)").foregroundStyle(.white)
                    .accessibilityIdentifier("intro.progress")
                HStack {
                    Button("Geri") { cursor.back() }.disabled(cursor.index == 0)
                    Spacer()
                    Button(cursor.isLast ? "Başla" : "Devam Et") {
                        if cursor.advance() { onboarding.completeIntro() }
                    }
                    .buttonStyle(.borderedProminent).tint(.white).foregroundStyle(PusulaTheme.ink)
                    .accessibilityIdentifier("intro.next")
                }
            }.padding(24).background(PusulaTheme.ink)
        }
        .interactiveDismissDisabled()
    }
}

private struct WelcomeOnboardingPage {
    let title: String
    let detail: String
    let icon: String
    let accent: Color
}

private struct WelcomeOnboardingPageView: View {
    let page: WelcomeOnboardingPage

    var body: some View {
        VStack(spacing: 28) {
            Spacer(minLength: 8)
            ZStack {
                Circle()
                    .fill(.white.opacity(0.12))
                    .frame(width: 190, height: 190)
                Circle()
                    .fill(page.accent.opacity(0.18))
                    .frame(width: 150, height: 150)
                Image(systemName: page.icon)
                    .font(.system(size: 68, weight: .semibold))
                    .foregroundStyle(.white)
            }
            .accessibilityHidden(true)

            VStack(spacing: 14) {
                Text(page.title)
                    .font(.system(.largeTitle, design: .rounded, weight: .bold))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.white)
                Text(page.detail)
                    .font(.title3)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.white.opacity(0.78))
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: 560)
            Spacer(minLength: 8)
        }
    }
}

struct OnboardingCoachOverlay: View {
    @ObservedObject var onboarding: OnboardingManager
    let targetFrames: [OnboardingTarget: CGRect]

    var body: some View {
        GeometryReader { proxy in
            if let step = onboarding.currentStep {
                let targetRect = highlightRect(for: step.target, in: proxy)

                ZStack {
                    Rectangle()
                        .fill(Color.black.opacity(0.62))
                        .overlay {
                            if let targetRect {
                                RoundedRectangle(cornerRadius: 18)
                                    .frame(width: targetRect.width, height: targetRect.height)
                                    .position(x: targetRect.midX, y: targetRect.midY)
                                    .blendMode(.destinationOut)
                            }
                        }
                        .compositingGroup()
                        .ignoresSafeArea()

                    if let targetRect {
                        RoundedRectangle(cornerRadius: 18)
                            .stroke(PusulaTheme.brandCyan, lineWidth: 3)
                            .frame(width: targetRect.width, height: targetRect.height)
                            .position(x: targetRect.midX, y: targetRect.midY)
                            .shadow(color: PusulaTheme.brandCyan.opacity(0.45), radius: 10)
                            .accessibilityHidden(true)
                            .allowsHitTesting(false)
                    }

                    VStack {
                        if (targetRect?.midY ?? 0) < proxy.size.height / 2 { Spacer(minLength: 12) }
                        coachCard(step: step)
                            .frame(maxWidth: min(520, max(1, proxy.size.width - 32)))
                            .frame(maxHeight: max(160, proxy.size.height * 0.65))
                        if (targetRect?.midY ?? 0) >= proxy.size.height / 2 { Spacer(minLength: 12) }
                    }
                    .padding(.top, proxy.safeAreaInsets.top + 16)
                    .padding(.bottom, proxy.safeAreaInsets.bottom + 16)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                .transition(.opacity)
            }
        }
        .ignoresSafeArea()
        .accessibilityElement(children: .contain)
    }

    private func coachCard(step: OnboardingStep) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            ScrollView {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: step.icon)
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(PusulaTheme.accent)
                    .frame(width: 38, height: 38)
                    .background(PusulaTheme.accent.opacity(0.11))
                    .clipShape(RoundedRectangle(cornerRadius: 10))

                VStack(alignment: .leading, spacing: 5) {
                    Text(step.title)
                        .font(.headline).accessibilityIdentifier("tour.title")
                    Text(step.detail)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                    if step.target == .information {
                        Label("Bilgilendirme adımı", systemImage: "info.circle.fill")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(PusulaTheme.accentStrong)
                            .padding(.top, 3)
                    }
                }
                Spacer(minLength: 0)
            }

            }
            HStack(spacing: 10) {
                Button("Atla") { onboarding.completeRoleTour() }.accessibilityIdentifier("tour.skip")
                    .foregroundStyle(.secondary)

                Spacer()

                if onboarding.currentStepIndex > 0 {
                    Button("Geri") { onboarding.goBack() }.accessibilityIdentifier("tour.back")
                        .buttonStyle(.bordered)
                }

                Button(onboarding.currentStepIndex == onboarding.stepCount - 1 ? "Bitir" : "İleri") {
                    onboarding.advance()
                }
                .buttonStyle(.borderedProminent)
                .tint(PusulaTheme.accent)
                .accessibilityIdentifier("tour.next")
            }

            Text("\(onboarding.currentStepIndex + 1) / \(onboarding.stepCount)").accessibilityIdentifier("tour.progress")
                .font(.caption.monospacedDigit())
                .foregroundStyle(.tertiary)
                .frame(maxWidth: .infinity, alignment: .center)
        }
        .padding(18)
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay {
            RoundedRectangle(cornerRadius: 18)
                .stroke(Color.white.opacity(0.22), lineWidth: 1)
        }
        .shadow(color: .black.opacity(0.28), radius: 18, y: 8)
        .padding(.horizontal, 16)
    }

    private func highlightRect(for target: OnboardingTarget, in proxy: GeometryProxy) -> CGRect? {
        guard target != .information,
              let globalRect = targetFrames[target] else { return nil }

        let overlayFrame = proxy.frame(in: .global)
        let localRect = globalRect.offsetBy(dx: -overlayFrame.minX, dy: -overlayFrame.minY)
        let visibleBounds = CGRect(origin: .zero, size: proxy.size).insetBy(dx: 8, dy: 8)
        var visibleRect = localRect.intersection(visibleBounds)
        guard !visibleRect.isNull, visibleRect.width >= 24, visibleRect.height >= 24 else { return nil }
        visibleRect.size.height = min(visibleRect.height, min(300, proxy.size.height * 0.38))
        return visibleRect.insetBy(dx: -6, dy: -6)
    }

}
