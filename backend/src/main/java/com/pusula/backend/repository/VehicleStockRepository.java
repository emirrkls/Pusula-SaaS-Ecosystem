package com.pusula.backend.repository;

import com.pusula.backend.entity.VehicleStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleStockRepository extends JpaRepository<VehicleStock, Long> {

    List<VehicleStock> findByCompanyId(Long companyId);

    List<VehicleStock> findByVehicleId(Long vehicleId);

    List<VehicleStock> findByInventoryId(Long inventoryId);

    Optional<VehicleStock> findByVehicleIdAndInventoryId(Long vehicleId, Long inventoryId);

    void deleteByVehicleId(Long vehicleId);
}
