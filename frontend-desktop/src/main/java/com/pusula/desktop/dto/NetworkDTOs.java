package com.pusula.desktop.dto;
import java.util.List;

public final class NetworkDTOs {
    private NetworkDTOs() {}
    public record Context(Long companyId, boolean canManage, boolean writable, int maxMembers, long usedMembers,
                          int maxMonthlyOrders, long usedMonthlyOrders) {}
    public record Member(Long id,Long parentCompanyId,Long childCompanyId,String parentName,String childName,String region,String status,String createdAt) {}
    public record CreatedChild(Member member,String orgCode,String username) {}
    public record Order(Long id,Long membershipId,Long parentCompanyId,Long childCompanyId,String parentName,String childName,
                        String title,String customerName,String customerPhone,String customerAddress,String instruction,String scheduledDate,
                        String scheduledEndDate,String status,Long acceptedTicketId,String ticketStatus,String currentScheduledDate,
                        String currentScheduledEndDate,String completedAt,String resolutionNote,String createdAt) {}
    public record Event(Long id,String action,String note,Long actorCompanyId,String createdAt) {}
    public record Page<T>(List<T> items,long totalElements,int page,int totalPages,boolean hasNext) {}
}
