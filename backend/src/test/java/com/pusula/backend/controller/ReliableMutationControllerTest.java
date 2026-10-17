package com.pusula.backend.controller;

import com.pusula.backend.aspect.FeatureGateAspect;
import com.pusula.backend.config.GlobalExceptionHandler;
import com.pusula.backend.entity.*;
import com.pusula.backend.dto.ReliableExpenseCreateRequest;
import com.pusula.backend.repository.*;
import com.pusula.backend.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.LongFunction;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReliableMutationController.class)
@ContextConfiguration(classes = {ReliableMutationController.class, FeatureGateAspect.class,
        GlobalExceptionHandler.class, ReliableMutationControllerTest.Security.class})
class ReliableMutationControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ReliableMutationService reliable;
    @MockBean CustomerService customers;
    @MockBean ServiceTicketService tickets;
    @MockBean ServiceTicketNoteService notes;
    @MockBean InventoryService inventory;
    @MockBean InventoryRepository inventoryRepository;
    @MockBean FinanceService finance;
    @MockBean ExpenseRepository expenses;
    @MockBean FeatureService features;
    @MockBean CompanyRepository companies;

    @Test void completionForwardsDiscountAndAddsClosureNoteInsideTheReceiptOnlyOnce() throws Exception {
        var ticket = com.pusula.backend.dto.ServiceTicketDTO.builder().id(100L).status(ServiceTicket.TicketStatus.COMPLETED).build();
        when(tickets.getTicketById(100L)).thenReturn(ticket);
        when(reliable.execute(anyString(), eq("ticket.complete"), any(), any(), any(), any()))
                .thenAnswer(call -> ((Supplier<?>) call.getArgument(3)).get());
        when(tickets.completeService(eq(100L), eq(new java.math.BigDecimal("1500")), eq(new java.math.BigDecimal("2000")),
                eq(PaymentMethod.CASH), isNull(), eq(90L), isNull(), eq(new java.math.BigDecimal("2000")),
                eq("PERCENT"), eq(new java.math.BigDecimal("10")), eq(new java.math.BigDecimal("3000")))).thenReturn(ticket);
        mvc.perform(post("/api/reliable-mutations/tickets/100/complete").with(actor("COMPANY_ADMIN"))
                .header("Idempotency-Key", UUID.randomUUID()).contentType("application/json")
                .content("{\"collectedAmount\":1500,\"laborFee\":2000,\"paymentMethod\":\"CASH\",\"discountType\":\"PERCENT\",\"discountValue\":10,\"billingPartyId\":90,\"billingPartyAmount\":2000,\"expectedPartsTotal\":3000,\"technicianNote\":\"Tamamlandı\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(100));
        verify(notes).addClosureNote(100L, "Tamamlandı");
    }

    @Test void completionReplayReloadsTheClosedTicketWithoutRepeatingTheClosureNote() throws Exception {
        var ticket = com.pusula.backend.dto.ServiceTicketDTO.builder().id(100L).status(ServiceTicket.TicketStatus.COMPLETED).build();
        when(tickets.getTicketById(100L)).thenReturn(ticket);
        when(reliable.execute(anyString(), eq("ticket.complete"), any(), any(), any(), any()))
                .thenAnswer(call -> ((LongFunction<?>) call.getArgument(5)).apply(100L));
        mvc.perform(post("/api/reliable-mutations/tickets/100/complete").with(actor("COMPANY_ADMIN"))
                .header("Idempotency-Key", UUID.randomUUID()).contentType("application/json")
                .content("{\"collectedAmount\":0,\"laborFee\":0,\"technicianNote\":\"Tamamlandı\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(100));
        verifyNoInteractions(notes);
    }

    @Test void reliableCompletionRequiresAnExplicitReplayKey() throws Exception {
        mvc.perform(post("/api/reliable-mutations/tickets/100/complete").with(actor("COMPANY_ADMIN"))
                .contentType("application/json").content("{\"collectedAmount\":0,\"laborFee\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reliable);
    }

    @Test void noteWriteBindsNormalizedContentTargetAndImportantFlagToTheReceipt() throws Exception {
        var saved = new com.pusula.backend.dto.ServiceTicketNoteDTO(); saved.setId(501L);
        when(reliable.execute(anyString(), eq("ticket.note.create"), any(), any(), any(), any()))
                .thenAnswer(call -> {
                    assertEquals(Map.of("ticketId", 999L, "content", "Synthetic note", "important", true), call.getArgument(2));
                    return ((Supplier<?>) call.getArgument(3)).get();
                });
        when(notes.addWorkLog(999L, "Synthetic note", true)).thenReturn(saved);
        mvc.perform(post("/api/reliable-mutations/tickets/999/technician-notes").with(actor("TECHNICIAN"))
                .header("Idempotency-Key", UUID.randomUUID()).contentType("application/json")
                .content("{\"content\":\"  Synthetic note  \",\"important\":true,\"companyId\":999,\"authorUserId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(501));
        verify(notes).checkTicketAccess(999L);
        verify(features).checkFeature(1L, "SERVICE_TICKETS");
    }

    @Test void noteReplayRechecksAccessAndOnlyReloadsTheOriginalScopedNote() throws Exception {
        var saved = new com.pusula.backend.dto.ServiceTicketNoteDTO(); saved.setId(501L);
        when(reliable.execute(anyString(), eq("ticket.note.create"), any(), any(), any(), any()))
                .thenAnswer(call -> ((LongFunction<?>) call.getArgument(5)).apply(501L));
        when(notes.getNoteForReplay(999L, 501L)).thenReturn(saved);
        mvc.perform(post("/api/reliable-mutations/tickets/999/technician-notes").with(actor("TECHNICIAN"))
                .header("Idempotency-Key", UUID.randomUUID()).contentType("application/json")
                .content("{\"content\":\"Synthetic\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(501));
        verify(notes).checkTicketAccess(999L);
        verify(notes).getNoteForReplay(999L, 501L);
        verify(notes, never()).addWorkLog(anyLong(), anyString(), anyBoolean());
    }

    @Test void notesRejectBlankMissingOrUnkeyedInputBeforeAnyMutation() throws Exception {
        for (String json : List.of("{}", "{\"content\":null}", "{\"content\":\" \\n \"}")) {
            mvc.perform(post("/api/reliable-mutations/tickets/999/technician-notes").with(actor("TECHNICIAN"))
                    .header("Idempotency-Key", UUID.randomUUID()).contentType("application/json").content(json))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/reliable-mutations/tickets/999/technician-notes").with(actor("TECHNICIAN"))
                .contentType("application/json").content("{\"content\":\"Synthetic\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(notes, reliable);
    }

    @Test void revokedNoteAccessCannotReachTheReceiptOrReturnItsResult() throws Exception {
        doThrow(new org.springframework.security.access.AccessDeniedException("Reassigned ticket"))
                .when(notes).checkTicketAccess(999L);
        mvc.perform(post("/api/reliable-mutations/tickets/999/technician-notes").with(actor("TECHNICIAN"))
                .header("Idempotency-Key", UUID.randomUUID()).contentType("application/json")
                .content("{\"content\":\"Synthetic\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reliable);
    }

    @Test void readOnlyCompanyCannotCreateOrReplayATechnicianNote() throws Exception {
        Company company = new Company(); company.setId(1L); company.setIsReadOnly(true);
        when(companies.findById(1L)).thenReturn(Optional.of(company));
        mvc.perform(post("/api/reliable-mutations/tickets/999/technician-notes").with(actor("TECHNICIAN"))
                .header("Idempotency-Key", UUID.randomUUID()).contentType("application/json")
                .content("{\"content\":\"Synthetic\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(notes, reliable);
    }

    private RequestPostProcessor actor(String role) {
        User user = new User(); user.setId(10L); user.setCompanyId(1L); user.setRole(role);
        return authentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
    @BeforeEach void setup() {
        Company company = new Company(); company.setId(1L); company.setIsReadOnly(false);
        when(companies.findById(1L)).thenReturn(Optional.of(company));
    }

    @Test void anonymousCannotUseTheVersionedWriteContract() throws Exception {
        mvc.perform(post("/api/reliable-mutations/tickets").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reliable);
    }
    @Test void missingKeyIsAClientErrorAndNeverInvokesAMutation() throws Exception {
        mvc.perform(post("/api/reliable-mutations/customers").with(actor("COMPANY_ADMIN"))
                        .contentType("application/json").content("{\"name\":\"Test\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSING_HEADER"));
        verifyNoInteractions(reliable, customers);
    }
    @Test void inventoryAdminRestrictionIsNotBypassedByTheNewPath() throws Exception {
        mvc.perform(post("/api/reliable-mutations/inventory").with(actor("TECHNICIAN"))
                        .header("Idempotency-Key",UUID.randomUUID()).contentType("application/json")
                        .content("{\"partName\":\"Test\",\"quantity\":1}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reliable, inventory);
    }
    @Test void featureChecksStillRunBeforeFirstAttemptsAndReplays() throws Exception {
        mvc.perform(post("/api/reliable-mutations/tickets").with(actor("COMPANY_ADMIN"))
                        .header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content("{}"))
                .andExpect(status().isOk());
        verify(features).checkFeature(1L,"SERVICE_TICKETS");
        verify(reliable).execute(anyString(),eq("ticket.create"),any(),any(),any(),any());
    }
    @Test void readOnlySubscriptionCannotReplayAMutationThroughTheNewPath() throws Exception {
        Company company = new Company(); company.setId(1L); company.setIsReadOnly(true);
        when(companies.findById(1L)).thenReturn(Optional.of(company));
        mvc.perform(post("/api/reliable-mutations/tickets").with(actor("COMPANY_ADMIN"))
                        .header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reliable);
    }
    @Test void mismatchedContentIsA409RatherThanAnAutomaticNewWrite() throws Exception {
        when(reliable.execute(anyString(),eq("ticket.create"),any(),any(),any(),any()))
                .thenThrow(new MutationConflictException("IDEMPOTENCY_MISMATCH","Önceki kaydı kontrol edin."));
        mvc.perform(post("/api/reliable-mutations/tickets").with(actor("COMPANY_ADMIN"))
                        .header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_MISMATCH"));
    }
    private static final String EXPENSE_JSON = """
            {"amount":125.50,"description":"Synthetic expense","date":"2026-10-14",
             "category":"OTHER","financialTreatment":"OPERATING_EXPENSE"}
            """;

    @Test void technicianCannotCreateAnExpenseThroughReliableRoute() throws Exception {
        mvc.perform(post("/api/reliable-mutations/expenses").with(actor("TECHNICIAN"))
                .header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(EXPENSE_JSON))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reliable,finance,expenses);
    }

    @Test void reliableExpenseUsesAuthenticatedTenantAndNeverClientRecordIds() throws Exception {
        when(reliable.execute(anyString(),eq("expense.create"),any(),any(),any(),any()))
                .thenAnswer(call -> ((Supplier<?>)call.getArgument(3)).get());
        when(finance.addExpense(any())).thenAnswer(call -> {
            Expense value = call.getArgument(0);
            assertEquals(1L,value.getCompanyId()); assertNull(value.getId());
            assertNull(value.getFixedExpenseId()); assertNull(value.getSourceId()); assertNull(value.getSourceType());
            value.setId(501L); return value;
        });
        String forged = EXPENSE_JSON.replace("{", "{\"companyId\":999,\"id\":480,\"fixedExpenseId\":8,\"sourceId\":7,\"sourceType\":\"SYSTEM\",");
        mvc.perform(post("/api/reliable-mutations/expenses").with(actor("COMPANY_ADMIN"))
                .header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(forged))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(501)).andExpect(jsonPath("$.companyId").value(1));
        verify(features).checkFeature(1L,"FINANCE_MODULE");
    }

    @Test void expenseReplayOnlyReloadsInsideTheAuthenticatedTenant() throws Exception {
        when(reliable.execute(anyString(),eq("expense.create"),any(),any(),any(),any()))
                .thenAnswer(call -> ((LongFunction<?>)call.getArgument(5)).apply(501L));
        Expense saved = Expense.builder().id(501L).companyId(1L).build();
        when(expenses.findByIdAndCompanyId(501L,1L)).thenReturn(Optional.of(saved));
        mvc.perform(post("/api/reliable-mutations/expenses").with(actor("COMPANY_ADMIN"))
                .header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(EXPENSE_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(501));
        verify(expenses).findByIdAndCompanyId(501L,1L); verifyNoInteractions(finance);
    }

    @Test void invalidOrUnkeyedExpenseNeverReachesTheMutationCoordinator() throws Exception {
        mvc.perform(post("/api/reliable-mutations/expenses").with(actor("COMPANY_ADMIN"))
                .contentType("application/json").content(EXPENSE_JSON)).andExpect(status().isBadRequest());
        for (String content : List.of(EXPENSE_JSON.replace("125.50","0"),
                EXPENSE_JSON.replace("125.50","-1"), EXPENSE_JSON.replace("Synthetic expense",""),
                EXPENSE_JSON.replace("\"2026-10-14\"","null"))) {
            mvc.perform(post("/api/reliable-mutations/expenses").with(actor("COMPANY_ADMIN"))
                    .header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(content))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(reliable,finance,expenses);
    }

    @Test void readOnlyCompanyCannotCreateOrReplayExpense() throws Exception {
        Company company = new Company(); company.setId(1L); company.setIsReadOnly(true);
        when(companies.findById(1L)).thenReturn(Optional.of(company));
        mvc.perform(post("/api/reliable-mutations/expenses").with(actor("COMPANY_ADMIN"))
                .header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(EXPENSE_JSON))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reliable,finance,expenses);
    }
    @TestConfiguration @EnableMethodSecurity @EnableAspectJAutoProxy
    static class Security {
        @Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
            return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().authenticated())
                    .exceptionHandling(e -> e.authenticationEntryPoint((q,r,x) -> r.setStatus(401))).build();
        }
    }
}
