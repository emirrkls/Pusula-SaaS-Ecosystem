package com.pusula.backend.network;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface NetworkMembershipRepository extends JpaRepository<NetworkMembership, Long>, JpaSpecificationExecutor<NetworkMembership> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from NetworkMembership m where m.id=:id and (m.parentCompanyId=:company or m.childCompanyId=:company)")
    Optional<NetworkMembership> lockVisible(@Param("id") Long id, @Param("company") Long company);
    long countByParentCompanyIdAndStatusIn(Long company, Collection<NetworkMembership.Status> statuses);
    boolean existsByChildCompanyIdAndStatusIn(Long company, Collection<NetworkMembership.Status> statuses);
    Optional<NetworkMembership> findByParentCompanyIdAndCreationRequestKey(Long company, String requestKey);
}
