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
    static var completedTicket: FieldTicketDTO {
        try! JSONDecoder().decode(FieldTicketDTO.self,
            from: Data("{\"id\":901,\"customerName\":\"Sentetik müşteri\",\"status\":\"COMPLETED\",\"invoiceTotal\":3000,\"collectedAmount\":3000}".utf8))
    }
    static var scenario: String {
        ProcessInfo.processInfo.environment["PUSULA_COLLECTION_SCENARIO"] ?? "lost-response"
    }
}

@MainActor private final class CollectionFixtureTransport: ObservableObject {
    @Published var attempts = 0
    private var firstKey: UUID?
    private var firstBody: Data?
    func complete(_ body: CollectionRequest, _ key: UUID) async throws -> FieldTicketDTO {
        attempts += 1
        let encoder = JSONEncoder(); encoder.outputFormatting = .sortedKeys
        let data = try encoder.encode(body)
        if attempts == 1 {
            firstKey = key; firstBody = data
            if CollectionFixture.scenario == "lost-response" { throw URLError(.networkConnectionLost) }
            if CollectionFixture.scenario == "rejected" { throw NetworkError.badRequest("Sentetik doğrulama hatası") }
        } else if CollectionFixture.scenario == "rejected" {
            guard firstKey != key else { throw CollectionPricing.Invalid(message: "Düzeltilmiş işlem için yeni anahtar kullanılmadı.") }
        } else {
            guard firstKey == key, firstBody == data else { throw CollectionPricing.Invalid(message: "Tekrar denemede anahtar veya tutar değişti.") }
        }
        if CollectionFixture.scenario == "slow-submit" { try await Task.sleep(nanoseconds: 6_000_000_000) }
        return CollectionFixture.completedTicket
    }
}

/// Exercises the actual view offline. The release app cannot activate this harness.
@MainActor struct CollectionHarness: View {
    @StateObject private var transport = CollectionFixtureTransport()
    @State private var showFlow = true
    @State private var receipt: FieldTicketDTO?
    @State private var refreshID: UUID?
    @State private var refreshMessage = "Liste yenilemesi bekliyor"
    @State private var interactionCount = 0
    var body: some View {
        VStack(spacing: 20) {
            if let receipt {
                Text("Tamamlandı #\(receipt.id) · \(receipt.status ?? "")")
                    .accessibilityIdentifier("collection.fixture.completed")
                Text(refreshMessage).accessibilityIdentifier("collection.fixture.refresh")
                Text("\(transport.attempts)").accessibilityIdentifier("collection.fixture.attempts")
                Button("Listeyi Kullan") { interactionCount += 1 }
                Text("\(interactionCount)").accessibilityIdentifier("collection.fixture.interactions")
            } else {
                Text("Form kapandı").accessibilityIdentifier("collection.fixture.cancelled")
            }
        }
        .sheet(isPresented: $showFlow, onDismiss: {
            if receipt != nil { refreshID = UUID() }
        }) {
            if CollectionFixture.scenario.hasPrefix("detail-") {
                NavigationStack { detail }
            } else if CollectionFixture.scenario == "keyboard-customer" {
                CustomerEditorSheet(customer: nil, onSaved: {})
            } else if CollectionFixture.scenario == "keyboard-expense" {
                AddExpenseSheet(onSaved: {})
            } else {
                collection
            }
        }
        .task(id: refreshID) {
            guard refreshID != nil else { return }
            if CollectionFixture.scenario.contains("slow-refresh") {
                refreshMessage = "Liste arka planda yenileniyor"
                do {
                    try await Task.sleep(nanoseconds: 6_000_000_000)
                    refreshMessage = "Liste yenilenemedi; tahsilat tamamlandı"
                } catch { return }
            } else {
                refreshMessage = "Liste güncel"
            }
        }
    }
    private var collection: CollectionView {
        var view = CollectionView(ticket: CollectionFixture.ticket, partsTotal: 3000, onComplete: { receipt = $0 })
        view.testLoaders = loaders
        return view
    }
    private var loaders: CollectionTestLoaders {
        CollectionTestLoaders(
            organizations: { [AccountPartyOptionDTO(id: 90, displayName: "Sentetik kurum", partyType: "ORGANIZATION")] },
            complete: transport.complete)
    }
    private var detail: TicketDetailView {
        var view = TicketDetailView(ticket: CollectionFixture.ticket, onCollected: { completed in
            receipt = completed
            showFlow = false
        }, onComplete: {})
        view.collectionTestLoaders = loaders
        view.testLoaders = TicketDetailTestLoaders(
            parts: { [UsedPartDTO(id: 1, ticketId: 901, inventoryId: 1, partName: "Sentetik parça",
                                 quantityUsed: 1, sellingPriceSnapshot: 3000, sourceVehicleId: nil,
                                 clientRequestId: nil)] },
            notes: { [] }, timeline: { [] })
        return view
    }
}
#endif
