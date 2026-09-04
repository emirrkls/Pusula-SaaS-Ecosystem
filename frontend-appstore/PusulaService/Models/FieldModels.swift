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
    let scheduledEndDate: String?
    let workProgressReason: String?
    let workProgressNote: String?
    let lastRescheduledAt: String?
    let description: String?
    let notes: String?
    let technicianPrivateNote: String?
    let collectedAmount: Double?
    let paymentMethod: String?
    let isWarrantyCall: Bool?
    let parentTicketId: Int?
    let createdAt: String?
    let updatedAt: String?
    let completedAt: String?
    
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
    var quantityUsed: Double
    let sellingPriceSnapshot: Double
    var unitOfMeasure: String? = nil
    var sourceVehicleId: Int?
    var clientRequestId: String?
    
    var totalPrice: Double {
        quantityUsed * sellingPriceSnapshot
    }
}

// MARK: - Waterfall Collection Request

struct CollectionRequest: Codable {
    let collectedAmount: Double
    let paymentMethod: String
    let laborFee: Double?
    let technicianNote: String?
}

struct TechnicianNoteDTO: Codable, Identifiable {
    let id: Int
    let serviceTicketId: Int
    let authorUserId: Int?
    let authorName: String
    let noteType: String
    let content: String
    let createdAt: String
}

struct AddTechnicianNoteRequest: Codable {
    let content: String
}

// MARK: - Signature Upload

struct SignatureRequest: Codable {
    let signature: String // Base64 PNG
}

struct CreateTicketRequest: Codable {
    let customerId: Int
    let description: String
    let notes: String?
    let technicianPrivateNote: String?
    let status: String
    let assignedTechnicianId: Int?
    let scheduledDate: String?
    let scheduledEndDate: String?
    
    init(customerId: Int, description: String, notes: String? = nil, technicianPrivateNote: String? = nil, assignedTechnicianId: Int? = nil,
         scheduledDate: String? = nil, scheduledEndDate: String? = nil) {
        self.customerId = customerId
        self.description = description
        self.notes = notes
        self.technicianPrivateNote = technicianPrivateNote
        self.status = "PENDING"
        self.assignedTechnicianId = assignedTechnicianId
        self.scheduledDate = scheduledDate
        self.scheduledEndDate = scheduledEndDate
    }
}

struct UpdateTicketRequest: Codable {
    let status: String?
    let notes: String?

    init(status: TicketStatus? = nil, notes: String? = nil) {
        self.status = status?.rawValue
        self.notes = notes
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
    let note: String?
    let uploadedByName: String?
    let uploadedAt: String?
    let serviceDate: String?
    let customerName: String?
    let ticketDescription: String?

    static let supportedTypes: [(String, String)] = [
        ("BEFORE", "İşlem Öncesi"), ("AFTER", "İşlem Sonrası"),
        ("INDOOR_UNIT_SERIAL", "İç Ünite Seri No"),
        ("OUTDOOR_UNIT_SERIAL", "Dış Ünite Seri No"),
        ("DEVICE_LABEL", "Cihaz Etiketi"), ("FAULT_DETAIL", "Arıza Detayı"),
        ("INSTALLATION", "Montaj / Tesisat"), ("OTHER", "Diğer")
    ]
    
    var typeLabel: String {
        Self.supportedTypes.first(where: { $0.0 == type })?.1 ?? "Diğer"
    }
    
    var fullURL: URL? {
        guard let baseURL = URL(string: "https://api.pusulaiklimlendirme.com/") else { return nil }
        return URL(string: url, relativeTo: baseURL)?.absoluteURL
    }
}

struct RescheduleTicketRequest: Codable {
    let scheduledDate: String
    let scheduledEndDate: String?
    let reason: String
    let note: String
}

struct AuditLogDTO: Codable, Identifiable {
    let id: Int?
    let userName: String?
    let actionType: String?
    let entityType: String?
    let description: String?
    let oldValue: String?
    let newValue: String?
    let timestamp: String?

    var stableId: String {
        id.map(String.init) ?? timestamp ?? UUID().uuidString
    }
}
