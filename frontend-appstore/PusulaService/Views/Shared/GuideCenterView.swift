import SwiftUI

@MainActor final class GuideProgressStore: ObservableObject {
    @Published private(set) var records: [String: GuideRecord]
    private let key: String
    private let defaults: UserDefaults
    init(scope: String, role: String, defaults: UserDefaults = .standard) {
        self.defaults = defaults
        key = "pusula.guide.\(scope).\(role)"
        records = defaults.data(forKey: key).flatMap { try? JSONDecoder().decode([String: GuideRecord].self, from: $0) } ?? [:]
    }
    func record(_ topic: GuideTopic) -> GuideRecord { records[topic.id] ?? GuideRecord() }
    func save(_ topic: GuideTopic, index: Int, completed: Bool = false) {
        var record = record(topic)
        record.step = GuideCursor(count: topic.steps.count, index: index).index
        if completed { record.completedRevision = topic.revision }
        records[topic.id] = record
        if let data = try? JSONEncoder().encode(records) { defaults.set(data, forKey: key) }
    }
    static func clear(scope: String, defaults: UserDefaults = .standard) {
        for role in ["COMPANY_ADMIN", "SUPER_ADMIN", "TECHNICIAN"] { defaults.removeObject(forKey: "pusula.guide.\(scope).\(role)") }
    }
}
struct GuideCenterDestination: View {
    @ObservedObject private var session = SessionManager.shared
    var body: some View {
        Group {
#if DEBUG
            if GuideFlowFixture.isEnabled {
                GuideCenterView(scope: GuideFlowFixture.scope, role: GuideFlowFixture.role, features: GuideFlowFixture.features, defaults: GuideFlowFixture.defaults)
            } else { authenticatedGuide }
#else
            authenticatedGuide
#endif
        }
    }
    @ViewBuilder private var authenticatedGuide: some View {
        if let snapshot = session.authenticatedSnapshot, let scope = SupportService.accountKey(snapshot.token, company: snapshot.companyId) {
            GuideCenterView(scope: scope, role: session.role, features: session.features)
                .id(scope + session.role)
        } else {
            ContentUnavailableView("Rehber için hesabınıza giriş yapın", systemImage: "person.crop.circle")
        }
    }
}

private struct GuideTourPresentation: Identifiable {
    let id: UUID
    let topic: GuideTopic
}

struct GuideCenterView: View {
    let role: String
    let features: [String: Bool]
    @StateObject private var progress: GuideProgressStore
    @State private var catalog: GuideCatalog?
    @State private var loadFailed = false
    @State private var search = ""
    @State private var category: String?
    @State private var unfinishedOnly = false
    @State private var activeTour: GuideTourPresentation?
    @State private var tourLease: UUID?
    @FocusState private var searchFocused: Bool
    init(scope: String, role: String, features: [String: Bool], defaults: UserDefaults = .standard) {
        self.role = role; self.features = features
        _progress = StateObject(wrappedValue: GuideProgressStore(scope: scope, role: role, defaults: defaults))
    }
    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                TextField("Özellik veya konu ara", text: $search)
                    .focused($searchFocused)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                    .submitLabel(.search)
                    .onSubmit { searchFocused = false }
                    .accessibilityIdentifier("guide.search")
                if !search.isEmpty {
                    Button { search = "" } label: { Image(systemName: "xmark.circle.fill") }
                        .accessibilityLabel("Aramayı temizle")
                        .accessibilityIdentifier("guide.search.clear")
                }
            }.padding(12).background(PusulaTheme.raisedSurface, in: RoundedRectangle(cornerRadius: 12))
                .padding(.horizontal).padding(.vertical, 8)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                if search.isEmpty {
                HStack(spacing: 12) {
                    PusulaBrandMark(size: 48)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("İhtiyacınız olan konuyu seçin").font(.headline)
                        Text("Kısa adımlar • Kaldığınız yer korunur").font(.caption).foregroundStyle(.secondary)
                    }
                }
                Text("Rehber bilgilendirme içindir. Kayıt oluşturmaz, silmez, ödeme veya mesaj göndermez.").font(.caption).foregroundStyle(.secondary)
                }
                if let catalog {
                    let visible = catalog.visibleTopics(role: role, platform: "ios")
                    let completed = visible.filter { progress.record($0).isComplete($0) }.count
                    if search.isEmpty {
                    ProgressView(value: Double(completed), total: Double(max(1, visible.count)))
                    Text("\(completed) / \(visible.count) konu tamamlandı").font(.caption).accessibilityIdentifier("guide.progress")
                    if search.isEmpty, category == nil, let quick = visible.first(where: { $0.category == "start" && $0.id.hasPrefix("quick-start-") }) {
                        Button { open(quick) } label: { Label("Hızlı Başlangıç", systemImage: "play.circle.fill").frame(maxWidth: .infinity).padding(.vertical, 6) }
                            .buttonStyle(.borderedProminent).accessibilityIdentifier("guide.quick-start")
                    }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            categoryButton("Tümü", id: nil)
                            ForEach(catalog.categories.filter { item in visible.contains { $0.category == item.id } }) { item in categoryButton(item.title, id: item.id) }
                        }
                    }
                    Toggle("Yalnızca tamamlanmayanlar", isOn: $unfinishedOnly).font(.subheadline).accessibilityIdentifier("guide.unfinished")
                    } else if category != nil || unfinishedOnly {
                        Button("Arama filtrelerini temizle") { category = nil; unfinishedOnly = false }
                            .font(.subheadline)
                    }
                    let matches = catalog.visibleTopics(role: role, platform: "ios", category: category, query: search).filter { !unfinishedOnly || !progress.record($0).isComplete($0) }
                    // Keep catalog order within each group; direct title hits
                    // precede mentions buried in another lesson's explanation.
                    let results = search.isEmpty ? matches : matches.filter { $0.titleMatches(search) } + matches.filter { !$0.titleMatches(search) }
                    if results.isEmpty { ContentUnavailableView("Konu bulunamadı", systemImage: "magnifyingglass", description: Text("Arama veya kategori filtresini değiştirin.")) }
                    ForEach(results) { topic in
                        Button { open(topic) } label: {
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Text(topic.title).font(.headline)
                                    Spacer()
                                    if progress.record(topic).isComplete(topic) { Image(systemName: "checkmark.circle.fill").foregroundStyle(.green) }
                                    else if topic.isNew { Text("Yeni").font(.caption.bold()).foregroundStyle(PusulaTheme.accent) }
                                }
                                Text(topic.summary).font(.subheadline).foregroundStyle(.secondary)
                                HStack {
                                    Text("\(topic.steps.count) adım")
                                    if let feature = topic.requiredFeature, features[feature] != true { Text("• Paket / yetkiye bağlı") }
                                }.font(.caption).foregroundStyle(.secondary)
                            }.frame(maxWidth: .infinity, alignment: .leading).padding().pusulaCard()
                                .contentShape(Rectangle())
                        }.buttonStyle(.plain).accessibilityIdentifier("guide.topic.\(topic.id)")
                    }
                } else if loadFailed {
                    ContentUnavailableView("Rehber yüklenemedi", systemImage: "book.closed", description: Text("Uygulamayı yeniden açmayı deneyin."))
                    Button("Tekrar Dene") { Task { await load() } }
                } else { ProgressView("Rehber yükleniyor…") }
            }.padding()
        }
        .accessibilityIdentifier("guide.catalog")
        }
        .background(PusulaTheme.page)
        .navigationTitle("Uygulama Rehberi")
        .pusulaKeyboardDismissal()
        .task { await load() }
        .fullScreenCover(item:$activeTour,onDismiss:{GuideTourRuntime.shared.end(tourLease);tourLease=nil}) { presentation in
            GuideTourHost(topic:presentation.topic,progress:progress,role:role,features:features,lease:presentation.id)
                .id(presentation.id)
        }
    }
    private func open(_ topic: GuideTopic) {
        searchFocused = false
        PusulaKeyboard.dismiss()
        guard let lease=GuideTourRuntime.shared.begin() else{return}
        tourLease=lease
        // A single item carries the destination and read-only lease into the
        // presentation. Never present an empty cover from separately-captured state.
        activeTour = GuideTourPresentation(id:lease,topic:topic)
    }
    private func categoryButton(_ title: String, id: String?) -> some View {
        Button { category = id } label: { Text(title).font(.caption.weight(category == id ? .bold : .regular)) }
            .buttonStyle(.bordered).tint(category == id ? PusulaTheme.accent : .secondary)
            .accessibilityIdentifier("guide.category.\(id ?? "all")")
    }
    private func load() async {
        guard catalog == nil else { return }
        loadFailed = false
        let loaded = await Task.detached(priority: .userInitiated) { () -> GuideCatalog? in
            guard let url = Bundle.main.url(forResource: "app-guide", withExtension: "json"), let data = try? Data(contentsOf: url) else { return nil }
            return try? GuideCatalog.decode(data)
        }.value
        guard !Task.isCancelled else { return }
        catalog = loaded; loadFailed = loaded == nil
    }
}
