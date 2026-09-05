import Foundation

struct AdminNotificationDTO: Codable, Identifiable {
    let id: Int
    let title: String
    let message: String
    let severity: String
    let category: String
    let referenceType: String?
    let referenceId: Int?
    let read: Bool
    let createdAt: String
}

struct NotificationUnreadCountDTO: Codable {
    let count: Int
}
