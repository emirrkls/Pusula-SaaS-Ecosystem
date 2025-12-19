package com.pusula.backend.service;

import com.pusula.backend.dto.PublicServiceRequestDTO;
import com.pusula.backend.dto.ServiceTicketDTO;
import com.pusula.backend.dto.ServiceUsedPartDTO;
import com.pusula.backend.entity.CurrentAccount;
import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.PaymentMethod;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.CurrentAccountRepository;
import com.pusula.backend.repository.CustomerRepository;
import com.pusula.backend.repository.InventoryRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import com.pusula.backend.repository.ServiceUsedPartRepository;
import com.pusula.backend.repository.UserRepository;
import com.pusula.backend.repository.VehicleStockRepository;
import com.pusula.backend.entity.VehicleStock;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ServiceTicketService {

    private final ServiceTicketRepository repository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final InventoryRepository inventoryRepository;
    private final ServiceUsedPartRepository serviceUsedPartRepository;
    private final AuditLogService auditLogService;
    private final CurrentAccountRepository currentAccountRepository;
    private final VehicleStockRepository vehicleStockRepository;

    public ServiceTicketService(ServiceTicketRepository repository,
            CustomerRepository customerRepository,
            UserRepository userRepository,
            InventoryRepository inventoryRepository,
            ServiceUsedPartRepository serviceUsedPartRepository,
            AuditLogService auditLogService,
            CurrentAccountRepository currentAccountRepository,
            VehicleStockRepository vehicleStockRepository) {
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.inventoryRepository = inventoryRepository;
        this.serviceUsedPartRepository = serviceUsedPartRepository;
        this.auditLogService = auditLogService;
        this.currentAccountRepository = currentAccountRepository;
        this.vehicleStockRepository = vehicleStockRepository;
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    public List<ServiceTicketDTO> getAllTickets() {
        User user = getCurrentUser();
        return repository.findByCompanyId(user.getCompanyId()).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public ServiceTicketDTO createTicket(ServiceTicketDTO dto) {
        User user = getCurrentUser();
        ServiceTicket ticket = ServiceTicket.builder()
                .companyId(user.getCompanyId())
                .customerId(dto.getCustomerId())
                .assignedTechnicianId(dto.getAssignedTechnicianId())
                .status(ServiceTicket.TicketStatus.PENDING)
                .scheduledDate(dto.getScheduledDate())
                .description(dto.getDescription())
                .notes(dto.getNotes())
                .build();

        if (dto.getStatus() != null) {
            ticket.setStatus(dto.getStatus());
        }

        ServiceTicket saved = repository.save(ticket);

        // Log ticket creation
        auditLogService.log(
                "CREATE",
                "TICKET",
                saved.getId(),
                "Yeni servis fişi oluşturuldu: " + saved.getDescription());

        return mapToDTO(saved);
    }

    public ServiceTicketDTO updateTicket(Long id, ServiceTicketDTO dto) {
        User user = getCurrentUser();
        ServiceTicket ticket = repository.findById(id)
                .filter(t -> t.getCompanyId().equals(user.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        // RBAC: If user is TECHNICIAN, they can only update if assigned to them
        if ("TECHNICIAN".equals(user.getRole())) {
            if (ticket.getAssignedTechnicianId() == null || !ticket.getAssignedTechnicianId().equals(user.getId())) {
                throw new RuntimeException("Access Denied: You can only update tickets assigned to you.");
            }
            // Prevent Technician from re-assigning the ticket
            if (dto.getAssignedTechnicianId() != null
                    && !dto.getAssignedTechnicianId().equals(ticket.getAssignedTechnicianId())) {
                throw new RuntimeException("Access Denied: Technicians cannot re-assign tickets.");
            }
        }

        // Track old values for audit logging
        String oldStatus = ticket.getStatus() != null ? getStatusInTurkish(ticket.getStatus()) : null;
        Long oldTechnicianId = ticket.getAssignedTechnicianId();

        // Apply updates
        if (dto.getStatus() != null && !dto.getStatus().equals(ticket.getStatus())) {
            String newStatus = getStatusInTurkish(dto.getStatus());
            auditLogService.log(
                    "UPDATE",
                    "TICKET",
                    ticket.getId(),
                    "Durum değişti",
                    oldStatus,
                    newStatus);
            ticket.setStatus(dto.getStatus());
        }

        if (dto.getAssignedTechnicianId() != null && !dto.getAssignedTechnicianId().equals(oldTechnicianId)) {
            auditLogService.log(
                    "UPDATE",
                    "TICKET",
                    ticket.getId(),
                    "Teknisyen atandı: ID " + dto.getAssignedTechnicianId());
            ticket.setAssignedTechnicianId(dto.getAssignedTechnicianId());
        }

        if (dto.getScheduledDate() != null)
            ticket.setScheduledDate(dto.getScheduledDate());
        if (dto.getNotes() != null)
            ticket.setNotes(dto.getNotes());

        ServiceTicket saved = repository.save(ticket);
        return mapToDTO(saved);
    }

    public ServiceTicketDTO createPublicTicket(PublicServiceRequestDTO dto) {
        // 1. Find or Create Customer
        Customer customer = customerRepository.findByCompanyId(dto.getCompanyId()).stream()
                .filter(c -> c.getPhone() != null && c.getPhone().equals(dto.getCustomerPhone()))
                .findFirst()
                .orElseGet(() -> {
                    Customer newCustomer = Customer.builder()
                            .companyId(dto.getCompanyId())
                            .name(dto.getCustomerName())
                            .phone(dto.getCustomerPhone())
                            .address(dto.getCustomerAddress())
                            .build();
                    return customerRepository.save(newCustomer);
                });

        // 2. Create Ticket
        ServiceTicket ticket = ServiceTicket.builder()
                .companyId(dto.getCompanyId())
                .customerId(customer.getId())
                .status(ServiceTicket.TicketStatus.PENDING)
                .description(dto.getDescription())
                .build();

        ServiceTicket saved = repository.save(ticket);
        return mapToDTO(saved);
    }

    public ServiceTicketDTO assignTechnician(Long ticketId, Long technicianId) {
        User currentUser = getCurrentUser();
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        User technician = userRepository.findById(technicianId)
                .filter(u -> u.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Technician not found or access denied"));

        ticket.setAssignedTechnicianId(technician.getId());
        ticket.setStatus(ServiceTicket.TicketStatus.ASSIGNED);

        ServiceTicket saved = repository.save(ticket);

        // Log technician assignment
        auditLogService.log(
                "UPDATE",
                "TICKET",
                saved.getId(),
                "Teknisyen atandı: " + technician.getFullName());

        return mapToDTO(saved);
    }

    @Transactional
    public ServiceUsedPartDTO addUsedPart(Long ticketId, ServiceUsedPartDTO dto) {
        User currentUser = getCurrentUser();
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        com.pusula.backend.entity.Inventory inventory = inventoryRepository.findById(dto.getInventoryId())
                .filter(i -> i.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Inventory item not found"));

        Long sourceVehicleId = null;
        int quantityNeeded = dto.getQuantityUsed();

        // Check if we should use vehicle stock
        if (dto.getSourceVehicleId() != null) {
            VehicleStock vehicleStock = vehicleStockRepository
                    .findByVehicleIdAndInventoryId(dto.getSourceVehicleId(), dto.getInventoryId())
                    .orElse(null);

            if (vehicleStock != null && vehicleStock.getQuantity() >= quantityNeeded) {
                // Deduct from vehicle stock
                vehicleStock.setQuantity(vehicleStock.getQuantity() - quantityNeeded);
                vehicleStockRepository.save(vehicleStock);
                sourceVehicleId = dto.getSourceVehicleId();

                // Also deduct from main inventory (vehicle parts are part of total)
                inventory.setQuantity(inventory.getQuantity() - quantityNeeded);
                inventoryRepository.save(inventory);
            } else {
                // Vehicle doesn't have enough, fall back to main inventory
                if (inventory.getQuantity() < quantityNeeded) {
                    throw new RuntimeException("Insufficient stock for item: " + inventory.getPartName());
                }
                inventory.setQuantity(inventory.getQuantity() - quantityNeeded);
                inventoryRepository.save(inventory);
            }
        } else {
            // No vehicle specified, use main inventory directly
            if (inventory.getQuantity() < quantityNeeded) {
                throw new RuntimeException("Insufficient stock for item: " + inventory.getPartName());
            }
            inventory.setQuantity(inventory.getQuantity() - quantityNeeded);
            inventoryRepository.save(inventory);
        }

        // Create Used Part record with source tracking
        com.pusula.backend.entity.ServiceUsedPart usedPart = com.pusula.backend.entity.ServiceUsedPart.builder()
                .companyId(currentUser.getCompanyId())
                .serviceTicket(ticket)
                .inventory(inventory)
                .quantityUsed(dto.getQuantityUsed())
                .sellingPriceSnapshot(inventory.getSellPrice())
                .sourceVehicleId(sourceVehicleId)
                .build();

        com.pusula.backend.entity.ServiceUsedPart saved = serviceUsedPartRepository.save(usedPart);

        return new ServiceUsedPartDTO(
                saved.getId(),
                saved.getServiceTicket().getId(),
                saved.getInventory().getId(),
                saved.getInventory().getPartName(),
                saved.getQuantityUsed(),
                saved.getSellingPriceSnapshot(),
                saved.getSourceVehicleId());
    }

    public List<ServiceUsedPartDTO> getUsedParts(Long ticketId) {
        User currentUser = getCurrentUser();
        // Verify access to ticket
        repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        return serviceUsedPartRepository.findByServiceTicketId(ticketId).stream()
                .map(part -> new ServiceUsedPartDTO(
                        part.getId(),
                        part.getServiceTicket().getId(),
                        part.getInventory().getId(),
                        part.getInventory().getPartName(),
                        part.getQuantityUsed(),
                        part.getSellingPriceSnapshot(),
                        part.getSourceVehicleId()))
                .collect(Collectors.toList());
    }

    public ServiceTicketDTO completeService(Long ticketId, BigDecimal collectedAmount, PaymentMethod paymentMethod) {
        User currentUser = getCurrentUser();
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        ticket.setStatus(ServiceTicket.TicketStatus.COMPLETED);
        ticket.setCollectedAmount(collectedAmount);
        ticket.setPaymentMethod(paymentMethod != null ? paymentMethod : PaymentMethod.CASH);

        // If CURRENT_ACCOUNT, create/update debt record (not liquid cash)
        if (paymentMethod == PaymentMethod.CURRENT_ACCOUNT && ticket.getCustomerId() != null) {
            // Fetch customer entity
            Customer customer = customerRepository.findById(ticket.getCustomerId())
                    .orElseThrow(() -> new RuntimeException("Customer not found"));

            CurrentAccount account = currentAccountRepository
                    .findByCustomerId(ticket.getCustomerId())
                    .orElseGet(() -> {
                        CurrentAccount newAccount = CurrentAccount.builder()
                                .companyId(ticket.getCompanyId())
                                .customer(customer)
                                .balance(BigDecimal.ZERO)
                                .build();
                        return currentAccountRepository.save(newAccount);
                    });

            // ADD to debt (positive balance = customer owes us)
            account.setBalance(account.getBalance().add(collectedAmount));
            currentAccountRepository.save(account);
        }

        return mapToDTO(repository.save(ticket));
    }

    @Transactional
    public ServiceTicketDTO cancelService(Long ticketId) {
        User currentUser = getCurrentUser();
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        // Return all used parts back to inventory
        List<com.pusula.backend.entity.ServiceUsedPart> usedParts = serviceUsedPartRepository
                .findByServiceTicketId(ticketId);

        for (com.pusula.backend.entity.ServiceUsedPart usedPart : usedParts) {
            com.pusula.backend.entity.Inventory inventory = usedPart.getInventory();
            // Add the quantity back to main inventory
            inventory.setQuantity(inventory.getQuantity() + usedPart.getQuantityUsed());
            inventoryRepository.save(inventory);

            // If part came from a vehicle, also restore vehicle stock
            if (usedPart.getSourceVehicleId() != null) {
                VehicleStock vehicleStock = vehicleStockRepository
                        .findByVehicleIdAndInventoryId(usedPart.getSourceVehicleId(), inventory.getId())
                        .orElse(null);
                if (vehicleStock != null) {
                    vehicleStock.setQuantity(vehicleStock.getQuantity() + usedPart.getQuantityUsed());
                    vehicleStockRepository.save(vehicleStock);
                }
            }

            // Log the return
            auditLogService.log(
                    "RETURN",
                    "INVENTORY",
                    inventory.getId(),
                    "Parça iade edildi (iptal): " + inventory.getPartName() + " x" + usedPart.getQuantityUsed());
        }

        // Delete the used parts records (soft delete)
        for (com.pusula.backend.entity.ServiceUsedPart usedPart : usedParts) {
            serviceUsedPartRepository.delete(usedPart);
        }

        ticket.setStatus(ServiceTicket.TicketStatus.CANCELLED);

        // Log cancellation
        auditLogService.log(
                "CANCEL",
                "TICKET",
                ticket.getId(),
                "Servis fişi iptal edildi");

        return mapToDTO(repository.save(ticket));
    }

    public ServiceTicketDTO createFollowUpTicket(Long originalTicketId) {
        User currentUser = getCurrentUser();

        // Fetch original ticket
        ServiceTicket originalTicket = repository.findById(originalTicketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Original ticket not found or access denied"));

        // Verify original ticket is COMPLETED
        if (originalTicket.getStatus() != ServiceTicket.TicketStatus.COMPLETED) {
            throw new RuntimeException("Can only create follow-up for completed tickets");
        }

        // Create new ticket
        ServiceTicket followUpTicket = new ServiceTicket();
        followUpTicket.setCompanyId(currentUser.getCompanyId());
        followUpTicket.setCustomerId(originalTicket.getCustomerId());
        followUpTicket.setDescription("RECALL: " + originalTicket.getDescription());
        followUpTicket.setStatus(ServiceTicket.TicketStatus.PENDING);
        followUpTicket.setParentTicketId(originalTicketId);

        // Check if warranty call (completed less than 30 days ago)
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        boolean isWarranty = originalTicket.getUpdatedAt() != null &&
                originalTicket.getUpdatedAt().isAfter(thirtyDaysAgo);
        followUpTicket.setWarrantyCall(isWarranty);

        // Save and return
        ServiceTicket saved = repository.save(followUpTicket);
        return mapToDTO(saved);
    }

    private ServiceTicketDTO mapToDTO(ServiceTicket ticket) {
        // Fetch customer name if customerId exists
        String customerName = null;
        if (ticket.getCustomerId() != null) {
            customerName = customerRepository.findById(ticket.getCustomerId())
                    .map(Customer::getName)
                    .orElse("Unknown Customer");
        }

        return ServiceTicketDTO.builder()
                .id(ticket.getId())
                .customerId(ticket.getCustomerId())
                .customerName(customerName)
                .assignedTechnicianId(ticket.getAssignedTechnicianId())
                .status(ticket.getStatus())
                .scheduledDate(ticket.getScheduledDate())
                .description(ticket.getDescription())
                .notes(ticket.getNotes())
                .collectedAmount(ticket.getCollectedAmount())
                .createdAt(ticket.getCreatedAt())
                .parentTicketId(ticket.getParentTicketId())
                .isWarrantyCall(ticket.isWarrantyCall())
                .paymentMethod(ticket.getPaymentMethod())
                .build();
    }

    private String getStatusInTurkish(ServiceTicket.TicketStatus status) {
        switch (status) {
            case PENDING:
                return "Beklemede";
            case ASSIGNED:
                return "Atandı";
            case IN_PROGRESS:
                return "Devam Ediyor";
            case COMPLETED:
                return "Tamamlandı";
            case CANCELLED:
                return "İptal Edildi";
            default:
                return status.toString();
        }
    }
}
