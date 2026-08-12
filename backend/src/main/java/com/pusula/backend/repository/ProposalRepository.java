package com.pusula.backend.repository;

import com.pusula.backend.entity.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Long> {
    List<Proposal> findByCustomerId(Long customerId);

    List<Proposal> findByCompanyId(Long companyId);

    Optional<Proposal> findByIdAndCompanyId(Long id, Long companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Proposal p where p.id = :id and p.companyId = :companyId")
    Optional<Proposal> findByIdAndCompanyIdForUpdate(@Param("id") Long id,
                                                      @Param("companyId") Long companyId);
}
