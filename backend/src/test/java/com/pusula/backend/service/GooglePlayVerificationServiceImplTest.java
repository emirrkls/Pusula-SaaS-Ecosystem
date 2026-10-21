package com.pusula.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GooglePlayVerificationServiceImplTest {
    private MockRestServiceServer server;
    private GooglePlayVerificationServiceImpl verifier;
    private static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");

    @BeforeEach void setup() {
        var rest = new RestTemplate();
        server = MockRestServiceServer.bindTo(rest).build();
        verifier = new GooglePlayVerificationServiceImpl("com.pusula.service",
                new GooglePlayAccessTokenProvider("", "test-access-token"), rest,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
    private GooglePlayVerificationService.GoogleVerificationResult respond(String state, String expiry, String product) {
        String body = "{\"subscriptionState\":\"" + state + "\",\"externalAccountIdentifiers\":{"
                + "\"obfuscatedExternalAccountId\":\"tenant-binding\"},\"lineItems\":[{\"productId\":\""
                + product + "\",\"expiryTime\":\"" + expiry + "\"}]}";
        server.expect(requestTo("https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
                + "com.pusula.service/purchases/subscriptionsv2/tokens/receipt"))
                .andExpect(header("Authorization", "Bearer test-access-token"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var result = verifier.verifySubscription("receipt", "usta");
        server.verify();
        return result;
    }
    @Test void validSubscriptionUsesGooglesActualExpiry() {
        var result = respond("SUBSCRIPTION_STATE_ACTIVE", "2027-10-21T00:00:00Z", "usta");
        assertTrue(result.valid());
        assertEquals(Instant.parse("2027-10-21T00:00:00Z"), result.expiresAt());
        assertEquals(GooglePlayAccountBinding.sha256("receipt"), result.subscriptionId());
        assertEquals("tenant-binding", result.obfuscatedAccountId());
    }
    @Test void expiredEvenWhenGoogleStateIsActive() {
        assertFalse(respond("SUBSCRIPTION_STATE_ACTIVE", "2026-10-20T00:00:00Z", "usta").valid());
    }
    @Test void expiryExactlyNowIsNotEntitled() {
        assertFalse(respond("SUBSCRIPTION_STATE_ACTIVE", NOW.toString(), "usta").valid());
    }
    @Test void canceledStillHasAccessUntilPaidExpiry() {
        assertTrue(respond("SUBSCRIPTION_STATE_CANCELED", "2026-10-22T00:00:00Z", "usta").valid());
    }
    @Test void googleGracePeriodStillHasAccess() {
        assertTrue(respond("SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "2026-10-22T00:00:00Z", "usta").valid());
    }
    @Test void onHoldIsNotEntitledEvenWithFutureExpiry() {
        assertFalse(respond("SUBSCRIPTION_STATE_ON_HOLD", "2026-10-22T00:00:00Z", "usta").valid());
    }
    @Test void pendingIsNotEntitled() {
        assertFalse(respond("SUBSCRIPTION_STATE_PENDING", "2026-10-22T00:00:00Z", "usta").valid());
    }
    @Test void unrelatedProductCannotBeUsed() {
        assertFalse(respond("SUBSCRIPTION_STATE_ACTIVE", "2026-10-22T00:00:00Z", "patron").valid());
    }
    @Test void invalidExpiryFailsClosed() {
        assertFalse(respond("SUBSCRIPTION_STATE_ACTIVE", "invalid", "usta").valid());
    }
    @Test void permissionOrProviderOutageIsRetryableAndDoesNotExposeResponseBody() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN).body("secret-receipt"));
        var result = verifier.verifySubscription("receipt", "usta");
        assertFalse(result.valid());
        assertTrue(result.retryable());
        assertEquals("PLAY_HTTP_403", result.reason());
    }
    @Test void missingCredentialsDoesNotAttemptGoogleRequest() {
        var rest = new RestTemplate();
        var unconfigured = new GooglePlayVerificationServiceImpl("com.pusula.service",
                new GooglePlayAccessTokenProvider("", ""), rest, Clock.systemUTC());
        assertTrue(unconfigured.verifySubscription("receipt", "usta").retryable());
    }
    @Test void notificationDiscoversActualProductFromGoogle() {
        server.expect(anything()).andRespond(withSuccess("{\"subscriptionState\":\"SUBSCRIPTION_STATE_ACTIVE\","
                + "\"lineItems\":[{\"productId\":\"patron\",\"expiryTime\":\"2027-10-21T00:00:00Z\"}]}", MediaType.APPLICATION_JSON));
        var result = verifier.verifySubscription("receipt", null);
        assertTrue(result.valid()); assertEquals("patron", result.productId());
    }
    @Test void ambiguousMultiProductNotificationDoesNotGrantOrRevoke() {
        server.expect(anything()).andRespond(withSuccess("{\"subscriptionState\":\"SUBSCRIPTION_STATE_ACTIVE\","
                + "\"lineItems\":[{\"productId\":\"patron\",\"expiryTime\":\"2027-10-21T00:00:00Z\"},"
                + "{\"productId\":\"usta\",\"expiryTime\":\"2027-10-21T00:00:00Z\"}]}", MediaType.APPLICATION_JSON));
        var result = verifier.verifySubscription("receipt", null);
        assertFalse(result.valid()); assertNull(result.expiresAt());
    }
    @Test void unknownProviderStateIsRetryableNotAnAuthoritativeExpiry() {
        var result = respond("SUBSCRIPTION_STATE_NEW_UNKNOWN", "2027-10-21T00:00:00Z", "usta");
        assertTrue(result.retryable()); assertNull(result.state()); assertNull(result.expiresAt());
    }
    private GooglePlayVerificationService.GoogleVerificationResult snapshot(String lines, String linked) {
        server.expect(anything()).andRespond(withSuccess("{\"subscriptionState\":\"SUBSCRIPTION_STATE_ACTIVE\","
                + "\"acknowledgementState\":\"ACKNOWLEDGEMENT_STATE_PENDING\",\"linkedPurchaseToken\":\""+linked+"\",\"lineItems\":["+lines+"]}",MediaType.APPLICATION_JSON));
        return verifier.verifySubscription("receipt",null);
    }
    @Test void deferredDowngradeKeepsThePaidOldEntitlementAndUpcomingPlanSeparate() {
        var r=snapshot("{\"productId\":\"patron\",\"expiryTime\":\"2026-10-22T00:00:00Z\",\"offerDetails\":{\"basePlanId\":\"patron-monthly\"},\"deferredItemReplacement\":{\"productId\":\"usta\"}},"
                + "{\"productId\":\"usta\",\"offerDetails\":{\"basePlanId\":\"usta-yearly\"}}","old-token");
        assertTrue(r.valid()); assertEquals("patron",r.productId()); assertEquals("usta",r.deferredProductId());
        assertEquals("patron-monthly",r.basePlanId()); assertEquals(GooglePlayAccountBinding.sha256("old-token"),r.linkedTokenHash());
    }
    @Test void deferredRenewalUsesNewActiveItemNotTheExpiredOldItem() {
        var r=snapshot("{\"productId\":\"patron\",\"expiryTime\":\"2026-10-20T00:00:00Z\"},"
                + "{\"productId\":\"usta\",\"expiryTime\":\"2027-10-21T00:00:00Z\",\"offerDetails\":{\"basePlanId\":\"usta-yearly\"}}","old-token");
        assertTrue(r.valid()); assertEquals("usta",r.productId()); assertNull(r.deferredProductId());
    }
    @Test void twoItemsWithoutAnAuthoritativeLinkAreNotAssumedToBeReplacement() {
        assertFalse(snapshot("{\"productId\":\"patron\",\"expiryTime\":\"2026-10-22T00:00:00Z\",\"deferredItemReplacement\":{\"productId\":\"usta\"}},"
                + "{\"productId\":\"usta\"}","").valid());
    }
    @Test void unsupportedBasePlanAndSelfLinkedTokenFailClosed() {
        assertFalse(snapshot("{\"productId\":\"usta\",\"expiryTime\":\"2026-10-22T00:00:00Z\",\"offerDetails\":{\"basePlanId\":\"unknown\"}}","old-token").valid());
    }
}
