package com.pusula.backend.support;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class SupportDtos {
    private SupportDtos() {}
    public record Upload(@NotBlank @Size(max=32) String mediaType,
                         @NotBlank @Size(max=2800000) String dataBase64) {}
    public record Create(@NotBlank @Pattern(regexp="[0-9a-fA-F-]{36}") String clientKey,
                         @NotBlank @Size(max=120) String subject,
                         @NotBlank @Pattern(regexp="QUESTION|BUG|SUGGESTION") String category,
                         @NotBlank @Size(max=4000) String body,
                         @NotBlank @Pattern(regexp="IOS|ANDROID|DESKTOP|WEB") String platform,
                         @Size(max=32) String appVersion,
                         @Size(max=3) List<@Valid Upload> attachments) {}
    public record Reply(@NotBlank @Pattern(regexp="[0-9a-fA-F-]{36}") String clientKey,
                        @Size(max=4000) String body, boolean internalNote,
                        @Pattern(regexp="NEW|IN_PROGRESS|WAITING_USER|RESOLVED|EVALUATING|PLANNED") String status,
                        @Pattern(regexp="NORMAL|HIGH|URGENT") String priority,
                        @Size(max=3) List<@Valid Upload> attachments) {}
    public record Ticket(Long id,String number,Long companyId,String companyName,Long userId,String requester,
                         String subject,String category,String status,String priority,String platform,String appVersion,
                         Instant createdAt,Instant updatedAt,boolean unread) {}
    public record Attachment(Long id,String fileName,String mediaType,int size) {}
    public record Message(Long id,String body,boolean staff,boolean internalNote,Instant createdAt,List<Attachment> attachments) {}
    public record Event(String eventType,String value,Instant createdAt) {}
    public record Detail(Ticket ticket,List<Message> messages,Long nextBefore,List<Event> events) {}
    public record Page(List<Ticket> items,int page,boolean hasNext,long unreadCount,boolean canWrite) {}
    public record Contact(boolean enabled,String email,String website,int attachmentLimit,int attachmentMaxBytes) {}
}
