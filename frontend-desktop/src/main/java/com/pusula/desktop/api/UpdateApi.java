package com.pusula.desktop.api;

import com.pusula.desktop.dto.UpdateInfoDTO;
import retrofit2.Call;
import retrofit2.http.GET;

public interface UpdateApi {
    @GET("/api/public/desktop-version")
    Call<UpdateInfoDTO> getLatestVersion();
}
