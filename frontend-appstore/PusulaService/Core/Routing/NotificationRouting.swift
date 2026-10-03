import Foundation
import CoreFoundation

/// Only value types cross from the notification callback to the UI actor.
enum PushNavigationDestination: Equatable, Sendable {
    case ticket(Int)
    case network(referenceType: String, referenceId: Int)

    static func parse(_ userInfo: [AnyHashable: Any]) -> Self? {
        if let reference = userInfo["referenceType"] as? String,
           reference.hasPrefix("NETWORK_") {
            guard let id = positiveID(userInfo["referenceId"]) else { return nil }
            return .network(referenceType: reference, referenceId: id)
        }
        if let id = positiveID(userInfo["ticketId"]) { return .ticket(id) }
        // A finance/customer reference is not a service-ticket identifier.
        guard userInfo["referenceType"] as? String == "TICKET",
              let id = positiveID(userInfo["referenceId"]) else { return nil }
        return .ticket(id)
    }

    private static func positiveID(_ value: Any?) -> Int? {
        let id: Int?
        if let number = value as? NSNumber {
            guard CFGetTypeID(number) != CFBooleanGetTypeID() else { return nil }
            id = Int(number.stringValue)
        } else if let text = value as? String {
            id = Int(text)
        } else {
            return nil
        }
        guard let id, id > 0 else { return nil }
        return id
    }
}

struct TicketNavigationRequest: Equatable, Identifiable, Sendable {
    let id: UUID
    let ticketId: Int
    let companyId: Int?
}

/// Latest tap wins. An older fetch/presentation must never consume a newer tap.
struct TicketNavigationQueue: Equatable, Sendable {
    private(set) var pending: TicketNavigationRequest?

    mutating func enqueue(ticketId: Int, companyId: Int?) {
        guard ticketId > 0 else { return }
        guard pending?.ticketId != ticketId || pending?.companyId != companyId else { return }
        pending = TicketNavigationRequest(id: UUID(), ticketId: ticketId, companyId: companyId)
    }

    mutating func acknowledge(_ request: TicketNavigationRequest) {
        guard pending?.id == request.id else { return }
        pending = nil
    }

    mutating func reset() { pending = nil }

    func matches(_ request: TicketNavigationRequest, companyId: Int?) -> Bool {
        pending?.id == request.id && (request.companyId == nil || request.companyId == companyId)
    }
}

struct TicketPresentationReadiness: Equatable, Sendable {
    let isAuthenticated: Bool
    let isRestoringSession: Bool
    let isSceneActive: Bool
    let isScreenVisible: Bool
    let isPresentationBlocked: Bool

    var canPresent: Bool {
        isAuthenticated && !isRestoringSession && isSceneActive
            && isScreenVisible && !isPresentationBlocked
    }
}

struct PendingTicketTrigger: Equatable, Sendable {
    let request: TicketNavigationRequest?
    let readiness: TicketPresentationReadiness
    let selectedTicketId: Int?
}

enum SessionResponsePolicy {
    static func belongsToCurrentSession(requestToken: String?, currentToken: String?) -> Bool {
        guard let requestToken, !requestToken.isEmpty else { return false }
        return requestToken == currentToken
    }
}
