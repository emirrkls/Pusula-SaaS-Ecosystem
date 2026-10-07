package com.pusula.backend.repository;

import com.pusula.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id=:id and "
            + "(u.companyId=:companyId or (:companyId is null and u.companyId is null))")
    Optional<User> lockByIdAndCompanyId(@org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("companyId") Long companyId);
    Optional<User> findByUsername(String username);

    List<User> findAllByUsername(String username);
    List<User> findAllByUsernameIgnoreCase(String username);

    /**
     * Company-scoped user lookup — prevents cross-tenant username collisions.
     * Used primarily in the corporate (B2B) authentication flow where org_code
     * is resolved to company_id first.
     */
    Optional<User> findByUsernameAndCompanyId(String username, Long companyId);

    Optional<User> findByIdAndCompanyId(Long id, Long companyId);

    List<User> findByCompanyId(Long companyId);
    List<User> findByCompanyIdAndRole(Long companyId, String role);

    List<User> findByCompanyIdInAndRoleOrderByIdAsc(List<Long> companyIds, String role);

    List<User> findByRoleIn(List<String> roles);

    Optional<User> findFirstByCompanyIdAndRoleOrderByIdAsc(Long companyId, String role);

    long countByCompanyIdAndRole(Long companyId, String role);
}
