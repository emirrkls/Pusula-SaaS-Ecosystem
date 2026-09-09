package com.pusula.backend.service;

import com.pusula.backend.dto.AccountPartyDTO;
import com.pusula.backend.entity.AccountParty;
import com.pusula.backend.entity.Customer;
import com.pusula.backend.repository.AccountPartyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AccountPartyService {
    private static final Locale TURKISH = Locale.forLanguageTag("tr");
    private final AccountPartyRepository repository;
    private final AuditLogService auditLogService;

    public AccountPartyService(AccountPartyRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    public List<AccountPartyDTO> list(Long companyId, AccountParty.PartyType type) {
        List<AccountParty> rows = type == null
                ? repository.findByCompanyIdAndActiveTrueOrderByDisplayNameAsc(companyId)
                : repository.findByCompanyIdAndPartyTypeAndActiveTrueOrderByDisplayNameAsc(companyId, type);
        return rows.stream().map(this::toDto).toList();
    }

    @Transactional
    public AccountPartyDTO create(Long companyId, AccountPartyDTO request) {
        validate(request);
        AccountParty.PartyType type = request.getPartyType();
        if (type == AccountParty.PartyType.CUSTOMER) {
            throw new IllegalArgumentException("Müşteri cari kartları müşteri kaydı üzerinden otomatik oluşturulur.");
        }
        String name = displayName(request.getDisplayName());
        String normalized = normalize(name);
        if (repository.findByCompanyIdAndPartyTypeAndNormalizedName(companyId, type, normalized).isPresent()) {
            throw new IllegalArgumentException("Bu ad ve türde bir cari kart zaten mevcut.");
        }
        AccountParty party = AccountParty.builder()
                .companyId(companyId).partyType(type).displayName(name).normalizedName(normalized)
                .legalName(clean(request.getLegalName())).taxNumber(clean(request.getTaxNumber()))
                .taxOffice(clean(request.getTaxOffice())).phone(clean(request.getPhone()))
                .email(clean(request.getEmail())).address(clean(request.getAddress()))
                .contactPerson(clean(request.getContactPerson()))
                .paymentTermDays(request.getPaymentTermDays() == null ? 0 : request.getPaymentTermDays())
                .active(true).notes(clean(request.getNotes())).build();
        try {
            party = repository.save(party);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalArgumentException("Bu cari kart zaten mevcut.");
        }
        auditLogService.log("CREATE", "ACCOUNT_PARTY", party.getId(),
                "Cari taraf oluşturuldu: " + party.getDisplayName() + " (" + party.getPartyType() + ")");
        return toDto(party);
    }

    @Transactional
    public AccountPartyDTO update(Long companyId, Long id, AccountPartyDTO request) {
        AccountParty party = getEntity(companyId, id);
        if (request.getDisplayName() == null || request.getDisplayName().isBlank()) {
            throw new IllegalArgumentException("Cari kart adı zorunludur.");
        }
        String normalized = normalize(request.getDisplayName());
        repository.findByCompanyIdAndPartyTypeAndNormalizedName(companyId, party.getPartyType(), normalized)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw new IllegalArgumentException("Bu ad ve türde bir cari kart zaten mevcut."); });
        party.setDisplayName(displayName(request.getDisplayName()));
        party.setNormalizedName(normalized);
        party.setLegalName(clean(request.getLegalName()));
        party.setTaxNumber(clean(request.getTaxNumber()));
        party.setTaxOffice(clean(request.getTaxOffice()));
        party.setPhone(clean(request.getPhone()));
        party.setEmail(clean(request.getEmail()));
        party.setAddress(clean(request.getAddress()));
        party.setContactPerson(clean(request.getContactPerson()));
        party.setPaymentTermDays(request.getPaymentTermDays() == null ? 0 : request.getPaymentTermDays());
        party.setNotes(clean(request.getNotes()));
        auditLogService.log("UPDATE", "ACCOUNT_PARTY", party.getId(), "Cari taraf güncellendi: " + party.getDisplayName());
        return toDto(repository.save(party));
    }

    @Transactional
    public void deactivate(Long companyId, Long id) {
        AccountParty party = getEntity(companyId, id);
        party.setActive(false);
        repository.save(party);
        auditLogService.log("UPDATE", "ACCOUNT_PARTY", id, "Cari taraf pasife alındı: " + party.getDisplayName());
    }

    public AccountParty getEntity(Long companyId, Long id) {
        return repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Cari kart bulunamadı."));
    }

    @Transactional
    public AccountParty ensureSupplierParty(Long companyId, String name, String phone) {
        String normalized = normalize(name);
        return repository.findByCompanyIdAndPartyTypeAndNormalizedName(
                        companyId, AccountParty.PartyType.SUPPLIER, normalized)
                .map(existing -> {
                    if (!existing.isActive()) {
                        existing.setActive(true);
                        return repository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> repository.save(AccountParty.builder().companyId(companyId)
                        .partyType(AccountParty.PartyType.SUPPLIER).displayName(displayName(name))
                        .normalizedName(normalized).phone(clean(phone)).active(true).paymentTermDays(0).build()));
    }

    @Transactional
    public AccountParty ensureCustomerParty(Customer customer) {
        return repository.findByCompanyIdAndCustomerId(customer.getCompanyId(), customer.getId())
                .orElseGet(() -> repository.save(AccountParty.builder()
                        .companyId(customer.getCompanyId()).partyType(AccountParty.PartyType.CUSTOMER)
                        .displayName(customer.getName()).normalizedName("customer-" + customer.getId())
                        .phone(customer.getPhone()).address(customer.getAddress()).customerId(customer.getId())
                        .active(true).paymentTermDays(0).build()));
    }

    public String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(TURKISH);
    }

    private void validate(AccountPartyDTO request) {
        if (request == null || request.getDisplayName() == null || request.getDisplayName().isBlank()) {
            throw new IllegalArgumentException("Cari kart adı zorunludur.");
        }
        if (request.getPartyType() == null) throw new IllegalArgumentException("Cari kart türü zorunludur.");
        if (request.getPaymentTermDays() != null && request.getPaymentTermDays() < 0) {
            throw new IllegalArgumentException("Ödeme vadesi negatif olamaz.");
        }
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String displayName(String value) { return value.trim().replaceAll("\\s+", " "); }

    private AccountPartyDTO toDto(AccountParty p) {
        return AccountPartyDTO.builder().id(p.getId()).partyType(p.getPartyType())
                .displayName(p.getDisplayName()).legalName(p.getLegalName()).taxNumber(p.getTaxNumber())
                .taxOffice(p.getTaxOffice()).phone(p.getPhone()).email(p.getEmail()).address(p.getAddress())
                .contactPerson(p.getContactPerson()).paymentTermDays(p.getPaymentTermDays())
                .customerId(p.getCustomerId()).active(p.isActive()).notes(p.getNotes())
                .createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt()).build();
    }
}
