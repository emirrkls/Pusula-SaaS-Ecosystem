import Foundation

// This schema deliberately has no headers, body, raw URL, error message,
// account/device identifiers, IP addresses, SSID or arbitrary userInfo fields.
enum ConnectionOperation: String, Codable, Sendable { case api, download, upload, photoLibraryRead, photoPreparation }
enum ConnectionOutcome: String, Codable, Sendable { case success, httpError, transportError, cancelled, localError }
enum ConnectionInterface: String, Codable, Sendable { case unknown, wifi, cellular, wired, other, unavailable }
enum ConnectionAppState: String, Codable, Sendable { case unknown, active, inactive, background }
enum ConnectionTarget: String, Codable, Sendable { case api, external, local }
enum ConnectionErrorDomain: String, Codable, Sendable {
    case url = "NSURLErrorDomain", cfNetwork = "kCFErrorDomainCFNetwork"
    case posix = "NSPOSIXErrorDomain", osStatus = "NSOSStatusErrorDomain"
    case photos = "PHPhotosErrorDomain", itemProvider = "NSItemProviderErrorDomain"
    case cancellation = "CancellationError", other
}

struct ConnectionErrorCode: Codable, Equatable, Sendable {
    let domain: ConnectionErrorDomain
    let code: Int

    static func capture(_ error: Error) -> [Self] {
        if error is CancellationError { return [Self(domain: .cancellation, code: 0)] }
        var result: [Self] = []
        var current: NSError? = error as NSError
        var visited = Set<ObjectIdentifier>()
        while let value = current, result.count < 4, visited.insert(ObjectIdentifier(value)).inserted {
            result.append(Self(domain: ConnectionErrorDomain(rawValue: value.domain) ?? .other,
                               code: value.code))
            current = value.userInfo[NSUnderlyingErrorKey] as? NSError
        }
        return result
    }
}

struct ConnectionTransaction: Codable, Equatable, Sendable {
    var dnsMilliseconds: Int?
    var connectMilliseconds: Int?
    var tlsMilliseconds: Int?
    var requestMilliseconds: Int?
    var responseMilliseconds: Int?
    var dnsStarted = false
    var connectionStarted = false
    var tlsStarted = false
    var tlsCompleted = false
    var requestStarted = false
    var responseStarted = false
    var reusedConnection = false
    var proxyConnection = false
    var cellular = false
    var protocolName: String?
    var tlsProtocol: Int?

    func redacted() -> Self {
        var result = self
        result.protocolName = ["h2", "h3", "http/1.1", "http/1.0"].contains(protocolName ?? "") ? protocolName : nil
        result.tlsProtocol = [0x0303, 0x0304].contains(tlsProtocol ?? 0) ? tlsProtocol : nil
        return result
    }
}

enum ConnectionDiagnosticPrivacy {
    // Only literal API vocabulary survives. Dynamic IDs, names, search terms,
    // signed file paths and query strings never enter storage or the report.
    private static let vocabulary: Set<String> = [
        "api", "auth", "authenticate", "google", "apple", "challenge", "register", "register-individual",
        "feature-context", "initial-password", "delete-account", "onboarding-version", "subscription",
        "my-context", "plans", "verify", "context", "tickets", "customer", "customers", "parts", "photos",
        "technician-notes", "signature", "complete", "cancel", "follow-up", "audit-logs", "ticket",
        "users", "technicians", "inventory", "vehicles", "catalog", "proposals", "notifications",
        "unread-count", "read", "all", "admin", "dashboard", "technician-stats", "quota-status",
        "finance", "expenses", "fixed-expenses", "close-day", "close-days", "close-days-range", "pay",
        "profit", "report", "reports", "daily", "summary", "history", "current-accounts", "company-debts",
        "account-parties", "billing-options", "payments", "parties", "add", "business-assets",
        "commercial-devices", "companies", "me", "logo", "settings", "push-devices", "unregister",
        "whatsapp-consent", "service-network", "members", "orders", "page", "accept", "reject", "notes",
        "public", "desktop-version", "uploads", "service-photos", "service-photo-thumbnails", ":id", ":redacted"
    ]

    static func route(_ path: String) -> String {
        let withoutQuery = path.split(separator: "?", maxSplits: 1).first.map(String.init) ?? ""
        return "/" + withoutQuery.split(separator: "/").prefix(10).map { segment in
            let value = String(segment)
            if vocabulary.contains(value) { return value }
            if !value.isEmpty && value.allSatisfy(\.isNumber) { return ":id" }
            return ":redacted"
        }.joined(separator: "/")
    }

    static func version(_ value: String) -> String {
        let bounded = String(value.prefix(32))
        return !bounded.isEmpty && bounded.allSatisfy({ $0.isNumber || $0 == "." }) ? bounded : "unknown"
    }
}

struct ConnectionDiagnosticRecord: Codable, Identifiable, Equatable, Sendable {
    let id: UUID
    let startedAt: Date
    let operation: ConnectionOperation
    let target: ConnectionTarget
    let route: String
    let method: String
    let outcome: ConnectionOutcome
    let status: Int?
    let elapsedMilliseconds: Int
    let interface: ConnectionInterface
    let appState: ConnectionAppState
    let errors: [ConnectionErrorCode]
    let streamErrorDomain: Int?
    let streamErrorCode: Int?
    let transactions: [ConnectionTransaction]

    init(id: UUID = UUID(), startedAt: Date = Date(), operation: ConnectionOperation,
         url: URL? = nil, method: String = "LOCAL", status: Int? = nil,
         elapsed: TimeInterval = 0, interface: ConnectionInterface = .unknown,
         appState: ConnectionAppState = .unknown, error: Error? = nil,
         transactions: [ConnectionTransaction] = []) {
        self.id = id
        self.startedAt = startedAt
        self.operation = operation
        target = url == nil ? .local : (url?.host == "api.pusulaiklimlendirme.com" ? .api : .external)
        route = target == .api ? ConnectionDiagnosticPrivacy.route(url?.path ?? "") : "/"
        self.method = ["GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "LOCAL"].contains(method) ? method : "OTHER"
        self.status = status.flatMap { (100...599).contains($0) ? $0 : nil }
        elapsedMilliseconds = elapsed.isFinite ? Int(min(max(elapsed, 0), 86_400) * 1_000) : 0
        self.interface = interface
        self.appState = appState
        errors = error.map(ConnectionErrorCode.capture) ?? []
        let nsError = error.map { $0 as NSError }
        streamErrorDomain = (nsError?.userInfo["_kCFStreamErrorDomainKey"] as? NSNumber)?.intValue
        streamErrorCode = (nsError?.userInfo["_kCFStreamErrorCodeKey"] as? NSNumber)?.intValue
        self.transactions = Array(transactions.suffix(4)).map { $0.redacted() }
        if error is CancellationError || errors.contains(where: { $0.domain == .url && $0.code == -999 }) {
            outcome = .cancelled
        } else if error != nil { outcome = target == .local ? .localError : .transportError }
        else if let status, !(200...299).contains(status) { outcome = .httpError }
        else { outcome = .success }
    }

    // Also re-sanitize restored records: no untrusted strings from a damaged or
    // modified local file can be passed through into a user-shared report.
    func redacted() -> Self {
        let safeURL = target == .api ? URL(string: "https://api.pusulaiklimlendirme.com" + ConnectionDiagnosticPrivacy.route(route))
            : (target == .external ? URL(string: "https://external.invalid") : nil)
        let error = errors.first.map { NSError(domain: $0.domain.rawValue, code: $0.code) }
        return Self(id: id, startedAt: startedAt, operation: operation, url: safeURL, method: method,
                    status: status, elapsed: Double(elapsedMilliseconds) / 1_000,
                    interface: interface, appState: appState, error: error, transactions: transactions)
            .preservingSafeErrorDetails(from: self)
    }

    private func preservingSafeErrorDetails(from source: Self) -> Self {
        // All these values have closed enum or numeric types; no free text.
        Self(id: id, startedAt: startedAt, operation: operation, target: target, route: route, method: method,
             outcome: source.outcome, status: status, elapsedMilliseconds: elapsedMilliseconds,
             interface: interface, appState: appState, errors: Array(source.errors.prefix(4)),
             streamErrorDomain: source.streamErrorDomain, streamErrorCode: source.streamErrorCode,
             transactions: transactions)
    }

    private init(id: UUID, startedAt: Date, operation: ConnectionOperation, target: ConnectionTarget,
                 route: String, method: String, outcome: ConnectionOutcome, status: Int?, elapsedMilliseconds: Int,
                 interface: ConnectionInterface, appState: ConnectionAppState, errors: [ConnectionErrorCode],
                 streamErrorDomain: Int?, streamErrorCode: Int?, transactions: [ConnectionTransaction]) {
        self.id = id; self.startedAt = startedAt; self.operation = operation; self.target = target
        self.route = route; self.method = method; self.outcome = outcome; self.status = status
        self.elapsedMilliseconds = elapsedMilliseconds; self.interface = interface; self.appState = appState
        self.errors = errors; self.streamErrorDomain = streamErrorDomain; self.streamErrorCode = streamErrorCode
        self.transactions = transactions
    }
}

struct ConnectionReportMetadata: Codable, Sendable {
    let appVersion: String
    let build: String
    let osVersion: String
    let deviceFamily: String

    init(appVersion: String, build: String, osVersion: String, deviceFamily: String) {
        self.appVersion = ConnectionDiagnosticPrivacy.version(appVersion)
        self.build = ConnectionDiagnosticPrivacy.version(build)
        self.osVersion = ConnectionDiagnosticPrivacy.version(osVersion)
        self.deviceFamily = ["iPhone", "iPad", "other"].contains(deviceFamily) ? deviceFamily : "other"
    }
}

struct ConnectionDiagnosticReport: Codable, Sendable {
    let schemaVersion = 1
    let generatedAt: Date
    let metadata: ConnectionReportMetadata
    let records: [ConnectionDiagnosticRecord]
    let scope = "Pusula API requests/downloads/uploads and instrumented local photo stages; not all OS, StoreKit, social-provider or AsyncImage traffic. Missing metrics do not prove a particular failure stage."
}
