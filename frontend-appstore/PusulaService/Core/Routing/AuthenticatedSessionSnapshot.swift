import Foundation

/// Captured before an async action. A new login invalidates even a reused JWT.
struct AuthenticatedSessionSnapshot: Equatable, Sendable {
    let token: String
    let companyId: Int
    let revision: UUID
}

enum SessionRestorePolicy {
    static func shouldDiscardCredentials(httpStatus: Int?, unsupportedRole: Bool = false) -> Bool {
        unsupportedRole || httpStatus == 401 || httpStatus == 403
    }
}
