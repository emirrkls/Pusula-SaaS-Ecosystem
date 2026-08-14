package com.pusula.backend.repository;

import com.pusula.backend.entity.CommercialDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

@Repository
public interface CommercialDeviceRepository extends JpaRepository<CommercialDevice, Long> {

    List<CommercialDevice> findByCompanyId(Long companyId);

    long countByCompanyId(Long companyId);

    List<CommercialDevice> findByCompanyIdAndDeviceTypeId(Long companyId, Long deviceTypeId);

    List<CommercialDevice> findByCompanyIdAndBrand(Long companyId, String brand);

    List<CommercialDevice> findByCompanyIdAndBtu(Long companyId, Integer btu);

    Optional<CommercialDevice> findByIdAndCompanyId(Long id, Long companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from CommercialDevice d where d.id = :id and d.companyId = :companyId")
    Optional<CommercialDevice> findByIdAndCompanyIdForUpdate(@Param("id") Long id,
                                                              @Param("companyId") Long companyId);
}
