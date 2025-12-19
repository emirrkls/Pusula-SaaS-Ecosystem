package com.pusula.desktop.api;

import com.pusula.desktop.dto.CommercialDeviceDTO;
import com.pusula.desktop.dto.SaleRequestDTO;
import com.pusula.desktop.dto.SaleResponseDTO;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;
import java.util.Map;

public interface CommercialDeviceApi {

    @GET("api/commercial-devices")
    Call<List<CommercialDeviceDTO>> getAll();

    @GET("api/commercial-devices/{id}")
    Call<CommercialDeviceDTO> getById(@Path("id") Long id);

    @POST("api/commercial-devices")
    Call<CommercialDeviceDTO> create(@Body CommercialDeviceDTO dto);

    @PUT("api/commercial-devices/{id}")
    Call<CommercialDeviceDTO> update(@Path("id") Long id, @Body CommercialDeviceDTO dto);

    @DELETE("api/commercial-devices/{id}")
    Call<Void> delete(@Path("id") Long id);

    @POST("api/commercial-devices/{id}/sell")
    Call<CommercialDeviceDTO> sell(@Path("id") Long id, @Body Map<String, Integer> payload);

    @POST("api/commercial-devices/sale")
    Call<SaleResponseDTO> processSale(@Body SaleRequestDTO request);
}
