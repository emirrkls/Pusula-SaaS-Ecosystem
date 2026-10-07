package com.pusula.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountSignatureErasureTest {
    @TempDir Path root;
    @Test void deletesOnlyTheExactUsersSignatureAndIsIdempotent() throws IOException {
        var service = new FileUploadService(mock(FeatureService.class), root.toString());
        var selected = root.resolve("signatures/9/signature.png");
        var other = root.resolve("signatures/10/signature.png");
        Files.createDirectories(selected.getParent()); Files.createDirectories(other.getParent());
        Files.write(selected, new byte[]{1}); Files.write(other, new byte[]{2});
        service.deleteUserSignature(9L, "signatures/9/signature.png");
        service.deleteUserSignature(9L, "signatures/9/signature.png");
        assertFalse(Files.exists(selected)); assertTrue(Files.exists(other));
    }
    @Test void rejectsTraversalOtherUserAndBusinessFiles() {
        var service = new FileUploadService(mock(FeatureService.class), root.toString());
        for (String path : new String[]{"../private-key", "signatures/10/signature.png", "service-photos/9/photo.png"})
            assertThrows(IllegalArgumentException.class, () -> service.deleteUserSignature(9L, path));
    }
}
