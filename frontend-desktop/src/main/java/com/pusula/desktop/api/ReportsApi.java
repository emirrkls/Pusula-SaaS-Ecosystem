package com.pusula.desktop.api;

import retrofit2.Call;
import retrofit2.http.GET;

import java.util.Map;

public interface ReportsApi {
    @GET("/api/reports/technician-performance")
    Call<Map<String, Map<String, Integer>>> getTechnicianPerformance();
}
