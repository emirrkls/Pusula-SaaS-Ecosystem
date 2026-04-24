package com.pusula.desktop.api;

import com.pusula.desktop.dto.VehicleStockDTO;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;
import java.util.Map;

public interface VehicleStockApi {

    @GET("api/vehicle-stocks")
    Call<List<VehicleStockDTO>> getAll();

    @GET("api/vehicle-stocks/by-vehicle/{vehicleId}")
    Call<List<VehicleStockDTO>> getByVehicle(@Path("vehicleId") Long vehicleId);

    @GET("api/vehicle-stocks/by-inventory/{inventoryId}")
    Call<List<VehicleStockDTO>> getByInventory(@Path("inventoryId") Long inventoryId);

    @POST("api/vehicle-stocks")
    Call<VehicleStockDTO> create(@Body Map<String, Object> request);

    @PUT("api/vehicle-stocks/{id}")
    Call<VehicleStockDTO> update(@Path("id") Long id, @Body Map<String, Object> request);

    @DELETE("api/vehicle-stocks/{id}")
    Call<Void> delete(@Path("id") Long id);
}
