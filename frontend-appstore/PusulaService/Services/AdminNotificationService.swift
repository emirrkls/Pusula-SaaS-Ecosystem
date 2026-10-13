import Foundation

enum AdminNotificationService {
    static func list(authToken: String? = nil) async throws -> [AdminNotificationDTO] {
        try await NetworkManager.shared.request(.GET, path: "/api/notifications", authTokenOverride: authToken)
    }

    static func unreadCount() async throws -> Int {
        let response: NotificationUnreadCountDTO = try await NetworkManager.shared.get("/api/notifications/unread-count")
        return response.count
    }

    static func markRead(id: Int) async throws -> AdminNotificationDTO {
        try await NetworkManager.shared.request(.PATCH, path: "/api/notifications/\(id)/read")
    }

    static func markAllRead() async throws {
        let _: EmptyResponse = try await NetworkManager.shared.request(.PATCH, path: "/api/notifications/read-all")
    }
}
