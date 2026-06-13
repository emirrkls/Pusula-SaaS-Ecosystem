import Foundation

struct DailySummaryDTO: Codable {
    let date: String?
    let totalIncome: Double?
    let totalExpense: Double?
    let netCash: Double?
    let isClosed: Bool?
    let closed: Bool?
    let incomeDetails: [IncomeItemDTO]?
    let expenseDetails: [ExpenseItemDTO]?
    
    var dayClosed: Bool { isClosed == true || closed == true }
}

struct IncomeItemDTO: Codable, Identifiable {
    var id: Int { ticketId ?? 0 }
    let ticketId: Int?
    let customerName: String?
    let amount: Double?
}

struct ExpenseItemDTO: Codable, Identifiable {
    let id: Int?
    let category: String?
    let description: String?
    let amount: Double?
}

struct FixedExpenseDefinitionDTO: Codable, Identifiable {
    var id: Int?
    let name: String?
    let defaultAmount: Double?
    let category: String?
    let dayOfMonth: Int?
    let description: String?
    let paidThisMonth: Bool?
    let paidAmountThisMonth: Double?
}

struct ExpenseDTO: Codable {
    var id: Int?
    let amount: Double
    let description: String
    let date: String
    let category: String
    var fixedExpenseId: Int?
}

struct DailyTotalDTO: Codable, Identifiable {
    var id: String { date ?? UUID().uuidString }
    let date: String?
    let income: Double?
    let expense: Double?
}

struct CategoryReportDTO: Codable {
    let breakdown: [String: Double]?
}

struct MonthlySummaryDTO: Codable, Identifiable {
    var id: String { period ?? UUID().uuidString }
    let period: String?
    let displayPeriod: String?
    let totalIncome: Double?
    let totalExpense: Double?
    let netProfit: Double?
    let carryOver: Double?
}

struct CurrentAccountDTO: Codable, Identifiable {
    var id: Int?
    let customerId: Int?
    let customerName: String?
    let balance: Double?
    let lastUpdated: String?
}

struct CloseDayRequest: Codable {
    let companyId: Int?
    let date: String
    let userId: Int?
}

struct DailyClosingDTO: Codable {
    let id: Int?
    let date: String?
    let totalIncome: Double?
    let totalExpense: Double?
    let netCash: Double?
}

struct PayDebtRequest: Codable {
    let paymentAmount: Double
    let discount: Double
}

enum ExpenseCategory: String, CaseIterable {
    case rent = "RENT"
    case salary = "SALARY"
    case bills = "BILLS"
    case fuel = "FUEL"
    case food = "FOOD"
    case other = "OTHER"
    
    var label: String {
        switch self {
        case .rent: return "Kira"
        case .salary: return "Maaş"
        case .bills: return "Faturalar"
        case .fuel: return "Yakıt"
        case .food: return "Yemek"
        case .other: return "Diğer"
        }
    }
}
