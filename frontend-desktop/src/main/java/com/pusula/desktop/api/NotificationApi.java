package com.pusula.desktop.api;

import com.pusula.desktop.dto.NotificationDTO;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.Path;
import java.util.List;
import java.util.Map;

public interface NotificationApi {
    @GET("api/notifications") Call<List<NotificationDTO>> list();
    @GET("api/notifications/unread-count") Call<Map<String, Long>> unreadCount();
    @PATCH("api/notifications/{id}/read") Call<NotificationDTO> markRead(@Path("id") Long id);
    @PATCH("api/notifications/read-all") Call<Void> markAllRead();
}
