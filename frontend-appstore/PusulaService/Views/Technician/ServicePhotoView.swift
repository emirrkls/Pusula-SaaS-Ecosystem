import SwiftUI
import PhotosUI
import UIKit

struct ServicePhotoView: View {
    let ticketId: Int
    @Environment(\.dismiss) private var dismiss
    @State private var photos: [ServicePhotoDTO] = []
    @State private var isLoading = true
    @State private var isUploading = false
    @State private var selectedType = "BEFORE"
    @State private var note = ""
    @State private var selectedLibraryItem: PhotosPickerItem?
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
                    TextField("Görsel notu (örn. İç ünite 2, seri no…)", text: $note, axis: .vertical)
                        .lineLimit(2...4)
                        .textFieldStyle(.roundedBorder)
                    Button { showSourcePicker = true } label: {
                        Label("Görsel Ekle", systemImage: "camera.fill")
                            .frame(maxWidth: .infinity).padding(.vertical, 8)
                    }
                    .buttonStyle(.borderedProminent).tint(PusulaTheme.accent).readOnlyProtected()
                }
                .pusulaCard()

                if isLoading || isUploading {
                    ProgressView(isUploading ? "Yükleniyor..." : "Görseller getiriliyor...")
                }
                if photos.isEmpty && !isLoading {
                    ContentUnavailableView("Görsel eklenmemiş", systemImage: "photo.on.rectangle.angled",
                        description: Text("Cihaz etiketi, seri numarası, arıza detayı veya işlem görseli ekleyebilirsiniz."))
                        .padding(.top, 40)
                } else {
                    LazyVStack(spacing: 12) { ForEach(photos) { photo in photoRow(photo) } }
                }
            }.padding()
        }
        .background(PusulaTheme.page)
        .navigationTitle("Servis Görselleri").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Tamam") { dismiss() } } }
        .task { await loadPhotos() }
        .confirmationDialog("Görsel kaynağı", isPresented: $showSourcePicker) {
            Button("Kamera ile Çek") { showCamera = true }
            Button("Galeriden Seç") { showPhotoLibrary = true }
            Button("Vazgeç", role: .cancel) {}
        }
        .photosPicker(isPresented: $showPhotoLibrary, selection: $selectedLibraryItem, matching: .images)
        .onChange(of: selectedLibraryItem) { _, item in
            guard let item else { return }
            Task { await upload(item: item) }
        }
        .sheet(isPresented: $showCamera) {
            CameraImagePicker { image in
                showCamera = false
                guard let data = image.jpegData(compressionQuality: 0.82) else { return }
                Task { await upload(data: data) }
            }
        }
        .fullScreenCover(item: $selectedPhoto) { ServicePhotoViewer(photo: $0) }
        .alert("Hata", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Tamam", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }

    private func photoRow(_ photo: ServicePhotoDTO) -> some View {
        HStack(spacing: 12) {
            Button { selectedPhoto = photo } label: {
                AsyncImage(url: photo.fullURL) { phase in
                    if case .success(let image) = phase { image.resizable().scaledToFill() } else { Color(.systemGray5) }
                }.frame(width: 84, height: 84).clipShape(RoundedRectangle(cornerRadius: 10))
            }.buttonStyle(.plain)
            VStack(alignment: .leading, spacing: 4) {
                Text(photo.typeLabel).font(.subheadline.weight(.semibold))
                if let note = photo.note, !note.isEmpty { Text(note).font(.caption).lineLimit(3) }
                Text(photo.uploadedByName ?? photo.uploadedAt ?? "Az önce").font(.caption2).foregroundStyle(.secondary)
            }
            Spacer()
            Button(role: .destructive) { Task { await deletePhoto(photo) } } label: { Image(systemName: "trash") }
                .readOnlyProtected()
        }.pusulaCard()
    }

    private func loadPhotos() async {
        isLoading = true; defer { isLoading = false }
        do { photos = try await TicketService.getServicePhotos(ticketId: ticketId) }
        catch { errorMessage = error.localizedDescription }
    }
    private func upload(item: PhotosPickerItem) async {
        do {
            guard let data = try await item.loadTransferable(type: Data.self) else { throw NetworkError.invalidResponse }
            await upload(data: data)
        } catch { await MainActor.run { errorMessage = error.localizedDescription } }
        await MainActor.run { selectedLibraryItem = nil }
    }
    private func upload(data: Data) async {
        isUploading = true
        do {
            let saved = try await TicketService.uploadServicePhoto(ticketId: ticketId, type: selectedType, note: note, imageData: data)
            await MainActor.run { photos.insert(saved, at: 0); note = ""; isUploading = false }
        } catch { await MainActor.run { errorMessage = error.localizedDescription; isUploading = false } }
    }
    private func deletePhoto(_ photo: ServicePhotoDTO) async {
        do {
            try await TicketService.deleteServicePhoto(ticketId: ticketId, photoId: photo.id)
            await MainActor.run { photos.removeAll { $0.id == photo.id } }
        } catch { await MainActor.run { errorMessage = error.localizedDescription } }
    }
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
