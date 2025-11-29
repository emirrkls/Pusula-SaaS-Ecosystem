package com.pusula.desktop.api;

import com.pusula.desktop.entity.Company;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;

public interface CompanyApi {

    @GET("api/companies/me")
    Call<Company> getMyCompany();

    @PUT("api/companies/me")
    Call<Company> updateMyCompany(@Body Company company);

    @retrofit2.http.Multipart
    @retrofit2.http.POST("api/companies/me/logo")
    Call<Company> uploadLogo(@retrofit2.http.Part okhttp3.MultipartBody.Part file);
}
