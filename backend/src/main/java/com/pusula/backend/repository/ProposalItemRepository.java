package com.pusula.backend.repository;

import com.pusula.backend.entity.ProposalItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProposalItemRepository extends JpaRepository<ProposalItem, Long> {
}
