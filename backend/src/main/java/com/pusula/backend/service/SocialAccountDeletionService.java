package com.pusula.backend.service;

import com.pusula.backend.repository.SocialAuthIdentityRepository;
import org.springframework.stereotype.Service;

@Service
public class SocialAccountDeletionService {
    private final SocialAuthIdentityRepository identities;
    private final AppleSignInOAuthClient apple;
    public SocialAccountDeletionService(SocialAuthIdentityRepository identities, AppleSignInOAuthClient apple) {
        this.identities = identities; this.apple = apple;
    }
    public void revokeAndRemove(Long userId) {
        for (var identity : identities.findByUserId(userId)) {
            if ("APPLE".equals(identity.getProvider()) && identity.getRefreshTokenCiphertext() != null)
                apple.revoke(identity.getRefreshTokenCiphertext());
            identities.delete(identity);
        }
    }
}
