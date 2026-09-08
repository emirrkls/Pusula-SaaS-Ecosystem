package com.pusula.backend.repository;

import com.pusula.backend.entity.WhatsAppBusinessIntegration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WhatsAppBusinessIntegrationRepository extends JpaRepository<WhatsAppBusinessIntegration, Long> {
    Optional<WhatsAppBusinessIntegration> findByCompanyIdAndDeletedFalse(Long companyId);
    Optional<WhatsAppBusinessIntegration> findByPhoneNumberIdAndDeletedFalse(String phoneNumberId);
}
