package com.pusula.desktop.api;
import retrofit2.Call;
import retrofit2.http.*;
import java.util.Map;
import com.pusula.desktop.dto.NetworkDTOs.*;

public interface ServiceNetworkApi {
    @GET("api/service-network/context") Call<Context> context();
    @GET("api/service-network/members") Call<Page<Member>> members(@Query("page") int page,@Query("query") String query);
    @POST("api/service-network/members/invite") Call<Member> invite(@Body Map<String,Object> body);
    @POST("api/service-network/members/create") Call<CreatedChild> create(@Body Map<String,Object> body);
    @POST("api/service-network/members/{id}/decision") Call<Member> decide(@Path("id") Long id,@Body Map<String,Object> body);
    @POST("api/service-network/members/{id}/close") Call<Member> close(@Path("id") Long id,@Body Map<String,Object> body);
    @PUT("api/service-network/policies/{id}") Call<Void> configure(@Path("id") Long id,@Body Map<String,Object> body);
    @GET("api/service-network/orders") Call<Page<Order>> orders(@Query("direction") String direction,@Query("query") String query,
            @Query("status") String status,@Query("from") String from,@Query("to") String to,@Query("page") int page);
    @POST("api/service-network/orders") Call<Order> dispatch(@Body Map<String,Object> body);
    @GET("api/service-network/orders/{id}") Call<Order> order(@Path("id") Long id);
    @POST("api/service-network/orders/{id}/accept") Call<Order> accept(@Path("id") Long id,@Body Map<String,Object> body);
    @POST("api/service-network/orders/{id}/reject") Call<Order> reject(@Path("id") Long id,@Body Map<String,Object> body);
    @POST("api/service-network/orders/{id}/cancel") Call<Order> cancel(@Path("id") Long id,@Body Map<String,Object> body);
    @POST("api/service-network/orders/{id}/notes") Call<Void> note(@Path("id") Long id,@Body Map<String,Object> body);
    @GET("api/service-network/orders/{id}/history") Call<Page<Event>> history(@Path("id") Long id,@Query("page") int page);
}
