import Foundation
import Network
import UIKit

/// Interface category only: never SSID, IP address, carrier or device identity.
final class ConnectionPathObserver: @unchecked Sendable {
    static let shared = ConnectionPathObserver()
    private let monitor = NWPathMonitor()
    private let lock = NSLock()
    private var value: ConnectionInterface = .unknown

    private init() {
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self else { return }
            let next: ConnectionInterface
            if path.status != .satisfied { next = .unavailable }
            else if path.usesInterfaceType(.wifi) { next = .wifi }
            else if path.usesInterfaceType(.cellular) { next = .cellular }
            else if path.usesInterfaceType(.wiredEthernet) { next = .wired }
            else { next = .other }
            self.lock.lock(); self.value = next; self.lock.unlock()
        }
        monitor.start(queue: DispatchQueue(label: "com.pusula.connection-path", qos: .utility))
    }

    func snapshot() -> ConnectionInterface {
        lock.lock(); defer { lock.unlock() }
        return value
    }
}

enum ConnectionDiagnosticEnvironment {
    static func context() async -> (ConnectionInterface, ConnectionAppState) {
        let interface = ConnectionPathObserver.shared.snapshot()
        let state: ConnectionAppState = await MainActor.run {
            switch UIApplication.shared.applicationState {
            case .active: return .active
            case .inactive: return .inactive
            case .background: return .background
            @unknown default: return .unknown
            }
        }
        return (interface, state)
    }

    @MainActor static func metadata() -> ConnectionReportMetadata {
        ConnectionReportMetadata(
            appVersion: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "",
            build: Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "",
            osVersion: UIDevice.current.systemVersion,
            deviceFamily: UIDevice.current.userInterfaceIdiom == .pad ? "iPad" : "iPhone")
    }
}
