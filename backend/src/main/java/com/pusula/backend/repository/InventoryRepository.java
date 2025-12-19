package com.pusula.backend.repository;

import com.pusula.backend.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    List<Inventory> findByCompanyId(Long companyId);

    @Query("SELECT i FROM Inventory i WHERE i.companyId = :companyId AND " +
            "(LOWER(i.partName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(i.brand) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(i.category) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<Inventory> findByCompanyIdAndSearchTerm(@Param("companyId") Long companyId,
            @Param("searchTerm") String searchTerm);
}
