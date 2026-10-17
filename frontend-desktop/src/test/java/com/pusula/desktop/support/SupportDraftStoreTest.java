package com.pusula.desktop.support;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
class SupportDraftStoreTest {
    @TempDir Path directory;
    @Test void frozenDraftIsEncryptedAndCanBeRestoredWithoutChangingItsKey()throws Exception{
        assumeTrue(com.sun.jna.Platform.isWindows());var store=new SupportDraftStore(directory);var key=SupportDraftStore.accountKey(1L,"a@example.invalid");
        var draft=new SupportModels.Draft();draft.body="private-draft-content";draft.frozen=true;store.save(key,draft);
        var restored=store.load(key);assertEquals(draft.clientKey,restored.clientKey);assertTrue(restored.frozen);assertEquals(draft.body,restored.body);
        assertFalse(new String(Files.readAllBytes(directory.resolve(key+".bin")),java.nio.charset.StandardCharsets.ISO_8859_1).contains(draft.body));
        var other=SupportDraftStore.accountKey(1L,"b@example.invalid");assertEquals("",store.load(other).body);
        store.clear(key);assertFalse(Files.exists(directory.resolve(key+".bin")));
    }
    @Test void pathTraversalIsRejected(){assertThrows(IllegalArgumentException.class,()->new SupportDraftStore(directory).load("../../other"));}
    @Test void subjectOnlyOrAttachmentOnlyDraftHasContent(){var draft=new SupportModels.Draft();assertFalse(draft.hasContent());draft.subject="Soru";assertTrue(draft.hasContent());draft.subject="";draft.attachments.add(new SupportModels.Upload("application/json","e30="));assertTrue(draft.hasContent());}
    @Test void payloadCannotInventTenantOrInternalNote(){var draft=new SupportModels.Draft();assertFalse(draft.payload().containsKey("companyId"));assertFalse(draft.payload().containsKey("userId"));draft.ticketId=1L;assertEquals(false,draft.payload().get("internalNote"));assertFalse(draft.payload().containsKey("status"));}
}
