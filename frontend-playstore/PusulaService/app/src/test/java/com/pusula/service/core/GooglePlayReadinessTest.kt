package com.pusula.service.core

import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking

class GooglePlayReadinessTest {
    @Test fun billingPreflightParsesExactBackendContract() {
        val status=com.google.gson.Gson().fromJson("""{"ready":true,"purchaseAllowed":true,"plan":"PATRON","receiptHash":"hash","basePlanId":"patron-monthly","scheduledPlan":"USTA"}""",
            GoogleBillingStatus::class.java)
        assertTrue(status.ready); assertTrue(status.purchaseAllowed); assertEquals("hash",status.receiptHash)
        assertEquals("patron-monthly",status.basePlanId); assertEquals("USTA",status.scheduledPlan)
    }
    @Test fun deferredVerificationParsesTheActualAndScheduledPlanSeparately() {
        val result=com.google.gson.Gson().fromJson("""{"verified":true,"plan":"PATRON","status":"processed","subscriptionId":"hash","scheduledPlan":"USTA"}""",
            GooglePurchaseVerification::class.java)
        assertTrue(result.verified); assertEquals("PATRON",result.plan); assertEquals("USTA",result.scheduledPlan)
    }
    private fun offer(id: String = "usta", base: String = "monthly", period: String = "P1M",
                      token: String = "offer", intro: String? = null, price: Long = 79990000) =
        PlayOffer(id, base, intro, token, listOf(PlayPricePhase("₺79,99", price, period, "TRY", true)))

    @Test fun preservesGooglesLocalizedPriceAndPeriod() {
        val option = GooglePlayCatalog.options(listOf(offer())).single()
        assertEquals("₺79,99", option.formattedPrice)
        assertEquals(BillingPeriod.MONTHLY, option.period)
        assertEquals("TRY", option.currency)
    }
    @Test fun annualAndMonthlyPlansAreExplicitlySeparate() {
        val options = GooglePlayCatalog.options(listOf(offer(), offer(base="annual", period="P1Y")))
        assertEquals(setOf(BillingPeriod.MONTHLY, BillingPeriod.YEARLY), options.map { it.period }.toSet())
    }
    @Test fun ambiguousBasePlansForTheSamePeriodAreNotPickedArbitrarily() {
        assertTrue(GooglePlayCatalog.options(listOf(offer(base="a"), offer(base="b"))).isEmpty())
    }
    @Test fun trialOffersAreNotMisrepresentedAsTheStandardRecurringPrice() {
        assertTrue(GooglePlayCatalog.options(listOf(offer(intro="trial"))).isEmpty())
    }
    @Test fun zeroPriceUnknownProductsAndMissingOfferTokensDisablePurchasing() {
        assertTrue(GooglePlayCatalog.options(listOf(offer(price=0), offer(id="unknown"), offer(token=""))).isEmpty())
    }
    @Test fun unsupportedBillingPeriodsAreNotAssumedToBeMonthly() {
        assertTrue(GooglePlayCatalog.options(listOf(offer(period="P3M"))).isEmpty())
    }
    @Test fun emptyGoogleCatalogDoesNotInventPrices() { assertTrue(GooglePlayCatalog.options(emptyList()).isEmpty()) }
    @Test fun tenantBindingIsStableAndTenantSpecificWithoutExposingTheRawId() {
        assertEquals(64, GooglePlayCatalog.accountBinding(10).length)
        assertEquals(GooglePlayCatalog.accountBinding(10), GooglePlayCatalog.accountBinding(10))
        assertNotEquals(GooglePlayCatalog.accountBinding(10), GooglePlayCatalog.accountBinding(11))
    }
    @Test fun http200WithVerifiedFalseIsNeverAcknowledged() = runBlocking {
        var acknowledged = false; var granted = false
        val result = GooglePurchaseProcessor().process("USTA", false, { true },
            { GooglePurchaseVerification(verified=false) }, { granted=true }, { acknowledged=true; true })
        assertEquals(ReceiptProcessingResult.REJECTED, result)
        assertFalse(acknowledged); assertFalse(granted)
    }
    @Test fun verificationPrecedesEntitlementAndAcknowledgement() = runBlocking {
        val events = mutableListOf<String>()
        val result = GooglePurchaseProcessor().process("USTA", false, { true },
            { events += "verify"; verified() }, { events += "entitlement" }, { events += "ack"; true })
        assertEquals(ReceiptProcessingResult.VERIFIED, result)
        assertEquals(listOf("verify", "entitlement", "ack"), events)
    }
    @Test fun aChangedAccountCannotGrantOrAcknowledgeTheOldReceipt() = runBlocking {
        var same = true; var acknowledged = false
        val result = GooglePurchaseProcessor().process("USTA", false, { same },
            { same=false; verified() }, { fail("Wrong account entitlement") }, { acknowledged=true; true })
        assertEquals(ReceiptProcessingResult.ACCOUNT_CHANGED, result); assertFalse(acknowledged)
    }
    @Test fun aVerificationOutageDoesNotAcknowledge() = runBlocking {
        var acknowledged = false
        try {
            GooglePurchaseProcessor().process("USTA", false, { true },
                { throw java.io.IOException("offline") }, { fail("Not verified") }, { acknowledged=true; true })
            fail("Must propagate the transport failure")
        } catch (_: java.io.IOException) { }
        assertFalse(acknowledged)
    }
    @Test fun wrongPlanCannotBeGranted() = runBlocking {
        assertEquals(ReceiptProcessingResult.REJECTED, GooglePurchaseProcessor().process("PATRON", false,
            { true }, { verified() }, { fail("Wrong plan") }, { fail("Wrong plan ack"); true }))
    }
    @Test fun alreadyAcknowledgedPurchasesCanBeRestoredWithoutAcknowledgingAgain() = runBlocking {
        assertEquals(ReceiptProcessingResult.VERIFIED, GooglePurchaseProcessor().process("USTA", true,
            { true }, { verified() }, {}, { fail("Must not acknowledge twice"); true }))
    }
    @Test fun acknowledgementOutageRemainsPendingInsteadOfPretendingSuccess() = runBlocking {
        assertEquals(ReceiptProcessingResult.ACK_PENDING, GooglePurchaseProcessor().process("USTA", false,
            { true }, { verified() }, {}, { false }))
    }
    @Test fun policyLinksUseOnlyTheProductDomainAndDeletionNoticePreservesSharedRecords() {
        for (url in listOf(ProductLegal.PRIVACY_URL, ProductLegal.TERMS_URL, ProductLegal.SUPPORT_URL)) {
            assertEquals("pusulaiklimlendirme.store", java.net.URI(url).host)
            assertEquals("https", java.net.URI(url).scheme)
        }
        assertTrue(ProductLegal.DELETE_NOTICE.contains("Ortak işletme"))
        assertTrue(ProductLegal.DELETE_NOTICE.contains("aboneliğini iptal etmez"))
        assertFalse(ProductLegal.DELETE_NOTICE.contains("tüm verilerinizi"))
    }
    private fun verified() = GooglePurchaseVerification(true, false, "USTA", "verified-receipt", "processed")
}
