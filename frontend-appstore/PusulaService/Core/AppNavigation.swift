import SwiftUI
import Observation

/// Shared navigation state between admin dashboard and tab shell.
@Observable
final class AppNavigation {
    static let shared = AppNavigation()
    
    var adminSelectedTab: AdminTab = .overview
    var operationFilter: String?
    
    func openOperations(with filter: String) {
        operationFilter = filter
        adminSelectedTab = .operations
    }
    
    func consumeOperationFilter() -> String? {
        defer { operationFilter = nil }
        return operationFilter
    }
}

enum AdminTab: Hashable {
    case overview
    case operations
    case more
    case finance
    case account
}
