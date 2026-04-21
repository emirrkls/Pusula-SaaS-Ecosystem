import Foundation

/// Service layer for technician field operations.
enum TicketService {
    
    /// Fetch tickets assigned to the current technician
    static func getMyAssignedTickets() async throws -> [FieldTicketDTO] {
        try await NetworkManager.shared.get("/api/tickets/my-assigned")
    }
    
    /// Fetch all tickets (admin view)
    static func getAllTickets() async throws -> [FieldTicketDTO] {
        try await NetworkManager.shared.get("/api/tickets")
    }
    
    /// Get used parts for a ticket
    static func getUsedParts(ticketId: Int) async throws -> [UsedPartDTO] {
        try await NetworkManager.shared.get("/api/tickets/\(ticketId)/parts")
    }
    
    /// Add a used part to a ticket (from barcode scan)
    static func addUsedPart(ticketId: Int, part: UsedPartDTO) async throws -> UsedPartDTO {
        try await NetworkManager.shared.post("/api/tickets/\(ticketId)/parts", body: part)
    }
    
    /// Complete a service with payment (waterfall model)
    static func completeService(ticketId: Int, amount: Double, paymentMethod: String) async throws -> FieldTicketDTO {
        let body = CollectionRequest(collectedAmount: amount, paymentMethod: paymentMethod)
        return try await NetworkManager.shared.request(
            .PATCH, path: "/api/tickets/\(ticketId)/complete", body: body
        )
    }
    
    /// Upload signature image
    static func uploadSignature(ticketId: Int, signatureBase64: String) async throws -> [String: String] {
        let body = SignatureRequest(signature: signatureBase64)
        return try await NetworkManager.shared.post("/api/tickets/\(ticketId)/signature", body: body)
    }
    
    /// Lookup inventory by barcode
    static func lookupBarcode(_ code: String) async throws -> InventoryItemDTO {
        try await NetworkManager.shared.get("/api/inventory/barcode/\(code)")
    }
    
    /// Get technician's inventory (sell prices only)
    static func getInventory() async throws -> [InventoryItemDTO] {
        try await NetworkManager.shared.get("/api/inventory")
    }
}
