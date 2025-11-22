package com.pusula.desktop.api;

import com.pusula.desktop.dto.CustomerDTO;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

import java.util.List;
import java.util.UUID;

public interface CustomerApi {
    @GET("api/customers")
    Call<List<CustomerDTO>> getAllCustomers();

    @POST("api/customers")
    Call<CustomerDTO> createCustomer(@Body CustomerDTO customer);

    @PUT("api/customers/{id}")
    Call<CustomerDTO> updateCustomer(@Path("id") UUID id, @Body CustomerDTO customer);
}
