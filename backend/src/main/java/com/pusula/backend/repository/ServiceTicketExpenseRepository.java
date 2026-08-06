package com.pusula.backend.repository;

import com.pusula.backend.entity.ServiceTicketExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceTicketExpenseRepository extends JpaRepository<ServiceTicketExpense, Long> {

    List<ServiceTicketExpense> findByServiceTicketId(Long serviceTicketId);

    List<ServiceTicketExpense> findByCompanyId(Long companyId);

    Optional<ServiceTicketExpense> findByIdAndServiceTicketId(Long id, Long serviceTicketId);
}
