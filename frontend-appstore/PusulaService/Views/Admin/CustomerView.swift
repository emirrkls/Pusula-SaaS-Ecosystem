import SwiftUI

struct CustomerView: View {
    @State private var customers: [CustomerDTO] = []
    @State private var searchText = ""
    @State private var isLoading = true
    @State private var editingCustomer: CustomerDTO?
    @State private var selectedCustomer: CustomerDTO?
    @State private var showCreate = false
    @State private var ticketCustomer: CustomerDTO?
    @State private var errorMessage: String?
    
    private var filtered: [CustomerDTO] {
        guard !searchText.isEmpty else { return customers }
        return customers.filter {
            $0.name.localizedCaseInsensitiveContains(searchText) ||
            ($0.phone ?? "").localizedCaseInsensitiveContains(searchText) ||
            ($0.address ?? "").localizedCaseInsensitiveContains(searchText)
        }
    }
    
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                TextField("Müşteri ara...", text: $searchText)
            }
            .padding(10)
            .background(PusulaTheme.raisedSurface)
            .overlay {
                RoundedRectangle(cornerRadius: PusulaTheme.radius)
                    .stroke(PusulaTheme.border, lineWidth: 1)
            }
            .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
            .padding()
            
            if isLoading {
                Spacer()
                ProgressView()
                Spacer()
            } else {
                List(filtered) { customer in
                    customerRow(customer)
                }
                .listStyle(.plain)
                .refreshable { await load() }
            }
        }
        .background(PusulaTheme.page)
        .navigationTitle("Müşteriler")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: { showCreate = true }) {
                    Image(systemName: "plus")
                }
                .readOnlyProtected()
            }
        }
        .task { await load() }
        .sheet(isPresented: $showCreate) {
            CustomerEditorSheet(customer: nil) { await load() }
        }
        .sheet(item: $editingCustomer) { customer in
            CustomerEditorSheet(customer: customer) { await load() }
        }
        .sheet(item: $selectedCustomer) { customer in
            CustomerDetailSheet(customer: customer)
        }
        .sheet(item: $ticketCustomer) { customer in
            CreateTicketFromCustomerSheet(customer: customer) { await load() }
        }
        .alert("Hata", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Tamam", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }
    
    private func customerRow(_ customer: CustomerDTO) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Button { selectedCustomer = customer } label: {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text(customer.name).font(.headline)
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.tertiary)
                    }
                    if let phone = customer.phone, !phone.isEmpty {
                        Label(phone, systemImage: "phone.fill")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    if let address = customer.address, !address.isEmpty {
                        Label(address, systemImage: "mappin")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            HStack(spacing: 18) {
                Button { editingCustomer = customer } label: {
                    Label("Düzenle", systemImage: "pencil")
                }
                .accessibilityLabel("Müşteriyi düzenle")
                .readOnlyProtected()

                Button(action: { ticketCustomer = customer }) {
                    Label("Servis Fişi Aç", systemImage: "doc.badge.plus")
                }
                .readOnlyProtected()
            }
            .font(.caption.weight(.semibold))
            .buttonStyle(.borderless)
        }
        .padding(.vertical, 6)
    }
    
    private func load() async {
        isLoading = true
        do {
            customers = try await CustomerService.getCustomers()
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}

private struct CustomerDetailSheet: View {
    let customer: CustomerDTO

    @Environment(\.dismiss) private var dismiss
    @State private var tickets: [FieldTicketDTO] = []
    @State private var selectedTicket: FieldTicketDTO?
    @State private var isLoading = true
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            List {
                Section("Müşteri") {
                    LabeledContent("Ad / Firma", value: customer.name)
                    if let phone = customer.phone, !phone.isEmpty {
                        LabeledContent("Telefon", value: phone)
                    }
                    if let address = customer.address, !address.isEmpty {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Adres").font(.caption).foregroundStyle(.secondary)
                            Text(address)
                        }
                    }
                }

                Section("İşlem Geçmişi") {
                    if isLoading {
                        HStack { Spacer(); ProgressView(); Spacer() }
                    } else if tickets.isEmpty {
                        ContentUnavailableView(
                            "İşlem geçmişi yok",
                            systemImage: "clock.arrow.circlepath",
                            description: Text("Bu müşteri için henüz servis kaydı bulunmuyor.")
                        )
                    } else {
                        ForEach(tickets) { ticket in
                            Button { selectedTicket = ticket } label: {
                                VStack(alignment: .leading, spacing: 6) {
                                    HStack {
                                        Text("İş Emri #\(ticket.id)")
                                            .font(.subheadline.weight(.semibold))
                                        Spacer()
                                        Label(ticket.statusEnum.displayName, systemImage: ticket.statusEnum.iconName)
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    Text(ticket.description ?? "Açıklama bulunmuyor")
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(2)
                                    if let date = serviceDate(ticket) {
                                        Text(date)
                                            .font(.caption)
                                            .foregroundStyle(.tertiary)
                                    }
                                }
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .navigationTitle(customer.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Kapat") { dismiss() }
                }
            }
            .task { await loadHistory() }
            .refreshable { await loadHistory() }
            .sheet(item: $selectedTicket) { ticket in
                NavigationStack {
                    TicketDetailView(ticket: ticket, isAdmin: true, onComplete: { await loadHistory() })
                }
            }
            .alert("İşlem Geçmişi Yüklenemedi", isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )) {
                Button("Tamam", role: .cancel) { errorMessage = nil }
            } message: {
                Text(errorMessage ?? "")
            }
        }
    }

    @MainActor
    private func loadHistory() async {
        guard let customerId = customer.id else {
            isLoading = false
            return
        }
        isLoading = true
        do {
            tickets = try await CustomerService.getServiceHistory(customerId: customerId)
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    private func serviceDate(_ ticket: FieldTicketDTO) -> String? {
        let raw = ticket.completedAt ?? ticket.scheduledDate ?? ticket.createdAt
        guard let raw else { return nil }
        guard let date = TicketFilters.parseBusinessDate(raw) else { return raw }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "tr_TR")
        formatter.timeZone = TimeZone(identifier: "Europe/Istanbul")
        formatter.dateFormat = "d MMMM yyyy, HH:mm"
        return formatter.string(from: date)
    }
}

struct CustomerEditorSheet: View {
    let customer: CustomerDTO?
    var onCustomerSaved: ((CustomerDTO) -> Void)? = nil
    let onSaved: () async -> Void
    
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var phone = ""
    @State private var address = ""
    @State private var isSaving = false
    @State private var errorMessage: String?
    
    var body: some View {
        NavigationStack {
            Form {
                TextField("Ad Soyad / Firma", text: $name)
                TextField("Telefon", text: $phone).keyboardType(.phonePad)
                TextField("Adres", text: $address, axis: .vertical).lineLimit(2...4)
            }
            .navigationTitle(customer == nil ? "Yeni Müşteri" : "Müşteri Düzenle")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("İptal") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") {
                        Task { await save() }
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || isSaving)
                    .readOnlyProtected()
                }
            }
            .onAppear {
                name = customer?.name ?? ""
                phone = customer?.phone ?? ""
                address = customer?.address ?? ""
            }
            .alert("Müşteri Kaydedilemedi", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Tamam", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
        }
    }

    private func save() async {
        isSaving = true
        defer { isSaving = false }
        let dto = CustomerDTO(id: customer?.id, name: name, phone: phone.nilIfEmpty, address: address.nilIfEmpty, coordinates: customer?.coordinates)
        do {
            let savedCustomer: CustomerDTO
            if let id = customer?.id {
                savedCustomer = try await CustomerService.updateCustomer(id: id, customer: dto)
            } else {
                savedCustomer = try await CustomerService.createCustomer(dto)
            }
            onCustomerSaved?(savedCustomer)
            await onSaved()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

struct CreateTicketFromCustomerSheet: View {
    let customer: CustomerDTO
    let onCreated: () async -> Void
    
    @Environment(\.dismiss) private var dismiss
    @State private var description = ""
    @State private var notes = ""
    @State private var isCreating = false
    @State private var errorMessage: String?
    
    var body: some View {
        NavigationStack {
            Form {
                Section("Müşteri") {
                    Text(customer.name)
                }
                Section("İş") {
                    TextField("Açıklama", text: $description, axis: .vertical)
                    TextField("Notlar", text: $notes, axis: .vertical)
                }
            }
            .navigationTitle("Servis Fişi")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("İptal") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Oluştur") {
                        Task { await createTicket() }
                    }
                    .disabled(description.trimmingCharacters(in: .whitespaces).isEmpty || isCreating)
                    .readOnlyProtected()
                }
            }
            .alert("Servis Fişi Oluşturulamadı", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Tamam", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
        }
    }

    private func createTicket() async {
        guard let customerId = customer.id else {
            errorMessage = "Müşteri kimliği bulunamadı."
            return
        }
        isCreating = true
        defer { isCreating = false }
        do {
            let request = CreateTicketRequest(customerId: customerId, description: description, notes: notes.nilIfEmpty)
            _ = try await TicketService.createTicket(request)
            await onCreated()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

private extension String {
    var nilIfEmpty: String? {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : self
    }
}
