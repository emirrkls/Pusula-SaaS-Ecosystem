package com.pusula.backend.repository;

import com.pusula.backend.entity.CompanyDebt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyDebtRepository extends JpaRepository<CompanyDebt, Long> {

    List<CompanyDebt> findByCompanyIdAndDeletedFalse(Long companyId);

    List<CompanyDebt> findByCompanyIdAndStatusAndDeletedFalse(Long companyId, CompanyDebt.DebtStatus status);

    List<CompanyDebt> findByCompanyIdAndCreditorNameContainingIgnoreCaseAndDeletedFalse(Long companyId,
            String creditorName);

    Optional<CompanyDebt> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
