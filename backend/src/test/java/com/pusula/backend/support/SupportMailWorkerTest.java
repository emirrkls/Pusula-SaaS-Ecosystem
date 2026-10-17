package com.pusula.backend.support;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SupportMailWorkerTest {
    private final SupportMails mails=mock(SupportMails.class);
    private final SupportProperties config=new SupportProperties();
    private final JavaMailSender sender=mock(JavaMailSender.class);
    private SupportMailWorker worker(boolean withSender) {
        var beans=new DefaultListableBeanFactory();if(withSender)beans.registerSingleton("supportSender",sender);
        var transactions=mock(PlatformTransactionManager.class);when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new SupportMailWorker(mails,config,beans.getBeanProvider(JavaMailSender.class),transactions);
    }
    private SupportMail pending() {
        config.setEnabled(true);config.setNotifyEmail("admin@example.invalid");config.setMailFrom("support@example.invalid");
        var mail=new SupportMail();mail.setId(1L);mail.setTicketId(9L);mail.setMessageId(3L);mail.setNextAttemptAt(Instant.now().minusSeconds(5));
        when(mails.findTop20ByDeliveredAtIsNullAndAttemptsLessThanAndNextAttemptAtBeforeOrderById(eq(8),any())).thenReturn(List.of(mail));
        when(mails.lockById(1L)).thenReturn(Optional.of(mail));return mail;
    }
    @Test void disabledOrUnconfiguredMailNeverSends() {
        worker(true).deliver();verifyNoInteractions(mails,sender);
        config.setEnabled(true);config.setNotifyEmail("admin@example.invalid");config.setMailFrom("support@example.invalid");worker(false).deliver();verifyNoInteractions(mails,sender);
    }
    @Test void successfulMailOnlyContainsTicketReferenceAndNoUserContent() {
        var mail=pending();worker(true).deliver();assertNotNull(mail.getDeliveredAt());assertEquals(1,mail.getAttempts());
        var sent=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(sender).send(sent.capture());
        assertEquals("Pusula destek: DST-000009",sent.getValue().getSubject());assertFalse(sent.getValue().getText().contains("Bağlantı sorunu"));verify(mails).save(mail);
    }
    @Test void smtpFailureKeepsQueueForBoundedRetry() {
        var mail=pending();doThrow(new MailSendException("test-only")).when(sender).send(any(SimpleMailMessage.class));
        worker(true).deliver();assertNull(mail.getDeliveredAt());assertEquals(1,mail.getAttempts());assertTrue(mail.getNextAttemptAt().isAfter(Instant.now()));verify(mails).save(mail);
    }
    @Test void alreadyDeliveredRowIsNotSentAgain() {
        var mail=pending();mail.setDeliveredAt(Instant.now());worker(true).deliver();verifyNoInteractions(sender);verify(mails,never()).save(any());
    }
    @Test void exhaustedRowFromStaleDueListIsNotSentAgain() {
        var mail=pending();mail.setAttempts(8);worker(true).deliver();verifyNoInteractions(sender);verify(mails,never()).save(any());
    }
}
