package com.pusula.backend.repository;

import com.pusula.backend.entity.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {
    List<FinancialTransaction> findByCompanyIdAndStatusAndDirectionAndEffectiveDateBetweenOrderByEffectiveDateAscIdAsc(
            Long companyId, FinancialTransaction.Status status, FinancialTransaction.Direction direction,
            LocalDate startDate, LocalDate endDate);

    List<FinancialTransaction> findByCompanyIdAndStatusAndDirection(
            Long companyId, FinancialTransaction.Status status, FinancialTransaction.Direction direction);

    List<FinancialTransaction> findByCompanyIdAndStatusAndCategory(
            Long companyId, FinancialTransaction.Status status, FinancialTransaction.Category category);

    Optional<FinancialTransaction> findByCompanyIdAndIdempotencyKey(Long companyId, String idempotencyKey);
}
