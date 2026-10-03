package com.pusula.backend.service;

import com.pusula.backend.entity.SocialAuthIdentity;
import com.pusula.backend.repository.SocialAuthIdentityRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SocialAccountDeletionServiceTest {
    private final SocialAuthIdentityRepository identities = mock(SocialAuthIdentityRepository.class);
    private final AppleSignInOAuthClient apple = mock(AppleSignInOAuthClient.class);
    private final SocialAccountDeletionService service = new SocialAccountDeletionService(identities, apple);

    @Test void onlyRevokesAppleTokenAndOnlyRemovesTheRequestedUsersBindings() {
        var appleIdentity = identity("APPLE", "encrypted"); var google = identity("GOOGLE", null);
        when(identities.findByUserId(7L)).thenReturn(List.of(appleIdentity, google));
        service.revokeAndRemove(7L);
        var order = inOrder(apple, identities);
        order.verify(identities).findByUserId(7L); order.verify(apple).revoke("encrypted");
        order.verify(identities).delete(appleIdentity); order.verify(identities).delete(google);
        verifyNoMoreInteractions(apple);
    }

    @Test void revokeFailureDoesNotDeleteAppleBinding() {
        var identity = identity("APPLE", "encrypted");
        when(identities.findByUserId(7L)).thenReturn(List.of(identity));
        doThrow(new IllegalStateException("unavailable")).when(apple).revoke("encrypted");
        assertThrows(IllegalStateException.class, () -> service.revokeAndRemove(7L));
        verify(identities, never()).delete(any());
    }

    private SocialAuthIdentity identity(String provider, String token) {
        var identity = new SocialAuthIdentity(); identity.setProvider(provider); identity.setRefreshTokenCiphertext(token);
        return identity;
    }
}
