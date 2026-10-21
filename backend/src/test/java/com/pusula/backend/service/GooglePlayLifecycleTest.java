package com.pusula.backend.service;

import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GooglePlayLifecycleTest {
    final GooglePlayPurchaseRepository receipts=mock(GooglePlayPurchaseRepository.class);
    final GooglePlayReceiptStore store=mock(GooglePlayReceiptStore.class);
    final SubscriptionService subscriptions=mock(SubscriptionService.class);
    final GooglePlayAcknowledgementService ack=mock(GooglePlayAcknowledgementService.class);
    final GooglePlayLifecycleJob job=new GooglePlayLifecycleJob(store,receipts,subscriptions,ack);
    GooglePlayPurchase row(String ackState) {
        var row=new GooglePlayPurchase("hash",10L);
        row.verified("ciphertext",new GooglePlayVerificationService.GoogleVerificationResult(true,"hash","usta",null,
                Instant.now().plusSeconds(3600),"SUBSCRIPTION_STATE_ACTIVE",false,"binding",null,"usta-monthly",null,ackState),LocalDateTime.now(ZoneOffset.UTC));
        return row;
    }
    void due(GooglePlayPurchase row) {
        when(store.enabled()).thenReturn(true);
        when(receipts.findTop20ByEncryptedTokenIsNotNullAndNextCheckAtLessThanEqualOrderByNextCheckAtAsc(any())).thenReturn(List.of(row));
        when(store.token(row)).thenReturn("secret-test-receipt");
    }
    @Test void backendAcknowledgesOnlyAfterSuccessfulEntitlementReconciliation() {
        var row=row("ACKNOWLEDGEMENT_STATE_PENDING"); due(row);
        when(subscriptions.reconcileGoogleNotification("secret-test-receipt")).thenReturn("APPLIED");
        when(store.find("hash")).thenReturn(Optional.of(row)); when(ack.acknowledge("secret-test-receipt","usta")).thenReturn(true);
        job.reconcileDue(); var order=inOrder(subscriptions,ack,store);
        order.verify(subscriptions).reconcileGoogleNotification("secret-test-receipt");
        order.verify(store).find("hash"); order.verify(ack).acknowledge("secret-test-receipt","usta"); order.verify(store).acknowledged("hash");
    }
    @Test void failedVerificationDoesNotAcknowledgeAndSchedulesRetry() {
        var row=row("ACKNOWLEDGEMENT_STATE_PENDING"); due(row);
        when(subscriptions.reconcileGoogleNotification(anyString())).thenReturn("RETRY"); job.reconcileDue();
        verifyNoInteractions(ack); verify(store).deferred("hash");
    }
    @Test void staleReceiptIsRetiredWithoutAcknowledging() {
        var row=row("ACKNOWLEDGEMENT_STATE_PENDING"); due(row);
        when(subscriptions.reconcileGoogleNotification(anyString())).thenReturn("IGNORED_STALE"); job.reconcileDue();
        verifyNoInteractions(ack); verify(store).retire("hash");
    }
    @Test void alreadyAcknowledgedReceiptIsNotAcknowledgedTwice() {
        var row=row("ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"); due(row);
        when(subscriptions.reconcileGoogleNotification(anyString())).thenReturn("APPLIED");
        when(store.find("hash")).thenReturn(Optional.of(row)); job.reconcileDue(); verifyNoInteractions(ack);
    }
    @Test void acknowledgementFailureDoesNotPretendSuccess() {
        var row=row("ACKNOWLEDGEMENT_STATE_PENDING"); due(row);
        when(subscriptions.reconcileGoogleNotification(anyString())).thenReturn("APPLIED");
        when(store.find("hash")).thenReturn(Optional.of(row)); job.reconcileDue();
        verify(store).deferred("hash"); verify(store,never()).acknowledged(anyString());
    }
    @Test void disabledJobDoesNotAccessTheVaultOrGoogle() { job.reconcileDue(); verifyNoInteractions(receipts,subscriptions,ack); }
    @Test void encryptedVaultIsOptionalOnlyWhenTheFeatureIsDisabled() {
        var crypto=new GooglePlayTokenCrypto(Base64.getEncoder().encodeToString(new byte[32]));
        var vault=new GooglePlayReceiptStore(receipts,crypto,false);
        vault.remember("receipt",10L,null); verifyNoInteractions(receipts);
    }
    @Test void receiptVaultEncryptsAndPreservesOwnerAndRetirement() {
        var crypto=new GooglePlayTokenCrypto(Base64.getEncoder().encodeToString(new byte[32]));
        var vault=new GooglePlayReceiptStore(receipts,crypto,true);
        String hash=GooglePlayAccountBinding.sha256("receipt");
        when(receipts.lockById(hash)).thenReturn(Optional.empty());
        var capture=org.mockito.ArgumentCaptor.forClass(GooglePlayPurchase.class);
        var result=new GooglePlayVerificationService.GoogleVerificationResult(true,hash,"usta",null,Instant.now().plusSeconds(3600),"SUBSCRIPTION_STATE_ACTIVE",false,"binding");
        vault.remember("receipt",10L,result); verify(receipts).saveAndFlush(capture.capture());
        var saved=capture.getValue(); assertFalse(saved.getEncryptedToken().contains("receipt")); assertEquals("receipt",vault.token(saved));
        when(receipts.lockById(hash)).thenReturn(Optional.of(saved));
        assertThrows(PaymentOwnershipException.class,()->vault.remember("receipt",11L,result));
        vault.retire(hash); assertTrue(saved.isRetired()); assertNull(saved.getEncryptedToken());
        assertThrows(IllegalStateException.class,()->vault.remember("receipt",10L,result));
    }
    @Test void providerAcknowledgementUsesTheProtectedCredentialAndEmptyJsonBody() {
        var rest=new RestTemplate(); var http=MockRestServiceServer.bindTo(rest).build();
        http.expect(requestTo("https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.pusula.service/purchases/subscriptions/usta/tokens/receipt:acknowledge"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization","Bearer test-only-access-token"))
                .andExpect(content().json("{}")).andRespond(withSuccess());
        var service=new GooglePlayAcknowledgementService("com.pusula.service",new GooglePlayAccessTokenProvider("","test-only-access-token"),rest);
        assertTrue(service.acknowledge("receipt","usta")); http.verify();
    }
    @Test void acknowledgementOutageIsRetriedWithoutReturningSecretErrorBody() {
        var rest=new RestTemplate(); var http=MockRestServiceServer.bindTo(rest).build();
        http.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("private-test-receipt"));
        assertFalse(new GooglePlayAcknowledgementService("com.pusula.service",new GooglePlayAccessTokenProvider("","test"),rest).acknowledge("receipt","usta"));
    }
}
