package com.pusula.desktop.api;

import com.pusula.desktop.dto.AuthRequest;
import com.pusula.desktop.dto.AuthResponse;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

import java.util.Map;

public interface AuthApi {
    @POST("/api/auth/authenticate")
    Call<AuthResponse> authenticate(@Body AuthRequest request);

    @POST("/api/auth/verify-password")
    Call<Map<String, Boolean>> verifyPassword(@Body AuthRequest request);
}
