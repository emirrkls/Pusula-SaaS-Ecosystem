import SwiftUI

enum AppStoreScreenshotScene: String {
    case overview
    case operations
    case serviceDetail
    case customerWorkflow
    case proposal
    case finance
    case inventory
}

enum AppStoreScreenshotMode {
    static let launchArgument = "--app-store-screenshots"
    static let sceneEnvironmentKey = "PUSULA_SCREENSHOT_SCENE"

    static var isEnabled: Bool {
#if DEBUG
        ProcessInfo.processInfo.arguments.contains(launchArgument)
#else
        false
#endif
    }

    static var scene: AppStoreScreenshotScene {
        let value = ProcessInfo.processInfo.environment[sceneEnvironmentKey] ?? "overview"
        return AppStoreScreenshotScene(rawValue: value) ?? .overview
    }
}

/// A deterministic, offline-only gallery used by the Xcode Cloud UI-test
/// workflow. It contains representative data and never authenticates or calls
/// the production API.
struct AppStoreScreenshotView: View {
    let scene: AppStoreScreenshotScene

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    companyHeader
                    content
                }
                .padding(.horizontal, PusulaTheme.pagePadding)
                .padding(.top, 12)
                .padding(.bottom, 24)
            }
            .background(PusulaTheme.page)
            .navigationTitle(scene.title)
            .navigationBarTitleDisplayMode(.inline)
            .safeAreaInset(edge: .bottom) { screenshotTabBar }
        }
        .tint(PusulaTheme.accent)
        .accessibilityIdentifier("screenshot.ready.\(scene.rawValue)")
    }

    private var companyHeader: some View {
        HStack(spacing: 12) {
            PusulaBrandMark(size: 46)
            VStack(alignment: .leading, spacing: 3) {
                Text("Örnek Teknik Servis")
                    .font(.headline)
                Text("İşletmenizin tamamı tek ekranda")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Text("PATRON")
                .font(.caption2.weight(.bold))
                .foregroundStyle(PusulaTheme.accentStrong)
                .padding(.horizontal, 9)
                .padding(.vertical, 5)
                .background(PusulaTheme.accent.opacity(0.12))
                .clipShape(Capsule())
        }
    }

    @ViewBuilder
    private var content: some View {
        switch scene {
        case .overview: overview
        case .operations: operations
        case .serviceDetail: serviceDetail
        case .customerWorkflow: customerWorkflow
        case .proposal: proposal
        case .finance: finance
        case .inventory: inventory
        }
    }

    private var overview: some View {
        VStack(alignment: .leading, spacing: 18) {
            ScreenshotHero(
                eyebrow: "BU AY",
                title: "İşletmenizin nabzı",
                value: "₺174.250",
                detail: "Aylık satış / ciro",
                icon: "chart.line.uptrend.xyaxis"
            )

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                ScreenshotMetric(title: "Aktif İş", value: "18", icon: "wrench.and.screwdriver", color: .orange)
                ScreenshotMetric(title: "Bu Ay Biten", value: "47", icon: "checkmark.circle.fill", color: PusulaTheme.accent)
                ScreenshotMetric(title: "Bekleyen Alacak", value: "₺58.200", icon: "clock.fill", color: .orange)
                ScreenshotMetric(title: "Net Kâr", value: "₺47.850", icon: "turkishlirasign.circle.fill", color: .green)
            }

            ScreenshotSection(title: "Saha Ekibi", subtitle: "Bugünkü operasyon") {
                ScreenshotPerson(name: "Teknisyen A", detail: "4 tamamlandı · 2 aktif", amount: "₺6.250")
                Divider()
                ScreenshotPerson(name: "Teknisyen B", detail: "3 tamamlandı · 1 aktif", amount: "₺4.800")
            }
        }
    }

    private var operations: some View {
        VStack(alignment: .leading, spacing: 16) {
            ScreenshotHero(
                eyebrow: "CANLI OPERASYON",
                title: "Servisleri anlık yönetin",
                value: "18 aktif iş",
                detail: "5 teknisyene atanmış",
                icon: "location.fill.viewfinder"
            )

            HStack(spacing: 8) {
                ScreenshotChip("Tümü", selected: true)
                ScreenshotChip("Bugün")
                ScreenshotChip("Bekliyor")
                ScreenshotChip("Tamamlandı")
            }

            ScreenshotTicket(number: "#128", title: "VRF klima arızası", customer: "Örnek Plaza", technician: "Teknisyen A", status: "Yolda", tone: .orange)
            ScreenshotTicket(number: "#127", title: "4 klima periyodik bakım", customer: "Demo Kafe", technician: "Teknisyen B", status: "Devam ediyor", tone: PusulaTheme.accent)
            ScreenshotTicket(number: "#126", title: "Soğuk oda kontrolü", customer: "Test Market", technician: "Teknisyen C", status: "Parça bekliyor", tone: .purple)
        }
    }

    private var finance: some View {
        VStack(alignment: .leading, spacing: 18) {
            ScreenshotHero(
                eyebrow: "FİNANS ÖZETİ",
                title: "Nakit ve kârlılık kontrolü",
                value: "₺96.850",
                detail: "Aylık faaliyet kârı",
                icon: "chart.pie.fill"
            )

            ScreenshotSection(title: "Ağustos Özeti", subtitle: "Gelir ve gider dağılımı") {
                ScreenshotFinanceRow(title: "Satış / Ciro", value: "₺174.250", color: .green)
                ScreenshotFinanceRow(title: "Cari Tahsilat", value: "₺42.000", color: .green)
                ScreenshotFinanceRow(title: "Servis Doğrudan Maliyeti", value: "₺36.400", color: .orange)
                ScreenshotFinanceRow(title: "Diğer Faaliyet Giderleri", value: "₺23.000", color: .red)
            }

            ScreenshotSection(title: "Açık Cari Hesaplar", subtitle: "Yaklaşan tahsilatlar") {
                ScreenshotFinanceRow(title: "Örnek Endüstri AŞ", value: "₺58.200", color: .orange)
                ScreenshotFinanceRow(title: "Demo Yapı Ltd.", value: "₺31.750", color: .orange)
            }
        }
    }

    private var serviceDetail: some View {
        VStack(alignment: .leading, spacing: 14) {
            ScreenshotHero(
                eyebrow: "İŞ EMRİ #128",
                title: "Sahadaki tüm adımlar tek fişte",
                value: "Devam ediyor",
                detail: "Örnek Plaza · Teknisyen A",
                icon: "doc.text.magnifyingglass"
            )

            HStack(spacing: 7) {
                ScreenshotChip("Parça eklendi", selected: true)
                ScreenshotChip("Not mevcut")
                ScreenshotChip("İmza hazır")
            }

            ScreenshotSection(title: "Servis Detayı", subtitle: "Ekip ve müşteri aynı bilgiye ulaşır") {
                ScreenshotDetailRow(icon: "calendar", title: "Müracaat", value: "18 Ağustos 2026")
                Divider()
                ScreenshotDetailRow(icon: "person.fill", title: "Atanan", value: "Teknisyen A")
                Divider()
                ScreenshotDetailRow(icon: "text.bubble.fill", title: "Teknisyen Notu", value: "Kontrol tamamlandı")
            }

            ScreenshotSection(title: "Parça ve Kapanış", subtitle: "Fişe özel fiyat, gider ve ödeme takibi") {
                ScreenshotPartRow(name: "Kontaktör 25A", detail: "1 adet · Liste ₺980", price: "₺850")
                Divider()
                ScreenshotDetailRow(icon: "banknote.fill", title: "Dış Gider", value: "₺450")
                Divider()
                HStack(spacing: 8) {
                    ScreenshotBadge(title: "Garanti", icon: "checkmark.shield.fill", color: .purple)
                    ScreenshotBadge(title: "İmza", icon: "signature", color: PusulaTheme.accent)
                    ScreenshotBadge(title: "PDF", icon: "doc.richtext", color: .orange)
                }
            }
        }
    }

    private var customerWorkflow: some View {
        VStack(alignment: .leading, spacing: 15) {
            ScreenshotHero(
                eyebrow: "HIZLI SERVİS KAYDI",
                title: "Müşteriyi bul, işi hemen aç",
                value: "Tek ekrandan",
                detail: "Ara, seç veya yeni müşteri oluştur",
                icon: "person.badge.plus"
            )

            ScreenshotSearchField(text: "demo", prompt: "Ad, firma veya telefonla ara")

            ScreenshotSection(title: "Arama Sonucu", subtitle: "Yalnızca eşleşen kayıtlar gösterilir") {
                HStack(spacing: 11) {
                    Image(systemName: "building.2.fill")
                        .foregroundStyle(PusulaTheme.accent)
                        .frame(width: 28)
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Demo Müşteri Ltd.").font(.subheadline.weight(.semibold))
                        Text("05XX XXX XX XX").font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                }
            }

            ScreenshotSection(title: "Yeni Müşteri", subtitle: "Sonuç yoksa iş akışından ayrılmadan kaydedin") {
                ScreenshotDetailRow(icon: "person.text.rectangle", title: "Müşteri", value: "Yeni Demo Firma")
                Divider()
                ScreenshotDetailRow(icon: "phone.fill", title: "Telefon", value: "05XX XXX XX XX")
                Divider()
                ScreenshotActionLabel(title: "Müşteriyi Kaydet ve Servis Fişi Aç", icon: "arrow.right.circle.fill")
            }
        }
    }

    private var proposal: some View {
        VStack(alignment: .leading, spacing: 15) {
            ScreenshotHero(
                eyebrow: "TEKLİF YÖNETİMİ",
                title: "Tekliften işe tek dokunuşla",
                value: "₺21.450",
                detail: "3 kalem · KDV dahil",
                icon: "doc.text.fill"
            )

            ScreenshotSearchField(text: "örnek", prompt: "Teklif müşterisi ara")

            ScreenshotSection(title: "Teklif Kalemleri", subtitle: "Stoktan seçin, teklife özel fiyatlandırın") {
                ScreenshotProposalRow(title: "VRF filtre seti", detail: "2 × ₺1.650", total: "₺3.300")
                Divider()
                ScreenshotProposalRow(title: "Bakır boru 3/8", detail: "30 m × ₺310", total: "₺9.300")
                Divider()
                ScreenshotProposalRow(title: "Montaj ve devreye alma", detail: "Hizmet", total: "₺8.850")
            }

            ScreenshotActionLabel(title: "Teklifi Onayla ve İşe Dönüştür", icon: "checkmark.seal.fill")
        }
    }

    private var inventory: some View {
        VStack(alignment: .leading, spacing: 16) {
            ScreenshotHero(
                eyebrow: "STOK YÖNETİMİ",
                title: "Parçanız nerede, bilin",
                value: "₺742.300",
                detail: "Tahmini envanter satış değeri",
                icon: "shippingbox.fill"
            )

            HStack(spacing: 10) {
                ScreenshotMetric(title: "Ürün Çeşidi", value: "142", icon: "square.grid.2x2.fill", color: PusulaTheme.accent)
                ScreenshotMetric(title: "Kritik Stok", value: "5", icon: "exclamationmark.triangle.fill", color: .red)
            }

            ScreenshotSection(title: "Envanter", subtitle: "Barkod, adet ve fiyat takibi") {
                ScreenshotStock(name: "VRF filtre seti", stock: "12 adet", price: "₺1.650", critical: false)
                Divider()
                ScreenshotStock(name: "Kontaktör 25A", stock: "2 adet", price: "₺980", critical: true)
                Divider()
                ScreenshotStock(name: "Bakır boru 3/8", stock: "64 metre", price: "₺310", critical: false)
            }
        }
    }

    private var screenshotTabBar: some View {
        HStack {
            ScreenshotTab(title: "Özet", icon: "chart.bar.fill", selected: scene == .overview)
            ScreenshotTab(title: "Operasyon", icon: "list.clipboard.fill", selected: scene.isOperationsScene)
            ScreenshotTab(title: "Finans", icon: "turkishlirasign.circle.fill", selected: scene == .finance)
            ScreenshotTab(title: "Stok", icon: "shippingbox.fill", selected: scene == .inventory)
        }
        .padding(.top, 10)
        .padding(.bottom, 5)
        .background(.ultraThinMaterial)
        .overlay(alignment: .top) { Divider() }
    }
}

private extension AppStoreScreenshotScene {
    var title: String {
        switch self {
        case .overview: "Genel Bakış"
        case .operations: "Operasyon"
        case .serviceDetail: "Servis Fişi"
        case .customerWorkflow: "Yeni Servis"
        case .proposal: "Teklif"
        case .finance: "Finans"
        case .inventory: "Envanter"
        }
    }

    var isOperationsScene: Bool {
        switch self {
        case .operations, .serviceDetail, .customerWorkflow, .proposal: true
        case .overview, .finance, .inventory: false
        }
    }
}

private struct ScreenshotHero: View {
    let eyebrow: String
    let title: String
    let value: String
    let detail: String
    let icon: String

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            VStack(alignment: .leading, spacing: 7) {
                Text(eyebrow).font(.caption2.weight(.bold)).tracking(1.1).opacity(0.8)
                Text(title).font(.title3.weight(.semibold))
                Text(value).font(.title.weight(.bold)).minimumScaleFactor(0.72).lineLimit(1)
                Text(detail).font(.caption).opacity(0.82)
            }
            Spacer(minLength: 8)
            Image(systemName: icon)
                .font(.system(size: 31, weight: .semibold))
                .padding(14)
                .background(.white.opacity(0.14))
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .foregroundStyle(.white)
        .padding(18)
        .background(LinearGradient(colors: [PusulaTheme.ink, PusulaTheme.accent], startPoint: .topLeading, endPoint: .bottomTrailing))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

private struct ScreenshotMetric: View {
    let title: String
    let value: String
    let icon: String
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: icon).foregroundStyle(color)
            Text(value).font(.title3.weight(.bold)).minimumScaleFactor(0.65).lineLimit(1)
            Text(title).font(.caption).foregroundStyle(.secondary).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .pusulaCard(padding: 14)
    }
}

private struct ScreenshotSection<Content: View>: View {
    let title: String
    let subtitle: String
    let content: Content

    init(title: String, subtitle: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.subtitle = subtitle
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            PusulaSectionHeader(title: title, subtitle: subtitle)
            content
        }
        .pusulaCard()
    }
}

private struct ScreenshotPerson: View {
    let name: String
    let detail: String
    let amount: String

    var body: some View {
        HStack(spacing: 11) {
            Image(systemName: "person.crop.circle.fill").font(.title2).foregroundStyle(PusulaTheme.accent)
            VStack(alignment: .leading, spacing: 2) {
                Text(name).font(.subheadline.weight(.semibold))
                Text(detail).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Text(amount).font(.subheadline.monospacedDigit().weight(.semibold)).foregroundStyle(.green)
        }
    }
}

private struct ScreenshotChip: View {
    let title: String
    let selected: Bool

    init(_ title: String, selected: Bool = false) {
        self.title = title
        self.selected = selected
    }

    var body: some View {
        Text(title)
            .font(.caption.weight(.semibold))
            .foregroundStyle(selected ? Color.white : Color.secondary)
            .padding(.horizontal, 11)
            .padding(.vertical, 8)
            .background(selected ? PusulaTheme.accent : PusulaTheme.raisedSurface)
            .clipShape(Capsule())
            .overlay { Capsule().stroke(selected ? .clear : PusulaTheme.border) }
    }
}

private struct ScreenshotTicket: View {
    let number: String
    let title: String
    let customer: String
    let technician: String
    let status: String
    let tone: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 11) {
            HStack {
                Text(number).font(.caption.monospacedDigit().weight(.bold)).foregroundStyle(PusulaTheme.accentStrong)
                Spacer()
                Text(status).font(.caption2.weight(.bold)).foregroundStyle(tone).padding(.horizontal, 9).padding(.vertical, 5).background(tone.opacity(0.12)).clipShape(Capsule())
            }
            Text(title).font(.headline)
            Label(customer, systemImage: "building.2").font(.subheadline).foregroundStyle(.secondary)
            Label(technician, systemImage: "person.fill").font(.caption).foregroundStyle(.secondary)
        }
        .pusulaCard()
    }
}

private struct ScreenshotFinanceRow: View {
    let title: String
    let value: String
    let color: Color

    var body: some View {
        HStack {
            Text(title).font(.subheadline)
            Spacer()
            Text(value).font(.subheadline.monospacedDigit().weight(.bold)).foregroundStyle(color)
        }
        .padding(.vertical, 2)
    }
}

private struct ScreenshotStock: View {
    let name: String
    let stock: String
    let price: String
    let critical: Bool

    var body: some View {
        HStack(spacing: 11) {
            Image(systemName: critical ? "exclamationmark.triangle.fill" : "shippingbox.fill")
                .foregroundStyle(critical ? Color.red : PusulaTheme.accent)
                .frame(width: 26)
            VStack(alignment: .leading, spacing: 3) {
                Text(name).font(.subheadline.weight(.semibold))
                Text(stock).font(.caption).foregroundStyle(critical ? Color.red : Color.secondary)
            }
            Spacer()
            Text(price).font(.subheadline.monospacedDigit().weight(.bold))
        }
    }
}

private struct ScreenshotDetailRow: View {
    let icon: String
    let title: String
    let value: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .foregroundStyle(PusulaTheme.accent)
                .frame(width: 24)
            Text(title).font(.subheadline).foregroundStyle(.secondary)
            Spacer(minLength: 8)
            Text(value).font(.subheadline.weight(.semibold)).multilineTextAlignment(.trailing)
        }
    }
}

private struct ScreenshotPartRow: View {
    let name: String
    let detail: String
    let price: String

    var body: some View {
        HStack(spacing: 11) {
            Image(systemName: "shippingbox.fill")
                .foregroundStyle(PusulaTheme.accent)
                .frame(width: 26)
            VStack(alignment: .leading, spacing: 3) {
                Text(name).font(.subheadline.weight(.semibold))
                Text(detail).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(price).font(.subheadline.monospacedDigit().weight(.bold)).foregroundStyle(.green)
                Text("Özel satış").font(.caption2).foregroundStyle(.secondary)
            }
        }
    }
}

private struct ScreenshotBadge: View {
    let title: String
    let icon: String
    let color: Color

    var body: some View {
        Label(title, systemImage: icon)
            .font(.caption2.weight(.semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 9)
            .padding(.vertical, 7)
            .background(color.opacity(0.11))
            .clipShape(Capsule())
    }
}

private struct ScreenshotSearchField: View {
    let text: String
    let prompt: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass").foregroundStyle(PusulaTheme.accent)
            VStack(alignment: .leading, spacing: 2) {
                Text(text).font(.subheadline.weight(.semibold))
                Text(prompt).font(.caption2).foregroundStyle(.secondary)
            }
            Spacer()
            Image(systemName: "line.3.horizontal.decrease.circle.fill").foregroundStyle(PusulaTheme.accent)
        }
        .pusulaCard(padding: 13)
    }
}

private struct ScreenshotProposalRow: View {
    let title: String
    let detail: String
    let total: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "checkmark.circle.fill").foregroundStyle(PusulaTheme.accent)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.subheadline.weight(.semibold))
                Text(detail).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Text(total).font(.subheadline.monospacedDigit().weight(.bold))
        }
    }
}

private struct ScreenshotActionLabel: View {
    let title: String
    let icon: String

    var body: some View {
        Label(title, systemImage: icon)
            .font(.subheadline.weight(.bold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 13)
            .background(PusulaTheme.accent)
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

private struct ScreenshotTab: View {
    let title: String
    let icon: String
    let selected: Bool

    var body: some View {
        VStack(spacing: 3) {
            Image(systemName: icon).font(.system(size: 17, weight: .semibold))
            Text(title).font(.caption2)
        }
        .foregroundStyle(selected ? PusulaTheme.accent : Color.secondary)
        .frame(maxWidth: .infinity)
    }
}
