package com.pusula.backend.service;

import com.pusula.backend.dto.CommercialDeviceDTO;
import com.pusula.backend.dto.SaleRequestDTO;
import com.pusula.backend.dto.SaleResponseDTO;
import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CommercialDeviceService {

    private final CommercialDeviceRepository commercialDeviceRepository;
    private final DeviceTypeRepository deviceTypeRepository;
    private final UserRepository userRepository;
    private final ServiceTicketRepository serviceTicketRepository;
    private final CustomerRepository customerRepository;
    private final CurrentAccountRepository currentAccountRepository;
    private final ExpenseRepository expenseRepository;
    private final AuditLogService auditLogService;

    public CommercialDeviceService(CommercialDeviceRepository commercialDeviceRepository,
            DeviceTypeRepository deviceTypeRepository,
            UserRepository userRepository,
            ServiceTicketRepository serviceTicketRepository,
            CustomerRepository customerRepository,
            CurrentAccountRepository currentAccountRepository,
            ExpenseRepository expenseRepository,
            AuditLogService auditLogService) {
        this.commercialDeviceRepository = commercialDeviceRepository;
        this.deviceTypeRepository = deviceTypeRepository;
        this.userRepository = userRepository;
        this.serviceTicketRepository = serviceTicketRepository;
        this.customerRepository = customerRepository;
        this.currentAccountRepository = currentAccountRepository;
        this.expenseRepository = expenseRepository;
        this.auditLogService = auditLogService;
    }

    public List<CommercialDeviceDTO> getAllByCompany(Long companyId) {
        boolean isTechnician = isTechnicianRole();
        return commercialDeviceRepository.findByCompanyId(companyId)
                .stream()
                .map(device -> mapToDTO(device, isTechnician))
                .collect(Collectors.toList());
    }

    public CommercialDeviceDTO getById(Long id) {
        User currentUser = getCurrentUser();
        boolean isTechnician = isTechnicianRole();
        return commercialDeviceRepository.findByIdAndCompanyId(id, currentUser.getCompanyId())
                .map(device -> mapToDTO(device, isTechnician))
                .orElse(null);
    }

    @Transactional
    public CommercialDeviceDTO create(CommercialDeviceDTO dto) {
        User currentUser = getCurrentUser();
        validateDevice(dto);

        CommercialDevice device = new CommercialDevice();
        device.setCompanyId(currentUser.getCompanyId());
        device.setBrand(dto.getBrand());
        device.setModel(dto.getModel());
        device.setBtu(dto.getBtu());
        device.setGasType(dto.getGasType());
        device.setQuantity(dto.getQuantity() != null ? dto.getQuantity() : 0);
        device.setBuyingPrice(dto.getBuyingPrice());
        device.setSellingPrice(dto.getSellingPrice());

        if (dto.getDeviceTypeId() != null) {
            DeviceType deviceType = deviceTypeRepository
                    .findByIdAndCompanyId(dto.getDeviceTypeId(), currentUser.getCompanyId())
                    .orElseThrow(() -> new IllegalArgumentException("Cihaz türü bulunamadı."));
            device.setDeviceType(deviceType);
        }

        CommercialDevice saved = commercialDeviceRepository.save(device);
        return mapToDTO(saved, false);
    }

    @Transactional
    public CommercialDeviceDTO update(Long id, CommercialDeviceDTO dto) {
        User currentUser = getCurrentUser();
        validateDevice(dto);
        CommercialDevice device = commercialDeviceRepository.findByIdAndCompanyId(id, currentUser.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Cihaz bulunamadı veya erişim reddedildi."));

        device.setBrand(dto.getBrand());
        device.setModel(dto.getModel());
        device.setBtu(dto.getBtu());
        device.setGasType(dto.getGasType());
        device.setQuantity(dto.getQuantity());
        device.setBuyingPrice(dto.getBuyingPrice());
        device.setSellingPrice(dto.getSellingPrice());

        if (dto.getDeviceTypeId() != null) {
            DeviceType deviceType = deviceTypeRepository
                    .findByIdAndCompanyId(dto.getDeviceTypeId(), currentUser.getCompanyId())
                    .orElseThrow(() -> new IllegalArgumentException("Cihaz türü bulunamadı."));
            device.setDeviceType(deviceType);
        }

        CommercialDevice saved = commercialDeviceRepository.save(device);
        return mapToDTO(saved, false);
    }

    @Transactional
    public void delete(Long id) {
        User currentUser = getCurrentUser();
        CommercialDevice device = commercialDeviceRepository.findByIdAndCompanyId(id, currentUser.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Cihaz bulunamadı veya erişim reddedildi."));
        commercialDeviceRepository.delete(device);
    }

    @Transactional
    public CommercialDeviceDTO sellDevice(Long id, Integer quantityToSell) {
        User currentUser = getCurrentUser();
        if (quantityToSell == null || quantityToSell <= 0) {
            throw new IllegalArgumentException("Satış adedi sıfırdan büyük olmalıdır.");
        }
        CommercialDevice device = commercialDeviceRepository
                .findByIdAndCompanyIdForUpdate(id, currentUser.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Cihaz bulunamadı veya erişim reddedildi."));

        if (device.getQuantity() < quantityToSell) {
            throw new RuntimeException("Insufficient stock");
        }

        device.setQuantity(device.getQuantity() - quantityToSell);
        CommercialDevice saved = commercialDeviceRepository.save(device);
        return mapToDTO(saved, isTechnicianRole());
    }

    /**
     * Process a device sale with full workflow:
     * 1. Decrease stock
     * 2. Create service ticket for installation (amount = 0)
     * 3. Record sale as income (DEVICE_SALE expense with negative amount)
     * 4. Handle Cari payment (add debt if applicable)
     */
    @Transactional
    public SaleResponseDTO processSale(SaleRequestDTO request) {
        User currentUser = getCurrentUser();

        // 1. Get and validate device
        CommercialDevice device = commercialDeviceRepository
                .findByIdAndCompanyIdForUpdate(request.getDeviceId(), currentUser.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Cihaz bulunamadı"));

        if (device.getQuantity() < 1) {
            throw new RuntimeException("Yetersiz stok");
        }

        // 2. Get customer
        Customer customer = customerRepository.findByIdAndCompanyId(request.getCustomerId(), currentUser.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Müşteri bulunamadı"));

        // 3. Calculate sale price
        BigDecimal salePrice = request.getSellingPrice() != null
                ? request.getSellingPrice()
                : device.getSellingPrice();

        String paymentMethod = request.getPaymentMethod();
        LocalDate saleDate = request.getSaleDate() != null ? request.getSaleDate() : LocalDate.now();

        if (salePrice == null || salePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Satış fiyatı sıfırdan büyük olmalıdır.");
        }
        PaymentMethod normalizedPaymentMethod;
        try {
            normalizedPaymentMethod = PaymentMethod.valueOf(paymentMethod.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Geçersiz ödeme yöntemi.");
        }

        // 4. Recognize the full sale and its cost separately. Profit is derived in reports.
        BigDecimal buyPrice = device.getBuyingPrice() != null ? device.getBuyingPrice() : BigDecimal.ZERO;
        BigDecimal profit = salePrice.subtract(buyPrice);

        String saleDescription = String.format("Satış: %s %s %s BTU - %s (Kâr: %.2f ₺)",
                device.getBrand(), device.getModel(),
                device.getBtu() != null ? device.getBtu() : "",
                customer.getName(), profit);

        Expense saleIncome = Expense.builder()
                .companyId(currentUser.getCompanyId())
                .amount(salePrice.negate())
                .description(saleDescription)
                .date(saleDate)
                .category(ExpenseCategory.DEVICE_SALE)
                .paymentMethod(normalizedPaymentMethod)
                .customerId(customer.getId())
                .sourceType("COMMERCIAL_DEVICE")
                .sourceId(device.getId())
                .financialTreatment(ExpenseTreatment.CASH_ONLY)
                .build();
        expenseRepository.save(saleIncome);

        if (buyPrice.compareTo(BigDecimal.ZERO) > 0) {
            Expense saleCost = Expense.builder()
                    .companyId(currentUser.getCompanyId())
                    .amount(buyPrice)
                    .description("Cihaz satış maliyeti: " + device.getBrand() + " " + device.getModel())
                    .date(saleDate)
                    .category(ExpenseCategory.MATERIAL)
                    .paymentMethod(normalizedPaymentMethod)
                    .customerId(customer.getId())
                    .sourceType("COMMERCIAL_DEVICE_COST")
                    .sourceId(device.getId())
                    .financialTreatment(ExpenseTreatment.SERVICE_DIRECT_EXPENSE)
                    .build();
            expenseRepository.save(saleCost);
        }

        // 5. Create service ticket for installation (PENDING, amount = 0)
        String ticketDescription = String.format("Montaj: %s %s %s BTU",
                device.getBrand(), device.getModel(), device.getBtu() != null ? device.getBtu() : "");

        ServiceTicket ticket = new ServiceTicket();
        ticket.setCompanyId(currentUser.getCompanyId());
        ticket.setCustomerId(customer.getId());
        ticket.setStatus(ServiceTicket.TicketStatus.PENDING);
        ticket.setDescription(ticketDescription);
        ticket.setScheduledDate(saleDate.atStartOfDay());
        ticket.setCollectedAmount(BigDecimal.ZERO); // Installation starts at 0

        // Set payment method reference and handle cari account creation
        if (normalizedPaymentMethod == PaymentMethod.CASH) {
            ticket.setPaymentMethod(PaymentMethod.CASH);
        } else if (normalizedPaymentMethod == PaymentMethod.CREDIT_CARD) {
            ticket.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        } else if (normalizedPaymentMethod == PaymentMethod.CURRENT_ACCOUNT) {
            ticket.setPaymentMethod(PaymentMethod.CURRENT_ACCOUNT);

            // Add to current account (create if not exists)
            CurrentAccount account = currentAccountRepository
                    .findByCustomerIdAndCompanyId(customer.getId(), currentUser.getCompanyId())
                    .orElseGet(() -> {
                        CurrentAccount newAccount = CurrentAccount.builder()
                                .companyId(currentUser.getCompanyId())
                                .customer(customer)
                                .balance(BigDecimal.ZERO)
                                .build();
                        return currentAccountRepository.save(newAccount);
                    });

            // Add debt (positive balance = customer owes us)
            account.setBalance(account.getBalance().add(salePrice));
            currentAccountRepository.save(account);
        }

        ServiceTicket savedTicket = serviceTicketRepository.save(ticket);

        // 6. Decrease stock
        device.setQuantity(device.getQuantity() - 1);
        commercialDeviceRepository.save(device);

        // 7. Log the sale
        auditLogService.log("SALE", "DEVICE", device.getId(),
                "Cihaz satıldı: " + device.getBrand() + " " + device.getModel() + " | Müşteri: " + customer.getName()
                        + " | Fiyat: " + salePrice + " ₺ | Ödeme: " + paymentMethod);

        return SaleResponseDTO.builder()
                .deviceId(device.getId())
                .serviceTicketId(savedTicket.getId())
                .message("Satış başarılı! Servis Fişi #" + savedTicket.getId() + " oluşturuldu.")
                .success(true)
                .build();
    }

    private CommercialDeviceDTO mapToDTO(CommercialDevice device, boolean hidePrices) {
        CommercialDeviceDTO.CommercialDeviceDTOBuilder builder = CommercialDeviceDTO.builder()
                .id(device.getId())
                .companyId(device.getCompanyId())
                .brand(device.getBrand())
                .model(device.getModel())
                .btu(device.getBtu())
                .gasType(device.getGasType())
                .quantity(device.getQuantity())
                .sellingPrice(device.getSellingPrice());

        if (device.getDeviceType() != null) {
            builder.deviceTypeId(device.getDeviceType().getId());
            builder.deviceTypeName(device.getDeviceType().getName());
        }

        if (hidePrices) {
            builder.buyingPrice(null);
            builder.formattedProfit("-");
        } else {
            builder.buyingPrice(device.getBuyingPrice());
            if (device.getSellingPrice() != null && device.getBuyingPrice() != null) {
                BigDecimal profit = device.getSellingPrice().subtract(device.getBuyingPrice());
                builder.formattedProfit(String.format("%.2f ₺", profit));
            } else {
                builder.formattedProfit("-");
            }
        }

        return builder.build();
    }

    private boolean isTechnicianRole() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElse(null);
        return user != null && "TECHNICIAN".equals(user.getRole());
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private void validateDevice(CommercialDeviceDTO dto) {
        if (dto == null || dto.getBrand() == null || dto.getBrand().isBlank()
                || dto.getModel() == null || dto.getModel().isBlank()) {
            throw new IllegalArgumentException("Marka ve model zorunludur.");
        }
        if (dto.getQuantity() == null || dto.getQuantity() < 0) {
            throw new IllegalArgumentException("Cihaz stoğu negatif olamaz.");
        }
        if (dto.getBuyingPrice() == null || dto.getBuyingPrice().compareTo(BigDecimal.ZERO) < 0
                || dto.getSellingPrice() == null || dto.getSellingPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Cihaz alış ve satış fiyatları negatif olamaz.");
        }
    }
}
