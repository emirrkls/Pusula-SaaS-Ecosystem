import Foundation

struct ProposalDTO: Codable, Identifiable {
    var id: Int?
    let companyId: Int?
    let customerId: Int?
    let customerName: String?
    let preparedById: Int?
    let preparedByName: String?
    let status: String?
    let validUntil: String?
    let note: String?
    let title: String?
    let taxRate: Double?
    let discount: Double?
    let subtotal: Double?
    let taxAmount: Double?
    let totalPrice: Double?
    var items: [ProposalItemDTO]?
}

struct ProposalItemDTO: Codable, Identifiable {
    var id: Int?
    let description: String
    var quantity: Int
    let unitCost: Double?
    var unitPrice: Double
    var totalPrice: Double?
}
