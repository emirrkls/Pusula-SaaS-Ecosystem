package com.pusula.backend.network;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;

public interface NetworkOrderRepository extends JpaRepository<NetworkOrder, Long>, JpaSpecificationExecutor<NetworkOrder> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from NetworkOrder o where o.id=:id and (o.parentCompanyId=:company or o.childCompanyId=:company)")
    Optional<NetworkOrder> lockVisible(@Param("id") Long id, @Param("company") Long company);
    Optional<NetworkOrder> findByParentCompanyIdAndRequestKey(Long company, String key);
    Optional<NetworkOrder> findByAcceptedTicketIdAndChildCompanyId(Long ticketId, Long companyId);
    long countByParentCompanyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long company, LocalDateTime from, LocalDateTime to);
    List<NetworkOrder> findByMembershipIdAndStatusIn(Long membership, Collection<NetworkOrder.Status> statuses);
}
