import Foundation

/// Coordinates view-scoped reads without allowing a dismissed view, an older
/// request or a previous account to publish its result into the current screen.
struct RefreshRequestState {
    private var activeRequest: UUID?
    private var requestSession: AuthenticatedSessionSnapshot?

    var isLoading: Bool { activeRequest != nil }

    mutating func begin(for session: AuthenticatedSessionSnapshot) -> UUID? {
        // Pull-to-refresh and the initial .task can overlap. Do not launch a
        // second read for the same session while the first read is in flight.
        guard activeRequest == nil || requestSession != session else { return nil }
        let request = UUID()
        activeRequest = request
        requestSession = session
        return request
    }

    func accepts(_ request: UUID, session: AuthenticatedSessionSnapshot?,
                 isCancelled: Bool) -> Bool {
        !isCancelled && activeRequest == request && requestSession == session
    }

    mutating func finish(_ request: UUID) {
        guard activeRequest == request else { return }
        invalidate()
    }

    mutating func invalidate() {
        activeRequest = nil
        requestSession = nil
    }
}

enum LoadCancellationPolicy {
    static func isCancellation(_ error: Error, taskIsCancelled: Bool = false) -> Bool {
        if taskIsCancelled || error is CancellationError { return true }
        let error = error as NSError
        return error.domain == NSURLErrorDomain && error.code == URLError.cancelled.rawValue
    }
}
