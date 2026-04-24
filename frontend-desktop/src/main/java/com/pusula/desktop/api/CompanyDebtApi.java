package com.pusula.desktop.api;

import com.pusula.desktop.dto.CompanyDebtDTO;
import retrofit2.Call;
import retrofit2.http.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * API for Company Debts (Borçlarımız)
 */
public interface CompanyDebtApi {

    @GET("/api/company-debts")
    Call<List<CompanyDebtDTO>> getAllDebts();

    @GET("/api/company-debts/unpaid")
    Call<List<CompanyDebtDTO>> getUnpaidDebts();

    @GET("/api/company-debts/total-unpaid")
    Call<Map<String, BigDecimal>> getTotalUnpaidDebt();

    @POST("/api/company-debts")
    Call<CompanyDebtDTO> createDebt(@Body CompanyDebtDTO dto);

    @PUT("/api/company-debts/{id}")
    Call<CompanyDebtDTO> updateDebt(@Path("id") Long id, @Body CompanyDebtDTO dto);

    @POST("/api/company-debts/{id}/pay")
    Call<CompanyDebtDTO> payDebt(@Path("id") Long id, @Query("amount") BigDecimal amount);

    @POST("/api/company-debts/{id}/add")
    Call<CompanyDebtDTO> addDebtAmount(@Path("id") Long id, @Query("amount") BigDecimal amount, @Query("notes") String notes);

    @DELETE("/api/company-debts/{id}")
    Call<Void> deleteDebt(@Path("id") Long id);
}
