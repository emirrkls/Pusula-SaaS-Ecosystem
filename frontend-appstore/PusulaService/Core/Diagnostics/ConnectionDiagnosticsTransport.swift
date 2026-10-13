import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Observes individual tasks on the existing session. No custom trust delegate,
/// TLS overrides, retry, redirects policy or URLSession replacement is added.
enum ConnectionDiagnosticsTransport {
    static func local<Value>(operation: ConnectionOperation, store: ConnectionDiagnosticsStore = .shared,
                             interface: ConnectionInterface = .unknown, appState: ConnectionAppState = .unknown,
                             work: () async throws -> Value) async rethrows -> Value {
        let started = Date()
        let generation = await store.currentGeneration()
        do {
            let value = try await work()
            await store.append(ConnectionDiagnosticRecord(startedAt: started, operation: operation,
                elapsed: Date().timeIntervalSince(started), interface: interface, appState: appState), generation: generation)
            return value
        } catch {
            await store.append(ConnectionDiagnosticRecord(startedAt: started, operation: operation,
                elapsed: Date().timeIntervalSince(started), interface: interface, appState: appState, error: error), generation: generation)
            throw error
        }
    }

    static func data(for originalRequest: URLRequest, operation: ConnectionOperation,
                     session: URLSession = .shared, store: ConnectionDiagnosticsStore = .shared,
                     interface: ConnectionInterface = .unknown, appState: ConnectionAppState = .unknown) async throws -> (Data, URLResponse) {
        let id = UUID()
        let started = Date()
        let generation = await store.currentGeneration()
        let observer = ConnectionMetricsObserver()
        var request = originalRequest
        request.setValue(id.uuidString, forHTTPHeaderField: "X-Pusula-Diagnostic-ID")
        do {
            let result = try await session.data(for: request, delegate: observer)
            await store.append(ConnectionDiagnosticRecord(id: id, startedAt: started, operation: operation,
                url: request.url, method: request.httpMethod ?? "GET", status: (result.1 as? HTTPURLResponse)?.statusCode,
                elapsed: Date().timeIntervalSince(started), interface: interface, appState: appState,
                transactions: observer.snapshot()), generation: generation)
            return result
        } catch {
            await store.append(ConnectionDiagnosticRecord(id: id, startedAt: started, operation: operation,
                url: request.url, method: request.httpMethod ?? "GET", elapsed: Date().timeIntervalSince(started),
                interface: interface, appState: appState, error: error, transactions: observer.snapshot()), generation: generation)
            throw error // Preserve exact error/cancellation and authentication behavior.
        }
    }
}

private final class ConnectionMetricsObserver: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    private let lock = NSLock()
    private var transactions: [ConnectionTransaction] = []

    func snapshot() -> [ConnectionTransaction] {
        lock.lock(); defer { lock.unlock() }
        return transactions
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didFinishCollecting metrics: URLSessionTaskMetrics) {
        let values = metrics.transactionMetrics.suffix(4).map { metric in
            var result = ConnectionTransaction()
            result.dnsMilliseconds = duration(metric.domainLookupStartDate, metric.domainLookupEndDate)
            result.connectMilliseconds = duration(metric.connectStartDate, metric.connectEndDate)
            result.tlsMilliseconds = duration(metric.secureConnectionStartDate, metric.secureConnectionEndDate)
            result.requestMilliseconds = duration(metric.requestStartDate, metric.requestEndDate)
            result.responseMilliseconds = duration(metric.responseStartDate, metric.responseEndDate)
            result.dnsStarted = metric.domainLookupStartDate != nil
            result.connectionStarted = metric.connectStartDate != nil
            result.tlsStarted = metric.secureConnectionStartDate != nil
            result.tlsCompleted = metric.secureConnectionEndDate != nil
            result.requestStarted = metric.requestStartDate != nil
            result.responseStarted = metric.responseStartDate != nil
            result.reusedConnection = metric.isReusedConnection
            result.proxyConnection = metric.isProxyConnection
            result.cellular = metric.isCellular
            result.protocolName = metric.networkProtocolName
#if !canImport(FoundationNetworking)
            result.tlsProtocol = metric.negotiatedTLSProtocolVersion.map { Int($0.rawValue) }
#endif
            return result.redacted()
        }
        lock.lock(); transactions = values; lock.unlock()
    }

    private func duration(_ start: Date?, _ end: Date?) -> Int? {
        guard let start, let end, end >= start else { return nil }
        return Int(min(end.timeIntervalSince(start), 86_400) * 1_000)
    }
}
