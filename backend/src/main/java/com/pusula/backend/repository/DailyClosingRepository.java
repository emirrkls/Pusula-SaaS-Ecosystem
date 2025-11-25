package com.pusula.backend.repository;

import com.pusula.backend.entity.DailyClosing;
import com.pusula.backend.entity.DailyClosing.ClosingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DailyClosingRepository extends JpaRepository<DailyClosing, Long> {

    Optional<DailyClosing> findByCompanyIdAndDate(Long companyId, LocalDate date);

    boolean existsByCompanyIdAndDateAndStatus(Long companyId, LocalDate date, ClosingStatus status);
}
