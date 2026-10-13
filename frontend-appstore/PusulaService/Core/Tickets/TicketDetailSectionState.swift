import Foundation

/// A failed read is not an empty result. Keep the last successful records while
/// a section is refreshed, and reject completions from superseded requests.
struct TicketDetailSectionState<Item> {
    enum Phase: Equatable {
        case idle, loading, loaded, cancelled
        case failed(TicketDetailLoadFailure)
    }

    private(set) var items: [Item] = []
    private(set) var phase: Phase = .idle
    private var activeRequest: UUID?
    private var previousPhase: Phase = .idle

    var isLoading: Bool { phase == .loading }
    var isLoaded: Bool { phase == .loaded }
    var showsEmpty: Bool { isLoaded && items.isEmpty }
    var failure: TicketDetailLoadFailure? {
        if case .failed(let failure) = phase { return failure }
        return nil
    }

    mutating func beginLoading() -> UUID? {
        guard activeRequest == nil else { return nil }
        previousPhase = phase
        phase = .loading
        let request = UUID()
        activeRequest = request
        return request
    }

    mutating func succeed(_ items: [Item], request: UUID) {
        guard activeRequest == request else { return }
        replaceVerifiedItems(items)
    }

    mutating func fail(_ failure: TicketDetailLoadFailure, request: UUID) {
        guard activeRequest == request else { return }
        activeRequest = nil
        phase = .failed(failure)
    }

    mutating func cancel(request: UUID) {
        guard activeRequest == request else { return }
        activeRequest = nil
        phase = previousPhase == .idle ? .cancelled : previousPhase
    }

    /// A successful mutation changes visible records without claiming that an
    /// earlier failed full-list read has now succeeded.
    mutating func updateVisibleItems(_ items: [Item]) {
        self.items = items
    }

    mutating func replaceVerifiedItems(_ items: [Item]) {
        activeRequest = nil
        self.items = items
        phase = .loaded
    }
}

enum TicketDetailLoadFailure: Equatable {
    case connection, secureConnection, unavailable

    init(error: Error) {
        let error = error as NSError
        guard error.domain == NSURLErrorDomain else {
            self = .unavailable
            return
        }
        switch URLError.Code(rawValue: error.code) {
        case .secureConnectionFailed, .serverCertificateHasBadDate,
             .serverCertificateUntrusted, .serverCertificateHasUnknownRoot,
             .serverCertificateNotYetValid, .clientCertificateRejected,
             .clientCertificateRequired:
            self = .secureConnection
        case .notConnectedToInternet, .networkConnectionLost, .timedOut,
             .cannotFindHost, .cannotConnectToHost, .dnsLookupFailed:
            self = .connection
        default:
            self = .unavailable
        }
    }

    var messageKey: String {
        switch self {
        case .connection: return "ticket.detail.connection_error"
        case .secureConnection: return "ticket.detail.secure_connection_error"
        case .unavailable: return "ticket.detail.load_error"
        }
    }

    static func isCancellation(_ error: Error) -> Bool {
        if error is CancellationError { return true }
        let error = error as NSError
        return error.domain == NSURLErrorDomain && error.code == URLError.cancelled.rawValue
    }
}
