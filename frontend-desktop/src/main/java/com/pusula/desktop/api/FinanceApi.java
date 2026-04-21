package com.pusula.desktop.api;

import com.pusula.desktop.dto.CategoryReportDTO;
import com.pusula.desktop.dto.CloseDayRequest;
import com.pusula.desktop.dto.DailyClosingDTO;
import com.pusula.desktop.dto.DailySummaryDTO;
import com.pusula.desktop.dto.DailyTotalDTO;
import com.pusula.desktop.dto.ExpenseDTO;
import com.pusula.desktop.dto.FinancialSummaryDTO;
import com.pusula.desktop.dto.FixedExpenseDefinitionDTO;
import retrofit2.Call;
import retrofit2.http.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * FinanceApi - Retrofit interface for Finance endpoints
 */
public interface FinanceApi {

        @GET("/api/finance/summary")
        Call<FinancialSummaryDTO> getSummary(
                        @Query("companyId") Long companyId,
                        @Query("period") String period);

        @GET("/api/finance/cumulative")
        Call<Map<String, Object>> getCumulativeSummary(@Query("companyId") Long companyId);

        @POST("/api/finance/expenses")
        Call<ExpenseDTO> addExpense(@Body ExpenseDTO expense);

        @PUT("/api/finance/expenses/{id}")
        Call<ExpenseDTO> updateExpense(
                        @Path("id") Long id,
                        @Body ExpenseDTO expense);

        @DELETE("/api/finance/expenses/{id}")
        Call<Void> deleteExpense(@Path("id") Long id);

        @GET("/api/finance/expenses")
        Call<List<ExpenseDTO>> getExpenses(@Query("companyId") Long companyId);

        @GET("/api/finance/daily-summary")
        Call<DailySummaryDTO> getDailySummary(
                        @Query("companyId") Long companyId,
                        @Query("date") String date);

        @POST("/api/finance/close-day")
        Call<DailyClosingDTO> closeDay(@Body CloseDayRequest request);

        @GET("/api/finance/category-report")
        Call<CategoryReportDTO> getCategoryReport(
                        @Query("companyId") Long companyId,
                        @Query("startDate") String startDate,
                        @Query("endDate") String endDate);

        @GET("/api/finance/fixed-expenses")
        Call<List<FixedExpenseDefinitionDTO>> getFixedExpenses(@Query("companyId") Long companyId);

        @POST("/api/finance/fixed-expenses")
        Call<FixedExpenseDefinitionDTO> createFixedExpense(@Body FixedExpenseDefinitionDTO definition);

        @PUT("/api/finance/fixed-expenses/{id}")
        Call<FixedExpenseDefinitionDTO> updateFixedExpense(
                        @Path("id") Long id,
                        @Body FixedExpenseDefinitionDTO definition);

        @DELETE("/api/finance/fixed-expenses/{id}")
        Call<Void> deleteFixedExpense(@Path("id") Long id);

        @POST("/api/finance/fixed-expenses/pay/{id}")
        Call<ExpenseDTO> payFixedExpense(
                        @Path("id") Long id,
                        @Query("companyId") Long companyId,
                        @Query("date") String date,
                        @Query("amount") java.math.BigDecimal amount);

        @GET("/api/finance/daily-totals")
        Call<List<DailyTotalDTO>> get30DayTotals(@Query("companyId") Long companyId);

        @GET("/api/finance/upcoming-fixed-expenses")
        Call<List<FixedExpenseDefinitionDTO>> getUpcomingFixedExpenses(
                        @Query("companyId") Long companyId,
                        @Query("daysThreshold") int daysThreshold);

        // Monthly Reporting
        @GET("/api/reports/finance/archives")
        Call<List<com.pusula.desktop.dto.MonthlySummaryDTO>> getMonthlyArchives(
                        @Query("companyId") Long companyId);

        @GET("/api/reports/finance/pdf")
        Call<okhttp3.ResponseBody> downloadMonthlyPDF(
                        @Query("month") String month,
                        @Query("companyId") Long companyId);

        @GET("/api/finance/inventory-value")
        Call<Map<String, BigDecimal>> getInventoryValue(@Query("companyId") Long companyId);
}
