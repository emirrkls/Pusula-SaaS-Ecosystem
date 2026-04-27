package com.pusula.service.data.remote

import com.pusula.service.data.model.AuthRequest
import com.pusula.service.data.model.AuthResponse
import com.pusula.service.data.model.CollectionRequest
import com.pusula.service.data.model.CloseDayRequest
import com.pusula.service.data.model.CategoryReportDTO
import com.pusula.service.data.model.CurrentAccountDTO
import com.pusula.service.data.model.DashboardKPIs
import com.pusula.service.data.model.DailyTotalDTO
import com.pusula.service.data.model.DailySummaryDTO
import com.pusula.service.data.model.DailyClosingDTO
import com.pusula.service.data.model.ExpenseDTO
import com.pusula.service.data.model.CustomerDTO
import com.pusula.service.data.model.CreateTicketRequest
import com.pusula.service.data.model.FieldPin
import com.pusula.service.data.model.FieldTicketDTO
import com.pusula.service.data.model.FixedExpenseDefinitionDTO
import com.pusula.service.data.model.GoogleAuthRequest
import com.pusula.service.data.model.InventoryItemDTO
import com.pusula.service.data.model.MonthlySummaryDTO
import com.pusula.service.data.model.PlanDTO
import com.pusula.service.data.model.ProfitAnalysis
import com.pusula.service.data.model.ProposalDTO
import com.pusula.service.data.model.QuotaStatus
import com.pusula.service.data.model.RegisterRequest
import com.pusula.service.data.model.ServiceTicketDTO
import com.pusula.service.data.model.SignatureRequest
import com.pusula.service.data.model.TechnicianDTO
import com.pusula.service.data.model.TechnicianStat
import com.pusula.service.data.model.UsedPartDTO
import com.pusula.service.data.model.UserDTO
import com.pusula.service.data.model.VehicleDTO
import com.pusula.service.data.model.CompanyDTO
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.PUT
import retrofit2.http.Multipart
import retrofit2.http.Part
import okhttp3.ResponseBody
import okhttp3.MultipartBody

interface ApiService {
    @POST("/api/auth/authenticate")
    suspend fun authenticate(@Body request: AuthRequest): AuthResponse

    @POST("/api/auth/register-individual")
    suspend fun registerIndividual(@Body request: RegisterRequest): AuthResponse

    @POST("/api/auth/google")
    suspend fun authenticateWithGoogle(@Body request: GoogleAuthRequest): AuthResponse

    @DELETE("/api/auth/delete-account")
    suspend fun deleteAccount()

    @GET("/api/subscription/my-context")
    suspend fun myContext(): AuthResponse

    @GET("/api/tickets/my-assigned")
    suspend fun myAssignedTickets(): List<FieldTicketDTO>

    @GET("/api/tickets")
    suspend fun allTickets(): List<ServiceTicketDTO>

    @GET("/api/tickets/{id}")
    suspend fun ticketById(@Path("id") ticketId: Long): ServiceTicketDTO

    @POST("/api/tickets")
    suspend fun createTicket(@Body request: CreateTicketRequest): FieldTicketDTO

    @GET("/api/customers")
    suspend fun customers(): List<CustomerDTO>

    @POST("/api/customers")
    suspend fun createCustomer(@Body request: CustomerDTO): CustomerDTO

    @PUT("/api/customers/{id}")
    suspend fun updateCustomer(@Path("id") id: Long, @Body request: CustomerDTO): CustomerDTO

    @GET("/api/tickets/{id}/parts")
    suspend fun ticketParts(@Path("id") ticketId: Long): List<UsedPartDTO>

    @POST("/api/tickets/{id}/parts")
    suspend fun addTicketPart(@Path("id") ticketId: Long, @Body request: UsedPartDTO): UsedPartDTO

    @PATCH("/api/tickets/{id}/complete")
    suspend fun completeTicket(@Path("id") ticketId: Long, @Body request: CollectionRequest): FieldTicketDTO

    @PATCH("/api/tickets/{id}/assign")
    suspend fun assignTechnician(
        @Path("id") ticketId: Long,
        @retrofit2.http.Query("technicianId") technicianId: Long
    ): FieldTicketDTO

    @POST("/api/tickets/{id}/signature")
    suspend fun uploadSignature(@Path("id") ticketId: Long, @Body request: SignatureRequest)

    @GET("/api/inventory/barcode/{code}")
    suspend fun inventoryByBarcode(@Path("code") code: String): InventoryItemDTO

    @GET("/api/inventory")
    suspend fun inventory(): List<InventoryItemDTO>

    @POST("/api/inventory")
    suspend fun createInventory(@Body request: InventoryItemDTO): InventoryItemDTO

    @PUT("/api/inventory/{id}")
    suspend fun updateInventory(@Path("id") id: Long, @Body request: InventoryItemDTO): InventoryItemDTO

    @DELETE("/api/inventory/{id}")
    suspend fun deleteInventory(@Path("id") id: Long)

    @GET("/api/finance/daily-summary")
    suspend fun financeDailySummary(@Query("date") date: String): DailySummaryDTO

    @GET("/api/finance/fixed-expenses")
    suspend fun financeFixedExpenses(): List<FixedExpenseDefinitionDTO>

    @POST("/api/finance/expenses")
    suspend fun financeAddExpense(@Body request: ExpenseDTO): ExpenseDTO

    @GET("/api/finance/daily-totals")
    suspend fun financeDailyTotals(): List<DailyTotalDTO>

    @GET("/api/finance/category-report")
    suspend fun financeCategoryReport(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String
    ): CategoryReportDTO

    @GET("/api/current-accounts")
    suspend fun financeCurrentAccounts(): List<CurrentAccountDTO>

    @POST("/api/current-accounts/{id}/pay")
    suspend fun financePayDebt(
        @Path("id") accountId: Long,
        @Body request: Map<String, Any>
    ): CurrentAccountDTO

    @GET("/api/reports/finance/archives")
    suspend fun financeMonthlyArchives(): List<MonthlySummaryDTO>

    @GET("/api/reports/finance/pdf")
    suspend fun financeMonthlyPdf(@Query("month") month: String): ResponseBody

    @POST("/api/finance/close-day")
    suspend fun financeCloseDay(@Body request: CloseDayRequest): DailyClosingDTO

    @GET("/api/proposals")
    suspend fun proposals(): List<ProposalDTO>

    @POST("/api/proposals")
    suspend fun createProposal(@Body request: ProposalDTO): ProposalDTO

    @PUT("/api/proposals/{id}")
    suspend fun updateProposal(@Path("id") id: Long, @Body request: ProposalDTO): ProposalDTO

    @DELETE("/api/proposals/{id}")
    suspend fun deleteProposal(@Path("id") id: Long)

    @POST("/api/proposals/{id}/convert")
    suspend fun convertProposalToJob(@Path("id") id: Long): ProposalDTO

    @GET("/api/proposals/{id}/pdf")
    suspend fun proposalPdf(@Path("id") id: Long): ResponseBody

    @GET("/api/admin/dashboard")
    suspend fun dashboard(): DashboardKPIs

    @GET("/api/admin/technician-stats")
    suspend fun technicianStats(): List<TechnicianStat>

    @GET("/api/users/technicians")
    suspend fun technicians(): List<TechnicianDTO>

    @GET("/api/users")
    suspend fun users(@Query("role") role: String? = null): List<UserDTO>

    @POST("/api/users")
    suspend fun createUser(@Body request: UserDTO): UserDTO

    @PUT("/api/users/{id}")
    suspend fun updateUser(@Path("id") id: Long, @Body request: UserDTO): UserDTO

    @DELETE("/api/users/{id}")
    suspend fun deleteUser(
        @Path("id") id: Long,
        @Query("reassignTo") reassignTo: Long? = null
    ): ResponseBody

    @POST("/api/users/{id}/reset-password")
    suspend fun resetUserPassword(@Path("id") id: Long, @Body payload: Map<String, String>)

    @Multipart
    @POST("/api/users/{id}/upload-signature")
    suspend fun uploadUserSignature(
        @Path("id") id: Long,
        @Part file: MultipartBody.Part
    ): String

    @GET("/api/vehicles")
    suspend fun vehicles(): List<VehicleDTO>

    @POST("/api/vehicles")
    suspend fun createVehicle(@Body request: VehicleDTO): VehicleDTO

    @PUT("/api/vehicles/{id}")
    suspend fun updateVehicle(@Path("id") id: Long, @Body request: VehicleDTO): VehicleDTO

    @DELETE("/api/vehicles/{id}")
    suspend fun deleteVehicle(@Path("id") id: Long)

    @GET("/api/companies/me")
    suspend fun myCompany(): CompanyDTO

    @PUT("/api/companies/me")
    suspend fun updateMyCompany(@Body request: CompanyDTO): CompanyDTO

    @Multipart
    @POST("/api/companies/me/logo")
    suspend fun uploadCompanyLogo(@Part file: MultipartBody.Part): CompanyDTO

    @GET("/api/admin/profit-analysis")
    suspend fun profitAnalysis(): ProfitAnalysis

    @GET("/api/admin/quota-status")
    suspend fun quotaStatus(): QuotaStatus

    @GET("/api/admin/field-radar")
    suspend fun fieldRadar(): List<FieldPin>

    @GET("/api/subscription/plans")
    suspend fun plans(): List<PlanDTO>

    @POST("/api/payment/init")
    suspend fun paymentInit(@Body payload: Map<String, Any>): Map<String, Any>

    @POST("/api/subscription/google-verify")
    suspend fun googleVerify(@Body payload: Map<String, String>): Map<String, Any>
}
