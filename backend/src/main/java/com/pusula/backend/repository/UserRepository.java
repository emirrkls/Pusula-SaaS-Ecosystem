package com.pusula.backend.repository;

import com.pusula.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    /**
     * Company-scoped user lookup — prevents cross-tenant username collisions.
     * Used primarily in the corporate (B2B) authentication flow where org_code
     * is resolved to company_id first.
     */
    Optional<User> findByUsernameAndCompanyId(String username, Long companyId);

    List<User> findByCompanyId(Long companyId);

    long countByCompanyIdAndRole(Long companyId, String role);
}
