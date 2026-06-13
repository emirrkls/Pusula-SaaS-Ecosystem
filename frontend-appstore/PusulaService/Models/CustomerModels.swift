import Foundation

struct CustomerDTO: Codable, Identifiable {
    var id: Int?
    let name: String
    let phone: String?
    let address: String?
    let coordinates: String?
}
