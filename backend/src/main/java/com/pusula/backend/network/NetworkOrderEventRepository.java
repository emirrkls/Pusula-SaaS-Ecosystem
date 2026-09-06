package com.pusula.backend.network;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NetworkOrderEventRepository extends JpaRepository<NetworkOrderEvent, Long> {
    Page<NetworkOrderEvent> findByOrderIdOrderByIdDesc(Long order, Pageable pageable);
}
