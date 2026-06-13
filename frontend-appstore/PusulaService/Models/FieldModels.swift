import Foundation

// MARK: - Enriched Ticket DTO (matching backend ServiceTicketDTO)

struct FieldTicketDTO: Codable, Identifiable {
    let id: Int
    let customerId: Int?
    let customerName: String?
    let customerPhone: String?
    let customerAddress: String?
    let customerCoordinates: String?
    let customerBalance: Double?
    let assignedTechnicianId: Int?
    let assignedTechnicianName: String?
    let status: String?
    let scheduledDate: String?
    let description: String?
    let notes: String?
    let collectedAmount: Double?
    let paymentMethod: String?
    let isWarrantyCall: Bool?
    let parentTicketId: Int?
    let createdAt: String?
    
    var statusEnum: TicketStatus {
        TicketStatus(rawValue: status ?? "") ?? .pending
    }
    
    var hasOutstandingBalance: Bool {
        guard let balance = customerBalance else { return false }
        return balance > 0
    }
}

// MARK: - Used Part (for barcode scanner cart)

struct UsedPartDTO: Codable, Identifiable {
    var id: Int?
    let ticketId: Int?
    let inventoryId: Int
    let partName: String
    var quantityUsed: Int
    let sellingPriceSnapshot: Double
    var sourceVehicleId: Int?
    
    var totalPrice: Double {
        Double(quantityUsed) * sellingPriceSnapshot
    }
}

// MARK: - Waterfall Collection Request

struct CollectionRequest: Codable {
    let collectedAmount: Double
    let paymentMethod: String  // CASH, CREDIT_CARD, CURRENT_ACCOUNT
}

// MARK: - Signature Upload

struct SignatureRequest: Codable {
    let signature: String // Base64 PNG
}

struct CreateTicketRequest: Codable {
    let customerId: Int
    let description: String
    let notes: String?
    let status: String
    let assignedTechnicianId: Int?
    
    init(customerId: Int, description: String, notes: String? = nil, assignedTechnicianId: Int? = nil) {
        self.customerId = customerId
        self.description = description
        self.notes = notes
        self.status = "PENDING"
        self.assignedTechnicianId = assignedTechnicianId
    }
}

struct TechnicianDTO: Codable, Identifiable {
    let id: Int
    let fullName: String?
    let role: String?
}

struct ServicePhotoDTO: Codable, Identifiable {
    let id: Int
    let ticketId: Int
    let url: String
    let type: String
    let uploadedAt: String?
    
    var typeLabel: String {
        type == "BEFORE" ? "Öncesi" : "Sonrası"
    }
    
    var fullURL: URL? {
        if url.hasPrefix("http") { return URL(string: url) }
        return URL(string: "https://api.pusulaiklimlendirme.com" + url)
    }
}
