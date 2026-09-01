package com.pusula.desktop.api;

import com.pusula.desktop.dto.ServiceTicketDTO;
import com.pusula.desktop.dto.ServiceUsedPartDTO;
import com.pusula.desktop.dto.BulkTicketAssignmentRequest;
import com.pusula.desktop.dto.AuthRequest;
import com.pusula.desktop.dto.ServicePhotoDTO;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

public interface ServiceTicketApi {
    @GET("api/tickets")
    Call<List<ServiceTicketDTO>> getAllTickets();

    @Headers("X-Pusula-Suppress-Forbidden-Alert: true")
    @GET("api/tickets")
    Call<List<ServiceTicketDTO>> getAllTicketsForDashboard();

    @POST("api/tickets")
    Call<ServiceTicketDTO> createTicket(@Body ServiceTicketDTO ticket);

    @PUT("api/tickets/{id}")
    Call<ServiceTicketDTO> updateTicket(@Path("id") Long id, @Body ServiceTicketDTO ticket);

    @PATCH("api/tickets/{id}/assign")
    Call<ServiceTicketDTO> assignTechnician(@Path("id") Long id, @Query("technicianId") Long technicianId);

    @PATCH("api/tickets/{id}/assign")
    Call<ServiceTicketDTO> assignTechnicianWithSchedule(@Path("id") Long id,
            @Query("technicianId") Long technicianId,
            @Query("scheduledDate") String scheduledDate,
            @Query("scheduledEndDate") String scheduledEndDate);

    @PATCH("api/tickets/{id}/reopen")
    Call<ServiceTicketDTO> reopenCompletedService(@Path("id") Long id, @Body AuthRequest request);

    @PATCH("api/tickets/bulk-assign")
    Call<List<ServiceTicketDTO>> assignTechnicianBulk(@Body BulkTicketAssignmentRequest request);

    @POST("api/tickets/{id}/parts")
    Call<ServiceUsedPartDTO> addUsedPart(@Path("id") Long id, @Body ServiceUsedPartDTO dto);

    @GET("api/tickets/{id}/parts")
    Call<List<ServiceUsedPartDTO>> getUsedParts(@Path("id") Long id);

    @PUT("api/tickets/{id}/parts/{partId}")
    Call<ServiceUsedPartDTO> updateUsedPart(@Path("id") Long id, @Path("partId") Long partId,
            @Body ServiceUsedPartDTO dto);

    @DELETE("api/tickets/{id}/parts/{partId}")
    Call<Void> deleteUsedPart(@Path("id") Long id, @Path("partId") Long partId);

    @PATCH("api/tickets/{id}/complete")
    Call<ServiceTicketDTO> completeService(@Path("id") Long id, @Body java.util.Map<String, Object> request);

    @PATCH("api/tickets/{id}/cancel")
    Call<ServiceTicketDTO> cancelService(@Path("id") Long id);

    @POST("api/tickets/{id}/follow-up")
    Call<ServiceTicketDTO> createFollowUp(@Path("id") Long id);

    @GET("api/tickets/photos")
    Call<List<ServicePhotoDTO>> getCompanyServicePhotos(
            @Query("type") String type,
            @Query("startDate") String startDate,
            @Query("endDate") String endDate,
            @Query("query") String query,
            @Query("limit") Integer limit);
}
