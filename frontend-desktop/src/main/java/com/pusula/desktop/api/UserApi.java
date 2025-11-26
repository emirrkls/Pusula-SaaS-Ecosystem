package com.pusula.desktop.api;

import com.pusula.desktop.dto.UserDTO;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;
import java.util.Map;

public interface UserApi {
    @GET("api/users")
    Call<List<UserDTO>> getAllUsers();

    @GET("api/users/technicians")
    Call<List<UserDTO>> getTechnicians();

    @POST("api/users")
    Call<UserDTO> createUser(@Body UserDTO user);

    @PUT("api/users/{id}")
    Call<UserDTO> updateUser(@Path("id") Long id, @Body UserDTO user);

    @DELETE("api/users/{id}")
    Call<Void> deleteUser(@Path("id") Long id);

    @POST("api/users/{id}/reset-password")
    Call<Void> resetPassword(@Path("id") Long id, @Body Map<String, String> payload);
}
