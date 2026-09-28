import Foundation

struct CustomerDTO: Codable, Identifiable {
    var id: Int?
    let name: String
    let phone: String?
    let address: String?
    let coordinates: String?
}

enum WhatsAppConsentSource: String, CaseIterable, Identifiable {
    case writtenForm = "WRITTEN_FORM"
    case verbalConfirmation = "VERBAL_CONFIRMATION"
    case digitalForm = "DIGITAL_FORM"
    case whatsAppConversation = "WHATSAPP_CONVERSATION"
    case other = "OTHER"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .writtenForm: return "Yazılı form"
        case .verbalConfirmation: return "Sözlü onay"
        case .digitalForm: return "Dijital form"
        case .whatsAppConversation: return "WhatsApp görüşmesi"
        case .other: return "Diğer"
        }
    }
}

struct CustomerWhatsAppConsentDTO: Decodable {
    let customerId: Int
    let optedIn: Bool
    let optedInAt: String?
    let source: String?
    let optedOutAt: String?

    var sourceTitle: String? {
        guard let source, !source.isEmpty else { return nil }
        if let knownSource = WhatsAppConsentSource(rawValue: source) {
            return knownSource.title
        }
        return source.replacingOccurrences(of: "_", with: " ").localizedCapitalized
    }
}

struct UpdateCustomerWhatsAppConsentRequest: Encodable {
    let optedIn: Bool
    let source: String?
}
