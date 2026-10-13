#if DEBUG
import SwiftUI

enum ConnectionDiagnosticsFixture {
    static var isEnabled: Bool {
#if targetEnvironment(simulator)
        AppStoreScreenshotMode.isEnabled
            && ProcessInfo.processInfo.environment["PUSULA_CONNECTION_DIAGNOSTICS_FIXTURE"] == "1"
#else
        false
#endif
    }
}

struct ConnectionDiagnosticsHarness: View {
    private let store = ConnectionDiagnosticsStore(directory: FileManager.default.temporaryDirectory
        .appendingPathComponent("OfflineConnectionDiagnostics", isDirectory: true))
    @State private var ready = false

    var body: some View {
        Group {
            if ready { NavigationStack { ConnectionDiagnosticsView(store: store) } }
            else { ProgressView() }
        }
        .task {
            guard !ready else { return }
            await store.clear()
            let generation = await store.currentGeneration()
            let error = NSError(domain: NSURLErrorDomain, code: -1200, userInfo: [
                NSLocalizedDescriptionKey: "fixture-secret-description",
                NSURLErrorFailingURLStringErrorKey: "https://fixture-secret-host.invalid/fixture-secret-path",
                "Authorization": "fixture-secret-token"
            ])
            await store.append(ConnectionDiagnosticRecord(operation: .api,
                url: URL(string: "https://api.pusulaiklimlendirme.com/api/tickets/987654/photos?token=fixture-secret-query"),
                method: "GET", interface: .cellular, appState: .active, error: error), generation: generation)
            ready = true
        }
    }
}
#endif
