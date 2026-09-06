package com.pusula.backend.repository;

import com.pusula.backend.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Company c where c.id=:id")
    Optional<Company> lockById(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<Company> findByOrgCode(String orgCode);
    Optional<Company> findByOrgCodeIgnoreCase(String orgCode);
    Optional<Company> findBySubscriptionProviderAndExternalSubscriptionId(
            String subscriptionProvider,
            String externalSubscriptionId);
    long countBySubscriptionStatus(String subscriptionStatus);
    long countByIsReadOnlyTrue();
}
