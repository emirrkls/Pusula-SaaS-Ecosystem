package com.pusula.desktop.support;
import retrofit2.Call;
import retrofit2.http.*;
import java.util.Map;
import okhttp3.ResponseBody;
public interface SupportApi {
    @GET("api/public/support-contact") Call<SupportModels.Contact> contact();
    @GET("api/support/unread-count") Call<Map<String,Long>> unread(@Header("Authorization") String token);
    @GET("api/support/tickets") Call<SupportModels.Page> list(@Header("Authorization") String token,@Query("page") int page);
    @GET("api/support/tickets/{id}") Call<SupportModels.Detail> detail(@Header("Authorization") String token,@Path("id") Long id,@Query("before") Long before);
    @POST("api/support/tickets") Call<SupportModels.Detail> create(@Header("Authorization") String token,@Body Map<String,Object> body);
    @POST("api/support/tickets/{id}/messages") Call<SupportModels.Detail> reply(@Header("Authorization") String token,@Path("id") Long id,@Body Map<String,Object> body);
    @PATCH("api/support/tickets/{id}/read") Call<Void> read(@Header("Authorization") String token,@Path("id") Long id);
    @GET("api/support/attachments/{id}") Call<ResponseBody> attachment(@Header("Authorization") String token,@Path("id") Long id);
}
