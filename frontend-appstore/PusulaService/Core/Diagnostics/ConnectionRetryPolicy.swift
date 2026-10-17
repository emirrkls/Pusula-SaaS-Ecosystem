import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Only reads or the explicit server-enforced mutation contract may be replayed.
/// A header on a legacy POST is not sufficient: an old server could ignore it.
enum ConnectionRetryPolicy {
    static func permitsReplay(_ request: URLRequest) -> Bool {
        guard let url = request.url, url.scheme == "https",
              url.host == "api.pusulaiklimlendirme.com" else { return false }
        if ["GET", "HEAD"].contains(request.httpMethod ?? "GET") { return true }
        guard request.httpMethod == "POST",
              let key = request.value(forHTTPHeaderField: "Idempotency-Key"),
              key.count == 36, UUID(uuidString: key) != nil else { return false }
        let root = "/api/reliable-mutations"
        if [root + "/customers", root + "/tickets", root + "/inventory", root + "/expenses"].contains(url.path) { return true }
        let parts = url.path.split(separator: "/")
        return parts.count == 5 && parts[0] == "api" && parts[1] == "reliable-mutations"
            && parts[2] == "tickets" && !parts[3].isEmpty && parts[3].allSatisfy(\.isNumber)
            && ["photos", "complete", "technician-notes"].contains(String(parts[4]))
    }

    static func isTransient(_ error: Error) -> Bool {
        if error is CancellationError { return false }
        let codes = ConnectionErrorCode.capture(error)
        guard let url = codes.first(where: { $0.domain == .url }) else { return false }
        if [-1001, -1004, -1005].contains(url.code) { return true }
        // Never retry generic TLS, certificate, hostname, trust or ATS failures.
        let stream = ConnectionErrorCode.streamDetails(error)
        return url.code == -1200 && stream.0 == 3 && stream.1 == -9816
    }

    static func isTransient(_ response: URLResponse) -> Bool {
        guard let http = response as? HTTPURLResponse else { return false }
        // An explicit Retry-After must not be ignored in favor of our short backoff.
        return [502, 503, 504].contains(http.statusCode)
            && http.value(forHTTPHeaderField: "Retry-After") == nil
    }
}

enum ConnectionReliableTransport {
    static func data(for request: URLRequest, operation: ConnectionOperation,
                     session: URLSession = .shared, store: ConnectionDiagnosticsStore = .shared,
                     interface: ConnectionInterface = .unknown, appState: ConnectionAppState = .unknown,
                     backoffNanoseconds: UInt64 = 350_000_000,
                     beforeSend: @Sendable () throws -> Void = {},
                     mayRetry: @Sendable () async -> Bool = { true }) async throws -> (Data, URLResponse) {
        let replayable = ConnectionRetryPolicy.permitsReplay(request)
        for attempt in 0...1 {
            try Task.checkCancellation()
            do {
                // Each attempt gets its own diagnostic UUID; the mutation key and
                // already-encoded body (including multipart boundary) stay identical.
                let result = try await ConnectionDiagnosticsTransport.data(for: request,
                    operation: operation, session: session, store: store,
                    interface: interface, appState: appState, beforeSend: beforeSend)
                if attempt == 0 && replayable && ConnectionRetryPolicy.isTransient(result.1) {
                    guard await mayRetry() else { throw CancellationError() }
                    try await Task.sleep(nanoseconds: backoffNanoseconds)
                    guard await mayRetry() else { throw CancellationError() }
                    continue
                }
                return result
            } catch {
                try Task.checkCancellation()
                guard attempt == 0, replayable,
                      ConnectionRetryPolicy.isTransient(error) else { throw error }
                guard await mayRetry() else { throw CancellationError() }
                try await Task.sleep(nanoseconds: backoffNanoseconds)
                guard await mayRetry() else { throw CancellationError() }
            }
        }
        preconditionFailure("The bounded retry loop must return or throw")
    }
}
