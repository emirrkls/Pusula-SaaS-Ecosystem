import SwiftUI

@MainActor
struct ServiceNetworkView: View {
    @State private var context: ServiceNetworkContext?
    @State private var members: [ServiceNetworkMember] = []
    @State private var orders: [ServiceNetworkOrder] = []
    @State private var section = "incoming"
    @State private var search = ""
    @State private var status = ""
    @State private var dateFilter = false
    @State private var from = Date()
    @State private var to = Date()
    @State private var page = 0
    @State private var total = 0
    @State private var hasNext = false
    @State private var loading = false
    @State private var error: String?
    @State private var memberForm: Bool?
    @State private var showMemberForm = false

    var body: some View {
        List {
            if let error { Text(error).foregroundStyle(.red) }
            Section {
                Picker("Görünüm", selection: $section) {
                    Text("Gelen İşler").tag("incoming")
                    Text("Gönderilen İşler").tag("outgoing")
                    Text("Alt Servisler / Davetler").tag("members")
                }
                TextField("İsim, iş başlığı, firma veya iş no ara", text: $search)
                    .submitLabel(.search).onSubmit { refresh() }
                if section != "members" {
                    Picker("Durum", selection: $status) {
                        Text("Tümü").tag("")
                        ForEach(["SENT", "ACCEPTED", "REJECTED", "CANCELLED"], id: \.self) {
                            Text(ServiceNetworkAPI.label($0)).tag($0)
                        }
                    }
                    Toggle("Randevu tarihine göre filtrele", isOn: $dateFilter)
                    if dateFilter {
                        DatePicker("Başlangıç", selection: $from, displayedComponents: .date)
                        DatePicker("Bitiş", selection: $to, displayedComponents: .date)
                    }
                }
                HStack {
                    Button("Ara", action: refresh)
                    Spacer()
                    Button("Temizle") { search = ""; status = ""; dateFilter = false; refresh() }
                }.buttonStyle(.borderless)
            }
            if let context {
                Section {
                    if context.canManage {
                        Text("Alt servis: \(context.usedMembers) / \(context.maxMembers) · Bu ay gönderilen: \(context.usedMonthlyOrders) / \(context.maxMonthlyOrders)")
                            .font(.caption).foregroundStyle(.secondary)
                        Button("İşletme Davet Et") { memberForm = false; showMemberForm = true }
                            .disabled(!context.writable)
                        Button("Yeni Alt Servis Oluştur") { memberForm = true; showMemberForm = true }
                            .disabled(!context.writable)
                    } else {
                        Text("Ağ yönetimi işletmenize tanımlı değil. Gelen davetleri ve işleri yönetebilirsiniz.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                Section("\(total) kayıt · Sayfa \(page + 1)") {
                    if section == "members" {
                        ForEach(members) { member in
                            NavigationLink {
                                NetworkMemberDetail(member: member, context: context)
                            } label: {
                                VStack(alignment: .leading, spacing: 6) {
                                    Text(member.parentName + " → " + member.childName).font(.headline)
                                    Text(ServiceNetworkAPI.label(member.status) + " · " + (member.region ?? ""))
                                        .font(.caption).foregroundStyle(.secondary)
                                }.padding(.vertical, 4)
                            }
                        }
                    } else {
                        ForEach(orders) { order in
                            NavigationLink {
                                NetworkOrderDetail(id: order.id, context: context)
                            } label: { NetworkOrderRow(order: order) }
                        }
                    }
                    if !loading && (section == "members" ? members.isEmpty : orders.isEmpty) {
                        Text("Bu filtreye uygun kayıt yok.").foregroundStyle(.secondary)
                    }
                    HStack {
                        Button("Önceki") { page -= 1; Task { await load() } }.disabled(page == 0)
                        Spacer()
                        Button("Sonraki") { page += 1; Task { await load() } }.disabled(!hasNext)
                    }.buttonStyle(.borderless)
                }
            }
        }
        .navigationTitle("Servis Ağı")
        .environment(\.timeZone, TimeZone(identifier: "Europe/Istanbul")!)
        .disabled(loading)
        .overlay { if loading { ProgressView() } }
        .task { await load() }
        .refreshable { await load() }
        .onChange(of: section) { _, _ in refresh() }
        .sheet(isPresented: $showMemberForm, onDismiss: refresh) {
            NavigationStack { NetworkMemberForm(create: memberForm == true) }
        }
    }

    private func refresh() { page = 0; Task { await load() } }
    private func load() async {
        guard !loading else { return }
        loading = true; error = nil
        defer { loading = false }
        do {
            context = try await ServiceNetworkAPI.get("/context")
            var query = ["page": String(page), "query": search]
            if section == "members" {
                let result: ServiceNetworkPage<ServiceNetworkMember> = try await ServiceNetworkAPI.get(ServiceNetworkAPI.query("/members", query))
                members = result.items; total = result.totalElements; hasNext = result.hasNext
            } else {
                query["direction"] = section
                if !status.isEmpty { query["status"] = status }
                if dateFilter {
                    query["from"] = ServiceNetworkAPI.timestamp(from, dayOnly: true)
                    query["to"] = ServiceNetworkAPI.timestamp(to, dayOnly: true)
                }
                let result: ServiceNetworkPage<ServiceNetworkOrder> = try await ServiceNetworkAPI.get(ServiceNetworkAPI.query("/orders", query))
                orders = result.items; total = result.totalElements; hasNext = result.hasNext
            }
        } catch { self.error = error.localizedDescription }
    }
}

private struct NetworkOrderRow: View {
    let order: ServiceNetworkOrder
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("#\(order.id) · \(order.title)").font(.headline)
            Text(order.customerName).font(.subheadline)
            Text(order.parentName + " → " + order.childName).font(.caption).foregroundStyle(.secondary)
            Text(ServiceNetworkAPI.date(order.scheduledDate)).font(.caption)
            Text(ServiceNetworkAPI.label(order.status) + (order.ticketStatus.map { " · " + ServiceNetworkAPI.label($0) } ?? ""))
                .font(.caption.weight(.medium)).foregroundStyle(PusulaTheme.accent)
        }.padding(.vertical, 6)
    }
}

@MainActor
private struct NetworkMemberForm: View {
    let create: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var code = ""
    @State private var region = ""
    @State private var admin = ""
    @State private var username = ""
    @State private var password = ""
    @State private var receipt: ServiceNetworkCreatedChild?
    @State private var busy = false
    @State private var error: String?
    var body: some View {
        Form {
            if let error { Text(error).foregroundStyle(.red) }
            if let receipt {
                Section("Alt servis oluşturuldu") {
                    Text(receipt.member.childName)
                    Text("İşletme kodu: \(receipt.orgCode)").textSelection(.enabled)
                    Text("Kullanıcı adı: \(receipt.username)").textSelection(.enabled)
                    Text("Bu bilgileri alt servis yöneticisiyle güvenli şekilde paylaşın.").font(.caption)
                }
            } else {
                Section {
                    if create {
                        Text("Yeni işletme kendi Çırak planında 14 günlük deneme hesabı olarak açılır. Stok, finans ve aboneliği ana firmadan ayrıdır.").font(.caption)
                        TextField("İşletme adı", text: $name)
                        TextField("Yönetici adı soyadı", text: $admin)
                        TextField("Kullanıcı adı", text: $username).textInputAutocapitalization(.never).autocorrectionDisabled()
                        SecureField("Şifre: en az 8 karakter, harf ve rakam", text: $password)
                    } else {
                        Text("İşletmenin yöneticisi daveti kabul etmeden bağlantı etkinleşmez.").font(.caption)
                        TextField("İşletme kodu", text: $code).textInputAutocapitalization(.characters).autocorrectionDisabled()
                    }
                    TextField("Bölge / il", text: $region)
                }
            }
        }
        .navigationTitle(create ? "Yeni Alt Servis" : "İşletme Daveti")
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button(receipt == nil ? "Vazgeç" : "Bitti") { dismiss() }.disabled(busy) }
            ToolbarItem(placement: .confirmationAction) {
                if receipt == nil { Button("Kaydet") { Task { await save() } }.disabled(busy || (create ? name.isEmpty || admin.isEmpty || username.isEmpty || password.isEmpty : code.isEmpty)) }
            }
        }.disabled(busy).interactiveDismissDisabled(busy)
    }
    private func save() async {
        busy = true; error = nil; defer { busy = false }
        do {
            if create {
                receipt = try await ServiceNetworkAPI.post("/members/create", ["name": name, "region": region, "adminName": admin, "username": username, "password": password])
                password = ""
            } else {
                let _: ServiceNetworkMember = try await ServiceNetworkAPI.post("/members/invite", ["orgCode": code, "region": region])
                dismiss()
            }
        } catch { self.error = error.localizedDescription }
    }
}

@MainActor
private struct NetworkMemberDetail: View {
    @State var member: ServiceNetworkMember
    let context: ServiceNetworkContext
    @State private var busy = false
    @State private var error: String?
    @State private var close = false
    var body: some View {
        Form {
            if let error { Text(error).foregroundStyle(.red) }
            Section("Bağlantı") {
                Text(member.parentName + " → " + member.childName)
                Text(member.region ?? "")
                Text(ServiceNetworkAPI.label(member.status))
                Text("Oluşturulma: " + ServiceNetworkAPI.date(member.createdAt)).font(.caption)
            }
            Section {
                if member.status == "ACTIVE", member.parentCompanyId == context.companyId, context.canManage {
                    NavigationLink("Alt Servise İş Gönder") { NetworkDispatchForm(member: member) }
                }
                if member.status == "INVITED", member.childCompanyId == context.companyId {
                    Button("Daveti Kabul Et") { decide(true) }
                    Button("Daveti Reddet", role: .destructive) { decide(false) }
                }
                if ["ACTIVE", "INVITED"].contains(member.status) {
                    Button("Bağlantıyı Kapat", role: .destructive) { close = true }
                }
            }.disabled(!context.writable || busy)
        }.navigationTitle("Alt Servis")
        .sheet(isPresented: $close) {
            NavigationStack {
                NetworkNoteForm(title: "Bağlantıyı Kapat", explanation: "Bekleyen veya açık işler varsa bağlantı kapatılamaz.") { note in
                    member = try await ServiceNetworkAPI.post("/members/\(member.id)/close", ["note": note])
                }
            }
        }
    }
    private func decide(_ accept: Bool) {
        busy = true; error = nil
        Task {
            defer { busy = false }
            do { member = try await ServiceNetworkAPI.post("/members/\(member.id)/decision", ["accept": accept]) }
            catch { self.error = error.localizedDescription }
        }
    }
}

@MainActor
private struct NetworkDispatchForm: View {
    let member: ServiceNetworkMember
    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var name = ""
    @State private var phone = ""
    @State private var address = ""
    @State private var instruction = ""
    @State private var date = Date()
    @State private var end = Date().addingTimeInterval(3600)
    @State private var useEnd = false
    @State private var requestKey = UUID().uuidString
    @State private var busy = false
    @State private var error: String?
    var body: some View {
        Form {
            if let error { Text(error).foregroundStyle(.red) }
            Section {
                Text("Alıcı: " + member.childName)
                Text("Alt servis kabul ettiğinde kendi servis fişini açar. Gönderim tek başına gelir veya gider oluşturmaz.").font(.caption)
            }
            Section("İş ve müşteri") {
                TextField("İş emri başlığı", text: $title)
                TextField("Müşteri adı", text: $name)
                TextField("Telefon", text: $phone).keyboardType(.phonePad)
                TextField("Servis adresi", text: $address, axis: .vertical)
            }
            Section("Randevu · Türkiye saati") {
                DatePicker("Başlangıç", selection: $date)
                Toggle("Bitiş saati belirt", isOn: $useEnd)
                if useEnd { DatePicker("Bitiş", selection: $end) }
            }
            Section("Alt servis ve teknisyen için özel talimat") {
                TextField("İşle ilgili ön bilgi", text: $instruction, axis: .vertical).lineLimit(3...8)
                Text("Bu alan müşteri PDF'ine yazılmaz.").font(.caption)
            }
        }.navigationTitle("Ağ İş Emri")
        .environment(\.timeZone, TimeZone(identifier: "Europe/Istanbul")!)
        .disabled(busy).navigationBarBackButtonHidden(busy)
        .toolbar { ToolbarItem(placement: .confirmationAction) {
            Button("Gönder") { Task { await save() } }.disabled(busy || title.trimmingCharacters(in: .whitespaces).isEmpty || name.trimmingCharacters(in: .whitespaces).isEmpty)
        } }
    }
    private func save() async {
        guard !useEnd || end > date else { error = "Bitiş başlangıçtan sonra olmalıdır."; return }
        busy = true; error = nil; defer { busy = false }
        do {
            let _: ServiceNetworkOrder = try await ServiceNetworkAPI.post("/orders", ServiceNetworkDispatch(membershipId: member.id, requestKey: requestKey,
                title: title, customerName: name, customerPhone: phone, customerAddress: address, instruction: instruction,
                scheduledDate: ServiceNetworkAPI.timestamp(date), scheduledEndDate: useEnd ? ServiceNetworkAPI.timestamp(end) : nil))
            dismiss()
        } catch { self.error = error.localizedDescription }
    }
}

@MainActor
private struct NetworkOrderDetail: View {
    let id: Int
    let context: ServiceNetworkContext
    @Environment(\.dismiss) private var dismiss
    @State private var order: ServiceNetworkOrder?
    @State private var events: [ServiceNetworkEvent] = []
    @State private var page = 0
    @State private var more = false
    @State private var busy = false
    @State private var error: String?
    @State private var noteAction = "notes"
    @State private var showNote = false
    var body: some View {
        List {
            if let error { Text(error).foregroundStyle(.red) }
            if let order {
                Section { NetworkOrderRow(order: order) }
                Section("İş bilgileri") {
                    Text(order.customerPhone ?? "Telefon belirtilmedi").textSelection(.enabled)
                    Text(order.customerAddress ?? "Adres belirtilmedi")
                    Text("İlk randevu: " + ServiceNetworkAPI.date(order.scheduledDate) + " – " + ServiceNetworkAPI.date(order.scheduledEndDate))
                    Text("Güncel randevu: " + ServiceNetworkAPI.date(order.currentScheduledDate) + " – " + ServiceNetworkAPI.date(order.currentScheduledEndDate))
                    Text("İş talimatı: " + (order.instruction ?? "—"))
                    if let note = order.resolutionNote { Text(note) }
                    if let completed = order.completedAt { Text("Tamamlanma: " + ServiceNetworkAPI.date(completed)) }
                }
                Section {
                    if order.status == "SENT" {
                        if order.childCompanyId == context.companyId {
                            NavigationLink("Kabul Et ve Fiş Aç") { NetworkAcceptForm(order: order) }
                            Button("İşi Reddet", role: .destructive) { noteAction = "reject"; showNote = true }
                        } else {
                            Button("Gönderimi Geri Çek", role: .destructive) { noteAction = "cancel"; showNote = true }
                        }
                    }
                    Button("Karşı Firmaya Not Ekle") { noteAction = "notes"; showNote = true }
                }.disabled(!context.writable || busy)
                if let ticketId = order.acceptedTicketId {
                    Section {
                        if order.childCompanyId == context.companyId {
                            Button("Servis Fişini Aç · #\(ticketId)") { AppNavigation.shared.openTicket(id: ticketId); dismiss() }
                        } else {
                            Text("Alt servis fişi #\(ticketId). Finans, stok ve özel notlar yalnızca alt serviste yönetilir.").font(.caption)
                        }
                    }
                }
                Section("İş geçmişi") {
                    ForEach(events) { event in
                        VStack(alignment: .leading, spacing: 5) {
                            Text(ServiceNetworkAPI.label(event.action)).font(.subheadline.weight(.semibold))
                            Text(event.note ?? "")
                            Text((event.actorCompanyId == order.parentCompanyId ? order.parentName : order.childName) + " · " + ServiceNetworkAPI.date(event.createdAt))
                                .font(.caption).foregroundStyle(.secondary)
                        }.padding(.vertical, 4)
                    }
                    if more { Button("Daha Fazla Geçmiş") { Task { await loadHistory() } }.disabled(busy) }
                }
            }
        }.navigationTitle("Ağ İşi #\(id)")
        .task { await load() }.refreshable { await load() }
        .overlay { if busy { ProgressView() } }
        .sheet(isPresented: $showNote, onDismiss: { Task { await load() } }) {
            NavigationStack {
                NetworkNoteForm(title: noteAction == "notes" ? "Paylaşılan Not" : "İşlem Gerekçesi", explanation: "Bu not karşı firmanın yöneticileri tarafından görülebilir.") { note in
                    if noteAction == "notes" {
                        let _: EmptyResponse = try await ServiceNetworkAPI.post("/orders/\(id)/notes", ["note": note])
                    } else {
                        let _: ServiceNetworkOrder = try await ServiceNetworkAPI.post("/orders/\(id)/\(noteAction)", ["note": note])
                    }
                }
            }
        }
    }
    private func load() async {
        guard !busy else { return }
        busy = true; error = nil
        do {
            order = try await ServiceNetworkAPI.get("/orders/\(id)")
            let result: ServiceNetworkPage<ServiceNetworkEvent> = try await ServiceNetworkAPI.get("/orders/\(id)/history?page=0")
            events = result.items; page = 1; more = result.hasNext
        } catch { self.error = error.localizedDescription }
        busy = false
    }
    private func loadHistory() async {
        guard !busy else { return }
        busy = true; defer { busy = false }
        do {
            let result: ServiceNetworkPage<ServiceNetworkEvent> = try await ServiceNetworkAPI.get("/orders/\(id)/history?page=\(page)")
            let existing = Set(events.map(\.id))
            events += result.items.filter { !existing.contains($0.id) }; page += 1; more = result.hasNext
        } catch { self.error = error.localizedDescription }
    }
}

@MainActor
private struct NetworkAcceptForm: View {
    let order: ServiceNetworkOrder
    @Environment(\.dismiss) private var dismiss
    @State private var customers: [CustomerDTO] = []
    @State private var technicians: [TechnicianDTO] = []
    @State private var existing = false
    @State private var search = ""
    @State private var customerId: Int?
    @State private var technicianId: Int?
    @State private var busy = false
    @State private var error: String?
    private var matches: [CustomerDTO] {
        guard !search.trimmingCharacters(in: .whitespaces).isEmpty else { return [] }
        return Array(customers.filter { ($0.name + " " + ($0.phone ?? "")).localizedStandardContains(search) }.prefix(40))
    }
    var body: some View {
        Form {
            if let error { Text(error).foregroundStyle(.red) }
            Section {
                Text("Kabul edildiğinde işletmenizde normal servis fişi oluşturulur. Müşteri seçmezseniz işteki bilgilerle yeni kayıt açılır.").font(.caption)
                Toggle("Mevcut müşterime bağla", isOn: $existing)
                if existing {
                    TextField("Müşteri adı veya telefon ara", text: $search)
                    if let selected = customers.first(where: { $0.id == customerId }), customerId != nil {
                        Text("Seçili: " + selected.name).foregroundStyle(PusulaTheme.accent)
                    }
                    ForEach(matches.indices, id: \.self) { index in
                        let customer = matches[index]
                        Button(customer.name + " · " + (customer.phone ?? "")) { customerId = customer.id }
                    }
                }
                Picker("Teknisyen", selection: $technicianId) {
                    Text("Daha sonra ata").tag(nil as Int?)
                    ForEach(technicians) { Text($0.fullName ?? "Teknisyen #\($0.id)").tag(Optional($0.id)) }
                }
            }
        }.navigationTitle("İşi Kabul Et")
        .disabled(busy).navigationBarBackButtonHidden(busy)
        .toolbar { ToolbarItem(placement: .confirmationAction) {
            Button("Kabul Et") { Task { await accept() } }.disabled(busy || (existing && customerId == nil))
        } }
        .task {
            busy = true; defer { busy = false }
            do { customers = try await CustomerService.getCustomers(); technicians = try await TicketService.getTechnicians() }
            catch { self.error = error.localizedDescription }
        }
    }
    private func accept() async {
        busy = true; error = nil; defer { busy = false }
        do {
            let _: ServiceNetworkOrder = try await ServiceNetworkAPI.post("/orders/\(order.id)/accept", ServiceNetworkAccept(customerId: existing ? customerId : nil, technicianId: technicianId))
            dismiss()
        } catch { self.error = error.localizedDescription }
    }
}

@MainActor
private struct NetworkNoteForm: View {
    let title: String
    let explanation: String
    let save: (String) async throws -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var note = ""
    @State private var busy = false
    @State private var error: String?
    var body: some View {
        Form {
            if let error { Text(error).foregroundStyle(.red) }
            Text(explanation).font(.caption)
            TextField("Not / gerekçe", text: $note, axis: .vertical).lineLimit(4...12)
            Text("\(note.count) / 1000 karakter").font(.caption).foregroundStyle(.secondary)
        }.navigationTitle(title)
        .disabled(busy).interactiveDismissDisabled(busy)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Vazgeç") { dismiss() }.disabled(busy) }
            ToolbarItem(placement: .confirmationAction) {
                Button("Kaydet") {
                    busy = true; error = nil
                    Task { defer { busy = false }; do { try await save(note); dismiss() } catch { self.error = error.localizedDescription } }
                }.disabled(busy || note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || note.count > 1000)
            }
        }
    }
}
