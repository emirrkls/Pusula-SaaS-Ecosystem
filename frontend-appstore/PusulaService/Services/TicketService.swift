import Foundation

enum TicketService {
    
    static func getMyAssignedTickets() async throws -> [FieldTicketDTO] {
        try await NetworkManager.shared.get("/api/tickets/my-assigned")
    }
    
    static func getAllTickets() async throws -> [FieldTicketDTO] {
        try await NetworkManager.shared.get("/api/tickets")
    }
    
    static func createTicket(_ request: CreateTicketRequest) async throws -> FieldTicketDTO {
        try await NetworkManager.shared.post("/api/tickets", body: request)
    }
    
    static func getUsedParts(ticketId: Int) async throws -> [UsedPartDTO] {
        try await NetworkManager.shared.get("/api/tickets/\(ticketId)/parts")
    }
    
    static func addUsedPart(ticketId: Int, part: UsedPartDTO) async throws -> UsedPartDTO {
        try await NetworkManager.shared.post("/api/tickets/\(ticketId)/parts", body: part)
    }
    
    static func completeService(ticketId: Int, amount: Double, paymentMethod: String) async throws -> FieldTicketDTO {
        let body = CollectionRequest(collectedAmount: amount, paymentMethod: paymentMethod)
        return try await NetworkManager.shared.request(
            .PATCH, path: "/api/tickets/\(ticketId)/complete", body: body
        )
    }
    
    static func assignTechnician(ticketId: Int, technicianId: Int) async throws -> FieldTicketDTO {
        try await NetworkManager.shared.request(
            .PATCH,
            path: "/api/tickets/\(ticketId)/assign?technicianId=\(technicianId)"
        )
    }
    
    static func assignTechnicianBulk(ticketIds: [Int], technicianId: Int) async throws -> [FieldTicketDTO] {
        var results: [FieldTicketDTO] = []
        for ticketId in ticketIds {
            let updated = try await assignTechnician(ticketId: ticketId, technicianId: technicianId)
            results.append(updated)
        }
        return results
    }
    
    static func uploadSignature(ticketId: Int, signatureBase64: String) async throws {
        let body = SignatureRequest(signature: signatureBase64)
        let _: EmptyResponse = try await NetworkManager.shared.post("/api/tickets/\(ticketId)/signature", body: body)
    }
    
    static func lookupBarcode(_ code: String) async throws -> InventoryItemDTO {
        try await NetworkManager.shared.get("/api/inventory/barcode/\(code.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? code)")
    }
    
    static func getInventory() async throws -> [InventoryItemDTO] {
        try await NetworkManager.shared.get("/api/inventory")
    }
    
    static func createInventory(_ item: InventoryItemDTO) async throws -> InventoryItemDTO {
        try await NetworkManager.shared.post("/api/inventory", body: item)
    }
    
    static func updateInventory(id: Int, item: InventoryItemDTO) async throws -> InventoryItemDTO {
        try await NetworkManager.shared.put("/api/inventory/\(id)", body: item)
    }
    
    static func deleteInventory(id: Int) async throws {
        try await NetworkManager.shared.delete("/api/inventory/\(id)")
    }
    
    static func getServicePhotos(ticketId: Int) async throws -> [ServicePhotoDTO] {
        try await NetworkManager.shared.get("/api/tickets/\(ticketId)/photos")
    }
    
    static func uploadServicePhoto(ticketId: Int, type: String, imageData: Data) async throws -> ServicePhotoDTO {
        try await NetworkManager.shared.uploadMultipart(
            path: "/api/tickets/\(ticketId)/photos",
            fileData: imageData,
            fileName: "photo.jpg",
            mimeType: "image/jpeg",
            textFields: ["type": type]
        )
    }
    
    static func deleteServicePhoto(ticketId: Int, photoId: Int) async throws {
        try await NetworkManager.shared.delete("/api/tickets/\(ticketId)/photos/\(photoId)")
    }
    
    static func getCompanyServicePhotos(
        type: String? = nil,
        ticketId: Int? = nil,
        startDate: String? = nil,
        endDate: String? = nil,
        limit: Int? = 200
    ) async throws -> [ServicePhotoDTO] {
        var query: [String] = []
        if let type { query.append("type=\(type)") }
        if let ticketId { query.append("ticketId=\(ticketId)") }
        if let startDate { query.append("startDate=\(startDate)") }
        if let endDate { query.append("endDate=\(endDate)") }
        if let limit { query.append("limit=\(limit)") }
        let suffix = query.isEmpty ? "" : "?" + query.joined(separator: "&")
        return try await NetworkManager.shared.get("/api/tickets/photos\(suffix)")
    }
    
    static func downloadServiceReportPDF(ticketId: Int) async throws -> Data {
        try await NetworkManager.shared.downloadData("/api/reports/pdf/service/\(ticketId)")
    }
    
    static func getTechnicians() async throws -> [TechnicianDTO] {
        try await NetworkManager.shared.get("/api/users/technicians")
    }
}
