package com.pusula.desktop.api;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;

import java.util.Map;

public interface ReportsApi {
    @Headers("X-Pusula-Suppress-Forbidden-Alert: true")
    @GET("/api/reports/technician-performance")
    Call<Map<String, Map<String, Integer>>> getTechnicianPerformance();
}
