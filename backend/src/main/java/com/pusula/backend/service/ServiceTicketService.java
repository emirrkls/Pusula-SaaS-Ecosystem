package com.pusula.backend.service;

import com.pusula.backend.dto.PublicServiceRequestDTO;
import com.pusula.backend.dto.ServiceTicketDTO;
import com.pusula.backend.dto.ServiceUsedPartDTO;
import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.CustomerRepository;
import com.pusula.backend.repository.InventoryRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import com.pusula.backend.repository.ServiceUsedPartRepository;
import com.pusula.backend.repository.UserRepository;

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

    public ServiceTicketService(ServiceTicketRepository repository,
            CustomerRepository customerRepository,
            UserRepository userRepository,
            InventoryRepository inventoryRepository,
            ServiceUsedPartRepository serviceUsedPartRepository) {
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.inventoryRepository = inventoryRepository;
        this.serviceUsedPartRepository = serviceUsedPartRepository;
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
        return mapToDTO(saved);
    }

    public ServiceTicketDTO updateTicket(Long id, ServiceTicketDTO dto) {
        User user = getCurrentUser();
        ServiceTicket ticket = repository.findById(id)
                .filter(t -> t.getCompanyId().equals(user.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        if (dto.getStatus() != null)
            ticket.setStatus(dto.getStatus());
        if (dto.getAssignedTechnicianId() != null)
            ticket.setAssignedTechnicianId(dto.getAssignedTechnicianId());
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

        return mapToDTO(repository.save(ticket));
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

        if (inventory.getQuantity() < dto.getQuantityUsed()) {
            throw new RuntimeException("Insufficient stock for item: " + inventory.getPartName());
        }

        // Deduct stock
        inventory.setQuantity(inventory.getQuantity() - dto.getQuantityUsed());
        inventoryRepository.save(inventory);

        // Create Used Part record
        com.pusula.backend.entity.ServiceUsedPart usedPart = com.pusula.backend.entity.ServiceUsedPart.builder()
                .companyId(currentUser.getCompanyId())
                .serviceTicket(ticket)
                .inventory(inventory)
                .quantityUsed(dto.getQuantityUsed())
                .sellingPriceSnapshot(inventory.getSellPrice())
                .build();

        com.pusula.backend.entity.ServiceUsedPart saved = serviceUsedPartRepository.save(usedPart);

        return new ServiceUsedPartDTO(
                saved.getId(),
                saved.getServiceTicket().getId(),
                saved.getInventory().getId(),
                saved.getInventory().getPartName(),
                saved.getQuantityUsed(),
                saved.getSellingPriceSnapshot());
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
                        part.getSellingPriceSnapshot()))
                .collect(Collectors.toList());
    }

    public ServiceTicketDTO completeService(Long ticketId, BigDecimal collectedAmount) {
        User currentUser = getCurrentUser();
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        ticket.setStatus(ServiceTicket.TicketStatus.COMPLETED);
        ticket.setCollectedAmount(collectedAmount);

        return mapToDTO(repository.save(ticket));
    }

    public ServiceTicketDTO cancelService(Long ticketId) {
        User currentUser = getCurrentUser();
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        ticket.setStatus(ServiceTicket.TicketStatus.CANCELLED);

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
        return ServiceTicketDTO.builder()
                .id(ticket.getId())
                .customerId(ticket.getCustomerId())
                .assignedTechnicianId(ticket.getAssignedTechnicianId())
                .status(ticket.getStatus())
                .scheduledDate(ticket.getScheduledDate())
                .description(ticket.getDescription())
                .notes(ticket.getNotes())
                .collectedAmount(ticket.getCollectedAmount())
                .createdAt(ticket.getCreatedAt())
                .parentTicketId(ticket.getParentTicketId())
                .isWarrantyCall(ticket.isWarrantyCall())
                .build();
    }
}
