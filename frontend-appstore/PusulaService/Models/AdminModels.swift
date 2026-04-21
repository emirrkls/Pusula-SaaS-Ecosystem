import Foundation

// MARK: - Dashboard KPIs

struct DashboardKPIs: Codable {
    let monthlyRevenue: Double?
    let outstandingDebt: Double?
    let netProfit: Double?
    let profitMargin: Double?
    let activeTickets: Int?
    let completedThisMonth: Int?
    let inventoryValue: Double?
}

// MARK: - Technician Stats

struct TechnicianStat: Codable, Identifiable {
    let userId: Int
    let fullName: String?
    let completedToday: Int?
    let completedThisMonth: Int?
    let collectedToday: Double?
    let collectedThisMonth: Double?
    let activeTickets: Int?
    let lastLocation: String? // "lat,lon"
    
    var id: Int { userId }
}

// MARK: - Profit Analysis

struct ProfitAnalysis: Codable {
    let totalCostOfGoodsSold: Double?
    let totalRevenueFromParts: Double?
    let grossProfit: Double?
    let grossMarginPercent: Double?
    let topProfitableParts: [PartProfit]?
}

struct PartProfit: Codable, Identifiable {
    let partName: String
    let buyPrice: Double?
    let sellPrice: Double?
    let quantitySold: Int?
    let totalProfit: Double?
    let marginPercent: Double?
    
    var id: String { partName }
}

// MARK: - Quota Status

struct QuotaStatus: Codable {
    let planName: String?
    let quotas: [QuotaItem]?
}

struct QuotaItem: Codable, Identifiable {
    let featureKey: String
    let featureLabel: String?
    let currentUsage: Int?
    let limit: Int?
    let usagePercent: Double?
    
    var id: String { featureKey }
}

// MARK: - Field Radar Pin

struct FieldPin: Codable, Identifiable {
    let technicianId: Int
    let technicianName: String?
    let coordinates: String? // "lat,lon"
    let customerName: String?
    let ticketStatus: String?
    let ticketId: Int?
    
    var id: Int { technicianId }
    
    var latitude: Double? {
        guard let coords = coordinates?.split(separator: ","), coords.count == 2 else { return nil }
        return Double(coords[0])
    }
    
    var longitude: Double? {
        guard let coords = coordinates?.split(separator: ","), coords.count == 2 else { return nil }
        return Double(coords[1])
    }
}

// MARK: - Admin Service

enum AdminService {
    static func getDashboardKPIs() async throws -> DashboardKPIs {
        try await NetworkManager.shared.get("/api/admin/dashboard")
    }
    
    static func getTechnicianStats() async throws -> [TechnicianStat] {
        try await NetworkManager.shared.get("/api/admin/technician-stats")
    }
    
    static func getProfitAnalysis() async throws -> ProfitAnalysis {
        try await NetworkManager.shared.get("/api/admin/profit-analysis")
    }
    
    static func getQuotaStatus() async throws -> QuotaStatus {
        try await NetworkManager.shared.get("/api/admin/quota-status")
    }
    
    static func getFieldRadar() async throws -> [FieldPin] {
        try await NetworkManager.shared.get("/api/admin/field-radar")
    }
}
