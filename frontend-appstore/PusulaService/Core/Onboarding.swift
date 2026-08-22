import Foundation
import SwiftUI

@MainActor
final class OnboardingManager: ObservableObject {
    static let shared = OnboardingManager()

    static let introVersion = 1
    static let roleTourVersion = 1

    @Published var isShowingIntro: Bool
    @Published private(set) var activeRole: String?
    @Published private(set) var currentStepIndex = 0

    private let defaults: UserDefaults
    private var activeAccountIdentifier: String?

    private init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        isShowingIntro = defaults.integer(forKey: Self.introStorageKey) < Self.introVersion
    }

    var currentStep: OnboardingStep? {
        guard let activeRole,
              steps(for: activeRole).indices.contains(currentStepIndex) else { return nil }
        return steps(for: activeRole)[currentStepIndex]
    }

    var stepCount: Int {
        guard let activeRole else { return 0 }
        return steps(for: activeRole).count
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
        guard let activeRole else { return }
        let nextIndex = currentStepIndex + 1
        if nextIndex < steps(for: activeRole).count {
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
        Task {
            if let syncedVersion = try? await AuthService.updateOnboardingVersion(Self.roleTourVersion) {
                SessionManager.shared.onboardingVersion = syncedVersion
            }
        }
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
            detail: "Aktif işler, tamamlanan servisler, bekleyen alacak ve kârlılık özetini burada takip edin.",
            icon: "chart.bar.fill",
            target: .content,
            destination: .admin(.overview)
        ),
        .init(
            title: "Operasyonu yönetin",
            detail: "Servisleri filtreleyin, ayrıntılarını açın ve teknisyen atamalarını takip edin.",
            icon: "list.clipboard.fill",
            target: .bottomTab(index: 1, count: 5),
            destination: .admin(.operations)
        ),
        .init(
            title: "Yeni servis kaydı",
            detail: "Operasyon ekranındaki ekleme düğmesiyle müşteri arayın veya yeni müşteri oluşturup işi hemen açın.",
            icon: "plus.circle.fill",
            target: .topTrailing,
            destination: .admin(.operations)
        ),
        .init(
            title: "Diğer modüller",
            detail: "Müşteriler, teklifler, stok ve servis kalite ekranlarına bu menüden ulaşın.",
            icon: "square.grid.2x2.fill",
            target: .bottomTab(index: 2, count: 5),
            destination: .admin(.overview)
        ),
        .init(
            title: "Finans takibi",
            detail: "Gelir, gider, cari hesap ve borç hareketlerini işletmenizle aynı ekrandan yönetin.",
            icon: "turkishlirasign.circle.fill",
            target: .bottomTab(index: 3, count: 5),
            destination: .admin(.finance)
        ),
        .init(
            title: "Ekip ve ayarlar",
            detail: "Kullanıcıları, araçları, firma bilgilerini ve hesap tercihlerini Hesap sekmesinden yönetin.",
            icon: "gearshape.fill",
            target: .bottomTab(index: 4, count: 5),
            destination: .admin(.account)
        )
    ]

    private static let technicianSteps: [OnboardingStep] = [
        .init(
            title: "Atanan işleriniz",
            detail: "Size atanan servisleri ve güncel durumlarını İşlerim ekranından takip edin.",
            icon: "wrench.and.screwdriver.fill",
            target: .bottomTab(index: 0, count: 2),
            destination: .technician(.jobs)
        ),
        .init(
            title: "Duruma göre filtreleyin",
            detail: "Bugünkü, bekleyen veya tamamlanan servisleri filtreleyerek doğru işe hızla ulaşın.",
            icon: "line.3.horizontal.decrease.circle.fill",
            target: .topLeading,
            destination: .technician(.jobs)
        ),
        .init(
            title: "Servis fişini açın",
            detail: "Bir işe dokunarak müşteri bilgilerini, servis notunu ve yapılacak işlemleri görüntüleyin.",
            icon: "doc.text.magnifyingglass",
            target: .content,
            destination: .technician(.jobs)
        ),
        .init(
            title: "Sahadaki tüm adımlar",
            detail: "Fiş içinde parça ve özel fiyat ekleyebilir; teknisyen notu, fotoğraf, imza, garanti ve ödeme bilgilerini tamamlayabilirsiniz.",
            icon: "checklist.checked",
            target: .content,
            destination: .technician(.jobs)
        ),
        .init(
            title: "Hesabınız",
            detail: "Paketinizi, görünüm tercihini ve yasal bağlantıları Hesap sekmesinde bulabilirsiniz.",
            icon: "person.crop.circle.fill",
            target: .bottomTab(index: 1, count: 2),
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

enum OnboardingTarget {
    case topLeading
    case topTrailing
    case content
    case bottomTab(index: Int, count: Int)

    var highlightsBottomBar: Bool {
        if case .bottomTab = self { return true }
        return false
    }
}

enum TechnicianTab: Hashable {
    case jobs
    case account
}

struct WelcomeOnboardingView: View {
    @ObservedObject var onboarding: OnboardingManager
    @State private var page = 0

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
                    Button("Atla") { onboarding.completeIntro() }
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white.opacity(0.9))
                }
                .padding(.horizontal, 22)
                .padding(.top, 12)

                TabView(selection: $page) {
                    ForEach(Array(pages.enumerated()), id: \.offset) { index, item in
                        WelcomeOnboardingPageView(page: item)
                            .tag(index)
                            .padding(.horizontal, 24)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .always))
                .indexViewStyle(.page(backgroundDisplayMode: .always))

                Button {
                    if page < pages.count - 1 {
                        withAnimation(.easeInOut) { page += 1 }
                    } else {
                        onboarding.completeIntro()
                    }
                } label: {
                    HStack(spacing: 9) {
                        Text(page == pages.count - 1 ? "Başla" : "Devam Et")
                        Image(systemName: page == pages.count - 1 ? "checkmark" : "arrow.right")
                    }
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .frame(height: 54)
                }
                .buttonStyle(.plain)
                .foregroundStyle(PusulaTheme.ink)
                .background(.white)
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .padding(.horizontal, 24)
                .padding(.bottom, 22)
            }
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
            Spacer(minLength: 20)
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
            Spacer(minLength: 70)
        }
    }
}

struct OnboardingCoachOverlay: View {
    @ObservedObject var onboarding: OnboardingManager

    var body: some View {
        GeometryReader { proxy in
            if let step = onboarding.currentStep {
                let targetRect = highlightRect(for: step.target, in: proxy)

                ZStack {
                    Rectangle()
                        .fill(Color.black.opacity(0.62))
                        .overlay {
                            RoundedRectangle(cornerRadius: 18)
                                .frame(width: targetRect.width, height: targetRect.height)
                                .position(x: targetRect.midX, y: targetRect.midY)
                                .blendMode(.destinationOut)
                        }
                        .compositingGroup()
                        .ignoresSafeArea()

                    RoundedRectangle(cornerRadius: 18)
                        .stroke(PusulaTheme.brandCyan, lineWidth: 3)
                        .frame(width: targetRect.width, height: targetRect.height)
                        .position(x: targetRect.midX, y: targetRect.midY)
                        .shadow(color: PusulaTheme.brandCyan.opacity(0.45), radius: 10)
                        .accessibilityHidden(true)

                    coachCard(step: step)
                        .frame(maxWidth: min(520, proxy.size.width - 32))
                        .position(
                            x: proxy.size.width / 2,
                            y: cardCenterY(for: step.target, in: proxy)
                        )
                }
                .transition(.opacity)
            }
        }
        .ignoresSafeArea()
        .accessibilityElement(children: .contain)
    }

    private func coachCard(step: OnboardingStep) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: step.icon)
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(PusulaTheme.accent)
                    .frame(width: 38, height: 38)
                    .background(PusulaTheme.accent.opacity(0.11))
                    .clipShape(RoundedRectangle(cornerRadius: 10))

                VStack(alignment: .leading, spacing: 5) {
                    Text(step.title)
                        .font(.headline)
                    Text(step.detail)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }

            HStack(spacing: 10) {
                Button("Atla") { onboarding.completeRoleTour() }
                    .foregroundStyle(.secondary)

                Spacer()

                if onboarding.currentStepIndex > 0 {
                    Button("Geri") { onboarding.goBack() }
                        .buttonStyle(.bordered)
                }

                Button(onboarding.currentStepIndex == onboarding.stepCount - 1 ? "Bitir" : "İleri") {
                    onboarding.advance()
                }
                .buttonStyle(.borderedProminent)
                .tint(PusulaTheme.accent)
            }

            Text("\(onboarding.currentStepIndex + 1) / \(onboarding.stepCount)")
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

    private func highlightRect(for target: OnboardingTarget, in proxy: GeometryProxy) -> CGRect {
        let safeTop = proxy.safeAreaInsets.top
        let safeBottom = proxy.safeAreaInsets.bottom

        switch target {
        case .topLeading:
            return CGRect(x: 12, y: safeTop + 44, width: max(210, proxy.size.width * 0.68), height: 110)
        case .topTrailing:
            return CGRect(x: proxy.size.width - 98, y: safeTop + 2, width: 86, height: 70)
        case .content:
            return CGRect(
                x: 12,
                y: safeTop + 76,
                width: proxy.size.width - 24,
                height: min(290, proxy.size.height * 0.36)
            )
        case let .bottomTab(index, count):
            let barHeight = 58 + safeBottom
            let itemWidth = proxy.size.width / CGFloat(count)
            return CGRect(
                x: itemWidth * CGFloat(index) + 5,
                y: proxy.size.height - barHeight - 2,
                width: itemWidth - 10,
                height: barHeight
            )
        }
    }

    private func cardCenterY(for target: OnboardingTarget, in proxy: GeometryProxy) -> CGFloat {
        if target.highlightsBottomBar {
            return max(210, proxy.size.height * 0.56)
        }
        return min(proxy.size.height - 170, proxy.size.height * 0.74)
    }
}
