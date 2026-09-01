import SwiftUI

struct ServiceQualityView: View {
    @State private var photos: [ServicePhotoDTO] = []
    @State private var filterType: String?
    @State private var searchText = ""
    @State private var useDateFilter = false
    @State private var startDate = Calendar.current.date(byAdding: .month, value: -1, to: Date()) ?? Date()
    @State private var endDate = Date()
    @State private var selectedPhoto: ServicePhotoDTO?
    @State private var isLoading = true
    @State private var errorMessage: String?

    private var filteredPhotos: [ServicePhotoDTO] {
        let needle = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return photos.filter { photo in
            guard filterType == nil || photo.type == filterType else { return false }
            guard !needle.isEmpty else { return true }
            return [String(photo.ticketId), photo.customerName, photo.ticketDescription, photo.note, photo.typeLabel]
                .compactMap { $0?.lowercased() }.joined(separator: " ").contains(needle)
        }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                categoryBar
                Toggle("Tarih aralığı kullan", isOn: $useDateFilter)
                if useDateFilter {
                    HStack {
                        DatePicker("Başlangıç", selection: $startDate, displayedComponents: .date)
                        DatePicker("Bitiş", selection: $endDate, displayedComponents: .date)
                    }.font(.caption)
                }
                Button { Task { await load() } } label: {
                    Label("Filtreleri Uygula", systemImage: "line.3.horizontal.decrease.circle")
                        .frame(maxWidth: .infinity)
                }.buttonStyle(.borderedProminent).tint(PusulaTheme.accent)

                if isLoading { ProgressView() }
                else if filteredPhotos.isEmpty {
                    ContentUnavailableView("Görsel bulunamadı", systemImage: "photo.on.rectangle.angled",
                        description: Text("Müşteri, iş başlığı, fiş no veya görsel notuyla arama yapabilirsiniz."))
                        .padding(.top, 40)
                } else {
                    LazyVStack(spacing: 12) { ForEach(filteredPhotos) { qualityCard($0) } }
                }
            }.padding()
        }
        .background(PusulaTheme.page)
        .navigationTitle("Servis Görselleri").navigationBarTitleDisplayMode(.inline)
        .searchable(text: $searchText, prompt: "Müşteri, iş, fiş no veya not ara")
        .task { await load() }
        .fullScreenCover(item: $selectedPhoto) { ServicePhotoViewer(photo: $0) }
        .alert("Görseller Yüklenemedi", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Tekrar Dene") { Task { await load() } }
            Button("Tamam", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }

    private var categoryBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                categoryChip("Tümü", type: nil)
                ForEach(ServicePhotoDTO.supportedTypes, id: \.0) { type, label in categoryChip(label, type: type) }
            }
        }
    }

    private func categoryChip(_ title: String, type: String?) -> some View {
        Button { filterType = type } label: {
            Text(title).font(.caption.weight(.semibold)).padding(.horizontal, 12).padding(.vertical, 8)
                .background(filterType == type ? PusulaTheme.accent : PusulaTheme.raisedSurface)
                .foregroundStyle(filterType == type ? .white : .primary).clipShape(Capsule())
        }
    }

    private func qualityCard(_ photo: ServicePhotoDTO) -> some View {
        Button { selectedPhoto = photo } label: {
            HStack(alignment: .top, spacing: 12) {
                AsyncImage(url: photo.fullURL) { phase in
                    if case .success(let image) = phase { image.resizable().scaledToFill() } else { Color(.systemGray5) }
                }.frame(width: 112, height: 112).clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
                VStack(alignment: .leading, spacing: 5) {
                    Text(photo.customerName ?? "Müşteri").font(.headline)
                    Text("#\(photo.ticketId) · \(photo.ticketDescription ?? "Servis iş emri")")
                        .font(.caption).lineLimit(2)
                    Text(photo.typeLabel).font(.caption.weight(.semibold)).foregroundStyle(PusulaTheme.accent)
                    if let note = photo.note, !note.isEmpty { Text(note).font(.caption).lineLimit(2) }
                    Text(photo.serviceDate ?? photo.uploadedAt ?? "")
                        .font(.caption2).foregroundStyle(.secondary)
                }
                Spacer()
            }.pusulaCard(padding: 10)
        }.buttonStyle(.plain)
    }

    private func load() async {
        isLoading = true; errorMessage = nil; defer { isLoading = false }
        let formatter = DateFormatter(); formatter.dateFormat = "yyyy-MM-dd"
        do {
            photos = try await TicketService.getCompanyServicePhotos(
                type: nil, ticketId: nil,
                startDate: useDateFilter ? formatter.string(from: startDate) : nil,
                endDate: useDateFilter ? formatter.string(from: endDate) : nil,
                searchText: searchText.trimmingCharacters(in: .whitespacesAndNewlines),
                limit: 500)
        } catch { errorMessage = error.localizedDescription }
    }
}
