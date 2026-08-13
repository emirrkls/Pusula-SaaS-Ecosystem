package com.pusula.backend.repository;

import com.pusula.backend.entity.CurrentAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurrentAccountRepository extends JpaRepository<CurrentAccount, Long> {

    List<CurrentAccount> findByCompanyId(Long companyId);

    Optional<CurrentAccount> findByCustomerId(Long customerId);

    Optional<CurrentAccount> findByCustomerIdAndCompanyId(Long customerId, Long companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM CurrentAccount a WHERE a.customer.id = :customerId AND a.companyId = :companyId")
    Optional<CurrentAccount> findByCustomerIdAndCompanyIdForUpdate(
            @Param("customerId") Long customerId, @Param("companyId") Long companyId);

    Optional<CurrentAccount> findByIdAndCompanyId(Long id, Long companyId);

    List<CurrentAccount> findByCompanyIdOrderByBalanceDesc(Long companyId);
}
