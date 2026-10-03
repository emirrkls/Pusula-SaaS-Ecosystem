package com.pusula.backend.repository;

import com.pusula.backend.entity.SocialAuthIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface SocialAuthIdentityRepository extends JpaRepository<SocialAuthIdentity, Long> {
    Optional<SocialAuthIdentity> findByProviderAndSubject(String provider, String subject);
    Optional<SocialAuthIdentity> findByUserIdAndProvider(Long userId, String provider);
    List<SocialAuthIdentity> findByUserId(Long userId);
}
