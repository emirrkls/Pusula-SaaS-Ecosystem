package com.pusula.backend.repository;

import com.pusula.backend.entity.AccountParty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountPartyRepository extends JpaRepository<AccountParty, Long> {
    List<AccountParty> findByCompanyIdAndActiveTrueOrderByDisplayNameAsc(Long companyId);
    List<AccountParty> findByCompanyIdAndPartyTypeAndActiveTrueOrderByDisplayNameAsc(
            Long companyId, AccountParty.PartyType partyType);
    Optional<AccountParty> findByIdAndCompanyId(Long id, Long companyId);
    Optional<AccountParty> findByCompanyIdAndPartyTypeAndNormalizedName(
            Long companyId, AccountParty.PartyType partyType, String normalizedName);
    Optional<AccountParty> findByCompanyIdAndCustomerId(Long companyId, Long customerId);
}
