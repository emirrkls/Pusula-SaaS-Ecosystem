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
    let frequency: String?
    let linkedExpenseId: Int?
    let linkedExpenseName: String?
    let linkedPaymentsThisMonth: Double?

    enum CodingKeys: String, CodingKey {
        case id, name, defaultAmount, category, dayOfMonth, description, paidAmountThisMonth, frequency
        case linkedExpenseId, linkedExpenseName, linkedPaymentsThisMonth
        case paidThisMonth = "isPaidThisMonth"
    }
}

struct ExpenseDTO: Codable {
    var id: Int?
    var amount: Double
    var description: String
    var date: String
    var category: String
    var fixedExpenseId: Int?
    var paymentMethod: String?
    var financialTreatment: String?
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
    let currentAccountTransferred: Double?
    let cashCardCollections: Double?
    let currentAccountCollections: Double?
    let otherCashIncome: Double?
    let totalCollected: Double?
    let serviceDirectCost: Double?
    let otherOperatingExpenses: Double?
    let totalProfitExpenses: Double?
    let serviceCashExpenses: Double?
    let otherCashExpenses: Double?
    let totalCashExpenses: Double?
    let totalExpense: Double?
    let netProfit: Double?
    let netCash: Double?
    let closingCumulativeProfit: Double?
    let openingCashBalance: Double?
    let closingCashBalance: Double?
    let carryOver: Double?
}

struct CurrentAccountDTO: Codable, Identifiable {
    var id: Int?
    let customerId: Int?
    let customerName: String?
    let balance: Double?
    let lastUpdated: String?
}

struct CurrentAccountHistoryDTO: Codable {
    let accountId: Int
    let customerId: Int
    let customerName: String
    let currentBalance: Double
    let transactions: [CurrentAccountTransactionDTO]
}

struct CurrentAccountTransactionDTO: Codable, Identifiable {
    let id: Int
    let type: String
    let amount: Double
    let balanceAfter: Double
    let effectiveDate: String
    let description: String?
    let paymentMethod: String?
    let sourceType: String?
    let sourceId: Int?
    let createdAt: String?
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
    let collectionDate: String
    let paymentMethod: String
    let notes: String?
}

struct CompanyDebtDTO: Codable, Identifiable {
    var id: Int? = nil
    var companyId: Int? = nil
    var creditorName: String? = nil
    var description: String? = nil
    var originalAmount: Double? = nil
    var remainingAmount: Double? = nil
    var expenseCategory: String? = nil
    var debtDate: String? = nil
    var dueDate: String? = nil
    var creditorPhone: String? = nil
    var status: String? = nil
    var notes: String? = nil
    var createdAt: String? = nil
    var updatedAt: String? = nil
}

struct CompanyDebtPaymentDTO: Codable, Identifiable {
    var id: Int?
    let debtId: Int?
    let amount: Double?
    let paymentDate: String?
    let expenseCategory: String?
    let creditorName: String?
    let notes: String?
    let createdAt: String?
}

struct CompanyDebtAdditionDTO: Codable, Identifiable {
    var id: Int?
    let debtId: Int?
    let amount: Double?
    let additionDate: String?
    let notes: String?
    let createdAt: String?
}

struct DebtPaymentRequest: Codable {
    let amount: Double
    let paymentDate: String
    let notes: String?
}

struct DebtAdditionRequest: Codable {
    let amount: Double
    let additionDate: String
    let notes: String?
}

struct BusinessAssetDTO: Codable, Identifiable {
    var id: Int?
    var assetName: String
    var category: String?
    var quantity: Int?
    var condition: String?
    var serialNumber: String?
    var location: String?
    var assignedTo: String?
    var purchaseDate: String?
    var purchasePrice: Double?
    var notes: String?

    var totalValue: Double { (purchasePrice ?? 0) * Double(quantity ?? 1) }
}

enum ExpenseCategory: String, CaseIterable {
    case rent = "RENT"
    case salary = "SALARY"
    case bills = "BILLS"
    case fuel = "FUEL"
    case food = "FOOD"
    case tax = "TAX"
    case material = "MATERIAL"
    case deviceSale = "DEVICE_SALE"
    case other = "OTHER"
    
    var label: String {
        switch self {
        case .rent: return "Kira"
        case .salary: return "Maaş"
        case .bills: return "Faturalar"
        case .fuel: return "Yakıt"
        case .food: return "Yemek"
        case .tax: return "Vergi"
        case .material: return "Malzeme"
        case .deviceSale: return "Cihaz Satışı"
        case .other: return "Diğer"
        }
    }
}

enum FinancialTreatment: String, CaseIterable {
    case operatingExpense = "OPERATING_EXPENSE"
    case serviceDirectExpense = "SERVICE_DIRECT_EXPENSE"
    case cashOnly = "CASH_ONLY"

    var label: String {
        switch self {
        case .operatingExpense: return "Faaliyet gideri"
        case .serviceDirectExpense: return "Servis doğrudan gideri"
        case .cashOnly: return "Yalnızca nakit hareketi"
        }
    }
}

enum FinancePaymentMethod: String, CaseIterable {
    case cash = "CASH"
    case creditCard = "CREDIT_CARD"

    var label: String { self == .cash ? "Nakit" : "Kart" }
}
