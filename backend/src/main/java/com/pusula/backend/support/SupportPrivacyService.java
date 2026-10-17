package com.pusula.backend.support;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
@Service
public class SupportPrivacyService {
    private final SupportTickets tickets; private final SupportMessages messages; private final SupportAttachments attachments;
    private final SupportEvents events; private final SupportMails mails;
    public SupportPrivacyService(SupportTickets tickets,SupportMessages messages,SupportAttachments attachments,SupportEvents events,SupportMails mails) { this.tickets=tickets;this.messages=messages;this.attachments=attachments;this.events=events;this.mails=mails; }
    @Transactional public void eraseUser(Long user) { for(var ticket:tickets.findByUserId(user)) erase(ticket); messages.anonymize(user); events.anonymize(user); }
    @Scheduled(fixedDelayString="${app.support.cleanup-delay-ms:86400000}",initialDelay=120000)
    @Transactional public void retention() { attachments.expire(Instant.now().minus(Duration.ofDays(30))); for(var ticket:tickets.findTop100ByClosedAtBefore(Instant.now().minus(Duration.ofDays(180)))) erase(ticket); }
    private void erase(SupportTicket t) { mails.erase(t.getId()); attachments.erase(t.getId()); events.erase(t.getId()); messages.erase(t.getId()); tickets.delete(t); }
}
