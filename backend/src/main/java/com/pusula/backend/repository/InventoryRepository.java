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

    java.util.Optional<Inventory> findByBarcodeAndCompanyId(String barcode, Long companyId);

    @Query("SELECT i FROM Inventory i WHERE i.companyId = :companyId AND i.barcode IS NOT NULL " +
            "AND LOWER(TRIM(i.barcode)) = LOWER(TRIM(:barcode))")
    java.util.Optional<Inventory> findByBarcodeNormalized(@Param("barcode") String barcode,
            @Param("companyId") Long companyId);
}
