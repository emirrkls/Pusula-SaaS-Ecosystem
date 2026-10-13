import SwiftUI
import MapKit

/// Live field radar — shows technician locations on a map.
struct FieldRadarView: View {
    @State private var pins: [FieldPin] = []
    @State private var isLoading = true
    @State private var loadRequest = RefreshRequestState()
    @State private var errorMessage: String?
    @State private var position: MapCameraPosition = .region(MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 39.9334, longitude: 32.8597), // Ankara default
        span: MKCoordinateSpan(latitudeDelta: 0.5, longitudeDelta: 0.5)
    ))
    
    var body: some View {
        ZStack {
            Map(position: $position) {
                ForEach(mapPins) { pin in
                    Annotation(pin.name, coordinate: pin.coordinate) {
                    VStack(spacing: 2) {
                        Image(systemName: statusIcon(pin.status))
                            .font(.title3)
                            .foregroundColor(.white)
                            .padding(8)
                            .background(statusColor(pin.status))
                            .clipShape(Circle())
                            .shadow(radius: 4)
                        
                        Text(pin.name)
                            .font(.caption2.weight(.semibold))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(.ultraThinMaterial)
                            .clipShape(Capsule())
                    }
                }
                }
            }
            .ignoresSafeArea(edges: .bottom)
            
            // Legend overlay
            VStack {
                Spacer()
                legendBar
            }
            
            if isLoading {
                ProgressView("Saha verileri yükleniyor...")
                    .padding()
                    .background(.ultraThickMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
            }
        }
        .navigationTitle("Saha Radarı")
        .navigationBarTitleDisplayMode(.inline)
        .task { await loadPins() }
        .refreshable { await loadPins() }
        .onDisappear { loadRequest.invalidate(); isLoading = false }
        .safeAreaInset(edge: .top) {
            if let errorMessage {
                ReadLoadFailureView(message: errorMessage) { Task { await loadPins() } }
                    .padding().background(.regularMaterial)
            }
        }
    }
    
    private var mapPins: [MapPinData] {
        pins.compactMap { pin in
            guard let lat = pin.latitude, let lon = pin.longitude else { return nil }
            return MapPinData(
                id: pin.technicianId,
                name: pin.technicianName ?? "Teknisyen",
                customer: pin.customerName ?? "",
                status: pin.ticketStatus ?? "PENDING",
                coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon)
            )
        }
    }
    
    private var legendBar: some View {
        HStack(spacing: 16) {
            legendItem("Aktif", color: .blue, icon: "wrench.and.screwdriver")
            legendItem("Bekliyor", color: .orange, icon: "clock")
            legendItem("Tamamlandı", color: .green, icon: "checkmark")
        }
        .padding()
        .background(.ultraThickMaterial)
        .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
        .padding()
    }
    
    private func legendItem(_ label: String, color: Color, icon: String) -> some View {
        HStack(spacing: 4) {
            Image(systemName: icon)
                .foregroundColor(color)
            Text(label)
                .font(.caption2)
        }
    }
    
    private func statusIcon(_ status: String) -> String {
        switch status {
        case "IN_PROGRESS": return "wrench.and.screwdriver"
        case "COMPLETED": return "checkmark"
        default: return "clock"
        }
    }
    
    private func statusColor(_ status: String) -> Color {
        switch status {
        case "IN_PROGRESS": return .blue
        case "COMPLETED": return .green
        default: return .orange
        }
    }
    
    private func loadPins() async {
        guard let snapshot = SessionManager.shared.authenticatedSnapshot,
              let request = loadRequest.begin(for: snapshot) else { isLoading = loadRequest.isLoading; return }
        isLoading = true
        defer { loadRequest.finish(request); isLoading = loadRequest.isLoading }
        do {
            let loaded = try await AdminService.getFieldRadar()
            guard loadRequest.accepts(request, session: SessionManager.shared.authenticatedSnapshot, isCancelled: Task.isCancelled) else { return }
            pins = loaded
            errorMessage = nil
            
            // Auto-center map on first pin
            if let first = mapPins.first {
                position = .region(MKCoordinateRegion(
                    center: first.coordinate,
                    span: MKCoordinateSpan(latitudeDelta: 0.15, longitudeDelta: 0.15)
                ))
            }
        } catch {
            guard !LoadCancellationPolicy.isCancellation(error, taskIsCancelled: Task.isCancelled),
                  loadRequest.accepts(request, session: SessionManager.shared.authenticatedSnapshot, isCancelled: Task.isCancelled) else { return }
            errorMessage = error.localizedDescription
        }
    }
}

struct MapPinData: Identifiable {
    let id: Int
    let name: String
    let customer: String
    let status: String
    let coordinate: CLLocationCoordinate2D
}
