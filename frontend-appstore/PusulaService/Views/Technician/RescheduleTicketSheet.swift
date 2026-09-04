import SwiftUI

private enum WorkProgressReason: String, CaseIterable, Identifiable {
    case partPending = "PART_PENDING"
    case customerAvailability = "CUSTOMER_AVAILABILITY"
    case customerApproval = "CUSTOMER_APPROVAL"
    case externalSupport = "EXTERNAL_SUPPORT"
    case rescheduled = "RESCHEDULED"
    case other = "OTHER"

    var id: String { rawValue }
    var title: String {
        switch self {
        case .partPending: return "Parça Bekleniyor"
        case .customerAvailability: return "Müşteri Uygunluğu Bekleniyor"
        case .customerApproval: return "Müşteri Onayı Bekleniyor"
        case .externalSupport: return "Harici Destek Bekleniyor"
        case .rescheduled: return "Yeniden Planlandı"
        case .other: return "Diğer"
        }
    }
}

struct RescheduleTicketSheet: View {
    let ticket: FieldTicketDTO
    let onSaved: (FieldTicketDTO) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var reason: WorkProgressReason = .partPending
    @State private var note = ""
    @State private var startDate: Date
    @State private var hasEndTime = true
    @State private var endDate: Date
    @State private var isSaving = false
    @State private var errorMessage: String?

    init(ticket: FieldTicketDTO, onSaved: @escaping (FieldTicketDTO) -> Void) {
        self.ticket = ticket
        self.onSaved = onSaved
        let minimum = Date().addingTimeInterval(15 * 60)
        let existing = TicketFilters.parseBusinessDate(ticket.scheduledDate) ?? minimum
        let initial = max(existing, minimum)
        _startDate = State(initialValue: initial)
        _endDate = State(initialValue: max(
            TicketFilters.parseBusinessDate(ticket.scheduledEndDate) ?? initial.addingTimeInterval(2 * 3600),
            initial.addingTimeInterval(15 * 60)
        ))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Neden") {
                    Picker("İlerleme nedeni", selection: $reason) {
                        ForEach(WorkProgressReason.allCases) { value in
                            Text(value.title).tag(value)
                        }
                    }
                }
                Section("Yeni Randevu") {
                    DatePicker("Tarih ve saat", selection: $startDate,
                               in: Date().addingTimeInterval(60)...,
                               displayedComponents: [.date, .hourAndMinute])
                    Toggle("Saat aralığı belirt", isOn: $hasEndTime)
                    if hasEndTime {
                        DatePicker("Tahmini bitiş", selection: $endDate,
                                   in: startDate.addingTimeInterval(60)...,
                                   displayedComponents: [.date, .hourAndMinute])
                    }
                }
                Section("Açıklama") {
                    TextEditor(text: $note)
                        .frame(minHeight: 100)
                    Text("En az 5 karakter. Bu açıklama yöneticiye ve işlem geçmişine görünür.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("İşi Yeniden Planla")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Vazgeç") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isSaving ? "Kaydediliyor..." : "Kaydet") { Task { await save() } }
                        .disabled(!canSave || isSaving)
                }
            }
            .onChange(of: startDate) { newValue in
                if endDate <= newValue { endDate = newValue.addingTimeInterval(2 * 3600) }
            }
            .alert("Hata", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Tamam", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
        }
    }

    private var canSave: Bool {
        note.trimmingCharacters(in: .whitespacesAndNewlines).count >= 5
            && startDate > Date()
            && (!hasEndTime || endDate > startDate)
    }

    @MainActor private func save() async {
        guard canSave else { return }
        isSaving = true
        defer { isSaving = false }
        let request = RescheduleTicketRequest(
            scheduledDate: Self.encode(startDate),
            scheduledEndDate: hasEndTime ? Self.encode(endDate) : nil,
            reason: reason.rawValue,
            note: note.trimmingCharacters(in: .whitespacesAndNewlines)
        )
        do {
            let updated = try await TicketService.reschedule(ticketId: ticket.id, request: request)
            onSaved(updated)
            dismiss()
        } catch { errorMessage = error.localizedDescription }
    }

    private static func encode(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Europe/Istanbul")
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        return formatter.string(from: date)
    }
}
