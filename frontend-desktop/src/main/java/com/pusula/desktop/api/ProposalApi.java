package com.pusula.desktop.api;

import com.pusula.desktop.dto.ProposalDTO;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

public interface ProposalApi {
    @GET("api/proposals")
    Call<List<ProposalDTO>> getAllProposals();

    @GET("api/proposals/{id}")
    Call<ProposalDTO> getById(@Path("id") Long id);

    @POST("api/proposals")
    Call<ProposalDTO> create(@Body ProposalDTO dto);

    @PUT("api/proposals/{id}")
    Call<ProposalDTO> update(@Path("id") Long id, @Body ProposalDTO dto);

    @DELETE("api/proposals/{id}")
    Call<Void> delete(@Path("id") Long id);

    @POST("api/proposals/{id}/convert")
    Call<ProposalDTO> convertToJob(@Path("id") Long id);

    @GET("api/proposals/{id}/pdf")
    Call<ResponseBody> getPdf(@Path("id") Long id);
}
