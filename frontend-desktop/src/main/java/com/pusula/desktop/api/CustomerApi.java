package com.pusula.desktop.api;

import com.pusula.desktop.dto.CustomerDTO;
import com.pusula.desktop.dto.CustomerWhatsAppConsentDTO;
import com.pusula.desktop.dto.UpdateCustomerWhatsAppConsentRequest;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
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

    @DELETE("api/customers/{id}")
    Call<Void> deleteCustomer(@Path("id") Long id);

    @GET("api/customers/{id}/whatsapp-consent")
    Call<CustomerWhatsAppConsentDTO> getWhatsAppConsent(@Path("id") Long id);

    @PUT("api/customers/{id}/whatsapp-consent")
    Call<CustomerWhatsAppConsentDTO> updateWhatsAppConsent(
            @Path("id") Long id,
            @Body UpdateCustomerWhatsAppConsentRequest request);
}
