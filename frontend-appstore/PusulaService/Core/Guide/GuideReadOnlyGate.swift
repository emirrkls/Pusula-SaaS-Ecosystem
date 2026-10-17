import Foundation

/// A tour is a temporary client-side safety barrier, not a server permission.
/// Independent leases prevent an old screen's cleanup from ending a newer tour.
public final class GuideReadOnlyGate: @unchecked Sendable {
    public static let shared = GuideReadOnlyGate()
    private let lock = NSLock()
    private var leases: Set<UUID> = []
    public init() {}
    @discardableResult public func begin() -> UUID {
        lock.lock(); defer { lock.unlock() }
        let lease = UUID(); leases.insert(lease); return lease
    }
    public func end(_ lease: UUID) { lock.lock(); defer { lock.unlock() }; leases.remove(lease) }
    public var isActive: Bool { lock.lock(); defer { lock.unlock() }; return !leases.isEmpty }
    public func allows(method: String) -> Bool {
        !isActive || ["GET", "HEAD"].contains(method.uppercased())
    }
    public func validate(method: String) throws {
        guard allows(method: method) else { throw GuideReadOnlyViolation() }
    }
}
public struct GuideReadOnlyViolation: LocalizedError {
    public init() {}
    public var errorDescription: String? { "Tur sırasında kayıt değiştiren işlemler kapalıdır. Normal kullanım için turdan çıkın." }
}

public enum GuideScreen: String, Codable, CaseIterable, Sendable {
    case home, customers, customerForm, customerDetail, customerContact
    case tickets, ticketCreate, ticketDetail, reschedule, photos, photosArchive, signature, parts, collection
    case catalog, proposals, finance, expenses, accounts, organizations, debts, fixedExpenses, assets, reports, profit
    case team, vehicles, company, radar, network, notifications
    case profile, plans, login, register, desktopPassword, support, supportForm, diagnostics
    public func needsTicket(role: String) -> Bool {
        [.ticketDetail,.reschedule,.photos,.signature,.collection].contains(self) || self == .customerContact && role == "TECHNICIAN"
    }
    public func needsCustomer(role: String) -> Bool {
        self == .customerDetail || self == .customerContact && role != "TECHNICIAN"
    }
}
public struct GuideTourRoute: Codable, Equatable, Sendable {
    public let screen: GuideScreen
    public let target: String
}
