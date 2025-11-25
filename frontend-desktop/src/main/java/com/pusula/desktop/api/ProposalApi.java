package com.pusula.desktop.api;

import com.pusula.desktop.dto.ProposalDTO;
import retrofit2.Call;
import retrofit2.http.GET;

import java.util.List;

public interface ProposalApi {
    @GET("api/proposals")
    Call<List<ProposalDTO>> getAllProposals();
}
