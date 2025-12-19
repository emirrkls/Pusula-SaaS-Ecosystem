package com.pusula.desktop.api;

import com.pusula.desktop.dto.VehicleDTO;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

public interface VehicleApi {

    @GET("api/vehicles")
    Call<List<VehicleDTO>> getAll(@Header("X-Company-Id") Long companyId);

    @GET("api/vehicles/active")
    Call<List<VehicleDTO>> getActive(@Header("X-Company-Id") Long companyId);

    @GET("api/vehicles/{id}")
    Call<VehicleDTO> getById(@Path("id") Long id);

    @POST("api/vehicles")
    Call<VehicleDTO> create(@Header("X-Company-Id") Long companyId, @Body VehicleDTO vehicle);

    @PUT("api/vehicles/{id}")
    Call<VehicleDTO> update(@Path("id") Long id, @Body VehicleDTO vehicle);

    @DELETE("api/vehicles/{id}")
    Call<Void> delete(@Path("id") Long id);
}
