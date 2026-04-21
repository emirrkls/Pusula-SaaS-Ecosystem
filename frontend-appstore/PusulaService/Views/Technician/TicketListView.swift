import SwiftUI

struct TicketListView: View {
    @State private var tickets: [FieldTicketDTO] = []
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var selectedFilter: TicketStatus? = nil
    @State private var selectedTicket: FieldTicketDTO?
    
    var filteredTickets: [FieldTicketDTO] {
        guard let filter = selectedFilter else { return tickets }
        return tickets.filter { $0.statusEnum == filter }
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // Status filter pills
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    filterPill("Tümü", filter: nil)
                    ForEach(TicketStatus.allCases, id: \.self) { status in
                        filterPill(status.displayName, filter: status)
                    }
                }
                .padding(.horizontal)
                .padding(.vertical, 12)
            }
            .background(.ultraThinMaterial)
            
            if isLoading {
                Spacer()
                ProgressView("İşler yükleniyor...")
                Spacer()
            } else if filteredTickets.isEmpty {
                Spacer()
                VStack(spacing: 12) {
                    Image(systemName: "tray")
                        .font(.system(size: 48))
                        .foregroundStyle(.secondary)
                    Text("Atanmış iş bulunamadı")
                        .foregroundStyle(.secondary)
                }
                Spacer()
            } else {
                List(filteredTickets) { ticket in
                    TicketCardView(ticket: ticket)
                        .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                        .listRowSeparator(.hidden)
                        .onTapGesture {
                            selectedTicket = ticket
                        }
                }
                .listStyle(.plain)
                .refreshable {
                    await loadTickets()
                }
            }
        }
        .navigationTitle("İşlerim")
        .task { await loadTickets() }
        .sheet(item: $selectedTicket) { ticket in
            NavigationStack {
                TicketDetailView(ticket: ticket, onComplete: {
                    await loadTickets()
                })
            }
        }
    }
    
    private func filterPill(_ title: String, filter: TicketStatus?) -> some View {
        Button(action: { selectedFilter = filter }) {
            Text(title)
                .font(.subheadline.weight(.medium))
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(selectedFilter == filter ? Color.cyan : Color(.systemGray5))
                .foregroundColor(selectedFilter == filter ? .white : .primary)
                .clipShape(Capsule())
        }
    }
    
    private func loadTickets() async {
        do {
            tickets = try await TicketService.getMyAssignedTickets()
            isLoading = false
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
            isLoading = false
        }
    }
}

// MARK: - Ticket Card

struct TicketCardView: View {
    let ticket: FieldTicketDTO
    
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Header: Status badge + date
            HStack {
                Label(ticket.statusEnum.displayName, systemImage: ticket.statusEnum.iconName)
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(statusColor.opacity(0.15))
                    .foregroundColor(statusColor)
                    .clipShape(Capsule())
                
                Spacer()
                
                if ticket.isWarrantyCall == true {
                    Label("Garanti", systemImage: "shield.checkered")
                        .font(.caption2.weight(.medium))
                        .foregroundColor(.orange)
                }
                
                if let date = ticket.scheduledDate {
                    Text(formatDate(date))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            
            // Customer name
            if let name = ticket.customerName {
                Text(name)
                    .font(.headline)
            }
            
            // Description
            if let desc = ticket.description, !desc.isEmpty {
                Text(desc)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
            
            // Customer address
            if let address = ticket.customerAddress, !address.isEmpty {
                HStack(spacing: 6) {
                    Image(systemName: "mappin.circle.fill")
                        .foregroundColor(.red.opacity(0.7))
                    Text(address)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            
            // Bottom row: phone + balance warning
            HStack {
                if let phone = ticket.customerPhone {
                    Link(destination: URL(string: "tel:\(phone)")!) {
                        HStack(spacing: 4) {
                            Image(systemName: "phone.fill")
                            Text(phone)
                        }
                        .font(.caption)
                        .foregroundColor(.cyan)
                    }
                }
                
                Spacer()
                
                // Outstanding balance warning
                if ticket.hasOutstandingBalance {
                    HStack(spacing: 4) {
                        Image(systemName: "exclamationmark.triangle.fill")
                        Text("₺\(String(format: "%.2f", ticket.customerBalance ?? 0)) Cari Borç")
                    }
                    .font(.caption.weight(.semibold))
                    .foregroundColor(.orange)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(.orange.opacity(0.1))
                    .clipShape(Capsule())
                }
            }
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(.systemBackground))
                .shadow(color: .black.opacity(0.06), radius: 8, y: 2)
        )
    }
    
    private var statusColor: Color {
        switch ticket.statusEnum {
        case .pending: return .orange
        case .inProgress: return .blue
        case .completed: return .green
        case .cancelled: return .red
        }
    }
    
    private func formatDate(_ dateString: String) -> String {
        // Try ISO 8601 parsing
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: dateString) {
            let display = DateFormatter()
            display.locale = Locale(identifier: "tr_TR")
            display.dateFormat = "d MMM HH:mm"
            return display.string(from: date)
        }
        return dateString
    }
}

#Preview {
    NavigationStack {
        TicketListView()
    }
}
