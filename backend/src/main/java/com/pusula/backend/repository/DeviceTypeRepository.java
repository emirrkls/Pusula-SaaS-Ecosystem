package com.pusula.backend.repository;

import com.pusula.backend.entity.DeviceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceTypeRepository extends JpaRepository<DeviceType, Long> {

    List<DeviceType> findByCompanyId(Long companyId);
}
