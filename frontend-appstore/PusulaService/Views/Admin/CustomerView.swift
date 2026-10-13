import SwiftUI

@MainActor
struct CustomerView: View {
#if DEBUG
    var readFixture: CustomerRefreshFixture? = nil
#endif
    @StateObject private var session = SessionManager.shared
    @State private var customers: [CustomerDTO] = []
    @State private var searchText = ""
    @State private var refreshRequest = RefreshRequestState()
    @State private var loadedSession: AuthenticatedSessionSnapshot?
    @State private var hasLoaded = false
    @State private var hasAttemptedLoad = false
    @State private var loadError: String?
    @State private var editingCustomer: CustomerDTO?
    @State private var selectedCustomer: CustomerDTO?
    @State private var showCreate = false
    @State private var ticketCustomer: CustomerDTO?
    @State private var customerPendingDeletion: CustomerDTO?
    @State private var isDeletingCustomer = false
    @State private var errorMessage: String?

    private var readSnapshot: AuthenticatedSessionSnapshot? {
#if DEBUG
        if CustomerRefreshFixture.isEnabled, let readFixture { return readFixture.snapshot }
#endif
        return session.authenticatedSnapshot
    }

    private var isReadFixture: Bool {
#if DEBUG
        CustomerRefreshFixture.isEnabled && readFixture != nil
#else
        false
#endif
    }
    
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
            
            // Keep the refreshable List mounted while its refresh action runs.
            // Replacing it with a spinner cancels the action that owns the read.
            List {
                if let loadError {
                    Section {
                        CustomerReadFailureView(
                            titleKey: "customers.load_failed",
                            message: loadError,
                            retryIdentifier: "customers.load.retry",
                            isLoading: refreshRequest.isLoading,
                            showsCachedRecords: hasLoaded && !customers.isEmpty
                        ) { await load() }
                    }
                }
                ForEach(filtered) { customer in
                    customerRow(customer)
                }
            }
            .listStyle(.plain)
            // Pull-to-refresh must also be reachable for an empty/short list.
            .scrollBounceBehavior(.always, axes: .vertical)
            .accessibilityIdentifier("customers.list")
            .refreshable { await load() }
            .overlay {
                if !hasLoaded && (refreshRequest.isLoading || (!hasAttemptedLoad && readSnapshot != nil)) {
                    ProgressView()
                        .accessibilityIdentifier("customers.loading")
                } else if !hasLoaded && loadError == nil {
                    CustomerReadFailureView(
                        titleKey: "customers.load_failed",
                        message: String(localized: "customers.load_unavailable"),
                        retryIdentifier: "customers.load.retry",
                        isLoading: false
                    ) { await load() }
                    .padding()
                } else if hasLoaded && loadError == nil && customers.isEmpty {
                    ContentUnavailableView(
                        "customers.empty", systemImage: "person.2",
                        description: Text("customers.empty.description")
                    )
                    .accessibilityIdentifier("customers.empty")
                    .allowsHitTesting(false)
                } else if hasLoaded && loadError == nil && filtered.isEmpty {
                    ContentUnavailableView(
                        "customers.search.empty", systemImage: "magnifyingglass",
                        description: Text("customers.search.empty.description")
                    )
                    .allowsHitTesting(false)
                }
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
                .disabled(isReadFixture)
            }
        }
        .task(id: readSnapshot) { await load() }
        .onDisappear { refreshRequest.invalidate() }
        .sheet(isPresented: $showCreate) {
            CustomerEditorSheet(customer: nil) { await load() }
        }
        .sheet(item: $editingCustomer) { customer in
            CustomerEditorSheet(customer: customer) { await load() }
        }
        .sheet(item: $selectedCustomer) { customer in
#if DEBUG
            CustomerDetailSheet(customer: customer, readFixture: readFixture)
#else
            CustomerDetailSheet(customer: customer)
#endif
        }
        .sheet(item: $ticketCustomer) { customer in
            CreateTicketFromCustomerSheet(customer: customer) { await load() }
        }
        .alert("Hata", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Tamam", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
        .confirmationDialog(
            "Müşteri silinsin mi?",
            isPresented: Binding(
                get: { customerPendingDeletion != nil },
                set: { if !$0 { customerPendingDeletion = nil } }
            ),
            titleVisibility: .visible,
            presenting: customerPendingDeletion
        ) { customer in
            Button("Müşteriyi Sil", role: .destructive) {
                Task { await deleteCustomer(customer) }
            }
            Button("Vazgeç", role: .cancel) { customerPendingDeletion = nil }
        } message: { customer in
            Text("\(customer.name) aktif müşteri listesinden kaldırılacak. Geçmiş iş, teklif veya cari kaydı bulunan müşteriler kayıt bütünlüğü için silinemez.")
        }
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
            .accessibilityIdentifier("customers.card.\(customer.id ?? 0)")

            HStack(spacing: 12) {
                Button(action: { ticketCustomer = customer }) {
                    Label("Servis Fişi Aç", systemImage: "doc.badge.plus")
                }
                .readOnlyProtected()
                .disabled(isReadFixture)

                Spacer()

                Menu {
                    Button { editingCustomer = customer } label: {
                        Label("Düzenle", systemImage: "pencil")
                    }
                    Button(role: .destructive) { customerPendingDeletion = customer } label: {
                        Label("Sil", systemImage: "trash")
                    }
                } label: {
                    Label("İşlemler", systemImage: "ellipsis.circle")
                }
                .disabled(isDeletingCustomer || isReadFixture)
                .readOnlyProtected()
            }
            .font(.caption.weight(.semibold))
            .buttonStyle(.borderless)
        }
        .padding(.vertical, 6)
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button(role: .destructive) { customerPendingDeletion = customer } label: {
                Label("Sil", systemImage: "trash")
            }
            .disabled(session.isReadOnly || isDeletingCustomer || isReadFixture)

            Button { editingCustomer = customer } label: {
                Label("Düzenle", systemImage: "pencil")
            }
            .tint(PusulaTheme.accent)
            .disabled(session.isReadOnly || isReadFixture)
        }
        .contextMenu {
            Button { editingCustomer = customer } label: {
                Label("Müşteriyi Düzenle", systemImage: "pencil")
            }
            .disabled(session.isReadOnly || isReadFixture)
            Button { ticketCustomer = customer } label: {
                Label("Servis Fişi Aç", systemImage: "doc.badge.plus")
            }
            .disabled(session.isReadOnly || isReadFixture)
            Divider()
            Button(role: .destructive) { customerPendingDeletion = customer } label: {
                Label("Müşteriyi Sil", systemImage: "trash")
            }
            .disabled(session.isReadOnly || isDeletingCustomer || isReadFixture)
        }
    }

    @MainActor
    private func deleteCustomer(_ customer: CustomerDTO) async {
        guard let id = customer.id, !session.isReadOnly, !isDeletingCustomer else { return }
        isDeletingCustomer = true
        defer {
            isDeletingCustomer = false
            customerPendingDeletion = nil
        }
        do {
            try await CustomerService.deleteCustomer(id: id)
            customers.removeAll { $0.id == id }
        } catch {
            errorMessage = error.localizedDescription
        }
    }
    
    private func load() async {
        guard !Task.isCancelled, let snapshot = readSnapshot,
              let request = refreshRequest.begin(for: snapshot) else { return }
        hasAttemptedLoad = true
        defer { refreshRequest.finish(request) }
        if loadedSession != snapshot {
            customers = []
            hasLoaded = false
            loadError = nil
            loadedSession = snapshot
        }
        do {
            let loaded = try await readCustomers(snapshot: snapshot)
            guard refreshRequest.accepts(request, session: readSnapshot,
                                         isCancelled: Task.isCancelled) else { return }
            customers = loaded
            hasLoaded = true
            loadError = nil
        } catch {
            guard refreshRequest.accepts(request, session: readSnapshot,
                                         isCancelled: Task.isCancelled),
                  !LoadCancellationPolicy.isCancellation(error) else { return }
            loadError = customerReadFailureMessage(
                error, fallback: String(localized: "customers.load_unavailable")
            )
        }
    }

    private func readCustomers(snapshot: AuthenticatedSessionSnapshot) async throws -> [CustomerDTO] {
#if DEBUG
        if CustomerRefreshFixture.isEnabled, let readFixture {
            return try await readFixture.loadCustomers()
        }
#endif
        return try await CustomerService.getCustomers(authToken: snapshot.token)
    }
}

@MainActor
private struct CustomerDetailSheet: View {
    let customer: CustomerDTO
#if DEBUG
    var readFixture: CustomerRefreshFixture? = nil
#endif

    @Environment(\.dismiss) private var dismiss
    @StateObject private var session = SessionManager.shared
    @State private var tickets: [FieldTicketDTO] = []
    @State private var selectedTicket: FieldTicketDTO?
    @State private var refreshRequest = RefreshRequestState()
    @State private var loadedSession: AuthenticatedSessionSnapshot?
    @State private var hasLoaded = false
    @State private var hasAttemptedLoad = false
    @State private var loadError: String?

    private var readSnapshot: AuthenticatedSessionSnapshot? {
#if DEBUG
        if CustomerRefreshFixture.isEnabled, let readFixture { return readFixture.snapshot }
#endif
        return session.authenticatedSnapshot
    }

    private var isReadFixture: Bool {
#if DEBUG
        CustomerRefreshFixture.isEnabled && readFixture != nil
#else
        false
#endif
    }

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

                if !isReadFixture, session.isAdmin, let customerId = customer.id {
                    Section("WhatsApp Bilgilendirme") {
                        CustomerWhatsAppConsentSection(customerId: customerId)
                    }
                }

                Section("İşlem Geçmişi") {
                    if let loadError {
                        CustomerReadFailureView(
                            titleKey: "customers.history.load_failed",
                            message: loadError,
                            retryIdentifier: "customers.history.retry",
                            isLoading: refreshRequest.isLoading,
                            showsCachedRecords: hasLoaded && !tickets.isEmpty
                        ) { await loadHistory() }
                    }
                    if !hasLoaded && (refreshRequest.isLoading || (!hasAttemptedLoad && readSnapshot != nil && loadError == nil)) {
                        HStack { Spacer(); ProgressView(); Spacer() }
                    } else if !hasLoaded && loadError == nil {
                        CustomerReadFailureView(
                            titleKey: "customers.history.load_failed",
                            message: String(localized: "customers.history.load_unavailable"),
                            retryIdentifier: "customers.history.retry",
                            isLoading: false
                        ) { await loadHistory() }
                    } else if hasLoaded && tickets.isEmpty && loadError == nil {
                        ContentUnavailableView(
                            "İşlem geçmişi yok",
                            systemImage: "clock.arrow.circlepath",
                            description: Text("Bu müşteri için henüz servis kaydı bulunmuyor.")
                        )
                        .accessibilityIdentifier("customers.history.empty")
                    }
                    if !tickets.isEmpty {
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
                            .disabled(isReadFixture)
                            .accessibilityIdentifier("customers.history.ticket.\(ticket.id)")
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
            .task(id: readSnapshot) { await loadHistory() }
            .onDisappear { refreshRequest.invalidate() }
            .refreshable { await loadHistory() }
            .sheet(item: $selectedTicket) { ticket in
                NavigationStack {
                    TicketDetailView(ticket: ticket, isAdmin: true, onComplete: { await loadHistory() })
                }
            }
        }
    }

    @MainActor
    private func loadHistory() async {
        guard let customerId = customer.id else {
            loadError = String(localized: "customers.history.missing_id")
            return
        }
        guard !Task.isCancelled, let snapshot = readSnapshot,
              let request = refreshRequest.begin(for: snapshot) else { return }
        hasAttemptedLoad = true
        defer { refreshRequest.finish(request) }
        if loadedSession != snapshot {
            tickets = []
            hasLoaded = false
            loadError = nil
            loadedSession = snapshot
        }
        do {
            let loaded = try await readHistory(customerId: customerId, snapshot: snapshot)
            guard refreshRequest.accepts(request, session: readSnapshot,
                                         isCancelled: Task.isCancelled) else { return }
            tickets = loaded
            hasLoaded = true
            loadError = nil
        } catch {
            guard refreshRequest.accepts(request, session: readSnapshot,
                                         isCancelled: Task.isCancelled),
                  !LoadCancellationPolicy.isCancellation(error) else { return }
            loadError = customerReadFailureMessage(
                error, fallback: String(localized: "customers.history.load_unavailable")
            )
        }
    }

    private func readHistory(customerId: Int, snapshot: AuthenticatedSessionSnapshot) async throws -> [FieldTicketDTO] {
#if DEBUG
        if CustomerRefreshFixture.isEnabled, let readFixture {
            return try await readFixture.loadHistory()
        }
#endif
        return try await CustomerService.getServiceHistory(customerId: customerId, authToken: snapshot.token)
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

@MainActor
private struct CustomerWhatsAppConsentSection: View {
    let customerId: Int

    @StateObject private var session = SessionManager.shared
    @State private var consent: CustomerWhatsAppConsentDTO?
    @State private var consentRequest = RefreshRequestState()
    @State private var loadedSession: AuthenticatedSessionSnapshot?
    @State private var isSaving = false
    @State private var errorMessage: String?
    @State private var showSourcePicker = false
    @State private var showRevokeConfirmation = false

    private var isLoading: Bool { consentRequest.isLoading }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if isLoading {
                HStack(spacing: 10) {
                    ProgressView()
                    Text("İzin durumu kontrol ediliyor…")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            } else if let consent {
                statusHeader(consent)

                if consent.optedIn {
                    consentDetail("İzin tarihi", value: formattedDate(consent.optedInAt) ?? "—")
                    consentDetail("İzin kaynağı", value: consent.sourceTitle ?? "—")
                } else {
                    consentDetail("Son izin tarihi", value: formattedDate(consent.optedInAt) ?? "—")
                    consentDetail("Son izin kaynağı", value: consent.sourceTitle ?? "—")
                    consentDetail("İptal tarihi", value: formattedDate(consent.optedOutAt) ?? "—")
                }

                if let errorMessage {
                    PusulaInlineMessage(text: errorMessage)
                }

                if !session.isReadOnly {
                    if consent.optedIn {
                        Button(role: .destructive) {
                            showRevokeConfirmation = true
                        } label: {
                            Label("İzni İptal Et", systemImage: "xmark.shield")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                        .disabled(isSaving)
                    } else {
                        Button {
                            showSourcePicker = true
                        } label: {
                            Label("İzin Kaydet", systemImage: "checkmark.shield")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(PusulaTheme.accent)
                        .disabled(isSaving)
                    }
                }

                if isSaving {
                    HStack(spacing: 8) {
                        ProgressView()
                        Text("İzin durumu güncelleniyor…")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            } else {
                PusulaInlineMessage(
                    text: errorMessage ?? "WhatsApp izin durumu alınamadı.",
                    kind: .warning
                )
                Button("Yeniden Dene") {
                    Task { await loadConsent() }
                }
                .font(.subheadline.weight(.semibold))
                .disabled(isLoading)
            }
        }
        .task(id: CustomerConsentReadIdentity(customerId: customerId, session: session.authenticatedSnapshot)) {
            await loadConsent()
        }
        .onDisappear { consentRequest.invalidate() }
        .confirmationDialog(
            "İzin kaynağını seçin",
            isPresented: $showSourcePicker,
            titleVisibility: .visible
        ) {
            ForEach(WhatsAppConsentSource.allCases) { source in
                Button(source.title) {
                    Task { await updateConsent(optedIn: true, source: source) }
                }
            }
            Button("Vazgeç", role: .cancel) { }
        } message: {
            Text("Müşterinin WhatsApp bilgilendirmeleri için açık izin verdiği yöntemi seçin.")
        }
        .confirmationDialog(
            "WhatsApp izni iptal edilsin mi?",
            isPresented: $showRevokeConfirmation,
            titleVisibility: .visible
        ) {
            Button("İzni İptal Et", role: .destructive) {
                Task { await updateConsent(optedIn: false, source: nil) }
            }
            Button("Vazgeç", role: .cancel) { }
        } message: {
            Text("Bu müşteriye yeni otomatik servis bilgilendirme mesajları gönderilmeyecek.")
        }
    }

    private func statusHeader(_ consent: CustomerWhatsAppConsentDTO) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: consent.optedIn ? "checkmark.shield.fill" : "shield.slash.fill")
                .font(.title3)
                .foregroundStyle(consent.optedIn ? Color.green : Color.orange)
                .frame(width: 28)

            VStack(alignment: .leading, spacing: 3) {
                Text(consent.optedIn ? "Bilgilendirme izni açık" : "Bilgilendirme izni kapalı")
                    .font(.subheadline.weight(.semibold))
                Text(consent.optedIn
                     ? "Otomatik servis bildirimleri gönderilebilir."
                     : "Otomatik WhatsApp mesajı gönderilmez.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func consentDetail(_ title: String, value: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            Spacer()
            Text(value)
                .font(.caption.weight(.medium))
                .multilineTextAlignment(.trailing)
        }
    }

    @MainActor
    private func loadConsent() async {
        guard session.isAdmin, !Task.isCancelled, let snapshot = session.authenticatedSnapshot,
              let request = consentRequest.begin(for: snapshot) else { return }
        defer { consentRequest.finish(request) }
        if loadedSession != snapshot {
            consent = nil
            errorMessage = nil
            loadedSession = snapshot
        }
        do {
            let loaded = try await CustomerService.getWhatsAppConsent(
                customerId: customerId, authToken: snapshot.token
            )
            guard consentRequest.accepts(request, session: session.authenticatedSnapshot,
                                         isCancelled: Task.isCancelled) else { return }
            consent = loaded
            errorMessage = nil
        } catch {
            guard consentRequest.accepts(request, session: session.authenticatedSnapshot,
                                         isCancelled: Task.isCancelled),
                  !LoadCancellationPolicy.isCancellation(error) else { return }
            errorMessage = customerReadFailureMessage(
                error, fallback: String(localized: "common.connection_error")
            )
        }
    }

    @MainActor
    private func updateConsent(optedIn: Bool, source: WhatsAppConsentSource?) async {
        guard session.isAdmin, !session.isReadOnly, !isSaving else { return }
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }
        do {
            consent = try await CustomerService.updateWhatsAppConsent(
                customerId: customerId,
                optedIn: optedIn,
                source: source
            )
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func formattedDate(_ raw: String?) -> String? {
        guard let date = TicketFilters.parseBusinessDate(raw) else { return raw }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "tr_TR")
        formatter.timeZone = TimeZone(identifier: "Europe/Istanbul")
        formatter.dateFormat = "d MMMM yyyy, HH:mm"
        return formatter.string(from: date)
    }
}

private struct CustomerConsentReadIdentity: Equatable {
    let customerId: Int
    let session: AuthenticatedSessionSnapshot?
}

@MainActor
private struct CustomerReadFailureView: View {
    let titleKey: String
    let message: String
    let retryIdentifier: String
    let isLoading: Bool
    var showsCachedRecords = false
    let retry: () async -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label(LocalizedStringKey(titleKey), systemImage: "exclamationmark.circle")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(message)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier(retryIdentifier + ".message")
            if showsCachedRecords {
                Text("common.cached_records")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Button {
                Task { await retry() }
            } label: {
                Label("customers.retry", systemImage: "arrow.clockwise")
            }
            .buttonStyle(.borderless)
            .disabled(isLoading)
            .accessibilityIdentifier(retryIdentifier)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 8)
    }
}

private func customerReadFailureMessage(_ error: Error, fallback: String) -> String {
    switch TicketDetailLoadFailure(error: error) {
    case .connection:
        return String(localized: "common.connection_error")
    case .secureConnection:
        return String(localized: "ticket.detail.secure_connection_error")
    case .unavailable:
        // Keep meaningful permission/quota errors; do not expose arbitrary
        // decoder details or internal URLs as a customer-facing error message.
        switch error {
        case NetworkError.forbidden, NetworkError.quotaExceeded:
            return error.localizedDescription
        default:
            return fallback
        }
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
