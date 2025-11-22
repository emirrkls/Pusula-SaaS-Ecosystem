package com.pusula.desktop.api;

import com.pusula.desktop.dto.ServiceTicketDTO;
import com.pusula.desktop.dto.ServiceUsedPartDTO;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;
import java.util.UUID;

public interface ServiceTicketApi {
    @GET("api/tickets")
    Call<List<ServiceTicketDTO>> getAllTickets();

    @POST("api/tickets")
    Call<ServiceTicketDTO> createTicket(@Body ServiceTicketDTO ticket);

    @PUT("api/tickets/{id}")
    Call<ServiceTicketDTO> updateTicket(@Path("id") UUID id, @Body ServiceTicketDTO ticket);

    @PATCH("api/tickets/{id}/assign")
    Call<ServiceTicketDTO> assignTechnician(@Path("id") UUID id, @Query("technicianId") UUID technicianId);

    @POST("api/tickets/{id}/parts")
    Call<ServiceUsedPartDTO> addUsedPart(@Path("id") UUID id, @Body ServiceUsedPartDTO dto);

    @GET("api/tickets/{id}/parts")
    Call<List<ServiceUsedPartDTO>> getUsedParts(@Path("id") UUID id);
}
