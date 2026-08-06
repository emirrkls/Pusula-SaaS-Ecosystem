package com.pusula.backend.repository;

import com.pusula.backend.entity.CompanyDebtPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyDebtPaymentRepository extends JpaRepository<CompanyDebtPayment, Long> {
    List<CompanyDebtPayment> findByDebtIdAndCompanyIdOrderByPaymentDateAscIdAsc(Long debtId, Long companyId);

    Optional<CompanyDebtPayment> findByIdAndDebtIdAndCompanyId(Long id, Long debtId, Long companyId);

    boolean existsByExpenseId(Long expenseId);

    boolean existsByDebtIdAndCompanyId(Long debtId, Long companyId);
}
