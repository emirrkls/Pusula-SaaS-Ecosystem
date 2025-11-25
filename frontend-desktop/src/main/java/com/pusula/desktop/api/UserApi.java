package com.pusula.desktop.api;

import com.pusula.desktop.dto.UserDTO;
import retrofit2.Call;
import retrofit2.http.GET;

import java.util.List;

public interface UserApi {
    @GET("api/users")
    Call<List<UserDTO>> getAllUsers();

    @GET("api/users/technicians")
    Call<List<UserDTO>> getTechnicians();
}
