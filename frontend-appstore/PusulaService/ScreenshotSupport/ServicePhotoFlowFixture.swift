#if DEBUG
import SwiftUI

@MainActor final class ServicePhotoFlowFixture: ObservableObject {
    static var isEnabled: Bool {
#if targetEnvironment(simulator)
        AppStoreScreenshotMode.isEnabled && ProcessInfo.processInfo.environment["PUSULA_PHOTO_BATCH_TEST"] == "1"
#else
        false
#endif
    }
    let owner = AuthenticatedSessionSnapshot(token: "offline-photo-fixture", companyId: 0, revision: UUID())
    @Published private(set) var attempts = 0
    @Published private(set) var photos: [ServicePhotoDTO] = []
    private var receipts: [UUID: Data] = [:]
    private var saved: [UUID: ServicePhotoDTO] = [:]
    func upload(key: UUID, type: String, note: String, data: Data) async throws -> ServicePhotoDTO {
        attempts += 1
        if let bytes = receipts[key], bytes != data { throw NetworkError.badRequest("Changed replay bytes") }
        receipts[key] = data
        if saved[key] == nil {
            let photo = ServicePhotoDTO(id: saved.count + 1, ticketId: 0, url: "file:///dev/null", type: type, note: note,
                uploadedByName: "Synthetic QA", uploadedAt: nil, serviceDate: nil, customerName: nil, ticketDescription: nil)
            saved[key] = photo; photos.append(photo)
        }
        try await Task.sleep(nanoseconds: 150_000_000)
        if attempts == 2 { throw URLError(.timedOut) } // Saved remotely; response was lost.
        return saved[key]!
    }
}

struct ServicePhotoFlowHarness: View {
    @StateObject private var fixture = ServicePhotoFlowFixture()
    var body: some View {
        NavigationStack { ServicePhotoView(ticketId: 0, flowFixture: fixture) }
            .safeAreaInset(edge: .bottom) {
                Text("\(fixture.attempts):\(fixture.photos.count)")
                    .accessibilityIdentifier("photos.fixture.counts").font(.caption2).padding(6)
            }
    }
}
#endif
