package com.pusula.backend.repository;

import com.pusula.backend.entity.BusinessAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessAssetRepository extends JpaRepository<BusinessAsset, Long> {
    List<BusinessAsset> findByCompanyIdOrderByAssetNameAsc(Long companyId);
    Optional<BusinessAsset> findByIdAndCompanyId(Long id, Long companyId);
}
