package com.pusula.backend.repository;

import com.pusula.backend.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByCompanyId(Long companyId);

    List<Expense> findByCompanyIdAndDateBetween(Long companyId, LocalDate startDate, LocalDate endDate);

    Optional<Expense> findByIdAndCompanyId(Long id, Long companyId);
}
