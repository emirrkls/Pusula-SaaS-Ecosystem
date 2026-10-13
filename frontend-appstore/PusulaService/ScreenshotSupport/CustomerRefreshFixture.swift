#if DEBUG
import SwiftUI

/// Offline transport for the real customer list and customer history views.
/// It never configures SessionManager, touches Keychain or contacts an API.
@MainActor
final class CustomerRefreshFixture: ObservableObject {
    static var isEnabled: Bool {
#if targetEnvironment(simulator)
        AppStoreScreenshotMode.isEnabled
            && ProcessInfo.processInfo.environment["PUSULA_CUSTOMER_REFRESH_FIXTURE"] != nil
#else
        false
#endif
    }

    let snapshot = AuthenticatedSessionSnapshot(
        token: "offline-customer-fixture-not-a-session", companyId: 0, revision: UUID()
    )
    @Published private(set) var customerAttempts = 0
    @Published private(set) var customerReadsInFlight = 0
    @Published private(set) var historyAttempts = 0
    private let scenario: String

    init(scenario: String = ProcessInfo.processInfo.environment["PUSULA_CUSTOMER_REFRESH_FIXTURE"] ?? "initial-cancel") {
        self.scenario = scenario
    }

    func loadCustomers() async throws -> [CustomerDTO] {
        customerAttempts += 1
        customerReadsInFlight += 1
        defer { customerReadsInFlight -= 1 }
        let attempt = customerAttempts
        try await Task.sleep(nanoseconds: 200_000_000)
        if scenario == "initial-cancel" && attempt == 1 {
            throw URLError(.cancelled)
        }
        if scenario == "refresh-cancel" && attempt == 2 {
            throw NSError(domain: NSURLErrorDomain, code: URLError.cancelled.rawValue)
        }
        if scenario == "refresh-cancel" && attempt == 3 {
            throw URLError(.secureConnectionFailed)
        }
        if scenario == "empty-failure-cancel" {
            if attempt == 1 { return [] }
            if attempt == 2 { throw URLError(.secureConnectionFailed) }
            if attempt == 3 { throw URLError(.cancelled) }
        }
        return [
            CustomerDTO(id: 901, name: "IOS-QA Customer", phone: nil, address: nil, coordinates: nil),
            CustomerDTO(id: 902, name: "IOS-QA Second Customer", phone: nil, address: nil, coordinates: nil)
        ]
    }

    func loadHistory() async throws -> [FieldTicketDTO] {
        historyAttempts += 1
        let attempt = historyAttempts
        try await Task.sleep(nanoseconds: 200_000_000)
        if scenario == "history-failure" && attempt == 1 {
            throw URLError(.secureConnectionFailed)
        }
        let json = """
        {"id":9901,"customerName":"IOS-QA Customer","description":"Synthetic history record",
         "status":"COMPLETED","completedAt":"2026-10-13T12:00:00"}
        """
        return [try JSONDecoder().decode(FieldTicketDTO.self, from: Data(json.utf8))]
    }
}

@MainActor
struct CustomerRefreshHarness: View {
    @StateObject private var fixture = CustomerRefreshFixture()

    var body: some View {
        NavigationStack {
            CustomerView(readFixture: fixture)
                .safeAreaInset(edge: .bottom) {
                    HStack {
                        Text("Customer reads: \(fixture.customerAttempts)")
                            .accessibilityIdentifier("customers.fixture.reads")
                            .accessibilityValue("\(fixture.customerAttempts):\(fixture.customerReadsInFlight)")
                        Text("History reads: \(fixture.historyAttempts)")
                            .accessibilityIdentifier("customers.fixture.history.reads")
                            .accessibilityValue(String(fixture.historyAttempts))
                    }
                    .font(.caption2)
                    .padding(6)
                }
        }
    }
}
#endif
