import SwiftUI
import MapKit

/// Live field radar — shows technician locations on a map.
struct FieldRadarView: View {
    @State private var pins: [FieldPin] = []
    @State private var isLoading = true
    @State private var region = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 39.9334, longitude: 32.8597), // Ankara default
        span: MKCoordinateSpan(latitudeDelta: 0.5, longitudeDelta: 0.5)
    )
    
    var body: some View {
        ZStack {
            Map(coordinateRegion: $region, annotationItems: mapPins) { pin in
                MapAnnotation(coordinate: pin.coordinate) {
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
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
        .navigationTitle("Saha Radarı")
        .navigationBarTitleDisplayMode(.inline)
        .task { await loadPins() }
        .refreshable { await loadPins() }
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
        .clipShape(RoundedRectangle(cornerRadius: 16))
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
        do {
            pins = try await AdminService.getFieldRadar()
            isLoading = false
            
            // Auto-center map on first pin
            if let first = mapPins.first {
                region = MKCoordinateRegion(
                    center: first.coordinate,
                    span: MKCoordinateSpan(latitudeDelta: 0.15, longitudeDelta: 0.15)
                )
            }
        } catch {
            isLoading = false
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
