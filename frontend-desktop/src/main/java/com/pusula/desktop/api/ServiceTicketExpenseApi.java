package com.pusula.desktop.api;

import com.pusula.desktop.dto.ServiceTicketExpenseDTO;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

/**
 * API for Service Ticket External Expenses
 */
public interface ServiceTicketExpenseApi {

    @GET("/api/service-tickets/{ticketId}/expenses")
    Call<List<ServiceTicketExpenseDTO>> getExpenses(@Path("ticketId") Long ticketId);

    @POST("/api/service-tickets/{ticketId}/expenses")
    Call<ServiceTicketExpenseDTO> addExpense(@Path("ticketId") Long ticketId, @Body ServiceTicketExpenseDTO dto);

    @DELETE("/api/service-tickets/{ticketId}/expenses/{expenseId}")
    Call<Void> deleteExpense(@Path("ticketId") Long ticketId, @Path("expenseId") Long expenseId);
}
