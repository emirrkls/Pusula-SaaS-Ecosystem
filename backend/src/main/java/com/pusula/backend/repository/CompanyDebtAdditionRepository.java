package com.pusula.backend.repository;

import com.pusula.backend.entity.CompanyDebtAddition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyDebtAdditionRepository extends JpaRepository<CompanyDebtAddition, Long> {
    List<CompanyDebtAddition> findByDebtIdAndCompanyIdOrderByAdditionDateAscIdAsc(Long debtId, Long companyId);

    List<CompanyDebtAddition> findByCompanyIdAndDebtIdInOrderByAdditionDateAscIdAsc(
            Long companyId, List<Long> debtIds);

    boolean existsByDebtIdAndCompanyId(Long debtId, Long companyId);
}
