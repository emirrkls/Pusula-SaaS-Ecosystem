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

    static func getExpenses(date: String? = nil) async throws -> [ExpenseDTO] {
        let query = date.map { "?date=\($0)" } ?? ""
        return try await NetworkManager.shared.get("/api/finance/expenses\(query)")
    }

    static func updateExpense(_ expense: ExpenseDTO) async throws -> ExpenseDTO {
        guard let id = expense.id else { throw NetworkError.invalidResponse }
        return try await NetworkManager.shared.put("/api/finance/expenses/\(id)", body: expense)
    }

    static func deleteExpense(id: Int) async throws {
        try await NetworkManager.shared.delete("/api/finance/expenses/\(id)")
    }

    static func createFixedExpense(_ expense: FixedExpenseDefinitionDTO) async throws -> FixedExpenseDefinitionDTO {
        try await NetworkManager.shared.post("/api/finance/fixed-expenses", body: expense)
    }

    static func updateFixedExpense(_ expense: FixedExpenseDefinitionDTO) async throws -> FixedExpenseDefinitionDTO {
        guard let id = expense.id else { throw NetworkError.invalidResponse }
        return try await NetworkManager.shared.put("/api/finance/fixed-expenses/\(id)", body: expense)
    }

    static func deleteFixedExpense(id: Int) async throws {
        try await NetworkManager.shared.delete("/api/finance/fixed-expenses/\(id)")
    }

    static func payFixedExpense(id: Int, date: String, amount: Double) async throws -> ExpenseDTO {
        try await NetworkManager.shared.post("/api/finance/fixed-expenses/pay/\(id)?date=\(date)&amount=\(amount)", body: EmptyBody())
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
    
    static func payDebt(accountId: Int, paymentAmount: Double, discount: Double, collectionDate: String, paymentMethod: String, notes: String?) async throws -> CurrentAccountDTO {
        let body = PayDebtRequest(paymentAmount: paymentAmount, discount: discount, collectionDate: collectionDate, paymentMethod: paymentMethod, notes: notes)
        return try await NetworkManager.shared.post("/api/current-accounts/\(accountId)/pay", body: body)
    }

    static func downloadCurrentAccountsPDF() async throws -> Data {
        try await NetworkManager.shared.downloadData("/api/reports/open-current-accounts/pdf")
    }

    static func getCompanyDebts() async throws -> [CompanyDebtDTO] {
        try await NetworkManager.shared.get("/api/company-debts")
    }

    static func createCompanyDebt(_ debt: CompanyDebtDTO) async throws -> CompanyDebtDTO {
        try await NetworkManager.shared.post("/api/company-debts", body: debt)
    }

    static func updateCompanyDebt(_ debt: CompanyDebtDTO) async throws -> CompanyDebtDTO {
        guard let id = debt.id else { throw NetworkError.invalidResponse }
        return try await NetworkManager.shared.put("/api/company-debts/\(id)", body: debt)
    }

    static func deleteCompanyDebt(id: Int) async throws {
        try await NetworkManager.shared.delete("/api/company-debts/\(id)")
    }

    static func payCompanyDebt(id: Int, request: DebtPaymentRequest) async throws -> CompanyDebtDTO {
        try await NetworkManager.shared.post("/api/company-debts/\(id)/pay", body: request)
    }

    static func addToCompanyDebt(id: Int, request: DebtAdditionRequest) async throws -> CompanyDebtDTO {
        try await NetworkManager.shared.post("/api/company-debts/\(id)/add", body: request)
    }

    static func getCompanyDebtPayments(id: Int) async throws -> [CompanyDebtPaymentDTO] {
        try await NetworkManager.shared.get("/api/company-debts/\(id)/payments")
    }

    static func getCompanyDebtAdditions(id: Int) async throws -> [CompanyDebtAdditionDTO] {
        try await NetworkManager.shared.get("/api/company-debts/\(id)/additions")
    }

    static func deleteCompanyDebtPayment(debtId: Int, paymentId: Int) async throws {
        try await NetworkManager.shared.delete("/api/company-debts/\(debtId)/payments/\(paymentId)")
    }

    static func downloadCompanyDebtsPDF() async throws -> Data {
        try await NetworkManager.shared.downloadData("/api/reports/open-company-debts/pdf")
    }

    static func getBusinessAssets() async throws -> [BusinessAssetDTO] {
        try await NetworkManager.shared.get("/api/business-assets")
    }

    static func createBusinessAsset(_ asset: BusinessAssetDTO) async throws -> BusinessAssetDTO {
        try await NetworkManager.shared.post("/api/business-assets", body: asset)
    }

    static func updateBusinessAsset(_ asset: BusinessAssetDTO) async throws -> BusinessAssetDTO {
        guard let id = asset.id else { throw NetworkError.invalidResponse }
        return try await NetworkManager.shared.put("/api/business-assets/\(id)", body: asset)
    }

    static func deleteBusinessAsset(id: Int) async throws {
        try await NetworkManager.shared.delete("/api/business-assets/\(id)")
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
        let body = ResetPasswordRequest(password: newPassword)
        let _: EmptyResponse = try await NetworkManager.shared.post("/api/users/\(userId)/reset-password", body: body)
    }

    static func uploadUserSignature(userId: Int, imageData: Data) async throws {
        _ = try await NetworkManager.shared.uploadMultipartString(
            path: "/api/users/\(userId)/upload-signature",
            fileData: imageData,
            fileName: "signature.jpg",
            mimeType: "image/jpeg"
        )
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
