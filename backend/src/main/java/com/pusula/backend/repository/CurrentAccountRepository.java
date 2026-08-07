package com.pusula.backend.repository;

import com.pusula.backend.entity.CurrentAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurrentAccountRepository extends JpaRepository<CurrentAccount, Long> {

    List<CurrentAccount> findByCompanyId(Long companyId);

    Optional<CurrentAccount> findByCustomerId(Long customerId);

    Optional<CurrentAccount> findByCustomerIdAndCompanyId(Long customerId, Long companyId);

    Optional<CurrentAccount> findByIdAndCompanyId(Long id, Long companyId);

    List<CurrentAccount> findByCompanyIdOrderByBalanceDesc(Long companyId);
}
