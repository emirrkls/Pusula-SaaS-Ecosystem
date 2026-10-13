import XCTest
@testable import PusulaDiagnostics

final class ConnectionDiagnosticsTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_800_000_000)
    private let metadata = ConnectionReportMetadata(appVersion: "1.0", build: "55", osVersion: "26.0", deviceFamily: "iPhone")

    func testRoutesRedactIDsQueriesNamesFilenamesAndExternalHosts() throws {
        let error = NSError(domain: "SECRET-DOMAIN", code: -1200, userInfo: [
            NSLocalizedDescriptionKey: "SECRET-DESCRIPTION",
            NSURLErrorFailingURLStringErrorKey: "https://secret-person.example/SECRET-URL",
            "Authorization": "Bearer SECRET-TOKEN"
        ])
        let record = ConnectionDiagnosticRecord(operation: .upload,
            url: URL(string: "https://api.pusulaiklimlendirme.com/api/tickets/987654/photos/SECRET-NAME.jpg?token=SECRET-QUERY"),
            method: "POST", error: error)
        let text = String(decoding: try ConnectionDiagnosticsStore.encoder().encode(record.redacted()), as: UTF8.self)
        XCTAssertEqual(record.route, "/api/tickets/:id/photos/:redacted")
        for secret in ["987654", "SECRET", "Bearer", "secret-person"] { XCTAssertFalse(text.contains(secret), secret) }
        XCTAssertEqual(record.errors.first?.domain, .other)
        let external = ConnectionDiagnosticRecord(operation: .download,
            url: URL(string: "https://secret-person.example/private-name?secret=1"), method: "SECRET-METHOD")
        XCTAssertEqual(external.target, .external)
        XCTAssertEqual(external.route, "/")
        XCTAssertEqual(external.method, "OTHER")
    }

    func testUnderlyingAndStreamCodesAreKeptButMessagesAreNot() throws {
        let underlying = NSError(domain: "NSOSStatusErrorDomain", code: -9816,
                                 userInfo: [NSLocalizedDescriptionKey: "SECRET"])
        let error = NSError(domain: NSURLErrorDomain, code: -1200, userInfo: [
            NSUnderlyingErrorKey: underlying, "_kCFStreamErrorDomainKey": 3, "_kCFStreamErrorCodeKey": -9816
        ])
        let record = ConnectionDiagnosticRecord(operation: .api, url: URL(string: "https://api.pusulaiklimlendirme.com/api/customers"), error: error).redacted()
        XCTAssertEqual(record.outcome, .transportError)
        XCTAssertEqual(record.errors.map(\.code), [-1200, -9816])
        XCTAssertEqual(record.streamErrorCode, -9816)
        XCTAssertEqual(record.streamErrorDomain, 3)
        XCTAssertFalse(String(decoding: try ConnectionDiagnosticsStore.encoder().encode(record), as: UTF8.self).contains("SECRET"))
    }

    func testCancellationIsDistinctFromTLSAndHTTPFailure() {
        let url = URL(string: "https://api.pusulaiklimlendirme.com/api/customers")!
        XCTAssertEqual(ConnectionDiagnosticRecord(operation: .api, url: url, error: CancellationError()).outcome, .cancelled)
        XCTAssertEqual(ConnectionDiagnosticRecord(operation: .api, url: url, error: URLError(.cancelled)).outcome, .cancelled)
        XCTAssertEqual(ConnectionDiagnosticRecord(operation: .api, url: url, error: URLError(.secureConnectionFailed)).outcome, .transportError)
        XCTAssertEqual(ConnectionDiagnosticRecord(operation: .api, url: url, status: 403).outcome, .httpError)
        XCTAssertEqual(ConnectionDiagnosticRecord(operation: .api, url: url, status: 200).outcome, .success)
    }

    func testNestedCFStreamCodesAreNotLostBehindGenericTLSFailure() {
        let detail = NSError(domain: "kCFErrorDomainCFNetwork", code: -1200, userInfo: [
            "_kCFStreamErrorDomainKey": 3, "_kCFStreamErrorCodeKey": -9824,
            NSLocalizedDescriptionKey: "SECRET-DETAIL"
        ])
        let outer = NSError(domain: NSURLErrorDomain, code: -1200, userInfo: [NSUnderlyingErrorKey: detail])
        let record = ConnectionDiagnosticRecord(operation: .api, error: outer).redacted()
        XCTAssertEqual(record.streamErrorDomain, 3)
        XCTAssertEqual(record.streamErrorCode, -9824)
        XCTAssertEqual(record.errors.map(\.code), [-1200, -1200])
    }

    func testRestoredUntrustedStringsAreSanitizedAgainIncludingOriginalBuild() throws {
        let source = ConnectionDiagnosticRecord(operation: .api, url: URL(string: "https://api.pusulaiklimlendirme.com/api/customers"))
        var object = try XCTUnwrap(JSONSerialization.jsonObject(with: ConnectionDiagnosticsStore.encoder().encode(source)) as? [String: Any])
        object["sourceAppVersion"] = "SECRET-USER"
        object["sourceBuild"] = "SECRET-TOKEN"
        object["sourceOSVersion"] = "SECRET-DEVICE"
        object["route"] = "/api/customers/SECRET-NAME?token=SECRET-QUERY"
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let restored = try decoder.decode(ConnectionDiagnosticRecord.self, from: JSONSerialization.data(withJSONObject: object))
        let safe = restored.redacted()
        XCTAssertEqual(safe.sourceBuild, "unknown")
        XCTAssertEqual(safe.route, "/api/customers/:redacted")
        XCTAssertFalse(String(decoding: try ConnectionDiagnosticsStore.encoder().encode(safe), as: UTF8.self).contains("SECRET"))
    }

    func testMetricsCannotExportArbitraryProtocolStringsOrUnrecognizedTLS() {
        var metrics = ConnectionTransaction()
        metrics.protocolName = "SECRET-PROTOCOL"
        metrics.tlsProtocol = 123456
        XCTAssertNil(metrics.redacted().protocolName)
        XCTAssertNil(metrics.redacted().tlsProtocol)
        metrics.protocolName = "h2"
        metrics.tlsProtocol = 0x0304
        XCTAssertEqual(metrics.redacted().protocolName, "h2")
        XCTAssertEqual(metrics.redacted().tlsProtocol, 0x0304)
    }

    func testMetadataRejectsDeviceNamesAndNonVersionText() throws {
        let unsafe = ConnectionReportMetadata(appVersion: "SECRET", build: "account@secret.test",
                                             osVersion: "26.0", deviceFamily: "Emir's iPhone")
        let text = String(decoding: try ConnectionDiagnosticsStore.encoder().encode(unsafe), as: UTF8.self)
        XCTAssertFalse(text.contains("SECRET"))
        XCTAssertFalse(text.contains("secret.test"))
        XCTAssertFalse(text.contains("Emir"))
        XCTAssertEqual(unsafe.deviceFamily, "other")
    }

    func testStoreBoundsRetentionCapacityAndFutureDates() async {
        let store = ConnectionDiagnosticsStore(limit: 3, retention: 60)
        let generation = await store.currentGeneration()
        for age in [100.0, 20, 15, 10, 5, -1] {
            await store.append(ConnectionDiagnosticRecord(startedAt: now.addingTimeInterval(-age), operation: .api), generation: generation, now: now)
        }
        let report = await store.report(metadata: metadata, now: now)
        XCTAssertEqual(report.records.map { now.timeIntervalSince($0.startedAt) }, [15, 10, 5])
    }

    func testClearRejectsLateCompletionsAndAcceptsNewRequests() async {
        let store = ConnectionDiagnosticsStore()
        let old = await store.currentGeneration()
        await store.append(ConnectionDiagnosticRecord(startedAt: now, operation: .api), generation: old, now: now)
        await store.clear()
        await store.append(ConnectionDiagnosticRecord(startedAt: now, operation: .upload), generation: old, now: now)
        let empty = await store.report(metadata: metadata, now: now)
        XCTAssertTrue(empty.records.isEmpty)
        let fresh = await store.currentGeneration()
        await store.append(ConnectionDiagnosticRecord(startedAt: now, operation: .download), generation: fresh, now: now)
        let report = await store.report(metadata: metadata, now: now)
        XCTAssertEqual(report.records.map(\.operation), [.download])
    }

    func testPersistenceExportAndClearOnlyTouchOwnedFiles() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = ConnectionDiagnosticsStore(directory: directory)
        let generation = await store.currentGeneration()
        await store.append(ConnectionDiagnosticRecord(startedAt: now, operation: .api,
            url: URL(string: "https://api.pusulaiklimlendirme.com/api/customers?search=SECRET")), generation: generation, now: now)
        let exported = try await store.export(metadata: metadata, now: now)
        XCTAssertEqual(exported.0.lastPathComponent, "Pusula-Connection-Report.json")
        let bytes = try Data(contentsOf: exported.0)
        XCTAssertFalse(String(decoding: bytes, as: UTF8.self).contains("SECRET"))
        let decoded = try JSONSerialization.jsonObject(with: bytes) as? [String: Any]
        XCTAssertEqual(decoded?["schemaVersion"] as? Int, 1)
        let restored = ConnectionDiagnosticsStore(directory: directory)
        let report = await restored.report(metadata: metadata, now: now)
        XCTAssertEqual(report.records.count, 1)
        let sibling = directory.appendingPathComponent("unrelated.txt")
        try Data("preserve".utf8).write(to: sibling)
        await store.clear()
        XCTAssertFalse(FileManager.default.fileExists(atPath: exported.0.path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: directory.appendingPathComponent("records.json").path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: sibling.path))
    }

    func testExpiredPersistentRecordsArePrunedOnRestore() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = ConnectionDiagnosticsStore(directory: directory, retention: 60)
        let generation = await store.currentGeneration()
        await store.append(ConnectionDiagnosticRecord(startedAt: now, operation: .api), generation: generation, now: now)
        let restored = ConnectionDiagnosticsStore(directory: directory, retention: 60)
        let report = await restored.report(metadata: metadata, now: now.addingTimeInterval(61))
        XCTAssertTrue(report.records.isEmpty)
    }

    func testDiskFailureDoesNotThrowAwayInMemoryRecord() async throws {
        let file = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try Data("not a directory".utf8).write(to: file)
        defer { try? FileManager.default.removeItem(at: file) }
        let store = ConnectionDiagnosticsStore(directory: file)
        let generation = await store.currentGeneration()
        await store.append(ConnectionDiagnosticRecord(startedAt: now, operation: .api), generation: generation, now: now)
        let report = await store.report(metadata: metadata, now: now)
        XCTAssertEqual(report.records.count, 1)
        let available = await store.storageAvailable
        XCTAssertFalse(available)
        do { _ = try await store.export(metadata: metadata, now: now); XCTFail("Expected file failure") }
        catch { /* Diagnostics storage failure is contained. */ }
    }

    func testOversizedOrCorruptLocalFileIsIgnoredWithBoundedRead() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let file = directory.appendingPathComponent("records.json")
        try Data(repeating: 65, count: 600 * 1_024).write(to: file)
        let oversized = ConnectionDiagnosticsStore(directory: directory)
        let first = await oversized.report(metadata: metadata, now: now)
        XCTAssertTrue(first.records.isEmpty)
        try Data("invalid JSON".utf8).write(to: file)
        let corrupt = ConnectionDiagnosticsStore(directory: directory)
        let second = await corrupt.report(metadata: metadata, now: now)
        XCTAssertTrue(second.records.isEmpty)
    }

    func testLocalPhotoFailureIsRecordedSeparatelyWithoutBackendRequest() async {
        let store = ConnectionDiagnosticsStore()
        do {
            let _: Data = try await ConnectionDiagnosticsTransport.local(operation: .photoLibraryRead, store: store) {
                throw URLError(.secureConnectionFailed)
            }
            XCTFail("Expected the original failure")
        } catch { XCTAssertEqual((error as NSError).code, URLError.secureConnectionFailed.rawValue) }
        let report = await store.report(metadata: metadata)
        XCTAssertEqual(report.records.count, 1)
        XCTAssertEqual(report.records.first?.operation, .photoLibraryRead)
        XCTAssertEqual(report.records.first?.target, .local)
        XCTAssertEqual(report.records.first?.outcome, .localError)
    }
}

final class ConnectionTransportTests: XCTestCase {
    private let metadata = ConnectionReportMetadata(appVersion: "1.0", build: "55", osVersion: "26", deviceFamily: "iPad")

    private func makeSession() -> URLSession {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [OfflineDiagnosticProtocol.self]
        return URLSession(configuration: configuration)
    }

    func testHTTPFailureKeepsResponseAndNeverRecordsBodyOrAuthorization() async throws {
        OfflineDiagnosticProtocol.configure(status: 403, failure: nil)
        let session = makeSession()
        defer { session.invalidateAndCancel() }
        let store = ConnectionDiagnosticsStore()
        var request = URLRequest(url: URL(string: "https://api.pusulaiklimlendirme.com/api/tickets/987654/photos?token=SECRET-QUERY")!)
        request.httpMethod = "POST"
        request.setValue("Bearer SECRET-TOKEN", forHTTPHeaderField: "Authorization")
        request.httpBody = Data("SECRET-BODY".utf8)
        let response = try await ConnectionDiagnosticsTransport.data(for: request, operation: .upload, session: session, store: store)
        XCTAssertEqual((response.1 as? HTTPURLResponse)?.statusCode, 403)
        XCTAssertEqual(String(decoding: response.0, as: UTF8.self), "SECRET-RESPONSE")
        XCTAssertEqual(OfflineDiagnosticProtocol.attempts, 1)
        XCTAssertTrue(OfflineDiagnosticProtocol.sawAuthorization)
        XCTAssertNotNil(UUID(uuidString: OfflineDiagnosticProtocol.diagnosticID ?? ""))
        let report = await store.report(metadata: metadata)
        XCTAssertEqual(report.records.first?.status, 403)
        XCTAssertEqual(report.records.first?.outcome, .httpError)
        let text = String(decoding: try ConnectionDiagnosticsStore.encoder().encode(report), as: UTF8.self)
        XCTAssertFalse(text.contains("SECRET"))
        XCTAssertFalse(text.contains("987654"))
    }

    func testTLSFailureIsRethrownAndPOSTIsNeverAutomaticallyRetried() async throws {
        OfflineDiagnosticProtocol.configure(status: 200, failure: URLError(.secureConnectionFailed))
        let session = makeSession()
        defer { session.invalidateAndCancel() }
        let store = ConnectionDiagnosticsStore()
        var request = URLRequest(url: URL(string: "https://api.pusulaiklimlendirme.com/api/tickets/42/photos")!)
        request.httpMethod = "POST"
        do { _ = try await ConnectionDiagnosticsTransport.data(for: request, operation: .upload, session: session, store: store); XCTFail("Expected TLS failure") }
        catch { XCTAssertEqual((error as NSError).code, URLError.secureConnectionFailed.rawValue) }
        XCTAssertEqual(OfflineDiagnosticProtocol.attempts, 1)
        let report = await store.report(metadata: metadata)
        XCTAssertEqual(report.records.first?.outcome, .transportError)
        XCTAssertEqual(report.records.first?.errors.first?.code, URLError.secureConnectionFailed.rawValue)
    }

    func testURLCancellationIsPreservedRatherThanConvertedToTLSOrSuccess() async throws {
        OfflineDiagnosticProtocol.configure(status: 200, failure: URLError(.cancelled))
        let session = makeSession()
        defer { session.invalidateAndCancel() }
        let store = ConnectionDiagnosticsStore()
        let request = URLRequest(url: URL(string: "https://api.pusulaiklimlendirme.com/api/customers")!)
        do { _ = try await ConnectionDiagnosticsTransport.data(for: request, operation: .api, session: session, store: store); XCTFail("Expected cancellation") }
        catch { XCTAssertEqual((error as NSError).code, URLError.cancelled.rawValue) }
        let report = await store.report(metadata: metadata)
        XCTAssertEqual(report.records.first?.outcome, .cancelled)
    }
}

// Installed only on each test's ephemeral URLSession. No real network access.
private final class OfflineDiagnosticProtocol: URLProtocol, @unchecked Sendable {
    private static let lock = NSLock()
    private static var status = 200
    private static var failure: URLError?
    private static var count = 0
    private static var authorization = false
    private static var requestID: String?
    static var attempts: Int { lock.lock(); defer { lock.unlock() }; return count }
    static var sawAuthorization: Bool { lock.lock(); defer { lock.unlock() }; return authorization }
    static var diagnosticID: String? { lock.lock(); defer { lock.unlock() }; return requestID }

    static func configure(status: Int, failure: URLError?) {
        lock.lock(); defer { lock.unlock() }
        self.status = status; self.failure = failure; count = 0; authorization = false; requestID = nil
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        Self.lock.lock()
        Self.count += 1
        let status = Self.status
        let failure = Self.failure
        Self.authorization = request.value(forHTTPHeaderField: "Authorization") == "Bearer SECRET-TOKEN"
        Self.requestID = request.value(forHTTPHeaderField: "X-Pusula-Diagnostic-ID")
        Self.lock.unlock()
        if let failure { client?.urlProtocol(self, didFailWithError: failure); return }
        let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: "HTTP/1.1", headerFields: nil)!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data("SECRET-RESPONSE".utf8))
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}
