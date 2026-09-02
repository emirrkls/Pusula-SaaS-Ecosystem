package com.pusula.backend.repository;

import com.pusula.backend.entity.CurrentAccountTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CurrentAccountTransactionRepository extends JpaRepository<CurrentAccountTransaction, Long> {
    List<CurrentAccountTransaction> findByCurrentAccountIdAndCompanyIdOrderByEffectiveDateAscCreatedAtAscIdAsc(
            Long currentAccountId, Long companyId);
}
