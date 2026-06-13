import SwiftUI

struct TicketListView: View {
    var requestedFilter: String? = nil
    var onRequestedFilterApplied: (() -> Void)? = nil
    
    private let session = SessionManager.shared
    @State private var tickets: [FieldTicketDTO] = []
    @State private var technicians: [TechnicianDTO] = []
    @State private var customers: [CustomerDTO] = []
    @State private var isLoading = true
    @State private var isRefreshing = false
    @State private var selectedFilter: String = TicketFilters.defaultFilter(isAdmin: SessionManager.shared.isAdmin)
    @State private var selectedTicket: FieldTicketDTO?
    @State private var showCreateTicket = false
    @State private var showBulkAssign = false
    @State private var errorMessage: String?
    
    private var isAdmin: Bool { session.isAdmin }
    private var availableFilters: [String] {
        isAdmin ? TicketFilters.adminFilters : TicketFilters.technicianFilters
    }
    
    private var filteredTickets: [FieldTicketDTO] {
        tickets.filter { TicketFilters.matches($0, filter: selectedFilter, isAdmin: isAdmin) }
    }
    
    private var pendingUnassigned: [FieldTicketDTO] {
        TicketFilters.pendingUnassigned(tickets)
    }
    
    var body: some View {
        VStack(spacing: 0) {
            headerSection
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(availableFilters, id: \.self) { filter in
                        filterPill(filter)
                    }
                }
                .padding(.horizontal)
                .padding(.vertical, 12)
            }
            .background(.ultraThinMaterial)
            
            if isAdmin {
                adminActionBar
                    .padding(.horizontal)
                    .padding(.bottom, 8)
            }
            
            if isLoading && tickets.isEmpty {
                Spacer()
                ProgressView("İş emirleri yükleniyor...")
                Spacer()
            } else if filteredTickets.isEmpty {
                Spacer()
                ContentUnavailableView(
                    isAdmin ? "İş emri yok" : "Atanmış iş bulunamadı",
                    systemImage: "tray",
                    description: Text(selectedFilter == "Tümü" ? "Yeni bir fiş açıldığında burada görünecek." : "Bu filtrede sonuç yok.")
                )
                Spacer()
            } else {
                List(filteredTickets) { ticket in
                    TicketCardView(ticket: ticket, isAdmin: isAdmin, technicians: technicians) { techId in
                        Task { await assignTechnician(ticketId: ticket.id, technicianId: techId) }
                    }
                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                    .listRowSeparator(.hidden)
                    .onTapGesture { selectedTicket = ticket }
                }
                .listStyle(.plain)
                .refreshable { await loadTickets(refresh: true) }
            }
        }
        .navigationTitle(isAdmin ? "Operasyon" : "İşlerim")
        .task { await loadTickets() }
        .onAppear {
            if let filter = requestedFilter ?? AppNavigation.shared.consumeOperationFilter(),
               availableFilters.contains(filter) {
                selectedFilter = filter
                onRequestedFilterApplied?()
            }
        }
        .onChange(of: session.isAdmin) { _, _ in
            selectedFilter = TicketFilters.defaultFilter(isAdmin: session.isAdmin)
        }
        .sheet(item: $selectedTicket) { ticket in
            NavigationStack {
                TicketDetailView(ticket: ticket, isAdmin: isAdmin, technicians: technicians) {
                    await loadTickets(refresh: true)
                }
            }
        }
        .sheet(isPresented: $showCreateTicket) {
            CreateTicketSheet(customers: customers, technicians: technicians) {
                await loadTickets(refresh: true)
            }
            .task {
                if customers.isEmpty {
                    customers = (try? await CustomerService.getCustomers()) ?? []
                }
            }
        }
        .sheet(isPresented: $showBulkAssign) {
            BulkAssignSheet(tickets: pendingUnassigned, technicians: technicians) { ticketIds, techId in
                await bulkAssign(ticketIds: ticketIds, technicianId: techId)
            }
        }
        .alert("Hata", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Tamam", role: .cancel) { errorMessage = nil }
        } message: {
            Text(errorMessage ?? "")
        }
    }
    
    private var headerSection: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(isAdmin ? "Operasyon" : "İşlerim")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                Text("\(tickets.count) iş emri")
                    .font(.title3.weight(.bold))
                if isAdmin {
                    Text("\(pendingUnassigned.count) atama bekliyor")
                        .font(.caption)
                        .foregroundStyle(.orange)
                }
            }
            Spacer()
        }
        .padding(.horizontal)
        .padding(.top, 12)
    }
    
    private var adminActionBar: some View {
        HStack(spacing: 12) {
            Button(action: { showCreateTicket = true }) {
                Label("Servis Fişi Oluştur", systemImage: "plus.circle.fill")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.borderedProminent)
            .tint(.cyan)
            .readOnlyProtected()
            
            if !pendingUnassigned.isEmpty {
                Button(action: { showBulkAssign = true }) {
                    Text("Toplu Atama (\(pendingUnassigned.count))")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.bordered)
                .readOnlyProtected()
            }
        }
    }
    
    private func filterPill(_ title: String) -> some View {
        Button(action: { selectedFilter = title }) {
            Text(title)
                .font(.subheadline.weight(.medium))
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(selectedFilter == title ? Color.cyan : Color(.systemGray5))
                .foregroundColor(selectedFilter == title ? .white : .primary)
                .clipShape(Capsule())
        }
    }
    
    private func loadTickets(refresh: Bool = false) async {
        if refresh { isRefreshing = true } else { isLoading = true }
        do {
            if isAdmin {
                async let ticketsTask = TicketService.getAllTickets()
                async let techTask = TicketService.getTechnicians()
                let (loadedTickets, loadedTechs) = try await (ticketsTask, techTask)
                await MainActor.run {
                    tickets = loadedTickets
                    technicians = loadedTechs
                    isLoading = false
                    isRefreshing = false
                }
            } else {
                let loaded = try await TicketService.getMyAssignedTickets()
                await MainActor.run {
                    tickets = loaded
                    isLoading = false
                    isRefreshing = false
                }
            }
        } catch {
            await MainActor.run {
                errorMessage = error.localizedDescription
                isLoading = false
                isRefreshing = false
            }
        }
    }
    
    private func assignTechnician(ticketId: Int, technicianId: Int) async {
        do {
            let updated = try await TicketService.assignTechnician(ticketId: ticketId, technicianId: technicianId)
            await MainActor.run {
                tickets = tickets.map { $0.id == ticketId ? updated : $0 }
            }
        } catch {
            await MainActor.run { errorMessage = error.localizedDescription }
        }
    }
    
    private func bulkAssign(ticketIds: [Int], technicianId: Int) async {
        do {
            let updated = try await TicketService.assignTechnicianBulk(ticketIds: ticketIds, technicianId: technicianId)
            let map = Dictionary(uniqueKeysWithValues: updated.map { ($0.id, $0) })
            await MainActor.run {
                tickets = tickets.map { map[$0.id] ?? $0 }
                showBulkAssign = false
            }
        } catch {
            await MainActor.run { errorMessage = error.localizedDescription }
        }
    }
}

// MARK: - Ticket Card

struct TicketCardView: View {
    let ticket: FieldTicketDTO
    var isAdmin: Bool = false
    var technicians: [TechnicianDTO] = []
    var onAssignTechnician: ((Int) -> Void)? = nil
    
    @State private var selectedTechId: Int?
    
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Label(displayStatus, systemImage: statusIcon)
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
            
            if let name = ticket.customerName {
                Text(name).font(.headline)
            }
            
            if let desc = ticket.description, !desc.isEmpty {
                Text(desc)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
            
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
                
                if ticket.hasOutstandingBalance {
                    HStack(spacing: 4) {
                        Image(systemName: "exclamationmark.triangle.fill")
                        Text("\(formatCurrency(ticket.customerBalance)) Cari Borç")
                    }
                    .font(.caption.weight(.semibold))
                    .foregroundColor(.orange)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(.orange.opacity(0.1))
                    .clipShape(Capsule())
                }
            }
            
            if isAdmin, let onAssign = onAssignTechnician, !technicians.isEmpty,
               ticket.statusEnum == .pending || ticket.assignedTechnicianId == nil {
                Menu {
                    ForEach(technicians) { tech in
                        Button(tech.fullName ?? "Teknisyen #\(tech.id)") {
                            onAssign(tech.id)
                        }
                    }
                } label: {
                    Label(ticket.assignedTechnicianName ?? "Teknisyen Ata", systemImage: "person.badge.plus")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(.cyan)
                }
                .readOnlyProtected()
            } else if let techName = ticket.assignedTechnicianName {
                Label(techName, systemImage: "person.fill")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(.systemBackground))
                .shadow(color: .black.opacity(0.06), radius: 8, y: 2)
        )
    }
    
    private var displayStatus: String {
        switch ticket.status?.uppercased() ?? "" {
        case "PENDING": return "Bekliyor"
        case "ASSIGNED": return "Atandı"
        case "IN_PROGRESS": return "Devam Ediyor"
        case "COMPLETED": return "Tamamlandı"
        case "CANCELLED": return "İptal"
        default: return ticket.statusEnum.displayName
        }
    }
    
    private var statusIcon: String {
        switch ticket.status?.uppercased() ?? "" {
        case "ASSIGNED": return "person.badge.clock"
        default: return ticket.statusEnum.iconName
        }
    }
    
    private var statusColor: Color {
        switch ticket.status?.uppercased() ?? "" {
        case "COMPLETED": return .green
        case "IN_PROGRESS": return .blue
        case "CANCELLED": return .red
        case "PENDING", "ASSIGNED": return .orange
        default: return .orange
        }
    }
    
    private func formatDate(_ dateString: String) -> String {
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

// MARK: - Create Ticket Sheet

struct CreateTicketSheet: View {
    let customers: [CustomerDTO]
    let technicians: [TechnicianDTO]
    let onCreated: () async -> Void
    
    @Environment(\.dismiss) private var dismiss
    @State private var selectedCustomerId: Int?
    @State private var description = ""
    @State private var notes = ""
    @State private var selectedTechId: Int?
    @State private var isSaving = false
    @State private var errorMessage: String?
    
    var body: some View {
        NavigationStack {
            Form {
                Section("Müşteri") {
                    Picker("Müşteri Seç", selection: $selectedCustomerId) {
                        Text("Seçiniz").tag(Optional<Int>.none)
                        ForEach(customers) { customer in
                            Text(customer.name).tag(Optional(customer.id))
                        }
                    }
                }
                
                Section("İş Detayı") {
                    TextField("Açıklama", text: $description, axis: .vertical)
                        .lineLimit(3...6)
                    TextField("Notlar (opsiyonel)", text: $notes, axis: .vertical)
                        .lineLimit(2...4)
                }
                
                if !technicians.isEmpty {
                    Section("Teknisyen") {
                        Picker("Atama", selection: $selectedTechId) {
                            Text("Atama yok").tag(Optional<Int>.none)
                            ForEach(technicians) { tech in
                                Text(tech.fullName ?? "Teknisyen").tag(Optional(tech.id))
                            }
                        }
                    }
                }
            }
            .navigationTitle("Servis Fişi Oluştur")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("İptal") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Oluştur") { Task { await createTicket() } }
                        .disabled(selectedCustomerId == nil || description.trimmingCharacters(in: .whitespaces).isEmpty || isSaving)
                }
            }
            .readOnlyProtected()
        }
    }
    
    private func createTicket() async {
        guard let customerId = selectedCustomerId else { return }
        isSaving = true
        do {
            let request = CreateTicketRequest(
                customerId: customerId,
                description: description,
                notes: notes.isEmpty ? nil : notes,
                assignedTechnicianId: selectedTechId
            )
            _ = try await TicketService.createTicket(request)
            await onCreated()
            await MainActor.run { dismiss() }
        } catch {
            await MainActor.run {
                errorMessage = error.localizedDescription
                isSaving = false
            }
        }
    }
}

// MARK: - Bulk Assign Sheet

struct BulkAssignSheet: View {
    let tickets: [FieldTicketDTO]
    let technicians: [TechnicianDTO]
    let onAssign: ([Int], Int) async -> Void
    
    @Environment(\.dismiss) private var dismiss
    @State private var selectedTicketIds: Set<Int> = []
    @State private var selectedTechId: Int?
    @State private var isAssigning = false
    
    var body: some View {
        NavigationStack {
            List {
                Section("Teknisyen") {
                    Picker("Teknisyen", selection: $selectedTechId) {
                        Text("Seçiniz").tag(Optional<Int>.none)
                        ForEach(technicians) { tech in
                            Text(tech.fullName ?? "Teknisyen").tag(Optional(tech.id))
                        }
                    }
                }
                
                Section("Atama Bekleyen Fişler") {
                    ForEach(tickets) { ticket in
                        Button(action: { toggle(ticket.id) }) {
                            HStack {
                                Image(systemName: selectedTicketIds.contains(ticket.id) ? "checkmark.circle.fill" : "circle")
                                    .foregroundColor(selectedTicketIds.contains(ticket.id) ? .cyan : .secondary)
                                VStack(alignment: .leading) {
                                    Text(ticket.customerName ?? "Müşteri")
                                    Text(ticket.description ?? "")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(1)
                                }
                            }
                        }
                        .foregroundColor(.primary)
                    }
                }
            }
            .navigationTitle("Toplu Atama")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("İptal") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Ata") {
                        guard let techId = selectedTechId else { return }
                        isAssigning = true
                        Task {
                            await onAssign(Array(selectedTicketIds), techId)
                            await MainActor.run { dismiss() }
                        }
                    }
                    .disabled(selectedTicketIds.isEmpty || selectedTechId == nil || isAssigning)
                }
            }
            .readOnlyProtected()
        }
    }
    
    private func toggle(_ id: Int) {
        if selectedTicketIds.contains(id) {
            selectedTicketIds.remove(id)
        } else {
            selectedTicketIds.insert(id)
        }
    }
}

#Preview {
    NavigationStack {
        TicketListView()
    }
}
