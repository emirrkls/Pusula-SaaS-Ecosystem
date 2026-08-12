package com.pusula.backend.repository;

import com.pusula.backend.entity.FixedExpenseDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FixedExpenseDefinitionRepository extends JpaRepository<FixedExpenseDefinition, Long> {
    List<FixedExpenseDefinition> findByCompanyId(Long companyId);
    Optional<FixedExpenseDefinition> findByIdAndCompanyId(Long id, Long companyId);
}
