import SwiftUI
import OSLog

/// Shared navigation state between admin dashboard and tab shell.
@MainActor
final class AppNavigation: ObservableObject {
    static let shared = AppNavigation()
    
    @Published var adminSelectedTab: AdminTab = .overview
    @Published var operationFilter: String?
    @Published var networkDestination: NetworkNotificationRoute?
    @Published private(set) var ticketQueue = TicketNavigationQueue()
    @Published private(set) var isSceneActive = false
    @Published private(set) var isSessionReady = false
    @Published private(set) var isRootPresentationBlocked = true
    private var pendingNetworkDestination: NetworkNotificationRoute?
    private let logger = Logger(subsystem: "com.pusula.service", category: "Navigation")

    var pendingTicketRequest: TicketNavigationRequest? { ticketQueue.pending }
    var pendingTicketId: Int? { pendingTicketRequest?.ticketId }
    var canNavigate: Bool { isSceneActive && isSessionReady && !isRootPresentationBlocked }

    func setSceneActive(_ active: Bool) {
        if isSceneActive != active { isSceneActive = active }
        activatePendingNetwork()
    }

    func updateReadiness(authenticated: Bool, restoring: Bool, blocked: Bool) {
        let ready = authenticated && !restoring
        if isSessionReady != ready { isSessionReady = ready }
        if isRootPresentationBlocked != blocked { isRootPresentationBlocked = blocked }
        activatePendingNetwork()
    }

    func receive(_ destination: PushNavigationDestination) {
        switch destination {
        case .ticket(let id): openTicket(id: id)
        case .network(let reference, let id): openNetwork(referenceType: reference, referenceId: id)
        }
    }

    func openNetwork(referenceType: String, referenceId: Int) {
        guard let destination = NetworkNotificationRoute(referenceType: referenceType, referenceId: referenceId) else { return }
        ticketQueue.reset()
        pendingNetworkDestination = destination
        activatePendingNetwork()
    }

    private func activatePendingNetwork() {
        guard canNavigate, SessionManager.shared.isAdmin,
              let destination = pendingNetworkDestination else { return }
        adminSelectedTab = .overview
        networkDestination = destination
        pendingNetworkDestination = nil
    }
    
    func openOperations(with filter: String) {
        operationFilter = filter
        adminSelectedTab = .operations
    }
    
    func consumeOperationFilter() -> String? {
        defer { operationFilter = nil }
        return operationFilter
    }

    func openTicket(id: Int) {
        guard id > 0 else { return }
        pendingNetworkDestination = nil
        ticketQueue.enqueue(ticketId: id, companyId: SessionManager.shared.companyId)
        logger.info("Ticket navigation queued; presentation will wait for readiness")
    }

    func acknowledgeTicket(_ request: TicketNavigationRequest) {
        guard ticketQueue.pending?.id == request.id else { return }
        ticketQueue.acknowledge(request)
        logger.info("Ticket navigation consumed by the visible detail screen")
    }

    func resetForLogout() {
        isSessionReady = false
        ticketQueue.reset()
        pendingNetworkDestination = nil
        networkDestination = nil
        operationFilter = nil
        adminSelectedTab = .overview
    }
}

enum AdminTab: Hashable {
    case overview
    case operations
    case more
    case finance
    case account
}
