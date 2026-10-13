#if DEBUG
import SwiftUI

/// Offline transports for the real detail view. No login, provider, Keychain
/// write, or production request is made by this simulator-only regression.
struct TicketDetailTestLoaders {
    var parts: @MainActor () async throws -> [UsedPartDTO]
    var notes: @MainActor () async throws -> [TechnicianNoteDTO]
    var timeline: @MainActor () async throws -> [AuditLogDTO]
}

enum TicketDetailLoadFixture {
    static var isEnabled: Bool {
#if targetEnvironment(simulator)
        AppStoreScreenshotMode.isEnabled
            && ProcessInfo.processInfo.environment["PUSULA_TICKET_DETAIL_LOAD_FIXTURE"] == "1"
#else
        false
#endif
    }
}

@MainActor
private final class TicketDetailFixtureTransport: ObservableObject {
    private var attempts: [String: Int] = [:]
    private var scenario: String {
        ProcessInfo.processInfo.environment["PUSULA_TICKET_DETAIL_LOAD_SCENARIO"] ?? "parts-tls"
    }

    private func begin(_ section: String) throws {
        attempts[section, default: 0] += 1
        if attempts[section] == 1 {
            if scenario == "all-fail" { throw URLError(.networkConnectionLost) }
            if section == "parts" {
                if scenario == "parts-tls" || scenario == "legacy-tls" { throw URLError(.secureConnectionFailed) }
                if scenario == "cancel" { throw URLError(.cancelled) }
            }
        }
    }

    func parts() throws -> [UsedPartDTO] {
        try begin("parts")
        if scenario == "empty" || scenario == "all-fail" { return [] }
        return [UsedPartDTO(id: 1, ticketId: 1, inventoryId: 1, partName: "Sentetik filtre",
                            quantityUsed: 1, sellingPriceSnapshot: 10, sourceVehicleId: nil,
                            clientRequestId: nil)]
    }

    func notes() throws -> [TechnicianNoteDTO] {
        try begin("notes")
        if scenario == "empty" || scenario == "all-fail" { return [] }
        return [TechnicianNoteDTO(id: 1, serviceTicketId: 1, authorUserId: nil,
                                 authorName: "Örnek teknisyen", noteType: "NORMAL",
                                 content: "Sentetik teknisyen notu", important: false,
                                 createdAt: "2026-10-13T10:00:00")]
    }

    func timeline() throws -> [AuditLogDTO] {
        try begin("timeline")
        if scenario == "empty" || scenario == "all-fail" { return [] }
        return [AuditLogDTO(id: 1, userName: "Örnek teknisyen", actionType: "COMPLETE",
                            entityType: "SERVICE_TICKET", description: "Sentetik işlem geçmişi",
                            oldValue: nil, newValue: nil, timestamp: "2026-10-13T10:00:00")]
    }
}

@MainActor
struct TicketDetailLoadHarness: View {
    @StateObject private var transport = TicketDetailFixtureTransport()

    private var detail: TicketDetailView {
        let legacy = ProcessInfo.processInfo.environment["PUSULA_TICKET_DETAIL_LOAD_SCENARIO"] == "legacy-tls"
        let summary = legacy ? "" : "\"partsTotal\":10,\"invoiceTotal\":10,"
        let data = Data("""
        {"id":1,"customerName":"Sentetik müşteri","status":"COMPLETED",
         "description":"Çevrimdışı yükleme testi",\(summary)"laborFee":0,
         "collectedAmount":10,"outstandingAmount":0}
        """.utf8)
        let ticket = try! JSONDecoder().decode(FieldTicketDTO.self, from: data)
        var view = TicketDetailView(ticket: ticket, onComplete: {})
        view.testLoaders = TicketDetailTestLoaders(parts: transport.parts,
                                                  notes: transport.notes,
                                                  timeline: transport.timeline)
        return view
    }

    var body: some View {
        NavigationStack { detail }
    }
}
#endif
