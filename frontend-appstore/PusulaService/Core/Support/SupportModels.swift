import Foundation

struct SupportContact: Decodable { let enabled: Bool; let email: String; let website: String }
struct SupportTicketDTO: Decodable, Identifiable {
    let id: Int; let number: String; let subject: String; let category: String; let status: String
    let priority: String; let platform: String; let appVersion: String; let updatedAt: String; let unread: Bool
}
struct SupportAttachmentDTO: Decodable, Identifiable { let id: Int; let fileName: String; let mediaType: String; let size: Int }
struct SupportMessageDTO: Decodable, Identifiable {
    let id: Int; let body: String; let staff: Bool; let internalNote: Bool; let createdAt: String; let attachments: [SupportAttachmentDTO]
}
struct SupportDetailDTO: Decodable { let ticket: SupportTicketDTO; let messages: [SupportMessageDTO]; let nextBefore: Int? }
struct SupportPageDTO: Decodable { let items: [SupportTicketDTO]; let page: Int; let hasNext: Bool; let unreadCount: Int }
struct SupportUpload: Codable, Equatable, Identifiable {
    var id: String { String(dataBase64.prefix(80)) }
    let mediaType: String; let dataBase64: String
}
struct SupportDraft: Codable, Equatable {
    var clientKey = UUID().uuidString
    var subject = ""
    var category = "QUESTION"
    var body = ""
    var platform = "IOS"
    var appVersion = ""
    var attachments: [SupportUpload] = []
    var frozen = false
    // Stored with the frozen payload, never with a token. No automatic background submission.
    var ticketId: Int?
    var hasContent: Bool { frozen || !subject.isEmpty || !body.isEmpty || !attachments.isEmpty }
}
struct SupportCreateRequest: Encodable {
    let clientKey: String; let subject: String; let category: String; let body: String
    let platform: String; let appVersion: String; let attachments: [SupportUpload]
    init(_ draft: SupportDraft) { clientKey=draft.clientKey;subject=draft.subject;category=draft.category;body=draft.body;platform=draft.platform;appVersion=draft.appVersion;attachments=draft.attachments }
}
struct SupportReplyRequest: Encodable {
    let clientKey: String; let body: String; let internalNote = false; let attachments: [SupportUpload]
    init(_ draft: SupportDraft) { clientKey=draft.clientKey;body=draft.body;attachments=draft.attachments }
}
enum SupportPresentation {
    static func label(_ value: String) -> String { NSLocalizedString("support.value." + value, comment: "Support status/category") }
    static let fallbackEmail = "pusulaiklimlendirme.didim@gmail.com"
}
