import Foundation

enum FinanceService {
    
    static func getDailySummary(date: String? = nil) async throws -> DailySummaryDTO {
        let dateString = date ?? {
            let formatter = DateFormatter()
            formatter.dateFormat = "yyyy-MM-dd"
            formatter.timeZone = TimeZone(identifier: "Europe/Istanbul")
            return formatter.string(from: Date())
        }()
        return try await NetworkManager.shared.get("/api/finance/daily-summary?date=\(dateString)")
    }
    
    static func getFixedExpenses() async throws -> [FixedExpenseDefinitionDTO] {
        try await NetworkManager.shared.get("/api/finance/fixed-expenses")
    }
    
    static func addExpense(_ expense: ExpenseDTO) async throws -> ExpenseDTO {
        try await NetworkManager.shared.post("/api/finance/expenses", body: expense)
    }
    
    static func getDailyTotals() async throws -> [DailyTotalDTO] {
        try await NetworkManager.shared.get("/api/finance/daily-totals")
    }
    
    static func getCategoryReport(startDate: String, endDate: String) async throws -> CategoryReportDTO {
        try await NetworkManager.shared.get("/api/finance/category-report?startDate=\(startDate)&endDate=\(endDate)")
    }
    
    static func getCurrentAccounts() async throws -> [CurrentAccountDTO] {
        try await NetworkManager.shared.get("/api/current-accounts")
    }
    
    static func payDebt(accountId: Int, paymentAmount: Double, discount: Double) async throws -> CurrentAccountDTO {
        let body = PayDebtRequest(paymentAmount: paymentAmount, discount: discount)
        return try await NetworkManager.shared.post("/api/current-accounts/\(accountId)/pay", body: body)
    }
    
    static func getMonthlyArchives() async throws -> [MonthlySummaryDTO] {
        try await NetworkManager.shared.get("/api/reports/finance/archives")
    }
    
    static func downloadMonthlyPDF(month: String) async throws -> Data {
        try await NetworkManager.shared.downloadData("/api/reports/finance/pdf?month=\(month)")
    }
    
    static func closeDay(date: String, companyId: Int?) async throws -> DailyClosingDTO {
        let body = CloseDayRequest(companyId: companyId, date: date, userId: nil)
        return try await NetworkManager.shared.post("/api/finance/close-day", body: body)
    }
}

enum CustomerService {
    
    static func getCustomers() async throws -> [CustomerDTO] {
        try await NetworkManager.shared.get("/api/customers")
    }
    
    static func createCustomer(_ customer: CustomerDTO) async throws -> CustomerDTO {
        try await NetworkManager.shared.post("/api/customers", body: customer)
    }
    
    static func updateCustomer(id: Int, customer: CustomerDTO) async throws -> CustomerDTO {
        try await NetworkManager.shared.put("/api/customers/\(id)", body: customer)
    }
}

enum ProposalService {
    
    static func getProposals() async throws -> [ProposalDTO] {
        try await NetworkManager.shared.get("/api/proposals")
    }
    
    static func createProposal(_ proposal: ProposalDTO) async throws -> ProposalDTO {
        try await NetworkManager.shared.post("/api/proposals", body: proposal)
    }
    
    static func updateProposal(id: Int, proposal: ProposalDTO) async throws -> ProposalDTO {
        try await NetworkManager.shared.put("/api/proposals/\(id)", body: proposal)
    }
    
    static func deleteProposal(id: Int) async throws {
        try await NetworkManager.shared.delete("/api/proposals/\(id)")
    }
    
    static func convertToJob(id: Int) async throws -> ProposalDTO {
        try await NetworkManager.shared.post("/api/proposals/\(id)/convert", body: EmptyBody())
    }
    
    static func downloadPDF(id: Int) async throws -> Data {
        try await NetworkManager.shared.downloadData("/api/proposals/\(id)/pdf")
    }
}

struct EmptyBody: Encodable {}

enum SettingsService {
    
    static func getUsers(role: String? = nil) async throws -> [UserDTO] {
        if let role {
            return try await NetworkManager.shared.get("/api/users?role=\(role)")
        }
        return try await NetworkManager.shared.get("/api/users")
    }
    
    static func createUser(_ user: UserDTO) async throws -> UserDTO {
        try await NetworkManager.shared.post("/api/users", body: user)
    }
    
    static func updateUser(id: Int, user: UserDTO) async throws -> UserDTO {
        try await NetworkManager.shared.put("/api/users/\(id)", body: user)
    }
    
    static func deleteUser(id: Int, reassignTo: Int? = nil) async throws {
        let suffix = reassignTo.map { "?reassignTo=\($0)" } ?? ""
        try await NetworkManager.shared.delete("/api/users/\(id)\(suffix)")
    }
    
    static func resetPassword(userId: Int, newPassword: String) async throws {
        let body = ResetPasswordRequest(newPassword: newPassword)
        let _: EmptyResponse = try await NetworkManager.shared.post("/api/users/\(userId)/reset-password", body: body)
    }
    
    static func getVehicles() async throws -> [VehicleDTO] {
        try await NetworkManager.shared.get("/api/vehicles")
    }
    
    static func createVehicle(_ vehicle: VehicleDTO) async throws -> VehicleDTO {
        try await NetworkManager.shared.post("/api/vehicles", body: vehicle)
    }
    
    static func updateVehicle(id: Int, vehicle: VehicleDTO) async throws -> VehicleDTO {
        try await NetworkManager.shared.put("/api/vehicles/\(id)", body: vehicle)
    }
    
    static func deleteVehicle(id: Int) async throws {
        try await NetworkManager.shared.delete("/api/vehicles/\(id)")
    }
    
    static func getCompany() async throws -> CompanyDTO {
        try await NetworkManager.shared.get("/api/companies/me")
    }
    
    static func updateCompany(_ company: CompanyDTO) async throws -> CompanyDTO {
        try await NetworkManager.shared.put("/api/companies/me", body: company)
    }
    
    static func uploadCompanyLogo(imageData: Data) async throws -> CompanyDTO {
        try await NetworkManager.shared.uploadMultipart(
            path: "/api/companies/me/logo",
            fileData: imageData,
            fileName: "logo.jpg",
            mimeType: "image/jpeg"
        )
    }
}
