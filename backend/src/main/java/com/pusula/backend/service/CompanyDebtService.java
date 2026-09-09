package com.pusula.backend.service;

import com.pusula.backend.dto.CompanyDebtDTO;
import com.pusula.backend.dto.CompanyDebtAdditionDTO;
import com.pusula.backend.dto.CompanyDebtPaymentDTO;
import com.pusula.backend.dto.DebtAdditionRequestDTO;
import com.pusula.backend.dto.DebtPaymentRequestDTO;
import com.pusula.backend.entity.CompanyDebt;
import com.pusula.backend.entity.CompanyDebtAddition;
import com.pusula.backend.entity.CompanyDebtPayment;
import com.pusula.backend.entity.Expense;
import com.pusula.backend.entity.ExpenseCategory;
import com.pusula.backend.entity.ExpenseTreatment;
import com.pusula.backend.repository.CompanyDebtPaymentRepository;
import com.pusula.backend.repository.CompanyDebtAdditionRepository;
import com.pusula.backend.repository.CompanyDebtRepository;
import com.pusula.backend.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Comparator;
import com.pusula.backend.dto.PayablePartySummaryDTO;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CompanyDebtService {

    private final CompanyDebtRepository debtRepository;
    private final CompanyDebtPaymentRepository paymentRepository;
    private final CompanyDebtAdditionRepository additionRepository;
    private final ExpenseRepository expenseRepository;
    private final AuditLogService auditLogService;
    private final FinanceService financeService;
    private final ZoneId businessZone;

    @Autowired(required = false)
    private AccountPartyService accountPartyService;

    public CompanyDebtService(CompanyDebtRepository debtRepository,
            CompanyDebtPaymentRepository paymentRepository,
            CompanyDebtAdditionRepository additionRepository,
            ExpenseRepository expenseRepository,
            AuditLogService auditLogService,
            FinanceService financeService,
            @Value("${app.business.timezone:Europe/Istanbul}") String businessTimezone) {
        this.debtRepository = debtRepository;
        this.paymentRepository = paymentRepository;
        this.additionRepository = additionRepository;
        this.expenseRepository = expenseRepository;
        this.auditLogService = auditLogService;
        this.financeService = financeService;
        this.businessZone = ZoneId.of(businessTimezone);
    }

    public List<CompanyDebtDTO> getAllDebts(Long companyId) {
        return debtRepository.findByCompanyIdAndDeletedFalse(companyId)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    public List<CompanyDebtDTO> getUnpaidDebts(Long companyId) {
        return debtRepository.findByCompanyIdAndDeletedFalse(companyId)
                .stream()
                .filter(debt -> debt.getStatus() != CompanyDebt.DebtStatus.PAID)
                .map(this::mapToDTO)
                .toList();
    }

    public List<PayablePartySummaryDTO> getPayablePartySummaries(Long companyId) {
        List<CompanyDebt> allDebts = debtRepository.findByCompanyIdAndDeletedFalse(companyId).stream()
                .filter(debt -> debt.getParty() != null)
                .toList();
        List<Long> debtIds = allDebts.stream().map(CompanyDebt::getId).toList();
        Map<Long, List<CompanyDebtPayment>> paymentsByDebt = debtIds.isEmpty() ? Map.of()
                : paymentRepository.findByCompanyIdAndDebtIdInOrderByPaymentDateAscIdAsc(companyId, debtIds).stream()
                        .collect(java.util.stream.Collectors.groupingBy(CompanyDebtPayment::getDebtId));
        Map<Long, List<CompanyDebtAddition>> additionsByDebt = debtIds.isEmpty() ? Map.of()
                : additionRepository.findByCompanyIdAndDebtIdInOrderByAdditionDateAscIdAsc(companyId, debtIds).stream()
                        .collect(java.util.stream.Collectors.groupingBy(CompanyDebtAddition::getDebtId));
        Map<Long, List<CompanyDebt>> grouped = allDebts.stream()
                .collect(java.util.stream.Collectors.groupingBy(debt -> debt.getParty().getId(),
                        LinkedHashMap::new, java.util.stream.Collectors.toList()));
        return grouped.values().stream().map(rows -> {
            CompanyDebt first = rows.get(0);
            BigDecimal purchases = rows.stream().map(CompanyDebt::getOriginalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal balance = rows.stream().map(CompanyDebt::getRemainingAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            LocalDate firstDate = rows.stream().map(CompanyDebt::getDebtDate).min(LocalDate::compareTo).orElse(null);
            LocalDate lastDate = rows.stream().map(CompanyDebt::getDebtDate).max(LocalDate::compareTo).orElse(null);
            for (CompanyDebt row : rows) {
                LocalDate paymentDate = paymentsByDebt.getOrDefault(row.getId(), List.of()).stream()
                        .map(CompanyDebtPayment::getPaymentDate).max(LocalDate::compareTo).orElse(null);
                LocalDate additionDate = additionsByDebt.getOrDefault(row.getId(), List.of()).stream()
                        .map(CompanyDebtAddition::getAdditionDate).max(LocalDate::compareTo).orElse(null);
                if (paymentDate != null && (lastDate == null || paymentDate.isAfter(lastDate))) lastDate = paymentDate;
                if (additionDate != null && (lastDate == null || additionDate.isAfter(lastDate))) lastDate = additionDate;
            }
            long open = rows.stream().filter(row -> row.getRemainingAmount().signum() > 0).count();
            return new PayablePartySummaryDTO(first.getParty().getId(), first.getParty().getDisplayName(),
                    first.getParty().getPhone(), purchases, purchases.subtract(balance), balance,
                    firstDate, lastDate, open);
        }).sorted(Comparator.comparing(PayablePartySummaryDTO::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public List<CompanyDebtDTO> getPartyDebts(Long companyId, Long partyId) {
        if (accountPartyService != null) accountPartyService.getEntity(companyId, partyId);
        return debtRepository.findByCompanyIdAndPartyIdAndDeletedFalseOrderByDebtDateAscIdAsc(companyId, partyId)
                .stream().map(this::mapToDTO).toList();
    }

    @Transactional
    public PayablePartySummaryDTO payParty(Long companyId, Long partyId, DebtPaymentRequestDTO request) {
        BigDecimal requestedAmount = request != null ? request.getAmount() : null;
        LocalDate paymentDate = request != null && request.getPaymentDate() != null
                ? request.getPaymentDate() : LocalDate.now(businessZone);
        validatePositive(requestedAmount, "Ödeme tutarı");
        if (paymentDate.isAfter(LocalDate.now(businessZone))) {
            throw new IllegalArgumentException("Ödeme tarihi gelecekte olamaz!");
        }
        List<CompanyDebt> partyDebts = debtRepository
                .findByCompanyIdAndPartyIdAndDeletedFalseOrderByDebtDateAscIdAsc(companyId, partyId);
        List<Long> partyDebtIds = partyDebts.stream().map(CompanyDebt::getId).toList();
        Map<Long, List<CompanyDebtPayment>> paymentsByDebt = partyDebtIds.isEmpty() ? Map.of()
                : paymentRepository.findByCompanyIdAndDebtIdInOrderByPaymentDateAscIdAsc(companyId, partyDebtIds).stream()
                        .collect(java.util.stream.Collectors.groupingBy(CompanyDebtPayment::getDebtId));
        Map<Long, List<CompanyDebtAddition>> additionsByDebt = partyDebtIds.isEmpty() ? Map.of()
                : additionRepository.findByCompanyIdAndDebtIdInOrderByAdditionDateAscIdAsc(companyId, partyDebtIds).stream()
                        .collect(java.util.stream.Collectors.groupingBy(CompanyDebtAddition::getDebtId));
        Map<Long, BigDecimal> availableByDebt = partyDebts.stream().collect(java.util.stream.Collectors.toMap(
                CompanyDebt::getId, debt -> historicalAvailable(debt, paymentDate,
                        additionsByDebt.getOrDefault(debt.getId(), List.of()),
                        paymentsByDebt.getOrDefault(debt.getId(), List.of()))));
        List<CompanyDebt> eligible = partyDebts.stream()
                .filter(debt -> debt.getRemainingAmount().signum() > 0 && availableByDebt.get(debt.getId()).signum() > 0)
                .toList();
        BigDecimal eligibleBalance = eligible.stream().map(debt -> availableByDebt.get(debt.getId()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (requestedAmount.compareTo(eligibleBalance) > 0) {
            throw new IllegalArgumentException("Ödeme tutarı, ödeme tarihindeki tedarikçi borcunu aşamaz.");
        }
        BigDecimal remaining = requestedAmount;
        for (CompanyDebt debt : eligible) {
            if (remaining.signum() == 0) break;
            BigDecimal allocated = remaining.min(availableByDebt.get(debt.getId()));
            payDebt(debt.getId(), companyId, DebtPaymentRequestDTO.builder().amount(allocated)
                    .paymentDate(paymentDate).notes(request.getNotes()).build());
            remaining = remaining.subtract(allocated);
        }
        auditLogService.log("UPDATE", "PAYABLE_PARTY", partyId,
                "Tedarikçi kartına toplu ödeme: " + requestedAmount + " ₺ (" + paymentDate + ")");
        return getPayablePartySummaries(companyId).stream().filter(row -> row.partyId().equals(partyId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Tedarikçi kartı bulunamadı."));
    }

    private BigDecimal historicalAvailable(CompanyDebt debt, LocalDate date,
            List<CompanyDebtAddition> additions, List<CompanyDebtPayment> payments) {
        if (debt.getDebtDate().isAfter(date)) return BigDecimal.ZERO;
        BigDecimal allAdditions = additions.stream().map(CompanyDebtAddition::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal initialAmount = debt.getOriginalAmount().subtract(allAdditions).max(BigDecimal.ZERO);
        BigDecimal datedAdditions = additions.stream().filter(row -> !row.getAdditionDate().isAfter(date))
                .map(CompanyDebtAddition::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal datedPayments = payments.stream().filter(row -> !row.getPaymentDate().isAfter(date))
                .map(CompanyDebtPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal historicalBalance = initialAmount.add(datedAdditions).subtract(datedPayments).max(BigDecimal.ZERO);
        return historicalBalance.min(debt.getRemainingAmount()).max(BigDecimal.ZERO);
    }

    @Transactional
    public CompanyDebtDTO createDebt(CompanyDebtDTO dto) {
        validatePositive(dto.getOriginalAmount(), "Borç tutarı");
        if (dto.getCreditorName() == null || dto.getCreditorName().isBlank()) {
            throw new IllegalArgumentException("Alacaklı kişi/firma zorunludur.");
        }
        com.pusula.backend.entity.AccountParty party = null;
        if (accountPartyService != null) {
            party = dto.getPartyId() != null
                    ? accountPartyService.getEntity(dto.getCompanyId(), dto.getPartyId())
                    : accountPartyService.ensureSupplierParty(dto.getCompanyId(), dto.getCreditorName(), dto.getCreditorPhone());
            if (!party.isActive()) {
                throw new IllegalArgumentException("Pasif tedarikçi kartına yeni borç eklenemez.");
            }
            if (party.getPartyType() != com.pusula.backend.entity.AccountParty.PartyType.SUPPLIER
                    && party.getPartyType() != com.pusula.backend.entity.AccountParty.PartyType.OTHER) {
                throw new IllegalArgumentException("Borç kartı tedarikçi veya diğer türünde olmalıdır.");
            }
            dto.setCreditorName(party.getDisplayName());
            if ((dto.getCreditorPhone() == null || dto.getCreditorPhone().isBlank()) && party.getPhone() != null) {
                dto.setCreditorPhone(party.getPhone());
            }
        }
        CompanyDebt debt = CompanyDebt.builder()
                .companyId(dto.getCompanyId())
                .creditorName(dto.getCreditorName())
                .party(party)
                .description(dto.getDescription())
                .originalAmount(dto.getOriginalAmount())
                .remainingAmount(dto.getOriginalAmount())
                .expenseCategory(parseExpenseCategory(dto.getExpenseCategory()))
                .debtDate(dto.getDebtDate() != null ? dto.getDebtDate() : LocalDate.now(businessZone))
                .dueDate(dto.getDueDate())
                .creditorPhone(dto.getCreditorPhone())
                .status(CompanyDebt.DebtStatus.UNPAID)
                .notes(dto.getNotes())
                .build();

        if (debt.getDebtDate().isAfter(LocalDate.now(businessZone))) {
            throw new IllegalArgumentException("Borç tarihi gelecekte olamaz.");
        }

        CompanyDebt saved = debtRepository.save(debt);
        auditLogService.log("CREATE", "COMPANY_DEBT", saved.getId(),
                "Borç eklendi: " + saved.getCreditorName() + " (" + saved.getOriginalAmount() + " ₺)");
        return mapToDTO(saved);
    }

    @Transactional
    public CompanyDebtDTO updateDebt(Long id, Long companyId, CompanyDebtDTO dto) {
        CompanyDebt debt = findDebt(id, companyId);
        debt.setCreditorName(debt.getParty() != null ? debt.getParty().getDisplayName() : dto.getCreditorName());
        debt.setDescription(dto.getDescription());
        debt.setDueDate(dto.getDueDate());
        debt.setCreditorPhone(dto.getCreditorPhone());
        debt.setNotes(dto.getNotes());
        if (dto.getExpenseCategory() != null) {
            debt.setExpenseCategory(parseExpenseCategory(dto.getExpenseCategory()));
        }
        if (dto.getRemainingAmount() != null) {
            debt.setRemainingAmount(dto.getRemainingAmount());
            updateStatus(debt);
        }
        return mapToDTO(debtRepository.save(debt));
    }

    @Transactional
    public CompanyDebtDTO payDebt(Long id, Long companyId, DebtPaymentRequestDTO request) {
        CompanyDebt debt = findDebt(id, companyId);
        BigDecimal paymentAmount = request != null ? request.getAmount() : null;
        LocalDate paymentDate = request != null && request.getPaymentDate() != null
                ? request.getPaymentDate()
                : LocalDate.now(businessZone);

        validatePositive(paymentAmount, "Ödeme tutarı");
        if (paymentAmount.compareTo(debt.getRemainingAmount()) > 0) {
            throw new IllegalArgumentException("Ödeme tutarı kalan borçtan fazla olamaz!");
        }
        if (paymentDate.isBefore(debt.getDebtDate())) {
            throw new IllegalArgumentException("Ödeme tarihi borç tarihinden önce olamaz!");
        }
        if (paymentDate.isAfter(LocalDate.now(businessZone))) {
            throw new IllegalArgumentException("Ödeme tarihi gelecekte olamaz!");
        }

        Expense savedExpense = expenseRepository.save(Expense.builder()
                .companyId(companyId)
                .amount(paymentAmount)
                .description("Borç Ödemesi: " + debt.getCreditorName()
                        + (debt.getDescription() != null && !debt.getDescription().isBlank()
                                ? " - " + debt.getDescription()
                                : ""))
                .date(paymentDate)
                .category(debt.getExpenseCategory())
                .financialTreatment(ExpenseTreatment.CASH_ONLY)
                .build());

        paymentRepository.save(CompanyDebtPayment.builder()
                .companyId(companyId)
                .debtId(debt.getId())
                .expenseId(savedExpense.getId())
                .amount(paymentAmount)
                .paymentDate(paymentDate)
                .expenseCategory(debt.getExpenseCategory())
                .notes(request != null ? request.getNotes() : null)
                .build());

        debt.setRemainingAmount(debt.getRemainingAmount().subtract(paymentAmount));
        updateStatus(debt);
        CompanyDebt saved = debtRepository.save(debt);
        financeService.reconcileClosedDay(companyId, paymentDate);

        auditLogService.log("UPDATE", "COMPANY_DEBT", saved.getId(),
                "Borç ödendi: " + saved.getCreditorName() + " (" + paymentAmount + " ₺, " + paymentDate + ")"
                        + (saved.getStatus() == CompanyDebt.DebtStatus.PAID ? " - TAMAMI ÖDENDİ" : ""));
        return mapToDTO(saved);
    }

    public List<CompanyDebtPaymentDTO> getPayments(Long debtId, Long companyId) {
        CompanyDebt debt = findDebt(debtId, companyId);
        return paymentRepository.findByDebtIdAndCompanyIdOrderByPaymentDateAscIdAsc(debtId, companyId)
                .stream()
                .map(payment -> mapPaymentToDTO(payment, debt.getCreditorName()))
                .toList();
    }

    public List<CompanyDebtAdditionDTO> getAdditions(Long debtId, Long companyId) {
        findDebt(debtId, companyId);
        return additionRepository.findByDebtIdAndCompanyIdOrderByAdditionDateAscIdAsc(debtId, companyId)
                .stream()
                .map(this::mapAdditionToDTO)
                .toList();
    }

    @Transactional
    public CompanyDebtDTO deletePayment(Long debtId, Long paymentId, Long companyId) {
        CompanyDebt debt = findDebt(debtId, companyId);
        CompanyDebtPayment payment = paymentRepository.findByIdAndDebtIdAndCompanyId(paymentId, debtId, companyId)
                .orElseThrow(() -> new RuntimeException("Borç ödemesi bulunamadı: " + paymentId));

        debt.setRemainingAmount(debt.getRemainingAmount().add(payment.getAmount()));
        updateStatus(debt);
        CompanyDebt saved = debtRepository.save(debt);

        paymentRepository.delete(payment);
        paymentRepository.flush();
        expenseRepository.deleteById(payment.getExpenseId());
        financeService.reconcileClosedDay(companyId, payment.getPaymentDate());

        auditLogService.log("DELETE", "COMPANY_DEBT_PAYMENT", paymentId,
                "Borç ödemesi geri alındı: " + debt.getCreditorName() + " (" + payment.getAmount() + " ₺)");
        return mapToDTO(saved);
    }

    @Transactional
    public CompanyDebtDTO addAmountToDebt(Long id, Long companyId, DebtAdditionRequestDTO request) {
        CompanyDebt debt = findDebt(id, companyId);
        BigDecimal amountToAdd = request != null ? request.getAmount() : null;
        LocalDate additionDate = request != null && request.getAdditionDate() != null
                ? request.getAdditionDate()
                : LocalDate.now(businessZone);
        String notes = request != null ? request.getNotes() : null;
        validatePositive(amountToAdd, "Eklenecek tutar");
        if (additionDate.isBefore(debt.getDebtDate())) {
            throw new IllegalArgumentException("İlave tarihi borç tarihinden önce olamaz.");
        }
        if (additionDate.isAfter(LocalDate.now(businessZone))) {
            throw new IllegalArgumentException("İlave tarihi gelecekte olamaz.");
        }

        additionRepository.save(CompanyDebtAddition.builder()
                .companyId(companyId)
                .debtId(debt.getId())
                .amount(amountToAdd)
                .additionDate(additionDate)
                .notes(notes)
                .build());

        debt.setOriginalAmount(debt.getOriginalAmount().add(amountToAdd));
        debt.setRemainingAmount(debt.getRemainingAmount().add(amountToAdd));
        updateStatus(debt);

        CompanyDebt saved = debtRepository.save(debt);
        auditLogService.log("UPDATE", "COMPANY_DEBT", saved.getId(),
                "Borca ilave yapıldı: " + amountToAdd + " ₺ (" + additionDate
                        + "). Yeni borç: " + saved.getRemainingAmount() + " ₺");
        return mapToDTO(saved);
    }

    @Transactional
    public void deleteDebt(Long id, Long companyId) {
        CompanyDebt debt = findDebt(id, companyId);
        if (paymentRepository.existsByDebtIdAndCompanyId(id, companyId)) {
            throw new IllegalStateException(
                    "Ödeme geçmişi bulunan borç silinemez. Önce kayıtlı ödemeleri geri alın.");
        }
        if (additionRepository.existsByDebtIdAndCompanyId(id, companyId)) {
            throw new IllegalStateException("İlave hareketi bulunan borç silinemez.");
        }
        debt.setDeleted(true);
        debtRepository.save(debt);
        auditLogService.log("DELETE", "COMPANY_DEBT", id, "Borç silindi: " + debt.getCreditorName());
    }

    public BigDecimal getTotalUnpaidDebt(Long companyId) {
        return debtRepository.findByCompanyIdAndDeletedFalse(companyId).stream()
                .filter(debt -> debt.getStatus() != CompanyDebt.DebtStatus.PAID)
                .map(CompanyDebt::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private CompanyDebt findDebt(Long id, Long companyId) {
        return debtRepository.findByIdAndCompanyIdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new RuntimeException("Borç bulunamadı: " + id));
    }

    private void validatePositive(BigDecimal amount, String label) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(label + " sıfırdan büyük olmalıdır.");
        }
    }

    private ExpenseCategory parseExpenseCategory(String value) {
        ExpenseCategory category;
        try {
            category = value == null || value.isBlank() ? ExpenseCategory.OTHER : ExpenseCategory.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Geçersiz gider kategorisi: " + value);
        }
        if (category == ExpenseCategory.DEVICE_SALE) {
            throw new IllegalArgumentException("Borç ödemesi için satış kategorisi kullanılamaz.");
        }
        return category;
    }

    private void updateStatus(CompanyDebt debt) {
        if (debt.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            debt.setStatus(CompanyDebt.DebtStatus.PAID);
        } else if (debt.getRemainingAmount().compareTo(debt.getOriginalAmount()) < 0) {
            debt.setStatus(CompanyDebt.DebtStatus.PARTIAL);
        } else {
            debt.setStatus(CompanyDebt.DebtStatus.UNPAID);
        }
    }

    private CompanyDebtDTO mapToDTO(CompanyDebt debt) {
        return CompanyDebtDTO.builder()
                .id(debt.getId())
                .companyId(debt.getCompanyId())
                .creditorName(debt.getCreditorName())
                .partyId(debt.getParty() != null ? debt.getParty().getId() : null)
                .partyType(debt.getParty() != null ? debt.getParty().getPartyType().name() : "SUPPLIER")
                .description(debt.getDescription())
                .originalAmount(debt.getOriginalAmount())
                .remainingAmount(debt.getRemainingAmount())
                .expenseCategory(debt.getExpenseCategory().name())
                .debtDate(debt.getDebtDate())
                .dueDate(debt.getDueDate())
                .creditorPhone(debt.getCreditorPhone())
                .status(debt.getStatus().name())
                .notes(debt.getNotes())
                .createdAt(debt.getCreatedAt())
                .updatedAt(debt.getUpdatedAt())
                .build();
    }

    private CompanyDebtPaymentDTO mapPaymentToDTO(CompanyDebtPayment payment, String creditorName) {
        return CompanyDebtPaymentDTO.builder()
                .id(payment.getId())
                .debtId(payment.getDebtId())
                .amount(payment.getAmount())
                .paymentDate(payment.getPaymentDate())
                .expenseCategory(payment.getExpenseCategory().name())
                .creditorName(creditorName)
                .notes(payment.getNotes())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    private CompanyDebtAdditionDTO mapAdditionToDTO(CompanyDebtAddition addition) {
        return CompanyDebtAdditionDTO.builder()
                .id(addition.getId())
                .debtId(addition.getDebtId())
                .amount(addition.getAmount())
                .additionDate(addition.getAdditionDate())
                .notes(addition.getNotes())
                .createdAt(addition.getCreatedAt())
                .build();
    }
}
