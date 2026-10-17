package com.pusula.backend.support;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.*;

interface SupportTickets extends JpaRepository<SupportTicket,Long>, JpaSpecificationExecutor<SupportTicket> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SupportTicket t where t.id=:id") Optional<SupportTicket> lockById(Long id);
    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);
    List<SupportTicket> findByUserId(Long userId);
    List<SupportTicket> findTop100ByClosedAtBefore(Instant before);
    @Query("select count(t) from SupportTicket t where t.companyId=:company and t.userId=:user and t.lastAdminAt is not null and (t.userReadAt is null or t.lastAdminAt>t.userReadAt)")
    long unread(Long company,Long user);
    @Query("select count(t) from SupportTicket t where t.lastUserAt is not null and (t.adminReadAt is null or t.lastUserAt>t.adminReadAt)") long adminUnread();
}
interface SupportMessages extends JpaRepository<SupportMessage,Long> {
    Optional<SupportMessage> findByAuthorIdAndClientKey(Long authorId,String clientKey);
    long countByAuthorIdAndCreatedAtAfter(Long authorId,Instant since);
    @Query("select m from SupportMessage m where m.ticketId=:ticket and (:staff=true or m.internalNote=false) and (:before is null or m.id<:before) order by m.id desc")
    List<SupportMessage> history(Long ticket,boolean staff,Long before,Pageable page);
    @Modifying @Query("update SupportMessage m set m.authorId=null where m.authorId=:user") void anonymize(Long user);
    @Modifying @Query("delete from SupportMessage m where m.ticketId=:ticket") void erase(Long ticket);
}
interface SupportAttachments extends JpaRepository<SupportAttachment,Long> {
    @Query(value="select coalesce(sum(octet_length(a.content)),0) from support_attachments a join support_messages m on m.id=a.message_id where m.author_id=:userId",nativeQuery=true)
    long storedBytes(Long userId);
    interface Metadata { Long getId(); String getFileName(); String getMediaType(); int getSize(); }
    @Query(value="select id as id,file_name as fileName,media_type as mediaType,octet_length(content) as size from support_attachments where message_id=:messageId",nativeQuery=true)
    List<Metadata> metadata(Long messageId);
    List<SupportAttachment> findByMessageId(Long messageId);
    @Modifying @Query("delete from SupportAttachment a where a.messageId in (select m.id from SupportMessage m where m.ticketId=:ticket)") void erase(Long ticket);
    @Modifying @Query("delete from SupportAttachment a where a.createdAt<:before") void expire(Instant before);
}
interface SupportEvents extends JpaRepository<SupportEvent,Long> {
    List<SupportEvent> findTop100ByTicketIdOrderByIdDesc(Long ticketId);
    @Modifying @Query("update SupportEvent e set e.actorId=null where e.actorId=:user") void anonymize(Long user);
    @Modifying @Query("delete from SupportEvent e where e.ticketId=:ticket") void erase(Long ticket);
}
interface SupportMails extends JpaRepository<SupportMail,Long> {
    List<SupportMail> findTop20ByDeliveredAtIsNullAndAttemptsLessThanAndNextAttemptAtBeforeOrderById(int attempts,Instant before);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select m from SupportMail m where m.id=:id") Optional<SupportMail> lockById(Long id);
    @Modifying @Query("delete from SupportMail m where m.ticketId=:ticket") void erase(Long ticket);
}
