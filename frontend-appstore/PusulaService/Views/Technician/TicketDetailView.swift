import SwiftUI

struct TicketDetailView: View {
    let ticket: FieldTicketDTO
    var isAdmin: Bool = false
    var technicians: [TechnicianDTO] = []
    let onComplete: () async -> Void
    
    @Environment(\.dismiss) private var dismiss
    @State private var usedParts: [UsedPartDTO] = []
    @State private var timeline: [AuditLogDTO] = []
    @State private var technicianNotes: [TechnicianNoteDTO] = []
    @State private var newTechnicianNote = ""
    @State private var isSavingTechnicianNote = false
    @State private var showScanner = false
    @State private var showPartPicker = false
    @State private var showCollection = false
    @State private var showSignature = false
    @State private var showPhotos = false
    @State private var isLoadingParts = false
    @State private var isAddingPart = false
    @State private var isGeneratingPDF = false
    @State private var pdfPreview: PDFPreviewItem?
    @State private var isUpdatingTicket = false
    @State private var showCancelConfirmation = false
    @State private var showFollowUpConfirmation = false
    @State private var operationMessage: String?
    @State private var errorMessage: String?
    @State private var currentTicket: FieldTicketDTO

    init(
        ticket: FieldTicketDTO,
        isAdmin: Bool = false,
        technicians: [TechnicianDTO] = [],
        onComplete: @escaping () async -> Void
    ) {
        self.ticket = ticket
        self.isAdmin = isAdmin
        self.technicians = technicians
        self.onComplete = onComplete
        _currentTicket = State(initialValue: ticket)
    }
    
    private var isEditable: Bool {
        currentTicket.statusEnum != .completed && currentTicket.statusEnum != .cancelled
    }

    private var availableOperationalStatuses: [TicketStatus] {
        if isAdmin {
            return [.pending, .assigned, .inProgress].filter {
                $0 != .assigned || currentTicket.assignedTechnicianId != nil
            }
        }
        return [.assigned, .inProgress]
    }
    
    var totalPartsValue: Double {
        usedParts.reduce(0) { $0 + $1.totalPrice }
    }
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                heroCard
                customerCard
                
                if let desc = currentTicket.description, !desc.isEmpty {
                    infoCard(title: "İş Açıklaması", icon: "doc.text", content: desc)
                }

                if let privateNote = currentTicket.technicianPrivateNote, !privateNote.isEmpty {
                    infoCard(title: "Teknisyene Özel Not", icon: "lock.fill", content: privateNote)
                }

                statusSection

                if let operationMessage {
                    Label(operationMessage, systemImage: "checkmark.circle.fill")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.green)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                
                partsSection
                technicianNotesSection
                timelineSection
                
                if isEditable {
                    quickActionsGrid
                    primaryActions
                } else {
                    secondaryActions
                }
            }
            .padding()
        }
        .background(PusulaTheme.page)
        .navigationTitle("İş Emri #\(ticket.id)")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Kapat") { dismiss() }
            }
        }
        .task { await loadDetailData() }
        .sheet(isPresented: $showScanner) {
            BarcodeScannerView { item, quantity, unitPrice in
                Task { await addPart(from: item, quantity: quantity, unitPrice: unitPrice) }
            }
        }
        .sheet(isPresented: $showPartPicker) {
            InventoryPartPickerView { item, quantity, unitPrice in
                Task { await addPart(from: item, quantity: quantity, unitPrice: unitPrice) }
            }
        }
        .sheet(isPresented: $showCollection) {
            CollectionView(
                ticket: currentTicket,
                partsTotal: totalPartsValue,
                onComplete: {
                    await onComplete()
                    dismiss()
                }
            )
        }
        .sheet(isPresented: $showSignature) {
            SignatureView(ticketId: ticket.id)
        }
        .sheet(isPresented: $showPhotos) {
            NavigationStack {
                ServicePhotoView(ticketId: ticket.id)
            }
        }
        .sheet(item: $pdfPreview) { item in
            PDFPreviewSheet(item: item)
        }
        .alert("Hata", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Tamam", role: .cancel) { errorMessage = nil }
        } message: {
            Text(errorMessage ?? "")
        }
        .confirmationDialog("Servis fişi iptal edilsin mi?", isPresented: $showCancelConfirmation, titleVisibility: .visible) {
            Button("Servis Fişini İptal Et", role: .destructive) {
                Task { await cancelTicket() }
            }
            Button("Vazgeç", role: .cancel) {}
        } message: {
            Text("Kullanılan parçalar stoğa geri alınacak.")
        }
        .confirmationDialog("Takip kaydı oluşturulsun mu?", isPresented: $showFollowUpConfirmation, titleVisibility: .visible) {
            Button("Takip Kaydı Oluştur") {
                Task { await createFollowUp() }
            }
            Button("Vazgeç", role: .cancel) {}
        }
    }
    
    private var heroCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Servis fişi #\(ticket.id)")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(currentTicket.customerName ?? "Müşteri")
                .font(.title2.weight(.bold))
            Label(currentTicket.statusEnum.displayName, systemImage: currentTicket.statusEnum.iconName)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            if let schedule = scheduleText {
                Label(schedule, systemImage: "calendar.badge.clock")
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(PusulaTheme.accent)
            }
            if totalPartsValue > 0 {
                Text(formatCurrency(totalPartsValue))
                    .font(.headline)
                    .foregroundColor(PusulaTheme.accent)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .pusulaCard()
    }
    
    private var customerCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Müşteri", systemImage: "person.crop.circle")
                .font(.subheadline.weight(.semibold))
            
            if let phone = currentTicket.customerPhone, !phone.isEmpty {
                HStack {
                    Label(phone, systemImage: "phone.fill")
                        .font(.subheadline)
                    Spacer()
                    Link(destination: URL(string: "tel:\(phone)")!) {
                        Image(systemName: "phone.circle.fill")
                            .font(.title2)
                            .foregroundColor(.green)
                    }
                }
            }
            
            if let address = currentTicket.customerAddress, !address.isEmpty {
                Button(action: { openMaps(address: address) }) {
                    HStack(alignment: .top) {
                        Image(systemName: "mappin.and.ellipse")
                            .foregroundColor(.red)
                        Text(address)
                            .font(.subheadline)
                            .foregroundStyle(.primary)
                            .multilineTextAlignment(.leading)
                        Spacer()
                        Image(systemName: "arrow.up.right")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            
            if isAdmin, !technicians.isEmpty, isEditable {
                Menu {
                    ForEach(technicians) { tech in
                        Button(tech.fullName ?? "Teknisyen") {
                            Task { await assign(techId: tech.id) }
                        }
                    }
                } label: {
                    Label(currentTicket.assignedTechnicianName ?? "Teknisyen Seç", systemImage: "person.badge.plus")
                        .font(.subheadline.weight(.medium))
                        .foregroundColor(PusulaTheme.accent)
                }
                .readOnlyProtected()
            }
            
            if currentTicket.hasOutstandingBalance {
                HStack {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.orange)
                    Text("Geçmiş Cari Borç: \(formatCurrency(currentTicket.customerBalance))")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(.orange)
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(.orange.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }
        }
        .pusulaCard()
    }

    private var statusSection: some View {
        HStack(spacing: 12) {
            Label("Durum", systemImage: currentTicket.statusEnum.iconName)
                .font(.subheadline.weight(.semibold))
            Spacer()

            if isEditable {
                Menu {
                    ForEach(availableOperationalStatuses, id: \.self) { status in
                        Button {
                            Task { await updateStatus(status) }
                        } label: {
                            Label(status.displayName, systemImage: status.iconName)
                        }
                        .disabled(status == currentTicket.statusEnum)
                    }
                } label: {
                    HStack(spacing: 6) {
                        if isUpdatingTicket { ProgressView().controlSize(.small) }
                        Text(currentTicket.statusEnum.displayName)
                        Image(systemName: "chevron.up.chevron.down")
                            .font(.caption2)
                    }
                    .font(.subheadline.weight(.medium))
                }
                .disabled(isUpdatingTicket)
                .readOnlyProtected()
            } else {
                Text(currentTicket.statusEnum.displayName)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.secondary)
            }
        }
        .pusulaCard()
    }
    
    private func infoCard(title: String, icon: String, content: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(title, systemImage: icon)
                .font(.subheadline.weight(.semibold))
            Text(content)
                .font(.body)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .pusulaCard()
    }
    
    private var partsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Label("Kullanılan Parçalar", systemImage: "shippingbox")
                    .font(.subheadline.weight(.semibold))
                Spacer()
                if isEditable {
                    Menu {
                        Button { showPartPicker = true } label: {
                            Label("Listeden Seç", systemImage: "magnifyingglass")
                        }
                        Button { showScanner = true } label: {
                            Label("Barkod Okut", systemImage: "barcode.viewfinder")
                        }
                    } label: {
                        Label("Parça Ekle", systemImage: "plus.circle")
                            .font(.caption.weight(.semibold))
                    }
                    .readOnlyProtected()
                    .disabled(isAddingPart)
                }
            }
            
            if isLoadingParts {
                ProgressView()
            } else if usedParts.isEmpty {
                Text("Henüz parça eklenmedi")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(.vertical, 8)
            } else {
                ForEach(usedParts) { part in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(part.partName)
                                .font(.subheadline.weight(.medium))
                            Text("\(formatCurrency(part.sellingPriceSnapshot)) × \(formatQuantity(part.quantityUsed)) \(unitLabel(part.unitOfMeasure))")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text(formatCurrency(part.totalPrice))
                            .font(.subheadline.weight(.semibold))
                    }
                    Divider()
                }
                
                HStack {
                    Text("Parça Toplamı")
                        .font(.subheadline.weight(.bold))
                    Spacer()
                    Text(formatCurrency(totalPartsValue))
                        .font(.headline.weight(.bold))
                        .foregroundColor(PusulaTheme.accent)
                }
            }
        }
        .pusulaCard()
    }

    private var timelineSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("İşlem Geçmişi", systemImage: "clock.arrow.circlepath")
                    .font(.subheadline.weight(.semibold))
                Spacer()
                if !timeline.isEmpty {
                    Text("\(timeline.count) kayıt")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            if timeline.isEmpty {
                Text("Henüz işlem kaydı bulunmuyor")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(Array(timeline.enumerated()), id: \.offset) { index, log in
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: timelineIcon(log.actionType))
                            .foregroundStyle(PusulaTheme.accent)
                            .frame(width: 22)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(log.description ?? actionLabel(log.actionType))
                                .font(.subheadline.weight(.medium))
                            HStack(spacing: 5) {
                                if let user = log.userName, !user.isEmpty { Text(user) }
                                if let date = formattedTimelineDate(log.timestamp) { Text(date) }
                            }
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        }
                    }

                    if index < timeline.count - 1 { Divider() }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .pusulaCard()
    }

    private var technicianNotesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Teknisyen Notları", systemImage: "note.text")
                .font(.subheadline.weight(.semibold))
            if technicianNotes.isEmpty {
                Text("Henüz teknisyen notu eklenmemiş")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(technicianNotes) { note in
                    VStack(alignment: .leading, spacing: 3) {
                        Text(note.authorName).font(.caption.weight(.semibold))
                        Text(note.content).font(.subheadline)
                    }
                    Divider()
                }
            }
            if isEditable {
                TextField("Yeni teknisyen notu", text: $newTechnicianNote, axis: .vertical)
                    .lineLimit(3...8)
                    .textFieldStyle(.roundedBorder)
                Button {
                    Task { await saveTechnicianNote() }
                } label: {
                    if isSavingTechnicianNote { ProgressView() } else { Label("Not Ekle", systemImage: "plus") }
                }
                .disabled(newTechnicianNote.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isSavingTechnicianNote)
                .readOnlyProtected()
            }
        }
        .pusulaCard()
    }

    private var scheduleText: String? {
        guard let start = TicketFilters.parseBusinessDate(currentTicket.scheduledDate) else { return nil }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "tr_TR")
        formatter.timeZone = TimeZone(identifier: "Europe/Istanbul")
        formatter.dateFormat = "d MMMM EEEE, HH:mm"
        var value = formatter.string(from: start)
        if let end = TicketFilters.parseBusinessDate(currentTicket.scheduledEndDate) {
            formatter.dateFormat = "HH:mm"
            value += "–\(formatter.string(from: end))"
        }
        return value
    }
    
    private var quickActionsGrid: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
            actionTile("Parça Seç", icon: "magnifyingglass", color: PusulaTheme.accent) { showPartPicker = true }
            actionTile("Barkod Okut", icon: "barcode.viewfinder", color: PusulaTheme.accent) { showScanner = true }
            actionTile("Görseller", icon: "photo.on.rectangle", color: PusulaTheme.accent) { showPhotos = true }
            actionTile("İmza", icon: "pencil.tip.crop.circle", color: PusulaTheme.accent) { showSignature = true }
            actionTile("PDF", icon: "doc.richtext", color: .orange) { Task { await generatePDF() } }
                .disabled(isGeneratingPDF)
        }
    }
    
    private var primaryActions: some View {
        VStack(spacing: 10) {
            Button(action: { showCollection = true }) {
                Label("Servisi Tamamla & Tahsilat", systemImage: "checkmark.circle.fill")
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: PusulaTheme.controlHeight)
                    .font(.headline)
            }
            .background(PusulaTheme.accent)
            .foregroundColor(.white)
            .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
            .readOnlyProtected()

            if isAdmin {
                Button(role: .destructive) { showCancelConfirmation = true } label: {
                    Label("Servis Fişini İptal Et", systemImage: "xmark.circle")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(isUpdatingTicket)
                .readOnlyProtected()
            }
        }
    }
    
    private var secondaryActions: some View {
        VStack(spacing: 10) {
            Button { showPhotos = true } label: {
                Label("Servis Görselleri", systemImage: "photo.on.rectangle.angled")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.bordered)
            .readOnlyProtected()

            Button(action: { Task { await generatePDF() } }) {
                Label(isGeneratingPDF ? "PDF Hazırlanıyor..." : "Servis Formu PDF", systemImage: "doc.richtext")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
            }
            .buttonStyle(.borderedProminent)
            .tint(.orange)
            .disabled(isGeneratingPDF)

            if isAdmin && currentTicket.statusEnum == .completed {
                Button { showFollowUpConfirmation = true } label: {
                    Label("Takip / Garanti Kaydı", systemImage: "arrow.clockwise.circle")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(isUpdatingTicket)
                .readOnlyProtected()
            }
        }
    }
    
    private func actionTile(_ title: String, icon: String, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.title3)
                    .foregroundColor(color)
                Text(title)
                    .font(.caption.weight(.medium))
                    .foregroundColor(.primary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(PusulaTheme.raisedSurface)
            .overlay {
                RoundedRectangle(cornerRadius: PusulaTheme.radius)
                    .stroke(PusulaTheme.border, lineWidth: 1)
            }
            .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
        }
        .readOnlyProtected()
    }
    
    private func loadParts() async {
        isLoadingParts = true
        do {
            usedParts = try await TicketService.getUsedParts(ticketId: ticket.id)
        } catch {
            errorMessage = "Kullanılan parçalar yüklenemedi: \(error.localizedDescription)"
        }
        isLoadingParts = false
    }

    private func loadDetailData() async {
        async let partsRequest: Void = loadParts()
        async let timelineRequest: Void = loadTimeline()
        async let notesRequest: Void = loadTechnicianNotes()
        _ = await (partsRequest, timelineRequest, notesRequest)
    }

    private func loadTechnicianNotes() async {
        do {
            technicianNotes = try await TicketService.getTechnicianNotes(ticketId: ticket.id)
        } catch {
            if errorMessage == nil { errorMessage = "Teknisyen notları yüklenemedi: \(error.localizedDescription)" }
        }
    }

    private func saveTechnicianNote() async {
        let content = newTechnicianNote.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !content.isEmpty else { return }
        isSavingTechnicianNote = true
        do {
            let saved = try await TicketService.addTechnicianNote(ticketId: ticket.id, content: content)
            technicianNotes.append(saved)
            newTechnicianNote = ""
        } catch {
            errorMessage = error.localizedDescription
        }
        isSavingTechnicianNote = false
    }

    private func loadTimeline() async {
        do {
            timeline = try await TicketService.getTimeline(ticketId: ticket.id)
        } catch {
            if errorMessage == nil {
                errorMessage = "İşlem geçmişi yüklenemedi: \(error.localizedDescription)"
            }
        }
    }

    private func actionLabel(_ action: String?) -> String {
        switch action?.uppercased() {
        case "CREATE": return "İş emri oluşturuldu"
        case "UPDATE": return "İş emri güncellendi"
        case "ASSIGN": return "Teknisyen atandı"
        case "COMPLETE": return "Servis tamamlandı"
        default: return action ?? "İşlem yapıldı"
        }
    }

    private func timelineIcon(_ action: String?) -> String {
        switch action?.uppercased() {
        case "CREATE": return "plus.circle"
        case "ASSIGN": return "person.badge.plus"
        case "COMPLETE": return "checkmark.circle"
        default: return "pencil.circle"
        }
    }

    private func formattedTimelineDate(_ value: String?) -> String? {
        guard let value else { return nil }
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let date = iso.date(from: value) ?? ISO8601DateFormatter().date(from: value) else { return value }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "tr_TR")
        formatter.dateFormat = "d MMM, HH:mm"
        return formatter.string(from: date)
    }
    
    @MainActor
    private func addPart(from item: InventoryItemDTO, quantity: Double, unitPrice: Double) async {
        guard !isAddingPart else { return }
        isAddingPart = true
        errorMessage = nil
        operationMessage = nil
        defer { isAddingPart = false }

        let requestId = UUID().uuidString
        let part = UsedPartDTO(
            id: nil,
            ticketId: ticket.id,
            inventoryId: item.id,
            partName: item.partName,
            quantityUsed: quantity,
            sellingPriceSnapshot: unitPrice,
            unitOfMeasure: item.unitCode,
            sourceVehicleId: nil,
            clientRequestId: requestId
        )
        do {
            let saved = try await TicketService.addUsedPart(ticketId: ticket.id, part: part)
            if !usedParts.contains(where: { $0.id == saved.id }) { usedParts.append(saved) }
            operationMessage = "Parça eklendi: \(saved.partName)"
        } catch {
            // If the response was interrupted after commit, reconcile from the server
            // before showing an error or encouraging a duplicate retry.
            if let refreshed = try? await TicketService.getUsedParts(ticketId: ticket.id),
               refreshed.contains(where: { $0.clientRequestId == requestId }) {
                usedParts = refreshed
                operationMessage = "Parça eklendi. Bağlantı sonrası liste yenilendi."
            } else {
                errorMessage = error.localizedDescription
            }
        }
    }
    
    private func assign(techId: Int) async {
        do {
            let updated = try await TicketService.assignTechnician(ticketId: ticket.id, technicianId: techId)
            await MainActor.run {
                currentTicket = updated
                operationMessage = "Teknisyen ataması güncellendi."
            }
            await loadTimeline()
            await onComplete()
        } catch {
            await MainActor.run { errorMessage = error.localizedDescription }
        }
    }

    private func updateStatus(_ status: TicketStatus) async {
        guard status != currentTicket.statusEnum, !isUpdatingTicket else { return }
        isUpdatingTicket = true
        operationMessage = nil
        defer { isUpdatingTicket = false }

        do {
            let updated = try await TicketService.updateStatus(ticketId: ticket.id, status: status)
            currentTicket = updated
            operationMessage = "İş emri durumu güncellendi."
            await loadTimeline()
            await onComplete()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func cancelTicket() async {
        guard !isUpdatingTicket else { return }
        isUpdatingTicket = true
        operationMessage = nil
        defer { isUpdatingTicket = false }

        do {
            let updated = try await TicketService.cancelService(ticketId: ticket.id)
            currentTicket = updated
            usedParts = []
            operationMessage = "Servis fişi iptal edildi."
            await loadTimeline()
            await onComplete()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func createFollowUp() async {
        guard !isUpdatingTicket else { return }
        isUpdatingTicket = true
        operationMessage = nil
        defer { isUpdatingTicket = false }

        do {
            let followUp = try await TicketService.createFollowUp(ticketId: ticket.id)
            operationMessage = followUp.isWarrantyCall == true
                ? "#\(followUp.id) garanti kaydı oluşturuldu."
                : "#\(followUp.id) takip kaydı oluşturuldu."
            await onComplete()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
    
    private func generatePDF() async {
        guard !isGeneratingPDF else { return }
        isGeneratingPDF = true
        defer { isGeneratingPDF = false }
        do {
            let data = try await TicketService.downloadServiceReportPDF(ticketId: ticket.id)
            pdfPreview = try PDFPreviewItem(
                data: data,
                fileName: "servis-formu-\(ticket.id).pdf",
                title: "Servis Formu #\(ticket.id)"
            )
        } catch {
            errorMessage = error.localizedDescription
        }
    }
    
    private func openMaps(address: String) {
        let encoded = address.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? address
        if let url = URL(string: "http://maps.apple.com/?q=\(encoded)") {
            UIApplication.shared.open(url)
        }
    }

    private func formatQuantity(_ value: Double) -> String {
        value.formatted(.number.precision(.fractionLength(0...3)).locale(Locale(identifier: "tr_TR")))
    }

    private func unitLabel(_ unit: String?) -> String {
        switch unit ?? "ADET" {
        case "KG": return "kg"
        case "GRAM": return "gr"
        case "METRE": return "m"
        case "LITRE": return "lt"
        default: return "adet"
        }
    }
}

import UIKit
