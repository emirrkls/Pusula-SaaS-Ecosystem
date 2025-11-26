package com.pusula.desktop.api;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface ReportApi {

    @GET("api/reports/pdf/service/{ticketId}")
    Call<ResponseBody> downloadServiceReport(@Path("ticketId") Long ticketId);

    @GET("api/reports/pdf/proposal/{proposalId}")
    Call<ResponseBody> downloadProposal(@Path("proposalId") Long proposalId);
}
