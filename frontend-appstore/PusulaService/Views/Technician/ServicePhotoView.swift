import SwiftUI
import PhotosUI

struct ServicePhotoView: View {
    let ticketId: Int
    
    @Environment(\.dismiss) private var dismiss
    @State private var photos: [ServicePhotoDTO] = []
    @State private var isLoading = true
    @State private var isUploading = false
    @State private var pendingType = "BEFORE"
    @State private var errorMessage: String?
    
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                HStack(spacing: 12) {
                    photoPickerButton("Öncesi Ekle", type: "BEFORE", prominent: false)
                    photoPickerButton("Sonrası Ekle", type: "AFTER", prominent: true)
                }
                
                if isLoading || isUploading {
                    ProgressView(isUploading ? "Yükleniyor..." : "Görseller getiriliyor...")
                }
                
                if photos.isEmpty && !isLoading {
                    ContentUnavailableView(
                        "Görsel eklenmemiş",
                        systemImage: "photo.on.rectangle.angled",
                        description: Text("Kalite takibi için önce/sonra fotoğrafı ekleyebilirsiniz.")
                    )
                    .padding(.top, 40)
                } else {
                    LazyVStack(spacing: 12) {
                        ForEach(photos) { photo in
                            photoRow(photo)
                        }
                    }
                }
            }
            .padding()
        }
        .navigationTitle("Servis Görselleri")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Tamam") { dismiss() }
            }
        }
        .task { await loadPhotos() }
        .alert("Hata", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Tamam", role: .cancel) { errorMessage = nil }
        } message: {
            Text(errorMessage ?? "")
        }
    }
    
    private func photoPickerButton(_ title: String, type: String, prominent: Bool) -> some View {
        PhotosPicker(selection: Binding(
            get: { nil as PhotosPickerItem? },
            set: { item in
                pendingType = type
                if let item { Task { await upload(item: item) } }
            }
        ), matching: .images) {
            Text(title)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
        }
        .buttonStyle(prominent ? .borderedProminent : .bordered)
        .tint(prominent ? .cyan : .primary)
        .readOnlyProtected()
    }
    
    private func photoRow(_ photo: ServicePhotoDTO) -> some View {
        HStack(spacing: 12) {
            AsyncImage(url: photo.fullURL) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFill()
                default:
                    Color(.systemGray5)
                }
            }
            .frame(width: 72, height: 72)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            
            VStack(alignment: .leading, spacing: 4) {
                Text(photo.typeLabel).font(.subheadline.weight(.semibold))
                Text(photo.uploadedAt ?? "Az önce").font(.caption).foregroundStyle(.secondary)
            }
            
            Spacer()
            
            Button(role: .destructive) {
                Task { await deletePhoto(photo) }
            } label: {
                Image(systemName: "trash")
            }
            .readOnlyProtected()
        }
        .padding()
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
    
    private func loadPhotos() async {
        isLoading = true
        do {
            photos = try await TicketService.getServicePhotos(ticketId: ticketId)
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
    
    private func upload(item: PhotosPickerItem) async {
        isUploading = true
        do {
            guard let data = try await item.loadTransferable(type: Data.self) else {
                throw NetworkError.invalidResponse
            }
            let saved = try await TicketService.uploadServicePhoto(ticketId: ticketId, type: pendingType, imageData: data)
            await MainActor.run {
                photos.insert(saved, at: 0)
                isUploading = false
            }
        } catch {
            await MainActor.run {
                errorMessage = error.localizedDescription
                isUploading = false
            }
        }
    }
    
    private func deletePhoto(_ photo: ServicePhotoDTO) async {
        do {
            try await TicketService.deleteServicePhoto(ticketId: ticketId, photoId: photo.id)
            await MainActor.run {
                photos.removeAll { $0.id == photo.id }
            }
        } catch {
            await MainActor.run { errorMessage = error.localizedDescription }
        }
    }
}
