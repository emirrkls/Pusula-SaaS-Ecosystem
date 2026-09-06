import SwiftUI

/// Shared navigation state between admin dashboard and tab shell.
final class AppNavigation: ObservableObject {
    static let shared = AppNavigation()
    
    @Published var adminSelectedTab: AdminTab = .overview
    @Published var operationFilter: String?
    @Published var networkDestination: NetworkNotificationRoute?

    func openNetwork(referenceType: String, referenceId: Int) {
        guard let destination = NetworkNotificationRoute(referenceType: referenceType, referenceId: referenceId) else { return }
        adminSelectedTab = .overview
        networkDestination = destination
    }
    @Published private(set) var pendingTicketId: Int?
    
    func openOperations(with filter: String) {
        operationFilter = filter
        adminSelectedTab = .operations
    }
    
    func consumeOperationFilter() -> String? {
        defer { operationFilter = nil }
        return operationFilter
    }

    func openTicket(id: Int) {
        pendingTicketId = id
        adminSelectedTab = .operations
    }

    func clearPendingTicket(id: Int) {
        guard pendingTicketId == id else { return }
        pendingTicketId = nil
    }
}

enum AdminTab: Hashable {
    case overview
    case operations
    case more
    case finance
    case account
}
