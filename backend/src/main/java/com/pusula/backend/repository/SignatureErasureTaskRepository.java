package com.pusula.backend.repository;

import com.pusula.backend.entity.SignatureErasureTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface SignatureErasureTaskRepository extends JpaRepository<SignatureErasureTask, Long> {
    List<SignatureErasureTask> findTop100ByOrderByIdAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SignatureErasureTask t where t.id=:id")
    Optional<SignatureErasureTask> lockById(@Param("id") Long id);
}
