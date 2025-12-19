package com.pusula.backend.repository;

import com.pusula.backend.entity.CommercialDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommercialDeviceRepository extends JpaRepository<CommercialDevice, Long> {

    List<CommercialDevice> findByCompanyId(Long companyId);

    List<CommercialDevice> findByCompanyIdAndDeviceTypeId(Long companyId, Long deviceTypeId);

    List<CommercialDevice> findByCompanyIdAndBrand(Long companyId, String brand);

    List<CommercialDevice> findByCompanyIdAndBtu(Long companyId, Integer btu);
}
