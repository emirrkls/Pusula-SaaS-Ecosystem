#if DEBUG
import SwiftUI

/// Exercises the actual editor without signing in, accessing Keychain or an API.
@MainActor
final class CustomerSaveFixture: ObservableObject {
    static var isEnabled: Bool {
#if targetEnvironment(simulator)
        AppStoreScreenshotMode.isEnabled
            && ProcessInfo.processInfo.environment["PUSULA_CUSTOMER_SAVE_FIXTURE"] != nil
#else
        false
#endif
    }

    let scenario = ProcessInfo.processInfo.environment["PUSULA_CUSTOMER_SAVE_FIXTURE"] ?? "slow-create"
    @Published private(set) var attempts = 0
    private var firstKey: UUID?

    var existingCustomer: CustomerDTO? {
        guard scenario == "slow-update" else { return nil }
        return CustomerDTO(id: 901, name: "IOS-QA Existing Customer", phone: "5550000000",
                           address: "Synthetic address", coordinates: nil)
    }

    func save(_ input: CustomerDTO, _ key: UUID) async throws -> CustomerDTO {
        attempts += 1
        if let firstKey, firstKey != key {
            throw NetworkError.badRequest("Synthetic retry changed its intent key")
        }
        firstKey = key
        try await Task.sleep(nanoseconds: 6_000_000_000)
        if scenario == "failure-then-success" && attempts == 1 {
            throw URLError(.timedOut)
        }
        return CustomerDTO(id: input.id ?? 902, name: input.name, phone: input.phone,
                           address: input.address, coordinates: input.coordinates)
    }
}

@MainActor
struct CustomerSaveHarness: View {
    @StateObject private var fixture = CustomerSaveFixture()
    @State private var showEditor = true
    @State private var savedCustomer: CustomerDTO?

    var body: some View {
        VStack(spacing: 12) {
            Text(String(fixture.attempts))
                .accessibilityIdentifier("customers.save.fixture.attempts")
            if let savedCustomer {
                Text(String(savedCustomer.id ?? 0))
                    .accessibilityIdentifier("customers.save.fixture.completed")
                Text(savedCustomer.name)
                    .accessibilityIdentifier("customers.save.fixture.name")
            }
        }
        .sheet(isPresented: $showEditor) { editor }
    }

    private var editor: CustomerEditorSheet {
        var view = CustomerEditorSheet(customer: fixture.existingCustomer,
                                      onCustomerSaved: { savedCustomer = $0 }, onSaved: {})
        view.testSave = fixture.save
        return view
    }
}
#endif
