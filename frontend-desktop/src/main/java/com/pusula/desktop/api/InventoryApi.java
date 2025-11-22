package com.pusula.desktop.api;

import com.pusula.desktop.dto.InventoryDTO;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

import java.util.List;
import java.util.UUID;

public interface InventoryApi {
    @GET("api/inventory")
    Call<List<InventoryDTO>> getAllInventory();

    @POST("api/inventory")
    Call<InventoryDTO> createInventory(@Body InventoryDTO inventory);

    @PUT("api/inventory/{id}")
    Call<InventoryDTO> updateInventory(@Path("id") UUID id, @Body InventoryDTO inventory);

    @DELETE("api/inventory/{id}")
    Call<Void> deleteInventory(@Path("id") UUID id);
}
