import Foundation

struct UserDTO: Codable, Identifiable {
    var id: Int?
    let username: String
    let fullName: String?
    let role: String
    var password: String?
}

struct VehicleDTO: Codable, Identifiable {
    var id: Int?
    let companyId: Int?
    let licensePlate: String
    let driverName: String?
    var isActive: Bool?
}

struct CompanyDTO: Codable, Identifiable {
    var id: Int?
    let name: String
    let phone: String?
    let address: String?
    let email: String?
    let logoUrl: String?
}

struct ResetPasswordRequest: Codable {
    let newPassword: String
}
