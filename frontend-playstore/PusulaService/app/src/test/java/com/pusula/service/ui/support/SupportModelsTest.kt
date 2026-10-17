package com.pusula.service.ui.support
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
class SupportModelsTest {
    @Test fun subjectOnlyOrAttachmentOnlyDraftHasContent(){assertFalse(SupportDraft().hasContent);assertTrue(SupportDraft(subject="Soru").hasContent);assertTrue(SupportDraft(attachments=listOf(SupportUpload("application/json","e30="))).hasContent)}
    @Test fun frozenDraftRestoresTheSameKeyAndAttachment(){val draft=SupportDraft(body="İşlem tamamlanmadı",frozen=true,attachments=listOf(SupportUpload("application/json","e30=")));val gson=Gson();val restored=gson.fromJson(gson.toJson(draft),SupportDraft::class.java);assertEquals(draft,restored);assertEquals(draft.clientKey,restored.clientKey)}
    @Test fun editingOrCopyingDoesNotInventANewReplayKey(){val draft=SupportDraft();assertEquals(draft.clientKey,draft.copy(frozen=true).clientKey);assertNotEquals(draft.clientKey,SupportDraft().clientKey)}
    @Test fun backendDetailsPreserveStatusAndPrivateAttachmentMetadata(){val detail=Gson().fromJson("""{"ticket":{"id":1,"number":"DST-000001","subject":"TLS","category":"BUG","status":"WAITING_USER","unread":true,"updatedAt":"2026-10-17T00:00:00Z"},"messages":[{"id":2,"body":"Yanıt","staff":true,"createdAt":"2026-10-17T00:00:00Z","attachments":[{"id":3,"fileName":"baglanti-raporu.json","mediaType":"application/json","size":123}]}],"nextBefore":null}""",SupportDetail::class.java);assertEquals("Yanıtınız bekleniyor",supportLabel(detail.ticket.status));assertEquals(3L,detail.messages.first().attachments.first().id);assertNull(detail.nextBefore)}
}
