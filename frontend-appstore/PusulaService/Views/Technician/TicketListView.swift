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
    @State private var searchText = ""
    @State private var selectedTicket: FieldTicketDTO?
    @State private var showCreateTicket = false
    @State private var showBulkAssign = false
    @State private var errorMessage: String?
    
    private var isAdmin: Bool { session.isAdmin }
    private var availableFilters: [String] {
        isAdmin ? TicketFilters.adminFilters : TicketFilters.technicianFilters
    }
    
    private var filteredTickets: [FieldTicketDTO] {
        TicketFilters.sorted(tickets.filter {
            TicketFilters.matches($0, filter: selectedFilter, isAdmin: isAdmin) &&
            TicketFilters.matchesSearch($0, query: searchText)
        }, filter: selectedFilter)
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
            .background(PusulaTheme.page)
            .onboardingTarget(.ticketFilters)

            ticketSearchField
                .padding(.horizontal)
                .padding(.bottom, 10)
            
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
        .background(PusulaTheme.page)
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
            CreateTicketSheet(
                customers: customers,
                technicians: technicians,
                onCustomerCreated: { customer in
                    if let index = customers.firstIndex(where: { $0.id == customer.id }) {
                        customers[index] = customer
                    } else {
                        customers.append(customer)
                    }
                },
                onCreated: { await loadTickets(refresh: true) }
            )
            .task {
                if customers.isEmpty {
                    do {
                        customers = try await CustomerService.getCustomers()
                    } catch {
                        errorMessage = error.localizedDescription
                    }
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
                Text("\(filteredTickets.count) iş emri")
                    .font(.title3.weight(.bold))
                if !isAdmin {
                    Text(selectedFilter)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
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
        .onboardingTarget(.ticketSummary)
    }
    
    private var adminActionBar: some View {
        HStack(spacing: 12) {
            Button(action: { showCreateTicket = true }) {
                Label("Servis Fişi Oluştur", systemImage: "plus")
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 44)
            }
            .buttonStyle(.borderedProminent)
            .tint(PusulaTheme.accent)
            .readOnlyProtected()
            
            if !pendingUnassigned.isEmpty {
                Button(action: { showBulkAssign = true }) {
                    Text("Toplu Atama (\(pendingUnassigned.count))")
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 44)
                }
                .buttonStyle(.bordered)
                .readOnlyProtected()
            }
        }
        .onboardingTarget(.createTicketAction)
    }

    private var ticketSearchField: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(.secondary)
            TextField("Fiş no, müşteri, telefon, iş veya teknisyen ara", text: $searchText)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            if !searchText.isEmpty {
                Button(action: { searchText = "" }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(.secondary)
                }
                .accessibilityLabel("Aramayı temizle")
            }
        }
        .padding(.horizontal, 14)
        .frame(minHeight: 44)
        .background(PusulaTheme.raisedSurface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay {
            RoundedRectangle(cornerRadius: 12)
                .stroke(PusulaTheme.border, lineWidth: 1)
        }
    }
    
    private func filterPill(_ title: String) -> some View {
        Button(action: { selectedFilter = title }) {
            Text(title)
                .font(.subheadline.weight(.medium))
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(selectedFilter == title ? PusulaTheme.accent : PusulaTheme.raisedSurface)
                .foregroundColor(selectedFilter == title ? .white : .primary)
                .clipShape(Capsule())
                .overlay {
                    Capsule()
                        .stroke(selectedFilter == title ? Color.clear : PusulaTheme.border, lineWidth: 1)
                }
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
                    openPendingTicket(in: loadedTickets)
                    isLoading = false
                    isRefreshing = false
                }
            } else {
                let loaded = try await TicketService.getMyAssignedTickets()
                await MainActor.run {
                    tickets = loaded
                    openPendingTicket(in: loaded)
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

    private func openPendingTicket(in loadedTickets: [FieldTicketDTO]) {
        guard let ticketId = AppNavigation.shared.pendingTicketId,
              let ticket = loadedTickets.first(where: { $0.id == ticketId }) else { return }
        selectedTicket = ticket
        AppNavigation.shared.clearPendingTicket(id: ticketId)
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
                    Text(formatSchedule(start: date, end: ticket.scheduledEndDate))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            if isOverdue {
                Label("Geciken çağrı", systemImage: "clock.badge.exclamationmark")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(.red)
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
                        .foregroundColor(PusulaTheme.accent)
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
                        .foregroundColor(PusulaTheme.accent)
                }
                .readOnlyProtected()
            } else if let techName = ticket.assignedTechnicianName {
                Label(techName, systemImage: "person.fill")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .pusulaCard()
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
    
    private func formatSchedule(start: String, end: String?) -> String {
        guard let startDate = TicketFilters.parseBusinessDate(start) else { return start }
        let display = DateFormatter()
        display.locale = Locale(identifier: "tr_TR")
        display.timeZone = TimeZone(identifier: "Europe/Istanbul")
        display.dateFormat = "d MMM HH:mm"
        var value = display.string(from: startDate)
        if let endDate = TicketFilters.parseBusinessDate(end) {
            display.dateFormat = "HH:mm"
            value += "–\(display.string(from: endDate))"
        }
        return value
    }

    private var isOverdue: Bool {
        guard ticket.statusEnum == .assigned || ticket.statusEnum == .inProgress,
              let scheduled = TicketFilters.parseBusinessDate(ticket.scheduledDate) else { return false }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Istanbul") ?? .current
        return calendar.startOfDay(for: scheduled) < calendar.startOfDay(for: Date())
    }
}

// MARK: - Create Ticket Sheet

struct CreateTicketSheet: View {
    let customers: [CustomerDTO]
    let technicians: [TechnicianDTO]
    let onCustomerCreated: (CustomerDTO) -> Void
    let onCreated: () async -> Void
    
    @Environment(\.dismiss) private var dismiss
    @State private var createdCustomers: [CustomerDTO] = []
    @State private var selectedCustomerId: Int?
    @State private var customerSearch = ""
    @State private var showCreateCustomer = false
    @State private var description = ""
    @State private var notes = ""
    @State private var technicianPrivateNote = ""
    @State private var selectedTechId: Int?
    @State private var scheduledDay: Date
    @State private var scheduledStart: Date
    @State private var scheduledEnd: Date
    @State private var isSaving = false
    @State private var errorMessage: String?

    init(
        customers: [CustomerDTO],
        technicians: [TechnicianDTO],
        onCustomerCreated: @escaping (CustomerDTO) -> Void = { _ in },
        onCreated: @escaping () async -> Void
    ) {
        self.customers = customers
        self.technicians = technicians
        self.onCustomerCreated = onCustomerCreated
        self.onCreated = onCreated
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Istanbul") ?? .current
        let start = calendar.date(bySettingHour: 9, minute: 0, second: 0, of: Date()) ?? Date()
        _scheduledDay = State(initialValue: Date())
        _scheduledStart = State(initialValue: start)
        _scheduledEnd = State(initialValue: calendar.date(byAdding: .hour, value: 2, to: start) ?? start)
    }

    private var availableCustomers: [CustomerDTO] {
        var merged = customers
        for customer in createdCustomers {
            if let index = merged.firstIndex(where: { $0.id == customer.id }) {
                merged[index] = customer
            } else {
                merged.append(customer)
            }
        }
        return merged
    }

    private var filteredCustomers: [CustomerDTO] {
        let query = customerSearch.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return [] }

        return availableCustomers.filter {
            $0.name.localizedCaseInsensitiveContains(query) ||
            ($0.phone ?? "").localizedCaseInsensitiveContains(query) ||
            ($0.address ?? "").localizedCaseInsensitiveContains(query)
        }.sorted {
            $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
        }
    }

    private var selectedCustomer: CustomerDTO? {
        availableCustomers.first { $0.id == selectedCustomerId }
    }

    private var hasCustomerSearchQuery: Bool {
        !customerSearch.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
    
    var body: some View {
        NavigationStack {
            Form {
                Section("Müşteri") {
                    HStack {
                        Image(systemName: "magnifyingglass")
                            .foregroundStyle(.secondary)
                        TextField("Ad, telefon veya adres ara", text: $customerSearch)
                            .textInputAutocapitalization(.words)
                    }

                    if let selectedCustomer {
                        HStack {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(.green)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(selectedCustomer.name)
                                    .font(.subheadline.weight(.semibold))
                                if let phone = selectedCustomer.phone, !phone.isEmpty {
                                    Text(phone)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            Button("Değiştir") {
                                selectedCustomerId = nil
                                customerSearch = ""
                            }
                            .font(.caption.weight(.semibold))
                        }
                    } else if hasCustomerSearchQuery && filteredCustomers.isEmpty {
                        ContentUnavailableView(
                            "Müşteri bulunamadı",
                            systemImage: "person.crop.circle.badge.questionmark",
                            description: Text("Aramayı değiştirin veya yeni bir müşteri oluşturun.")
                        )
                    } else {
                        ForEach(filteredCustomers.prefix(8)) { customer in
                            Button {
                                selectedCustomerId = customer.id
                                customerSearch = ""
                            } label: {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(customer.name)
                                        .foregroundStyle(.primary)
                                    HStack(spacing: 8) {
                                        if let phone = customer.phone, !phone.isEmpty { Text(phone) }
                                        if let address = customer.address, !address.isEmpty {
                                            Text(address).lineLimit(1)
                                        }
                                    }
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }

                        if filteredCustomers.count > 8 {
                            Text("İlk 8 sonuç gösteriliyor. Aramayı daraltarak diğer sonuçlara ulaşabilirsiniz.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }

                    Button { showCreateCustomer = true } label: {
                        Label("Yeni Müşteri Oluştur", systemImage: "person.badge.plus")
                    }
                }
                
                Section("İş Detayı") {
                    TextField("Açıklama", text: $description, axis: .vertical)
                        .lineLimit(3...6)
                    TextField("Notlar (opsiyonel)", text: $notes, axis: .vertical)
                        .lineLimit(2...4)
                }

                Section("Planlanan Zaman") {
                    DatePicker("Servis tarihi", selection: $scheduledDay, displayedComponents: .date)
                    DatePicker("Başlangıç", selection: $scheduledStart, displayedComponents: .hourAndMinute)
                    DatePicker("Bitiş", selection: $scheduledEnd, displayedComponents: .hourAndMinute)
                    Text("Teknisyen bildirimi, servis başlangıcına 24 saat veya daha az kaldığında gönderilir.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                
                if !technicians.isEmpty {
                    Section("Teknisyen") {
                        Picker("Atama", selection: $selectedTechId) {
                            Text("Atama yok").tag(Optional<Int>.none)
                            ForEach(technicians) { tech in
                                Text(tech.fullName ?? "Teknisyen").tag(Optional(tech.id))
                            }
                        }
                        TextField("Teknisyene özel not", text: $technicianPrivateNote, axis: .vertical)
                            .lineLimit(2...5)
                        Label("Yalnızca yönetici ve atanan teknisyen görür. Müşteri PDF'ine eklenmez.", systemImage: "lock.fill")
                            .font(.caption)
                            .foregroundStyle(.secondary)
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
            .sheet(isPresented: $showCreateCustomer) {
                CustomerEditorSheet(
                    customer: nil,
                    onCustomerSaved: { customer in
                        if let index = createdCustomers.firstIndex(where: { $0.id == customer.id }) {
                            createdCustomers[index] = customer
                        } else {
                            createdCustomers.append(customer)
                        }
                        selectedCustomerId = customer.id
                        customerSearch = ""
                        onCustomerCreated(customer)
                    },
                    onSaved: { }
                )
            }
            .alert("Servis Fişi Oluşturulamadı", isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )) {
                Button("Tamam", role: .cancel) { errorMessage = nil }
            } message: {
                Text(errorMessage ?? "")
            }
        }
    }
    
    private func createTicket() async {
        guard let customerId = selectedCustomerId else { return }
        guard let start = combinedSchedule(day: scheduledDay, time: scheduledStart),
              let end = combinedSchedule(day: scheduledDay, time: scheduledEnd), end > start else {
            errorMessage = "Bitiş saati başlangıç saatinden sonra olmalıdır."
            return
        }
        isSaving = true
        do {
            let request = CreateTicketRequest(
                customerId: customerId,
                description: description,
                notes: notes.isEmpty ? nil : notes,
                technicianPrivateNote: technicianPrivateNote.isEmpty ? nil : technicianPrivateNote,
                assignedTechnicianId: selectedTechId,
                scheduledDate: backendDateTime(start),
                scheduledEndDate: backendDateTime(end)
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

    private func combinedSchedule(day: Date, time: Date) -> Date? {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Istanbul") ?? .current
        let dayParts = calendar.dateComponents([.year, .month, .day], from: day)
        let timeParts = calendar.dateComponents([.hour, .minute], from: time)
        return calendar.date(from: DateComponents(
            year: dayParts.year, month: dayParts.month, day: dayParts.day,
            hour: timeParts.hour, minute: timeParts.minute, second: 0
        ))
    }

    private func backendDateTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Europe/Istanbul")
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        return formatter.string(from: date)
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
                                    .foregroundColor(selectedTicketIds.contains(ticket.id) ? PusulaTheme.accent : .secondary)
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

struct TicketListView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationStack {
            TicketListView()
        }
    }
}
