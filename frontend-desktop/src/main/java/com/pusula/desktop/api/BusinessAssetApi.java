package com.pusula.desktop.api;

import com.pusula.desktop.dto.BusinessAssetDTO;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

import java.util.List;

public interface BusinessAssetApi {
    @GET("api/business-assets")
    Call<List<BusinessAssetDTO>> getAll();

    @POST("api/business-assets")
    Call<BusinessAssetDTO> create(@Body BusinessAssetDTO asset);

    @PUT("api/business-assets/{id}")
    Call<BusinessAssetDTO> update(@Path("id") Long id, @Body BusinessAssetDTO asset);

    @DELETE("api/business-assets/{id}")
    Call<Void> delete(@Path("id") Long id);
}
