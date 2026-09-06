package com.pusula.backend.network;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class NetworkDtos {
    private NetworkDtos() {}
    public record PolicyRequest(boolean enabled, @Min(0) @Max(10000) int maxMembers,
                                @Min(0) @Max(1000000) int maxMonthlyOrders) {}
    public record Context(Long companyId, boolean canManage, boolean writable, int maxMembers,
                          long usedMembers, int maxMonthlyOrders, long usedMonthlyOrders) {}
    public record Invite(@NotBlank @Size(max=20) String orgCode, @Size(max=255) String region) {}
    public record CreateChild(@NotBlank @Size(max=64) String requestKey,
                              @NotBlank @Size(max=255) String name, @Size(max=255) String region,
                              @NotBlank @Size(max=100) String adminName,
                              @NotBlank @Size(max=100) String username,
                              @NotBlank @Size(max=72) String password) {
        @Override public String toString() { return "CreateChild[credentials redacted]"; }
    }
    public record CreatedChild(Member member, String orgCode, String username) {}
    public record Decision(boolean accept) {}
    public record Note(@NotBlank @Size(max=1000) String note) {}
    public record Dispatch(@NotNull Long membershipId, @NotBlank @Size(max=64) String requestKey,
                           @NotBlank @Size(max=500) String title,
                           @NotBlank @Size(max=255) String customerName,
                           @Size(max=40) String customerPhone, @Size(max=255) String customerAddress,
                           @Size(max=2000) String instruction, @NotNull LocalDateTime scheduledDate,
                           LocalDateTime scheduledEndDate) {}
    public record Accept(Long customerId, Long technicianId) {}
    public record Member(Long id, Long parentCompanyId, Long childCompanyId, String parentName,
                         String childName, String region, String status, LocalDateTime createdAt) {}
    public record Order(Long id, Long membershipId, Long parentCompanyId, Long childCompanyId,
                        String parentName, String childName, String title, String customerName,
                        String customerPhone, String customerAddress, String instruction,
                        LocalDateTime scheduledDate, LocalDateTime scheduledEndDate, String status,
                        Long acceptedTicketId, String ticketStatus, LocalDateTime currentScheduledDate,
                        LocalDateTime currentScheduledEndDate, LocalDateTime completedAt,
                        String resolutionNote, LocalDateTime createdAt) {}
    public record Event(Long id, String action, String note, Long actorCompanyId, LocalDateTime createdAt) {}
    public record PageResult<T>(List<T> items, long totalElements, int page, int totalPages, boolean hasNext) {}
}
