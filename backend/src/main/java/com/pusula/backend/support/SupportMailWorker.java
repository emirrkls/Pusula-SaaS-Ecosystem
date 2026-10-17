package com.pusula.backend.support;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;

/** Durable, bounded, at-least-once admin alerts. No customer text or attachments leave the support store. */
@Service
public class SupportMailWorker {
    private final SupportMails mails; private final SupportProperties config;
    private final ObjectProvider<JavaMailSender> sender; private final TransactionTemplate transaction;
    public SupportMailWorker(SupportMails mails,SupportProperties config,ObjectProvider<JavaMailSender> sender,org.springframework.transaction.PlatformTransactionManager manager) { this.mails=mails;this.config=config;this.sender=sender;this.transaction=new TransactionTemplate(manager); }
    @Scheduled(fixedDelayString="${app.support.mail-delay-ms:60000}",initialDelay=60000)
    public void deliver() {
        if(!config.isEnabled() || config.getNotifyEmail().isBlank() || config.getMailFrom().isBlank() || sender.getIfAvailable()==null) return;
        for(var pending:mails.findTop20ByDeliveredAtIsNullAndAttemptsLessThanAndNextAttemptAtBeforeOrderById(8,Instant.now())) {
            transaction.executeWithoutResult(tx->{
                var mail=mails.lockById(pending.getId()).orElse(null);
                if(mail==null || mail.getDeliveredAt()!=null || mail.getAttempts()>=8 || mail.getNextAttemptAt().isAfter(Instant.now())) return;
                try {
                    var message=new SimpleMailMessage(); message.setFrom(config.getMailFrom()); message.setTo(config.getNotifyEmail());
                    message.setSubject(String.format(java.util.Locale.ROOT,"Pusula destek: DST-%06d",mail.getTicketId()));
                    message.setText("Yeni destek mesajı var. Yetkili Süper Admin panelindeki Destek Merkezi'nden okuyabilirsiniz. Bu e-posta müşteri içeriği barındırmaz.");
                    sender.getObject().send(message); mail.setDeliveredAt(Instant.now());
                } catch(org.springframework.mail.MailException ignored) { /* No recipient, SMTP credential or content in logs. */ }
                mail.setAttempts(mail.getAttempts()+1); mail.setNextAttemptAt(Instant.now().plusSeconds(Math.min(21600,60L << Math.min(mail.getAttempts(),8)))); mails.save(mail);
            });
        }
    }
}
