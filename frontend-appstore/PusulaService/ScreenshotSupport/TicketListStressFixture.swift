#if DEBUG
import SwiftUI

/// Simulator-only, synthetic data using the real ticket list and processing
/// actor. Never creates a session, accesses Keychain, or calls a service/API.
enum TicketListStressFixture {
    static var requestedCount: Int? {
        guard AppStoreScreenshotMode.isEnabled,
              let raw = ProcessInfo.processInfo.environment["PUSULA_TICKET_STRESS_COUNT"],
              let count = Int(raw), (1...10_000).contains(count) else { return nil }
        return count
    }

    static func makeTickets(count: Int) async throws -> [FieldTicketDTO] {
        // Use an independent actor instead of an unstructured detached task:
        // cancellation from the view propagates to fixture generation too.
        try await TicketStressDataBuilder().make(count: count)
    }
}

private actor TicketStressDataBuilder {
    func make(count: Int) throws -> [FieldTicketDTO] {
        let formatter = DateFormatter()
        formatter.calendar = BusinessDateParser.calendar
        formatter.timeZone = BusinessDateParser.timeZone
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        let today = formatter.string(from: Date())
        var result: [FieldTicketDTO] = []
        let decoder = JSONDecoder()
        result.reserveCapacity(count)
        for id in 1...count {
            if id.isMultiple(of: 64) { try Task.checkCancellation() }
            let json = """
            {"id":\(id),"customerName":"Örnek müşteri \(id)",
             "description":"Sentetik performans testi", "status":"ASSIGNED",
             "scheduledDate":"\(today)","createdAt":"\(today)"}
            """
            result.append(try decoder.decode(FieldTicketDTO.self, from: Data(json.utf8)))
        }
        return result
    }
}

struct TicketListStressHarness: View {
    let count: Int
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        NavigationStack {
            TicketListView(requestedFilter: "Tümü", performanceFixtureCount: count)
        }
        .onAppear { AppNavigation.shared.setSceneActive(scenePhase == .active) }
        .onChange(of: scenePhase) { _, phase in
            AppNavigation.shared.setSceneActive(phase == .active)
        }
    }
}
#endif
