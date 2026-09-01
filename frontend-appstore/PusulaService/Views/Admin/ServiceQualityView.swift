import SwiftUI

struct ServicePhotoTicketGroup: Identifiable {
    let ticketId: Int
    let customerName: String
    let ticketDescription: String
    let serviceDate: String
    let photos: [ServicePhotoDTO]

    var id: Int { ticketId }
    var coverPhoto: ServicePhotoDTO? { photos.first }
    var categoryCount: Int { Set(photos.map(\.type)).count }
}

private struct ServicePhotoCategoryGroup: Identifiable {
    let type: String
    let title: String
    let photos: [ServicePhotoDTO]
    var id: String { type }
}

struct ServiceQualityView: View {
    @State private var photos: [ServicePhotoDTO] = []
    @State private var filterType: String?
    @State private var searchText = ""
    @State private var useDateFilter = false
    @State private var startDate = Calendar.current.date(byAdding: .month, value: -1, to: Date()) ?? Date()
    @State private var endDate = Date()
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

    private var ticketGroups: [ServicePhotoTicketGroup] {
        Dictionary(grouping: filteredPhotos, by: \.ticketId)
            .map { ticketId, ticketPhotos in
                let sorted = ticketPhotos.sorted { photoSortKey($0) > photoSortKey($1) }
                let first = sorted.first
                return ServicePhotoTicketGroup(
                    ticketId: ticketId,
                    customerName: first?.customerName ?? "Müşteri",
                    ticketDescription: first?.ticketDescription ?? "Servis iş emri",
                    serviceDate: first?.serviceDate ?? first?.uploadedAt ?? "",
                    photos: sorted
                )
            }
            .sorted { photoSortKey($0.photos.first) > photoSortKey($1.photos.first) }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                archiveHeader
                categoryBar
                dateFilters

                if isLoading {
                    ProgressView("Servis dosyaları getiriliyor…").padding(.top, 30)
                } else if ticketGroups.isEmpty {
                    ContentUnavailableView("Servis dosyası bulunamadı", systemImage: "folder.badge.questionmark",
                        description: Text("Müşteri, iş başlığı, fiş no veya görsel notuyla arama yapabilirsiniz."))
                        .padding(.top, 40)
                } else {
                    LazyVStack(spacing: 12) {
                        ForEach(ticketGroups) { group in
                            NavigationLink { ServicePhotoArchiveDetailView(group: group) } label: {
                                serviceFolderCard(group)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .padding()
        }
        .background(PusulaTheme.page)
        .navigationTitle("Servis Görselleri")
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $searchText, prompt: "Müşteri, iş, fiş no veya not ara")
        .task { await load() }
        .refreshable { await load() }
        .alert("Görseller Yüklenemedi", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Tekrar Dene") { Task { await load() } }
            Button("Tamam", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }

    private var archiveHeader: some View {
        HStack(spacing: 12) {
            Image(systemName: "folder.fill.badge.gearshape")
                .font(.title2).foregroundStyle(PusulaTheme.accent)
                .frame(width: 46, height: 46)
                .background(PusulaTheme.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 13))
            VStack(alignment: .leading, spacing: 3) {
                Text("Servis Dosyaları").font(.headline)
                Text("\(ticketGroups.count) iş emri · \(filteredPhotos.count) görsel")
                    .font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
        }
        .pusulaCard()
    }

    private var dateFilters: some View {
        VStack(spacing: 10) {
            Toggle("Tarih aralığı kullan", isOn: $useDateFilter)
            if useDateFilter {
                HStack {
                    DatePicker("Başlangıç", selection: $startDate, displayedComponents: .date)
                    DatePicker("Bitiş", selection: $endDate, displayedComponents: .date)
                }
                .font(.caption)
            }
            Button { Task { await load() } } label: {
                Label("Filtreleri Uygula", systemImage: "line.3.horizontal.decrease.circle")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(PusulaTheme.accent)
        }
    }

    private var categoryBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                categoryChip("Tümü", type: nil)
                ForEach(ServicePhotoDTO.supportedTypes, id: \.0) { type, label in
                    categoryChip(label, type: type)
                }
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

    private func serviceFolderCard(_ group: ServicePhotoTicketGroup) -> some View {
        HStack(alignment: .top, spacing: 12) {
            ZStack(alignment: .topTrailing) {
                AsyncImage(url: group.coverPhoto?.fullURL) { phase in
                    if case .success(let image) = phase {
                        image.resizable().scaledToFill()
                    } else {
                        ZStack {
                            Color(.systemGray5)
                            Image(systemName: "photo.on.rectangle.angled").foregroundStyle(.secondary)
                        }
                    }
                }
                .frame(width: 112, height: 112)
                .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))

                Text("\(group.photos.count)")
                    .font(.caption2.bold()).foregroundStyle(.white)
                    .padding(.horizontal, 7).padding(.vertical, 4)
                    .background(.black.opacity(0.72), in: Capsule()).padding(7)
            }

            VStack(alignment: .leading, spacing: 6) {
                Text(group.customerName).font(.headline).lineLimit(1)
                Text("#\(group.ticketId) · \(group.ticketDescription)")
                    .font(.subheadline).lineLimit(2)
                Label("\(group.photos.count) görsel · \(group.categoryCount) kategori", systemImage: "photo.stack")
                    .font(.caption.weight(.semibold)).foregroundStyle(PusulaTheme.accent)
                Text(group.serviceDate).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer(minLength: 4)
            Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(.tertiary).padding(.top, 5)
        }
        .pusulaCard(padding: 10)
    }

    private func load() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        do {
            photos = try await TicketService.getCompanyServicePhotos(
                type: nil, ticketId: nil,
                startDate: useDateFilter ? formatter.string(from: startDate) : nil,
                endDate: useDateFilter ? formatter.string(from: endDate) : nil,
                searchText: searchText.trimmingCharacters(in: .whitespacesAndNewlines),
                limit: 1000)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func photoSortKey(_ photo: ServicePhotoDTO?) -> String {
        photo?.serviceDate ?? photo?.uploadedAt ?? ""
    }
}

struct ServicePhotoArchiveDetailView: View {
    let group: ServicePhotoTicketGroup
    @State private var selectedPhoto: ServicePhotoDTO?

    private var categories: [ServicePhotoCategoryGroup] {
        ServicePhotoDTO.supportedTypes.compactMap { type, title in
            let matches = group.photos.filter { $0.type == type }
            return matches.isEmpty ? nil : ServicePhotoCategoryGroup(type: type, title: title, photos: matches)
        }
    }

    private let columns = [GridItem(.adaptive(minimum: 145), spacing: 12)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(group.customerName).font(.title3.bold())
                    Text("#\(group.ticketId) · \(group.ticketDescription)").font(.subheadline)
                    Label("\(group.photos.count) görsel", systemImage: "photo.stack")
                        .font(.caption.weight(.semibold)).foregroundStyle(PusulaTheme.accent)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .pusulaCard()

                ForEach(categories) { category in
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Text(category.title).font(.headline)
                            Spacer()
                            Text("\(category.photos.count)").font(.caption.bold()).foregroundStyle(.secondary)
                        }
                        LazyVGrid(columns: columns, spacing: 12) {
                            ForEach(category.photos) { photo in
                                photoTile(photo)
                            }
                        }
                    }
                }
            }
            .padding()
        }
        .background(PusulaTheme.page)
        .navigationTitle("Servis Dosyası")
        .navigationBarTitleDisplayMode(.inline)
        .fullScreenCover(item: $selectedPhoto) { photo in
            ServicePhotoGalleryViewer(photos: group.photos, initialPhotoID: photo.id)
        }
    }

    private func photoTile(_ photo: ServicePhotoDTO) -> some View {
        Button { selectedPhoto = photo } label: {
            VStack(alignment: .leading, spacing: 7) {
                AsyncImage(url: photo.fullURL) { phase in
                    if case .success(let image) = phase {
                        image.resizable().scaledToFill()
                    } else {
                        ZStack {
                            Color(.systemGray5)
                            ProgressView()
                        }
                    }
                }
                .frame(height: 130)
                .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 11))

                Text(photo.note?.isEmpty == false ? photo.note! : "Görsel notu yok")
                    .font(.caption).lineLimit(2)
                Text(photo.uploadedByName ?? "Ekleyen belirtilmemiş")
                    .font(.caption2).foregroundStyle(.secondary).lineLimit(1)
            }
            .pusulaCard(padding: 8)
        }
        .buttonStyle(.plain)
    }
}
