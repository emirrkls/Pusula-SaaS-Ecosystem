package com.pusula.service.core

import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking

class GoogleReplacementPolicyTest {
    private fun status(plan: String="USTA",base: String="usta-monthly",scheduled: String?=null)=
        GoogleBillingStatus(true,true,null,plan,GooglePlayCatalog.receiptHash("receipt"),base,scheduled)
    private fun owned(product: String="usta",pending: Boolean=false,binding: String?=GooglePlayCatalog.accountBinding(10))=
        OwnedPlaySubscription("receipt",listOf(product),pending,!pending,binding)
    private fun target(product: String="patron",base: String="patron-monthly")=
        PlayPlanOption(product,base,"offer",BillingPeriod.MONTHLY,"₺149,99","TRY")
    @Test fun aNewSubscriptionRequiresReadyAuthoritativeServer() {
        assertFalse(GoogleReplacementPolicy.decide(10,GoogleBillingStatus(),emptyList(),target()).allowed)
        assertTrue(GoogleReplacementPolicy.decide(10,status(),emptyList(),target()).allowed)
    }
    @Test fun upgradeUsesExistingTokenAndTimeCreditNotParallelPurchase() {
        val r=GoogleReplacementPolicy.decide(10,status(),listOf(owned()),target())
        assertTrue(r.allowed); assertEquals("receipt",r.oldToken); assertEquals(PlayChangeMode.TIME_CREDIT,r.mode)
    }
    @Test fun downgradeIsDeferredUntilPaidPeriodEnds() {
        assertEquals(PlayChangeMode.DEFERRED,GoogleReplacementPolicy.decide(10,status("PATRON","patron-yearly"),
            listOf(owned("patron")),target("usta","usta-monthly")).mode)
    }
    @Test fun sameProductPeriodChangeUsesNextRenewalMode() {
        assertEquals(PlayChangeMode.NEXT_RENEWAL,GoogleReplacementPolicy.decide(10,status(),listOf(owned()),target("usta","usta-yearly")).mode)
    }
    @Test fun sameBasePlanDoesNotOpenAnotherPurchase() {
        assertEquals("already_active",GoogleReplacementPolicy.decide(10,status(),listOf(owned()),target("usta","usta-monthly")).reason)
    }
    @Test fun missingOrWrongOwnershipRequiresRestoreBeforePurchase() {
        assertFalse(GoogleReplacementPolicy.decide(10,status().copy(receiptHash="wrong"),listOf(owned()),target()).allowed)
        assertFalse(GoogleReplacementPolicy.decide(10,status(),listOf(owned(binding=GooglePlayCatalog.accountBinding(11))),target()).allowed)
        assertFalse(GoogleReplacementPolicy.decide(10,status().copy(basePlanId=null),listOf(owned()),target()).allowed)
    }
    @Test fun multipleSubscriptionsAndPendingPurchaseAreNotArbitrarilySelected() {
        assertFalse(GoogleReplacementPolicy.decide(10,status(),listOf(owned(),owned()),target()).allowed)
        assertFalse(GoogleReplacementPolicy.decide(10,status(),listOf(owned(pending=true)),target()).allowed)
    }
    @Test fun anotherProviderOrRestrictedAccountCannotStartGooglePurchase() {
        assertFalse(GoogleReplacementPolicy.decide(10,status().copy(purchaseAllowed=false,reason="another_provider_active"),emptyList(),target()).allowed)
    }
    @Test fun plannedDowngradeCannotBeStackedWithAnotherChange() {
        assertFalse(GoogleReplacementPolicy.decide(10,status("PATRON","patron-monthly","USTA"),listOf(owned("patron")),target()).allowed)
    }
    @Test fun deferredPurchaseAcknowledgesOnlyTheServerVerifiedCurrentEntitlement()=runBlocking {
        var granted=false; var acknowledged=false
        val result=GooglePurchaseProcessor().process("USTA",false,{true},
            {GooglePurchaseVerification(true,false,"PATRON","new-receipt","processed","USTA")},
            {granted=true},{acknowledged=true;true})
        assertEquals(ReceiptProcessingResult.VERIFIED,result); assertTrue(granted); assertTrue(acknowledged)
    }
    @Test fun unsupportedServerPlanIsNeverGrantedEvenWithAScheduledMatch()=runBlocking {
        assertEquals(ReceiptProcessingResult.REJECTED,GooglePurchaseProcessor().process("USTA",false,{true},
            {GooglePurchaseVerification(true,false,"UNKNOWN","receipt","processed","USTA")},
            {fail("unverified entitlement")},{fail("unverified ack");true}))
    }
}
