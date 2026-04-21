import Foundation

// MARK: - Auth DTOs

struct AuthRequest: Codable {
    let username: String
    let password: String
    var orgCode: String?
}

struct RegisterRequest: Codable {
    var email: String?
    var username: String?
    let password: String
    let fullName: String
    var authType: String = "INDIVIDUAL"
}

struct AuthResponse: Codable {
    let token: String
    let role: String
    let fullName: String?
    let companyId: Int?
    let companyName: String?
    let planType: String?
    let features: [String: Bool]?
    let quota: QuotaDTO?
    let isReadOnly: Bool?
    let trialDaysRemaining: Int?
}

struct QuotaDTO: Codable {
    let maxTechnicians: Int
    let maxCustomers: Int
    let maxMonthlyTickets: Int
    let maxMonthlyProposals: Int
    let maxInventoryItems: Int
    let storageLimitMb: Int
    let currentTechnicians: Int
    let currentCustomers: Int
    let currentMonthlyTickets: Int
    let currentMonthlyProposals: Int
    let currentInventoryItems: Int
    let currentStorageMb: Int
    
    var isUnlimited: Bool { maxTechnicians == -1 }
    
    func usagePercentage(for type: UsageType) -> Double {
        let (current, max) = values(for: type)
        guard max > 0 else { return 0 }
        return Double(current) / Double(max)
    }
    
    func values(for type: UsageType) -> (current: Int, max: Int) {
        switch type {
        case .technicians: return (currentTechnicians, maxTechnicians)
        case .customers: return (currentCustomers, maxCustomers)
        case .tickets: return (currentMonthlyTickets, maxMonthlyTickets)
        case .proposals: return (currentMonthlyProposals, maxMonthlyProposals)
        case .inventory: return (currentInventoryItems, maxInventoryItems)
        case .storage: return (currentStorageMb, storageLimitMb)
        }
    }
    
    enum UsageType: String, CaseIterable {
        case technicians = "Teknisyen"
        case customers = "Müşteri"
        case tickets = "Aylık İş Emri"
        case proposals = "Aylık Teklif"
        case inventory = "Stok Kalemi"
        case storage = "Depolama"
    }
}

// MARK: - Ticket DTOs

struct ServiceTicketDTO: Codable, Identifiable {
    let id: Int
    let description: String?
    let status: String?
    let scheduledDate: String?
    let customerName: String?
    let customerPhone: String?
    let customerAddress: String?
    let assignedTechnicianId: Int?
    let assignedTechnicianName: String?
    let collectedAmount: Double?
    let paymentMethod: String?
    let completedAt: String?
    
    var statusEnum: TicketStatus {
        TicketStatus(rawValue: status ?? "") ?? .pending
    }
}

enum TicketStatus: String, Codable, CaseIterable {
    case pending = "PENDING"
    case inProgress = "IN_PROGRESS"
    case completed = "COMPLETED"
    case cancelled = "CANCELLED"
    
    var displayName: String {
        switch self {
        case .pending: return "Bekliyor"
        case .inProgress: return "Devam Ediyor"
        case .completed: return "Tamamlandı"
        case .cancelled: return "İptal"
        }
    }
    
    var iconName: String {
        switch self {
        case .pending: return "clock"
        case .inProgress: return "wrench.and.screwdriver"
        case .completed: return "checkmark.circle.fill"
        case .cancelled: return "xmark.circle"
        }
    }
}

// MARK: - Inventory DTO (Technician — buyPrice excluded for DTO isolation)

struct InventoryItemDTO: Codable, Identifiable {
    let id: Int
    let partName: String
    let quantity: Int
    let sellPrice: Double?
    let brand: String?
    let category: String?
}

// MARK: - Finance DTOs (Admin Only)

struct FinancialSummaryDTO: Codable {
    let totalIncome: Double
    let totalExpense: Double
    let netProfit: Double
    let ticketCount: Int
    let completedCount: Int
    let pendingCount: Int
}

// MARK: - Plan DTOs

struct PlanDTO: Codable, Identifiable {
    let id: Int
    let name: String
    let displayName: String
    let priceMonthly: Double?
    let priceYearly: Double?
    let maxTechnicians: Int?
    let maxCustomers: Int?
    let maxMonthlyTickets: Int?
    let maxMonthlyProposals: Int?
    let maxInventoryItems: Int?
    let storageLimitMb: Int?
    
    var isUnlimited: Bool { maxTechnicians == -1 }
}
