package com.pusula.desktop.api;

import com.pusula.desktop.dto.AuditLogDTO;
import com.pusula.desktop.entity.AuditLog;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;
import java.util.List;

public interface AuditLogApi {
    // For Activity Log page
    @GET("api/audit-logs")
    Call<PageResponse<AuditLog>> getAuditLogs(
            @Query("page") int page,
            @Query("size") int size,
            @Query("userId") Long userId,
            @Query("actionType") String actionType,
            @Query("startDate") String startDate,
            @Query("endDate") String endDate);

    @GET("api/audit-logs/count")
    Call<Long> getLogCount();

    // For Timeline in Ticket Details
    @GET("/api/audit-logs/ticket/{ticketId}")
    Call<List<AuditLogDTO>> getTicketTimeline(@Path("ticketId") Long ticketId);
}