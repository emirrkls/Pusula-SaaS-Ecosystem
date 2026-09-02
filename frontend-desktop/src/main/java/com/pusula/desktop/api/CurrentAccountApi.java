package com.pusula.desktop.api;

import com.pusula.desktop.dto.CurrentAccountDTO;
import com.pusula.desktop.dto.CurrentAccountHistoryDTO;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;
import java.util.Map;

public interface CurrentAccountApi {

    @GET("api/current-accounts")
    Call<List<CurrentAccountDTO>> getAll();

    @GET("api/current-accounts/by-customer/{customerId}")
    Call<CurrentAccountDTO> getByCustomer(@Path("customerId") Long customerId);

    @GET("api/current-accounts/{id}/history")
    Call<CurrentAccountHistoryDTO> getHistory(@Path("id") Long id);

    @POST("api/current-accounts")
    Call<CurrentAccountDTO> createOrUpdate(@Body Map<String, Object> request);

    @PUT("api/current-accounts/{id}/adjust")
    Call<CurrentAccountDTO> adjustBalance(@Path("id") Long id, @Body Map<String, Object> request);

    @PUT("api/current-accounts/{id}/set")
    Call<CurrentAccountDTO> setBalance(@Path("id") Long id, @Body Map<String, Object> request);

    @POST("api/current-accounts/{id}/pay")
    Call<CurrentAccountDTO> payDebt(@Path("id") Long id, @Body Map<String, Object> request);
}
