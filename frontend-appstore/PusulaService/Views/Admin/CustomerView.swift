import SwiftUI

struct CustomerView: View {
    @State private var customers: [CustomerDTO] = []
    @State private var searchText = ""
    @State private var isLoading = true
    @State private var editingCustomer: CustomerDTO?
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
        .sheet(item: $ticketCustomer) { customer in
            CreateTicketFromCustomerSheet(customer: customer) { await load() }
        }
        .alert("Hata", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Tamam", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }
    
    private func customerRow(_ customer: CustomerDTO) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(customer.name).font(.headline)
                Spacer()
                Button { editingCustomer = customer } label: {
                    Image(systemName: "pencil")
                        .frame(width: 32, height: 32)
                }
                    .accessibilityLabel("Müşteriyi düzenle")
                    .readOnlyProtected()
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
            
            Button(action: { ticketCustomer = customer }) {
                Label("Servis Fişi Aç", systemImage: "doc.badge.plus")
                    .font(.caption.weight(.semibold))
            }
            .readOnlyProtected()
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

struct CustomerEditorSheet: View {
    let customer: CustomerDTO?
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
            if let id = customer?.id {
                _ = try await CustomerService.updateCustomer(id: id, customer: dto)
            } else {
                _ = try await CustomerService.createCustomer(dto)
            }
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
