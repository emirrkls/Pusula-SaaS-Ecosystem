package com.pusula.backend.repository;

import com.pusula.backend.entity.UsageTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface UsageTrackingRepository extends JpaRepository<UsageTracking, Long> {
    Optional<UsageTracking> findByCompanyIdAndUsageTypeAndPeriodStart(
            Long companyId, String usageType, LocalDate periodStart);
}
