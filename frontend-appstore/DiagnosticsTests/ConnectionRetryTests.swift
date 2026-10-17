import Foundation
import XCTest
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
@testable import PusulaDiagnostics

final class ConnectionRetryTests: XCTestCase {
    func testBeforeSendBarrierPreventsEvenTheInitialPhysicalRequest() async throws {
        RetryTestProtocol.configure([.http(200)])
        let session=session();defer{session.invalidateAndCancel()}
        do {
            _ = try await ConnectionReliableTransport.data(for:request(),operation:.api,session:session,
                beforeSend:{throw URLError(.cancelled)})
            XCTFail("Expected safety barrier")
        } catch { XCTAssertEqual((error as NSError).code,-999) }
        XCTAssertTrue(RetryTestProtocol.requests.isEmpty)
    }
    func testBeforeSendBarrierIsCheckedAgainOnPhysicalRetry() async throws {
        RetryTestProtocol.configure([.http(503),.http(200)])
        let session=session();defer{session.invalidateAndCancel()}
        let counter=SendBarrierCounter()
        do {
            _ = try await ConnectionReliableTransport.data(for:request(),operation:.api,session:session,backoffNanoseconds:0,
                beforeSend:{if counter.next()>1 {throw URLError(.cancelled)}})
            XCTFail("Expected retry safety barrier")
        } catch { XCTAssertEqual((error as NSError).code,-999) }
        XCTAssertEqual(RetryTestProtocol.requests.count,1)
    }
    private let metadata = ConnectionReportMetadata(appVersion: "1.0", build: "55", osVersion: "26", deviceFamily: "iPhone")
    private func request(_ path: String = "/api/reliable-mutations/tickets", method: String = "POST", keyed: Bool = true) -> URLRequest {
        var value = URLRequest(url: URL(string: "https://api.pusulaiklimlendirme.com" + path)!)
        value.httpMethod = method; value.httpBody = Data("PRIVATE-FIXTURE".utf8)
        if keyed { value.setValue(UUID().uuidString, forHTTPHeaderField: "Idempotency-Key") }
        return value
    }
    private func session() -> URLSession {
        let config = URLSessionConfiguration.ephemeral; config.protocolClasses = [RetryTestProtocol.self]
        return URLSession(configuration: config)
    }
    private func abruptTLS() -> NSError {
        NSError(domain: NSURLErrorDomain, code: -1200, userInfo: [NSUnderlyingErrorKey:
            NSError(domain: "kCFErrorDomainCFNetwork", code: -1200, userInfo: [
                "_kCFStreamErrorDomainKey": 3, "_kCFStreamErrorCodeKey": -9816])])
    }

    func testAbruptTLSRetryPreservesKeyAndBodyButHasSeparateDiagnosticIDs() async throws {
        RetryTestProtocol.configure([.failure(abruptTLS()), .http(200)])
        let session = session(); defer { session.invalidateAndCancel() }
        let store = ConnectionDiagnosticsStore(); let original = request()
        let result = try await ConnectionReliableTransport.data(for: original, operation: .api,
                session: session, store: store, backoffNanoseconds: 0)
        XCTAssertEqual((result.1 as? HTTPURLResponse)?.statusCode, 200)
        let requests = RetryTestProtocol.requests
        XCTAssertEqual(requests.count, 2)
        XCTAssertEqual(requests[0].value(forHTTPHeaderField: "Idempotency-Key"), requests[1].value(forHTTPHeaderField: "Idempotency-Key"))
        XCTAssertEqual(requests[0].httpBody, requests[1].httpBody)
        XCTAssertNotEqual(requests[0].value(forHTTPHeaderField: "X-Pusula-Diagnostic-ID"), requests[1].value(forHTTPHeaderField: "X-Pusula-Diagnostic-ID"))
        let report = await store.report(metadata: metadata)
        XCTAssertEqual(report.records.count, 2)
        XCTAssertEqual(report.records.filter { $0.outcome == .transportError }.count, 1)
        XCTAssertEqual(report.records.filter { $0.outcome == .success }.count, 1)
        XCTAssertFalse(String(decoding: try ConnectionDiagnosticsStore.encoder().encode(report), as: UTF8.self).contains("PRIVATE-FIXTURE"))
    }

    func testLegacyPostIsNotRetriedEvenIfItHasAKey() async throws {
        RetryTestProtocol.configure([.failure(abruptTLS()), .http(200)])
        let session = session(); defer { session.invalidateAndCancel() }
        do {
            _ = try await ConnectionReliableTransport.data(for: request("/api/tickets"), operation: .api,
                    session: session, backoffNanoseconds: 0); XCTFail("Expected original failure")
        } catch { XCTAssertEqual((error as NSError).code, -1200) }
        XCTAssertEqual(RetryTestProtocol.requests.count, 1)
    }

    func testMissingKeyPreventsMutationReplay() {
        XCTAssertFalse(ConnectionRetryPolicy.permitsReplay(request(keyed: false)))
        XCTAssertFalse(ConnectionRetryPolicy.permitsReplay(request("/api/finance/pay")))
        XCTAssertFalse(ConnectionRetryPolicy.permitsReplay(request("/api/auth/apple")))
        XCTAssertFalse(ConnectionRetryPolicy.permitsReplay(request("/api/reliable-mutations/tickets/1/photos/extra")))
        XCTAssertTrue(ConnectionRetryPolicy.permitsReplay(request("/api/reliable-mutations/tickets/123/photos")))
        XCTAssertTrue(ConnectionRetryPolicy.permitsReplay(request("/api/reliable-mutations/tickets/123/complete")))
        XCTAssertTrue(ConnectionRetryPolicy.permitsReplay(request("/api/reliable-mutations/tickets/999/technician-notes")))
        XCTAssertFalse(ConnectionRetryPolicy.permitsReplay(request("/api/reliable-mutations/tickets/999/technician-notes", keyed: false)))
        XCTAssertFalse(ConnectionRetryPolicy.permitsReplay(request("/api/tickets/999/technician-notes")))
        XCTAssertFalse(ConnectionRetryPolicy.permitsReplay(request("/api/reliable-mutations/tickets/999/technician-notes/1")))
        XCTAssertFalse(ConnectionRetryPolicy.permitsReplay(request("/api/tickets/123/complete", method: "PATCH")))
        XCTAssertTrue(ConnectionRetryPolicy.permitsReplay(request("/api/customers", method: "GET", keyed: false)))
        var external = request(); external.url = URL(string: "https://other.invalid/api/reliable-mutations/tickets")
        XCTAssertFalse(ConnectionRetryPolicy.permitsReplay(external))
    }

    func testTechnicianNoteAbruptTLSRetriesOnceWithIdenticalIntentAndFreshDiagnosticID() async throws {
        RetryTestProtocol.configure([.failure(abruptTLS()), .http(200)])
        let session = session(); defer { session.invalidateAndCancel() }
        let original = request("/api/reliable-mutations/tickets/999/technician-notes")
        _ = try await ConnectionReliableTransport.data(for: original, operation: .api,
            session: session, backoffNanoseconds: 0)
        let sent = RetryTestProtocol.requests
        XCTAssertEqual(sent.count, 2)
        XCTAssertEqual(sent[0].httpBody, sent[1].httpBody)
        XCTAssertEqual(sent[0].value(forHTTPHeaderField: "Idempotency-Key"), sent[1].value(forHTTPHeaderField: "Idempotency-Key"))
        XCTAssertNotEqual(sent[0].value(forHTTPHeaderField: "X-Pusula-Diagnostic-ID"), sent[1].value(forHTTPHeaderField: "X-Pusula-Diagnostic-ID"))
    }

    func testTechnicianNoteRepeatedLostConnectionStopsAfterOneAutomaticRetry() async throws {
        RetryTestProtocol.configure([.failure(URLError(.networkConnectionLost) as NSError),
                                     .failure(URLError(.networkConnectionLost) as NSError), .http(200)])
        let session = session(); defer { session.invalidateAndCancel() }
        do {
            _ = try await ConnectionReliableTransport.data(for: request("/api/reliable-mutations/tickets/999/technician-notes"),
                operation: .api, session: session, backoffNanoseconds: 0)
            XCTFail("Expected unconfirmed failure")
        } catch { XCTAssertEqual((error as NSError).code, -1005) }
        XCTAssertEqual(RetryTestProtocol.requests.count, 2)
    }

    func testExpenseTimeoutReplaysOnlyVersionedKeyedWriteWithSameIntent() async throws {
        XCTAssertFalse(ConnectionRetryPolicy.permitsReplay(request("/api/finance/expenses")))
        XCTAssertFalse(ConnectionRetryPolicy.permitsReplay(request("/api/reliable-mutations/expenses", keyed: false)))
        XCTAssertTrue(ConnectionRetryPolicy.permitsReplay(request("/api/reliable-mutations/expenses")))
        XCTAssertFalse(ConnectionRetryPolicy.permitsReplay(request("/api/reliable-mutations/expenses/1", method: "PUT")))
        RetryTestProtocol.configure([.failure(NSError(domain: NSURLErrorDomain,code:-1001)),.http(200)])
        let session=session(); defer { session.invalidateAndCancel() }
        let original=request("/api/reliable-mutations/expenses")
        _ = try await ConnectionReliableTransport.data(for:original,operation:.api,session:session,backoffNanoseconds:0)
        let sent=RetryTestProtocol.requests
        XCTAssertEqual(sent.count,2)
        XCTAssertEqual(sent[0].httpBody,sent[1].httpBody)
        XCTAssertEqual(sent[0].value(forHTTPHeaderField:"Idempotency-Key"),sent[1].value(forHTTPHeaderField:"Idempotency-Key"))
        XCTAssertNotEqual(sent[0].value(forHTTPHeaderField:"X-Pusula-Diagnostic-ID"),sent[1].value(forHTTPHeaderField:"X-Pusula-Diagnostic-ID"))
    }

    func testGenericTLSCertificateOfflineAndCancellationFailuresAreNotRetryable() {
        for code in [-1200, -1201, -1202, -1203, -1204, -1022, -1009, -999] {
            XCTAssertFalse(ConnectionRetryPolicy.isTransient(NSError(domain: NSURLErrorDomain, code: code)), "code \(code)")
        }
        XCTAssertFalse(ConnectionRetryPolicy.isTransient(CancellationError()))
        XCTAssertTrue(ConnectionRetryPolicy.isTransient(abruptTLS()))
        XCTAssertTrue(ConnectionRetryPolicy.isTransient(URLError(.networkConnectionLost)))
    }

    func testRepeatedTransportFailureStopsAfterOneRetry() async throws {
        RetryTestProtocol.configure([.failure(abruptTLS()), .failure(abruptTLS()), .http(200)])
        let session = session(); defer { session.invalidateAndCancel() }
        do {
            _ = try await ConnectionReliableTransport.data(for: request(), operation: .api,
                    session: session, backoffNanoseconds: 0); XCTFail("Expected failure")
        } catch { XCTAssertEqual((error as NSError).code, -1200) }
        XCTAssertEqual(RetryTestProtocol.requests.count, 2)
    }

    func testGatewayFailureCanSafelyReplayAnAlreadyEncodedPhotoBody() async throws {
        RetryTestProtocol.configure([.http(502), .http(200)])
        let session = session(); defer { session.invalidateAndCancel() }
        _ = try await ConnectionReliableTransport.data(for: request("/api/reliable-mutations/tickets/5/photos"),
                operation: .upload, session: session, backoffNanoseconds: 0)
        XCTAssertEqual(RetryTestProtocol.requests.count, 2)
    }

    func testAccountChangePreventsRetryWithAnOldCredential() async throws {
        RetryTestProtocol.configure([.failure(abruptTLS()), .http(200)])
        let session = session(); defer { session.invalidateAndCancel() }
        do {
            _ = try await ConnectionReliableTransport.data(for: request(), operation: .api,
                session: session, backoffNanoseconds: 0, mayRetry: { false }); XCTFail("Expected cancellation")
        } catch { XCTAssertTrue(error is CancellationError) }
        XCTAssertEqual(RetryTestProtocol.requests.count, 1)
    }

    func testCancellationDuringBackoffDoesNotSendAnotherRequest() async throws {
        let first = expectation(description: "first attempt")
        RetryTestProtocol.configure([.failure(abruptTLS()), .http(200)], onRequest: { first.fulfill() })
        let session = session(); defer { session.invalidateAndCancel() }
        let original = request()
        let task = Task { try await ConnectionReliableTransport.data(for: original, operation: .api,
                session: session, backoffNanoseconds: 5_000_000_000) }
        await fulfillment(of: [first], timeout: 2)
        task.cancel()
        do { _ = try await task.value; XCTFail("Expected cancellation") }
        catch { XCTAssertTrue(error is CancellationError || (error as NSError).code == -999) }
        XCTAssertEqual(RetryTestProtocol.requests.count, 1)
    }

    func testHTTPAuthenticationValidationAndRateLimitErrorsAreNotRetried() async throws {
        for code in [400, 401, 403, 404, 409, 413, 422, 429] {
            RetryTestProtocol.configure([.http(code), .http(200)])
            let session = session()
            let result = try await ConnectionReliableTransport.data(for: request(), operation: .api,
                    session: session, backoffNanoseconds: 0)
            session.invalidateAndCancel()
            XCTAssertEqual((result.1 as? HTTPURLResponse)?.statusCode, code)
            XCTAssertEqual(RetryTestProtocol.requests.count, 1)
        }
        let response = HTTPURLResponse(url: request().url!, statusCode: 503, httpVersion: nil, headerFields: ["Retry-After": "60"])!
        XCTAssertFalse(ConnectionRetryPolicy.isTransient(response))
    }
}

private final class SendBarrierCounter: @unchecked Sendable {
    private let lock=NSLock();private var count=0
    func next()->Int {lock.lock();defer{lock.unlock()};count+=1;return count}
}
private final class RetryTestProtocol: URLProtocol, @unchecked Sendable {
    enum Result { case failure(NSError), http(Int) }
    private static let lock = NSLock()
    private static var steps: [Result] = []
    private static var received: [URLRequest] = []
    private static var callback: (() -> Void)?
    static var requests: [URLRequest] { lock.lock(); defer { lock.unlock() }; return received }
    static func configure(_ results: [Result], onRequest: (() -> Void)? = nil) {
        lock.lock(); defer { lock.unlock() }; steps = results; received = []; callback = onRequest
    }
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        Self.lock.lock(); Self.received.append(request)
        let result = Self.steps.isEmpty ? .http(500) : Self.steps.removeFirst()
        let callback = Self.callback; Self.callback = nil; Self.lock.unlock()
        callback?()
        switch result {
        case .failure(let error): client?.urlProtocol(self, didFailWithError: error)
        case .http(let status):
            let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: "HTTP/1.1", headerFields: [:])!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: Data("ok".utf8)); client?.urlProtocolDidFinishLoading(self)
        }
    }
    override func stopLoading() {}
}
