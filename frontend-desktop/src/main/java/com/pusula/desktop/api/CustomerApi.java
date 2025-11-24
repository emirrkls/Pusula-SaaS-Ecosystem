package com.pusula.desktop.api;

import com.pusula.desktop.dto.CustomerDTO;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

import java.util.List;

public interface CustomerApi {
    @GET("api/customers")
    Call<List<CustomerDTO>> getAllCustomers();

    @GET("api/customers/{id}")
    Call<CustomerDTO> getCustomerById(@Path("id") Long id);

    @POST("api/customers")
    Call<CustomerDTO> createCustomer(@Body CustomerDTO customer);

    @PUT("api/customers/{id}")
    Call<CustomerDTO> updateCustomer(@Path("id") Long id, @Body CustomerDTO customer);
}
