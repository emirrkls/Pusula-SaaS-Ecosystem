package com.pusula.desktop.api;

import com.pusula.desktop.dto.AccountPartyOptionDTO;
import com.pusula.desktop.dto.AccountPartyDTO;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

import java.util.List;

public interface AccountPartyApi {
    @GET("/api/account-parties/billing-options")
    Call<List<AccountPartyOptionDTO>> getBillingOptions();

    @GET("/api/account-parties")
    Call<List<AccountPartyDTO>> getParties(@Query("type") String type);

    @POST("/api/account-parties")
    Call<AccountPartyDTO> create(@Body AccountPartyDTO party);
}
