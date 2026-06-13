import SwiftUI

struct ServiceQualityView: View {
    @State private var photos: [ServicePhotoDTO] = []
    @State private var filterType: String?
    @State private var ticketNo = ""
    @State private var startDate = Date()
    @State private var endDate = Date()
    @State private var isLoading = true
    
    private var filteredPhotos: [ServicePhotoDTO] {
        photos.filter { photo in
            if let filterType, photo.type != filterType { return false }
            if !ticketNo.isEmpty, !String(photo.ticketId).contains(ticketNo) { return false }
            return true
        }
    }
    
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                filterBar
                
                HStack(spacing: 12) {
                    DatePicker("Başlangıç", selection: $startDate, displayedComponents: .date)
                    DatePicker("Bitiş", selection: $endDate, displayedComponents: .date)
                }
                .font(.caption)
                
                Button("Filtrele") { Task { await load() } }
                    .buttonStyle(.borderedProminent)
                    .tint(.cyan)
                
                if isLoading {
                    ProgressView()
                } else if filteredPhotos.isEmpty {
                    ContentUnavailableView("Görsel bulunamadı", systemImage: "photo.on.rectangle.angled")
                        .padding(.top, 40)
                } else {
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                        ForEach(filteredPhotos) { photo in
                            qualityCard(photo)
                        }
                    }
                }
            }
            .padding()
        }
        .navigationTitle("Servis Kalite")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }
    
    private var filterBar: some View {
        HStack(spacing: 8) {
            filterChip("Tümü", type: nil, selected: filterType == nil)
            filterChip("Öncesi", type: "BEFORE", selected: filterType == "BEFORE")
            filterChip("Sonrası", type: "AFTER", selected: filterType == "AFTER")
        }
        
        TextField("Fiş No", text: $ticketNo)
            .padding(10)
            .background(Color(.systemGray6))
            .clipShape(RoundedRectangle(cornerRadius: 10))
    }
    
    private func filterChip(_ title: String, type: String?, selected: Bool) -> some View {
        Button(action: { filterType = type }) {
            Text(title)
                .font(.caption.weight(.semibold))
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(selected ? Color.cyan : Color(.systemGray5))
                .foregroundColor(selected ? .white : .primary)
                .clipShape(Capsule())
        }
    }
    
    private func qualityCard(_ photo: ServicePhotoDTO) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            AsyncImage(url: photo.fullURL) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFill()
                default:
                    Color(.systemGray5)
                }
            }
            .frame(height: 120)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            
            Text(photo.typeLabel)
                .font(.caption.weight(.semibold))
            Text("#\(photo.ticketId)")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .padding(10)
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
    
    private func load() async {
        isLoading = true
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        photos = (try? await TicketService.getCompanyServicePhotos(
            type: filterType,
            ticketId: Int(ticketNo),
            startDate: formatter.string(from: startDate),
            endDate: formatter.string(from: endDate)
        )) ?? []
        isLoading = false
    }
}
