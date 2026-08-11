package com.pusula.backend.repository;

import com.pusula.backend.entity.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Long> {
    List<Proposal> findByCustomerId(Long customerId);

    List<Proposal> findByCompanyId(Long companyId);

    Optional<Proposal> findByIdAndCompanyId(Long id, Long companyId);
}
