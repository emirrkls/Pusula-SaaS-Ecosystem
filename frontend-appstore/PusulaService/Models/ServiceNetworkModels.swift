import Foundation

struct ServiceNetworkContext: Decodable {
    let companyId: Int
    let canManage: Bool
    let writable: Bool
    let maxMembers: Int
    let usedMembers: Int
    let maxMonthlyOrders: Int
    let usedMonthlyOrders: Int
}

struct ServiceNetworkPage<T: Decodable>: Decodable {
    let items: [T]
    let totalElements: Int
    let page: Int
    let totalPages: Int
    let hasNext: Bool
}

struct ServiceNetworkMember: Decodable, Identifiable {
    let id: Int
    let parentCompanyId: Int
    let childCompanyId: Int
    let parentName: String
    let childName: String
    let region: String?
    let status: String
    let createdAt: String
}

struct ServiceNetworkOrder: Decodable, Identifiable {
    let id: Int
    let membershipId: Int
    let parentCompanyId: Int
    let childCompanyId: Int
    let parentName: String
    let childName: String
    let title: String
    let customerName: String
    let customerPhone: String?
    let customerAddress: String?
    let instruction: String?
    let scheduledDate: String
    let scheduledEndDate: String?
    let status: String
    let acceptedTicketId: Int?
    let ticketStatus: String?
    let currentScheduledDate: String?
    let currentScheduledEndDate: String?
    let completedAt: String?
    let resolutionNote: String?
    let createdAt: String
}

struct ServiceNetworkEvent: Decodable, Identifiable {
    let id: Int
    let action: String
    let note: String?
    let actorCompanyId: Int
    let createdAt: String
}

struct ServiceNetworkCreatedChild: Decodable {
    let member: ServiceNetworkMember
    let orgCode: String
    let username: String
}

enum ServiceNetworkAPI {
    static let base = "/api/service-network"
    static func get<T: Decodable>(_ path: String) async throws -> T {
        try await NetworkManager.shared.get(base + path)
    }
    static func post<T: Decodable>(_ path: String, _ body: any Encodable) async throws -> T {
        try await NetworkManager.shared.post(base + path, body: body)
    }
    static func query(_ path: String, _ values: [String: String]) -> String {
        var components = URLComponents()
        components.queryItems = values.sorted { $0.key < $1.key }.map { URLQueryItem(name: $0.key, value: $0.value) }
        return path + "?" + (components.percentEncodedQuery ?? "")
    }
    static func timestamp(_ date: Date, dayOnly: Bool = false) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Europe/Istanbul")
        formatter.dateFormat = dayOnly ? "yyyy-MM-dd" : "yyyy-MM-dd'T'HH:mm:ss"
        return formatter.string(from: date)
    }
    static func date(_ value: String?) -> String {
        guard let value else { return "—" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Europe/Istanbul")
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        guard let date = formatter.date(from: String(value.prefix(19))) else { return value }
        formatter.dateFormat = "dd.MM.yyyy HH:mm"
        return formatter.string(from: date)
    }
    static func label(_ status: String?) -> String {
        switch status {
        case "SENT": return "Kabul bekliyor"
        case "ACCEPTED": return "Kabul edildi"
        case "REJECTED": return "Reddedildi"
        case "CANCELLED": return "İptal edildi"
        case "INVITED": return "Davet bekliyor"
        case "ACTIVE": return "Aktif"
        case "DECLINED": return "Davet reddedildi"
        case "CLOSED": return "Bağlantı kapalı"
        case "OPEN", "PENDING": return "Atama bekliyor"
        case "ASSIGNED": return "Atandı"
        case "IN_PROGRESS": return "İşlemde"
        case "COMPLETED": return "Tamamlandı"
        case "NOTE": return "Paylaşılan not"
        case "PROGRESS": return "Operasyon güncellendi"
        default: return status ?? "—"
        }
    }
}

struct ServiceNetworkAccept: Encodable { let customerId: Int?; let technicianId: Int? }
struct ServiceNetworkDispatch: Encodable {
    let membershipId: Int
    let requestKey: String
    let title: String
    let customerName: String
    let customerPhone: String
    let customerAddress: String
    let instruction: String
    let scheduledDate: String
    let scheduledEndDate: String?
}
