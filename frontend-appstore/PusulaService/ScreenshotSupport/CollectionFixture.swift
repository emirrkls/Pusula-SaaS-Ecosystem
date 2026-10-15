#if DEBUG
import SwiftUI

struct CollectionTestLoaders {
    var organizations: @MainActor () async throws -> [AccountPartyOptionDTO]
    var complete: @MainActor (CollectionRequest, UUID) async throws -> FieldTicketDTO
}

enum CollectionFixture {
    static var isEnabled: Bool {
#if targetEnvironment(simulator)
        AppStoreScreenshotMode.isEnabled && ProcessInfo.processInfo.environment["PUSULA_COLLECTION_FIXTURE"] == "1"
#else
        false
#endif
    }
    static var ticket: FieldTicketDTO {
        try! JSONDecoder().decode(FieldTicketDTO.self,
            from: Data("{\"id\":901,\"customerName\":\"Sentetik müşteri\",\"status\":\"IN_PROGRESS\"}".utf8))
    }
}

@MainActor private final class CollectionFixtureTransport: ObservableObject {
    @Published var completed = false
    private var firstKey: UUID?
    private var firstBody: Data?
    func complete(_ body: CollectionRequest, _ key: UUID) throws -> FieldTicketDTO {
        let encoder = JSONEncoder(); encoder.outputFormatting = .sortedKeys
        let data = try encoder.encode(body)
        if firstKey == nil {
            firstKey = key; firstBody = data
            throw URLError(.networkConnectionLost)
        }
        guard firstKey == key, firstBody == data else { throw CollectionPricing.Invalid(message: "Tekrar denemede anahtar veya tutar değişti.") }
        completed = true
        return CollectionFixture.ticket
    }
}

/// Exercises the actual view offline. The release app cannot activate this harness.
@MainActor struct CollectionHarness: View {
    @StateObject private var transport = CollectionFixtureTransport()
    var body: some View {
        if transport.completed {
            Text("Aynı anahtar ve bilgilerle tamamlandı").accessibilityIdentifier("collection.fixture.completed")
        } else {
            collection
        }
    }
    private var collection: CollectionView {
        var view = CollectionView(ticket: CollectionFixture.ticket, partsTotal: 3000, onComplete: {})
        view.testLoaders = CollectionTestLoaders(
            organizations: { [AccountPartyOptionDTO(id: 90, displayName: "Sentetik kurum", partyType: "ORGANIZATION")] },
            complete: transport.complete)
        return view
    }
}
#endif
