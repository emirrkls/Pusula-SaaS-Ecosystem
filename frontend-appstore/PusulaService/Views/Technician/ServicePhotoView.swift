import SwiftUI
import PhotosUI
import UIKit
import ImageIO

private enum ServicePhotoSource {
    case library(PhotosPickerItem)
    case camera(UIImage)
#if DEBUG
    case fixture(Int)
#endif
}

@MainActor
struct ServicePhotoView: View {
    let ticketId: Int
#if DEBUG
    var flowFixture: ServicePhotoFlowFixture? = nil
#endif
    @Environment(\.dismiss) private var dismiss
    @State private var photos: [ServicePhotoDTO] = []
    @State private var isLoading = true
    @State private var loadRequest = RefreshRequestState()
    @State private var loadError: String?
    @State private var hasLoaded = false
    @State private var isUploading = false
    @ObservedObject private var session = SessionManager.shared
    @State private var batch: ServicePhotoUploadBatch<ServicePhotoSource, Data>?
    @State private var batchOwner: AuthenticatedSessionSnapshot?
    @State private var uploadTask: Task<Void, Never>?
    @State private var currentUpload = 0
    @State private var showLeaveWarning = false
    @State private var selectedType = "BEFORE"
    @State private var note = ""
    @State private var selectedLibraryItems: [PhotosPickerItem] = []
    @State private var showSourcePicker = false
    @State private var showPhotoLibrary = false
    @State private var showCamera = false
    @State private var selectedPhoto: ServicePhotoDTO?
    @State private var errorMessage: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 10) {
                    Picker("Görsel kategorisi", selection: $selectedType) {
                        ForEach(ServicePhotoDTO.supportedTypes, id: \.0) { type, label in
                            Text(label).tag(type)
                        }
                    }
                    .pickerStyle(.menu)
                    .disabled(isUploading || hasPendingPhotos)
                    TextField("Görsel notu (örn. İç ünite 2, seri no…)", text: $note, axis: .vertical)
                        .lineLimit(2...4)
                        .textFieldStyle(.roundedBorder)
                        .disabled(isUploading || hasPendingPhotos)
                    Button { PusulaKeyboard.dismiss(); showSourcePicker = true } label: {
                        Label("Görsel Ekle", systemImage: "photo.badge.plus")
                            .frame(maxWidth: .infinity).padding(.vertical, 8)
                    }
                    .buttonStyle(.borderedProminent).tint(PusulaTheme.accent).readOnlyProtected()
                    .disabled(isUploading || hasPendingPhotos)
                    Text("Galeriden tek seferde en fazla 10 görsel seçebilirsiniz. Kategori ve not seçilen tüm görsellere uygulanır.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                .pusulaCard()
                .guideTourTarget("photo-add")
#if DEBUG
                if ServicePhotoFlowFixture.isEnabled, flowFixture != nil {
                    Button("3 test görseli seç") { beginUpload((1...3).map { .fixture($0) }) }
                        .disabled(isUploading || hasPendingPhotos).accessibilityIdentifier("photos.fixture.pick")
                }
#endif

                if isLoading { ProgressView("Görseller getiriliyor...") }
                if let batch { uploadStatus(batch) }
                if let loadError {
                    ReadLoadFailureView(message: loadError) { Task { await loadPhotos() } }
                }
                if !hasLoaded && !isLoading && loadError == nil {
                    Button("common.retry") { Task { await loadPhotos() } }
                }
                if photos.isEmpty && hasLoaded && !isLoading && loadError == nil {
                    ContentUnavailableView("Görsel eklenmemiş", systemImage: "photo.on.rectangle.angled",
                        description: Text("Cihaz etiketi, seri numarası, arıza detayı veya işlem görseli ekleyebilirsiniz."))
                        .padding(.top, 40)
                } else {
                    LazyVStack(spacing: 12) { ForEach(photos) { photo in photoRow(photo) } }
                }
            }.padding().guideTourTarget("photo-list")
        }
        .guideTourScrollable()
        .background(PusulaTheme.page)
        .pusulaKeyboardDismissal()
        .navigationTitle("Servis Görselleri").navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(isUploading || hasPendingPhotos)
        .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Tamam") {
            if hasPendingPhotos { showLeaveWarning = true } else { dismiss() }
        }.disabled(isUploading) } }
        .interactiveDismissDisabled(isUploading || hasPendingPhotos)
        .task { await loadPhotos() }
        .refreshable { if !isUploading { await loadPhotos() } }
        .onDisappear { loadRequest.invalidate(); isLoading = false; uploadTask?.cancel() }
        .onChange(of: session.authenticatedSnapshot) { _, snapshot in
            if let batchOwner, snapshot != batchOwner {
                uploadTask?.cancel(); batch = nil; self.batchOwner = nil; photos = []
            }
        }
        .confirmationDialog("Görsel kaynağı", isPresented: $showSourcePicker) {
            Button("Kamera ile Çek") { showCamera = true }
            Button("Galeriden Seç") { showPhotoLibrary = true }
            Button("Vazgeç", role: .cancel) {}
        }
        .photosPicker(isPresented: $showPhotoLibrary, selection: $selectedLibraryItems,
                      maxSelectionCount: 10, selectionBehavior: .ordered, matching: .images)
        .onChange(of: selectedLibraryItems) { _, items in
            guard !items.isEmpty else { return }
            beginUpload(items.map { .library($0) })
            selectedLibraryItems = []
        }
        .sheet(isPresented: $showCamera) {
            CameraImagePicker { image in
                showCamera = false
                beginUpload([.camera(image)])
            }
        }
        .fullScreenCover(item: $selectedPhoto) { ServicePhotoViewer(photo: $0) }
        .alert(showLeaveWarning ? "Yüklenemeyen görseller var" : "Hata",
               isPresented: Binding(get: { showLeaveWarning || errorMessage != nil }, set: {
                   if !$0 { showLeaveWarning = false; errorMessage = nil }
               })) {
            if showLeaveWarning {
                Button("Burada Kal", role: .cancel) { showLeaveWarning = false }
                Button("Bekleyenleri Bırak ve Çık", role: .destructive) { batch = nil; batchOwner = nil; dismiss() }
            } else {
                Button("Tamam", role: .cancel) { errorMessage = nil }
            }
        } message: {
            Text(showLeaveWarning
                ? "Yüklenen görseller korunur. Yanıtı alınamayan bir görsel sunucuya kaydedilmiş olabilir. Çıkarsanız yeniden eklemeden önce görsel listesini kontrol edin."
                : errorMessage ?? "")
        }
    }

    private func photoRow(_ photo: ServicePhotoDTO) -> some View {
        HStack(spacing: 12) {
            Button { selectedPhoto = photo } label: {
                AsyncImage(url: photo.fullURL) { phase in
                    if case .success(let image) = phase { image.resizable().scaledToFill() } else { Color(.systemGray5) }
                }.frame(width: 84, height: 84).clipShape(RoundedRectangle(cornerRadius: 10))
            }.buttonStyle(.plain).disabled(isUploading)
            VStack(alignment: .leading, spacing: 4) {
                Text(photo.typeLabel).font(.subheadline.weight(.semibold))
                if let note = photo.note, !note.isEmpty { Text(note).font(.caption).lineLimit(3) }
                Text(photo.uploadedByName ?? photo.uploadedAt ?? "Az önce").font(.caption2).foregroundStyle(.secondary)
            }
            Spacer()
            Button(role: .destructive) { Task { await deletePhoto(photo) } } label: { Image(systemName: "trash") }
                .readOnlyProtected()
                .disabled(isUploading)
        }.pusulaCard()
    }

    private func loadPhotos() async {
#if DEBUG
        if ServicePhotoFlowFixture.isEnabled, let flowFixture {
            photos = flowFixture.photos; isLoading = false; hasLoaded = true; return
        }
#endif
        guard let snapshot = SessionManager.shared.authenticatedSnapshot,
              let request = loadRequest.begin(for: snapshot) else { isLoading = loadRequest.isLoading; return }
        isLoading = true
        defer { loadRequest.finish(request); isLoading = loadRequest.isLoading }
        do {
            let loaded = try await TicketService.getServicePhotos(ticketId: ticketId)
            guard loadRequest.accepts(request, session: SessionManager.shared.authenticatedSnapshot, isCancelled: Task.isCancelled) else { return }
            photos = loaded
            loadError = nil
            hasLoaded = true
        } catch {
            guard !LoadCancellationPolicy.isCancellation(error, taskIsCancelled: Task.isCancelled),
                  loadRequest.accepts(request, session: SessionManager.shared.authenticatedSnapshot, isCancelled: Task.isCancelled) else { return }
            loadError = error.localizedDescription
        }
    }
    private var hasPendingPhotos: Bool { !(batch?.pendingIDs.isEmpty ?? true) }
    private var currentPhotoOwner: AuthenticatedSessionSnapshot? {
#if DEBUG
        if ServicePhotoFlowFixture.isEnabled, let flowFixture { return flowFixture.owner }
#endif
        return session.authenticatedSnapshot
    }
    private func photoOwnerIsCurrent(_ owner: AuthenticatedSessionSnapshot) -> Bool { currentPhotoOwner == owner }

    private func uploadStatus(_ value: ServicePhotoUploadBatch<ServicePhotoSource, Data>) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("\(value.confirmedCount) / \(value.items.count) görsel yüklendi",
                  systemImage: value.pendingIDs.isEmpty ? "checkmark.circle.fill" : "photo.on.rectangle")
                .font(.subheadline.weight(.semibold)).accessibilityIdentifier("photos.batch.summary")
            if isUploading {
                ProgressView("\(currentUpload) / \(value.items.count) işleniyor…")
                Text("Görseller sırayla ve güvenli şekilde yükleniyor. Lütfen bu ekranı açık tutun.").font(.caption).foregroundStyle(.secondary)
            } else if !value.pendingIDs.isEmpty {
                Text("\(value.pendingIDs.count) görsel teyit edilemedi. Tekrar denediğinizde yüklenenler yeniden gönderilmez.")
                    .font(.caption).foregroundStyle(.secondary)
                DisclosureGroup("Ayrıntılar") {
                    ForEach(Array(value.items.enumerated()), id: \.element.id) { index, item in
                        if let message = item.error { Text("Görsel \(index + 1): \(message)").font(.caption) }
                    }
                }
                Button("Yüklenemeyenleri Tekrar Dene") { resumeUploads() }
                    .buttonStyle(.borderedProminent).readOnlyProtected()
                    .accessibilityIdentifier("photos.batch.retry")
            }
        }.frame(maxWidth: .infinity, alignment: .leading).pusulaCard()
    }

    private func beginUpload(_ sources: [ServicePhotoSource]) {
        guard !isUploading, !hasPendingPhotos, !sources.isEmpty,
              let snapshot = currentPhotoOwner, !GuideReadOnlyGate.shared.isActive else { return }
        batch = ServicePhotoUploadBatch(sources: sources, type: selectedType, note: note)
        batchOwner = snapshot
        loadRequest.invalidate(); isLoading = false
        resumeUploads()
    }

    private func resumeUploads() {
        guard !isUploading, hasPendingPhotos, let owner = batchOwner, photoOwnerIsCurrent(owner) else { return }
        isUploading = true
        uploadTask = Task { await runUploads(owner: owner) }
    }

    private func runUploads(owner: AuthenticatedSessionSnapshot) async {
        defer { isUploading = false }
        guard let pending = batch?.pendingIDs else { return }
        for id in pending {
            guard !Task.isCancelled, photoOwnerIsCurrent(owner), let value = batch, let item = value.item(id) else { return }
            currentUpload = (value.items.firstIndex { $0.id == id } ?? 0) + 1
            do {
                if item.payload == nil, let source = item.source {
                    let prepared = try await prepare(source)
                    guard !Task.isCancelled, photoOwnerIsCurrent(owner) else { return }
                    batch?.prepare(id, payload: prepared)
                }
                guard !Task.isCancelled, photoOwnerIsCurrent(owner), let payload = batch?.item(id)?.payload else { return }
                let saved = try await sendPhoto(value: value, payload: payload, id: id, owner: owner)
                guard !Task.isCancelled, photoOwnerIsCurrent(owner) else { return }
                batch?.succeed(id)
                if !photos.contains(where: { $0.id == saved.id }) { photos.insert(saved, at: 0) }
            } catch {
                guard !Task.isCancelled, photoOwnerIsCurrent(owner) else { return }
                batch?.fail(id, message: error.localizedDescription)
            }
        }
        if batch?.pendingIDs.isEmpty == true { note = "" }
    }

    private func sendPhoto(value: ServicePhotoUploadBatch<ServicePhotoSource, Data>, payload: Data,
                           id: UUID, owner: AuthenticatedSessionSnapshot) async throws -> ServicePhotoDTO {
#if DEBUG
        if ServicePhotoFlowFixture.isEnabled, let flowFixture {
            return try await flowFixture.upload(key: id, type: value.type, note: value.note, data: payload)
        }
#endif
        return try await TicketService.uploadServicePhoto(ticketId: ticketId, type: value.type, note: value.note,
            imageData: payload, requestId: id, authToken: owner.token)
    }

    private func prepare(_ source: ServicePhotoSource) async throws -> Data {
        let context = await ConnectionDiagnosticEnvironment.context()
        switch source {
        case .library(let item):
            let data = try await ConnectionDiagnosticsTransport.local(operation: .photoLibraryRead,
                interface: context.0, appState: context.1) {
                guard let bytes = try await item.loadTransferable(type: Data.self) else { throw NetworkError.invalidResponse }
                return bytes
            }
            return try await ConnectionDiagnosticsTransport.local(operation: .photoPreparation,
                interface: context.0, appState: context.1) {
                try await Task.detached(priority: .userInitiated) {
                    try autoreleasepool { try preparedServicePhotoData(raw: data) }
                }.value
            }
        case .camera(let image):
            return try await ConnectionDiagnosticsTransport.local(operation: .photoPreparation,
                interface: context.0, appState: context.1) { try preparedServicePhotoData(from: image) }
#if DEBUG
        case .fixture(let index):
            guard ServicePhotoFlowFixture.isEnabled else { throw NetworkError.invalidResponse }
            return Data([UInt8(index)]) // Offline fixture never calls the HTTP transport.
#endif
        }
    }
    private func deletePhoto(_ photo: ServicePhotoDTO) async {
#if DEBUG
        if ServicePhotoFlowFixture.isEnabled, flowFixture != nil { photos.removeAll { $0.id == photo.id }; return }
#endif
        do {
            try await TicketService.deleteServicePhoto(ticketId: ticketId, photoId: photo.id)
            await MainActor.run { photos.removeAll { $0.id == photo.id } }
        } catch { await MainActor.run { errorMessage = error.localizedDescription } }
    }
}

private func preparedServicePhotoData(raw: Data) throws -> Data {
    guard let source = CGImageSourceCreateWithData(raw as CFData, [kCGImageSourceShouldCache: false] as CFDictionary),
          let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: 2_048
          ] as CFDictionary) else { throw NetworkError.invalidResponse }
    return try preparedServicePhotoData(from: UIImage(cgImage: thumbnail))
}

private func preparedServicePhotoData(from image: UIImage) throws -> Data {
    let maxDimension: CGFloat = 2_048
    let largestDimension = max(image.size.width, image.size.height)
    let scale = largestDimension > maxDimension ? maxDimension / largestDimension : 1
    let targetSize = CGSize(width: max(1, image.size.width * scale), height: max(1, image.size.height * scale))
    let format = UIGraphicsImageRendererFormat.default()
    format.scale = 1
    format.opaque = true
    let normalized = UIGraphicsImageRenderer(size: targetSize, format: format).image { _ in
        image.draw(in: CGRect(origin: .zero, size: targetSize))
    }
    var quality: CGFloat = 0.82
    guard var data = normalized.jpegData(compressionQuality: quality) else {
        throw NetworkError.invalidResponse
    }
    let targetBytes = 2 * 1_024 * 1_024
    while data.count > targetBytes && quality > 0.48 {
        quality -= 0.08
        guard let compressed = normalized.jpegData(compressionQuality: quality) else { break }
        data = compressed
    }
    return data
}

struct CameraImagePicker: UIViewControllerRepresentable {
    let onImage: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss
    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }
    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = UIImagePickerController.isSourceTypeAvailable(.camera) ? .camera : .photoLibrary
        picker.delegate = context.coordinator
        return picker
    }
    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}
    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let parent: CameraImagePicker
        init(parent: CameraImagePicker) { self.parent = parent }
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let image = info[.originalImage] as? UIImage { parent.onImage(image) } else { parent.dismiss() }
        }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { parent.dismiss() }
    }
}

struct ServicePhotoViewer: View {
    let photo: ServicePhotoDTO

    var body: some View {
        ServicePhotoGalleryViewer(photos: [photo], initialPhotoID: photo.id)
    }
}

struct ServicePhotoGalleryViewer: View {
    let photos: [ServicePhotoDTO]
    @Environment(\.dismiss) private var dismiss
    @State private var currentPhotoID: Int

    init(photos: [ServicePhotoDTO], initialPhotoID: Int) {
        self.photos = photos
        _currentPhotoID = State(initialValue: initialPhotoID)
    }

    private var currentPhoto: ServicePhotoDTO {
        photos.first(where: { $0.id == currentPhotoID }) ?? photos[0]
    }

    private var currentIndex: Int {
        (photos.firstIndex(where: { $0.id == currentPhotoID }) ?? 0) + 1
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.black.ignoresSafeArea()
                TabView(selection: $currentPhotoID) {
                    ForEach(photos) { photo in
                        ZoomableServicePhotoImage(photo: photo)
                            .tag(photo.id)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
            }
            .navigationTitle(currentPhoto.typeLabel)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Kapat") { dismiss() } }
                if let url = currentPhoto.fullURL {
                    ToolbarItem(placement: .topBarTrailing) {
                        ShareLink(item: url) { Image(systemName: "square.and.arrow.up") }
                    }
                }
            }
            .safeAreaInset(edge: .bottom) {
                VStack(spacing: 4) {
                    if photos.count > 1 {
                        Text("\(currentIndex) / \(photos.count)").font(.caption.bold())
                    }
                    if let note = currentPhoto.note, !note.isEmpty {
                        Text(note).font(.caption).lineLimit(3).multilineTextAlignment(.center)
                    }
                    Text("Dokunulan noktaya çift dokunun; yakınlaştırınca görseli sürükleyebilirsiniz.")
                        .font(.caption2).foregroundStyle(.secondary)
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 18).padding(.vertical, 10)
                .frame(maxWidth: .infinity)
                .background(.black.opacity(0.72))
            }
        }
    }
}

private struct ZoomableServicePhotoImage: View {
    let photo: ServicePhotoDTO
    @State private var zoom: CGFloat = 1
    @State private var zoomAtGestureStart: CGFloat = 1
    @State private var zoomAnchor: UnitPoint = .center
    @State private var imageOffset: CGSize = .zero
    @State private var offsetAtGestureStart: CGSize = .zero

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                Color.black.ignoresSafeArea()
                AsyncImage(url: photo.fullURL) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFit()
                            .scaleEffect(zoom, anchor: zoomAnchor)
                            .offset(imageOffset)
                            .gesture(magnifyGesture)
                            .simultaneousGesture(dragGesture)
                            .simultaneousGesture(doubleTapGesture(in: proxy.size))
                    case .failure:
                        ContentUnavailableView("Görsel açılamadı", systemImage: "photo.badge.exclamationmark")
                            .foregroundStyle(.white)
                    default:
                        ProgressView().tint(.white)
                    }
                }
            }
        }
    }

    private var magnifyGesture: some Gesture {
        MagnifyGesture()
            .onChanged { value in
                zoomAnchor = value.startAnchor
                zoom = min(max(zoomAtGestureStart * value.magnification, 1), 6)
                if zoom <= 1 {
                    imageOffset = .zero
                    offsetAtGestureStart = .zero
                }
            }
            .onEnded { _ in
                zoomAtGestureStart = zoom
                if zoom <= 1 { resetView() }
            }
    }

    private var dragGesture: some Gesture {
        DragGesture()
            .onChanged { value in
                guard zoom > 1 else { return }
                imageOffset = CGSize(
                    width: offsetAtGestureStart.width + value.translation.width,
                    height: offsetAtGestureStart.height + value.translation.height
                )
            }
            .onEnded { _ in
                offsetAtGestureStart = imageOffset
            }
    }

    private func doubleTapGesture(in size: CGSize) -> some Gesture {
        SpatialTapGesture(count: 2).onEnded { value in
            withAnimation(.easeInOut(duration: 0.2)) {
                if zoom > 1 {
                    resetView()
                } else {
                    zoomAnchor = UnitPoint(
                        x: min(max(value.location.x / max(size.width, 1), 0), 1),
                        y: min(max(value.location.y / max(size.height, 1), 0), 1)
                    )
                    zoom = 2.5
                    zoomAtGestureStart = zoom
                }
            }
        }
    }

    private func resetView() {
        zoom = 1
        zoomAtGestureStart = 1
        zoomAnchor = .center
        imageOffset = .zero
        offsetAtGestureStart = .zero
    }
}
