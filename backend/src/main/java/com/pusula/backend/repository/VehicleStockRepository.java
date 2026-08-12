package com.pusula.backend.repository;

import com.pusula.backend.entity.VehicleStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

@Repository
public interface VehicleStockRepository extends JpaRepository<VehicleStock, Long> {

    List<VehicleStock> findByCompanyId(Long companyId);

    List<VehicleStock> findByVehicleIdAndCompanyId(Long vehicleId, Long companyId);

    List<VehicleStock> findByInventoryIdAndCompanyId(Long inventoryId, Long companyId);

    Optional<VehicleStock> findByVehicleIdAndInventoryIdAndCompanyId(Long vehicleId, Long inventoryId, Long companyId);

    Optional<VehicleStock> findByIdAndCompanyId(Long id, Long companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from VehicleStock s where s.vehicle.id = :vehicleId and s.inventory.id = :inventoryId and s.companyId = :companyId")
    Optional<VehicleStock> findForUpdate(@Param("vehicleId") Long vehicleId,
                                         @Param("inventoryId") Long inventoryId,
                                         @Param("companyId") Long companyId);

    void deleteByVehicleId(Long vehicleId);
}
