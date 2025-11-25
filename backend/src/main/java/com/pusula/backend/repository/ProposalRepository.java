package com.pusula.backend.repository;

import com.pusula.backend.entity.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Long> {
    List<Proposal> findByCustomerId(Long customerId);

    List<Proposal> findByCompanyId(Long companyId);
}
