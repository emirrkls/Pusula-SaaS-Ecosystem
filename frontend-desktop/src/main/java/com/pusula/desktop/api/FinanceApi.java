package com.pusula.desktop.api;

import com.pusula.desktop.dto.ExpenseDTO;
import com.pusula.desktop.dto.FinancialSummaryDTO;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

/**
 * FinanceApi - Retrofit interface for Finance endpoints
 */
public interface FinanceApi {

    @GET("/api/finance/summary")
    Call<FinancialSummaryDTO> getSummary(
            @Query("companyId") Long companyId,
            @Query("period") String period);

    @POST("/api/finance/expenses")
    Call<ExpenseDTO> addExpense(@Body ExpenseDTO expense);

    @GET("/api/finance/expenses")
    Call<List<ExpenseDTO>> getExpenses(@Query("companyId") Long companyId);
}
